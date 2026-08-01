package com.videogenerator.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.videogenerator.config.Configuration;
import com.videogenerator.model.ApiProvider;
import com.videogenerator.model.ContentIdea;
import com.videogenerator.model.NicheData;
import com.videogenerator.model.VideoMetadata;
import com.videogenerator.model.VoiceoverScript;
import com.videogenerator.util.ApiException;
import com.videogenerator.util.Constants;
import com.videogenerator.util.HttpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client for OpenAI GPT-4 API for text/metadata generation
 */
public class OpenAiGptClient {
    private static final Logger logger = LoggerFactory.getLogger(OpenAiGptClient.class);
    private final Configuration config;
    private final Gson gson;
    private final String apiKey;

    private static final String DEFAULT_MODEL = "gpt-4";
    private static final double DEFAULT_TEMPERATURE = 0.7;
    private static final int MAX_TOKENS = 500;

    public OpenAiGptClient() {
        this.config = Configuration.getInstance();
        this.gson = new Gson();
        this.apiKey = config.getOpenAiApiKey();

        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("OpenAI API key is not configured");
        }
    }

    /**
     * Generates video metadata (title, description, hashtags) based on video/music context
     */
    public VideoMetadata generateMetadata(String musicPrompt, String videoPrompt) throws ApiException {
        logger.info("Generating metadata for video content");

        String systemPrompt = "You are a YouTube Shorts expert specializing in creating engaging titles, " +
                "descriptions, and hashtags for short-form video content. Your goal is to maximize viewer " +
                "engagement and discoverability. Generate content in JSON format only.";

        String userPrompt = String.format(
                "Create YouTube Shorts metadata for a video with the following characteristics:\n" +
                        "Music: %s\n" +
                        "Video: %s\n\n" +
                        "Generate a JSON response with this exact structure:\n" +
                        "{\n" +
                        "  \"title\": \"[Engaging title under 100 characters]\",\n" +
                        "  \"description\": \"[Compelling description with emojis, 100-300 characters]\",\n" +
                        "  \"hashtags\": [\"#hashtag1\", \"#hashtag2\", \"#hashtag3\", ...]\n" +
                        "}\n\n" +
                        "Requirements:\n" +
                        "- Title must be catchy, under 100 characters, and include keywords\n" +
                        "- Description should be engaging with 2-3 emojis\n" +
                        "- Include 5-10 relevant hashtags (trending + niche)\n" +
                        "- All hashtags must start with #\n" +
                        "- Return ONLY valid JSON, no additional text",
                musicPrompt, videoPrompt
        );

        try {
            String response = chatCompletion(systemPrompt, userPrompt);

            // Parse JSON response
            // Remove any markdown code blocks if present
            response = response.trim();
            if (response.startsWith("```json")) {
                response = response.substring(7);
            }
            if (response.startsWith("```")) {
                response = response.substring(3);
            }
            if (response.endsWith("```")) {
                response = response.substring(0, response.length() - 3);
            }
            response = response.trim();

            JsonObject metadataJson = gson.fromJson(response, JsonObject.class);

            VideoMetadata metadata = new VideoMetadata();
            metadata.setTitle(metadataJson.get("title").getAsString());
            metadata.setDescription(metadataJson.get("description").getAsString());

            JsonArray hashtagsArray = metadataJson.getAsJsonArray("hashtags");
            List<String> hashtags = new java.util.ArrayList<>();
            for (int i = 0; i < hashtagsArray.size(); i++) {
                String tag = hashtagsArray.get(i).getAsString();
                if (!tag.startsWith("#")) {
                    tag = "#" + tag;
                }
                hashtags.add(tag);
            }
            metadata.setHashtags(hashtags);

            logger.info("Metadata generated: {}", metadata);

            // Validate metadata
            if (!metadata.isValid()) {
                throw new ApiException(ApiProvider.OPENAI_GPT, "Generated metadata is invalid");
            }

            return metadata;

        } catch (Exception e) {
            if (e instanceof ApiException) {
                throw (ApiException) e;
            }
            logger.error("Error generating metadata", e);
            throw new ApiException(ApiProvider.OPENAI_GPT, "Error generating metadata", e);
        }
    }

    /**
     * Generates a creative prompt for music generation
     */
    public String generateMusicPrompt() throws ApiException {
        logger.info("Generating music prompt");

        String systemPrompt = "You are a creative music director specializing in electronic and modern music styles " +
                "suitable for YouTube Shorts. Generate short, descriptive music prompts.";

        String userPrompt = "Generate a creative music prompt for a 20-second instrumental track suitable for " +
                "YouTube Shorts. The prompt should describe the genre, mood, tempo, and style. " +
                "Keep it concise (20-50 words). Return ONLY the prompt text, no explanations.";

        try {
            return chatCompletion(systemPrompt, userPrompt);
        } catch (Exception e) {
            logger.error("Error generating music prompt", e);
            throw new ApiException(ApiProvider.OPENAI_GPT, "Error generating music prompt", e);
        }
    }

    /**
     * Generates a creative prompt for video generation
     */
    public String generateVideoPrompt(String musicMood) throws ApiException {
        logger.info("Generating video prompt based on music mood: {}", musicMood);

        String systemPrompt = "You are a creative video director specializing in short-form vertical video content " +
                "for social media. Generate vivid, detailed video prompts optimized for AI video generation.";

        String userPrompt = String.format(
                "Generate a video prompt for a 15-second vertical video (9:16 aspect ratio) that matches this music mood: %s.\n" +
                        "The prompt should describe:\n" +
                        "- Visual style and atmosphere\n" +
                        "- Colors and lighting\n" +
                        "- Movement and camera angles\n" +
                        "- Overall aesthetic\n\n" +
                        "Keep it concise (30-70 words) and optimized for AI video generation. " +
                        "Return ONLY the prompt text, no explanations.",
                musicMood
        );

        try {
            return chatCompletion(systemPrompt, userPrompt);
        } catch (Exception e) {
            logger.error("Error generating video prompt", e);
            throw new ApiException(ApiProvider.OPENAI_GPT, "Error generating video prompt", e);
        }
    }

    /**
     * Generates viral content ideas for a specific niche.
     * Returns 10 SEO-optimized video ideas with titles, hooks, and hashtags.
     *
     * @param niche the niche to generate ideas for
     * @param count number of ideas to generate
     * @return list of content ideas
     * @throws ApiException if generation fails
     */
    public List<ContentIdea> generateViralIdeas(NicheData niche, int count) throws ApiException {
        logger.info("Generating {} viral ideas for niche: {}", count, niche.getTopic());

        String systemPrompt = "You are a YouTube Shorts viral content strategist. You specialize in " +
                "creating SEO-optimized, engagement-focused video ideas that go viral. You understand " +
                "platform algorithms, trending topics, and what makes people click and watch. " +
                "Generate content in JSON format only.";

        String userPrompt = String.format(
                "Generate %d viral YouTube Shorts video ideas for the niche: \"%s\"\n\n" +
                "Context:\n" +
                "- Keywords: %s\n" +
                "- Viral Potential: %.2f/1.0\n" +
                "- Competition: %.2f/1.0\n\n" +
                "For EACH idea, provide:\n" +
                "1. A catchy, SEO-optimized title (under 100 characters) with numbers, curiosity, or emotions\n" +
                "2. An emotional hook (the first 3-second text overlay to grab attention)\n" +
                "3. A brief description (50-100 characters)\n" +
                "4. 5-7 trending hashtags (mix of popular + niche)\n" +
                "5. Estimated CTR score (0.0 to 1.0)\n\n" +
                "Return a JSON array with this exact structure:\n" +
                "[\n" +
                "  {\n" +
                "    \"title\": \"5 Secrets Nobody Tells You About [Topic]\",\n" +
                "    \"hook\": \"This will blow your mind!\",\n" +
                "    \"description\": \"Discover hidden secrets that will change everything\",\n" +
                "    \"hashtags\": [\"#shorts\", \"#viral\", \"#%s\"],\n" +
                "    \"estimated_ctr\": 0.85\n" +
                "  }\n" +
                "]\n\n" +
                "Guidelines:\n" +
                "- Use power words: Secret, Hack, Never, Always, Mistake, Truth, Hidden\n" +
                "- Include numbers: Top 5, 3 Ways, 7 Tips\n" +
                "- Create curiosity gaps: \"What happens next will shock you\"\n" +
                "- Make hooks emotional: fear, excitement, curiosity, surprise\n" +
                "- Ensure titles are clickable but not clickbait\n" +
                "- All hashtags must start with #\n" +
                "- Estimated CTR should reflect title quality (0.5-0.95 range)\n\n" +
                "Return ONLY valid JSON array, no additional text.",
                count, niche.getTopic(), niche.getKeywordsAsString(),
                niche.getViralPotential(), niche.getCompetitionScore(), niche.getTopic().toLowerCase()
        );

        try {
            String response = chatCompletion(systemPrompt, userPrompt);

            // Clean response
            response = response.trim();
            if (response.startsWith("```json")) {
                response = response.substring(7);
            }
            if (response.startsWith("```")) {
                response = response.substring(3);
            }
            if (response.endsWith("```")) {
                response = response.substring(0, response.length() - 3);
            }
            response = response.trim();

            // Parse JSON array with defensive validation
            JsonArray ideasArray = gson.fromJson(response, JsonArray.class);
            List<ContentIdea> ideas = new java.util.ArrayList<>();

            for (int i = 0; i < ideasArray.size(); i++) {
                try {
                    JsonObject ideaJson = ideasArray.get(i).getAsJsonObject();

                    // Validate required fields exist
                    if (!ideaJson.has("title") || !ideaJson.has("hook") ||
                        !ideaJson.has("description") || !ideaJson.has("hashtags") ||
                        !ideaJson.has("estimated_ctr")) {
                        logger.warn("Skipping idea {} due to missing required fields", i);
                        continue;
                    }

                    ContentIdea idea = new ContentIdea();
                    idea.setTitle(ideaJson.get("title").getAsString());
                    idea.setHook(ideaJson.get("hook").getAsString());
                    idea.setDescription(ideaJson.get("description").getAsString());
                    idea.setEstimatedCtr(ideaJson.get("estimated_ctr").getAsDouble());

                    // Parse hashtags
                    JsonArray hashtagsArray = ideaJson.getAsJsonArray("hashtags");
                    List<String> hashtags = new java.util.ArrayList<>();
                    for (int j = 0; j < hashtagsArray.size(); j++) {
                        String tag = hashtagsArray.get(j).getAsString();
                        if (!tag.startsWith("#")) {
                            tag = "#" + tag;
                        }
                        hashtags.add(tag);
                    }
                    idea.setHashtags(hashtags);

                    ideas.add(idea);

                } catch (Exception e) {
                    logger.error("Failed to parse idea {}: {}", i, e.getMessage());
                    // Skip this idea and continue with others
                    continue;
                }
            }

            // Validate we got at least some ideas
            if (ideas.isEmpty()) {
                throw new ApiException(ApiProvider.OPENAI_GPT, "GPT returned no valid ideas");
            }

            logger.info("Generated {} viral ideas for niche: {}", ideas.size(), niche.getTopic());
            return ideas;

        } catch (Exception e) {
            logger.error("Error generating viral ideas", e);
            throw new ApiException(ApiProvider.OPENAI_GPT, "Error generating viral ideas", e);
        }
    }

    /**
     * Generates a voiceover script with custom prompt.
     * Public method for ScriptWriter to use.
     *
     * @param systemPrompt system prompt
     * @param userPrompt user prompt
     * @return generated script text
     * @throws ApiException if generation fails
     */
    public String generateCustomScript(String systemPrompt, String userPrompt) throws ApiException {
        return chatCompletion(systemPrompt, userPrompt);
    }

    /**
     * Makes a chat completion request to GPT API
     */
    private String chatCompletion(String systemMessage, String userMessage) throws ApiException {
        try {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", DEFAULT_MODEL);
            requestBody.addProperty("temperature", DEFAULT_TEMPERATURE);
            requestBody.addProperty("max_tokens", MAX_TOKENS);

            JsonArray messages = new JsonArray();

            JsonObject systemMsg = new JsonObject();
            systemMsg.addProperty("role", "system");
            systemMsg.addProperty("content", systemMessage);
            messages.add(systemMsg);

            JsonObject userMsg = new JsonObject();
            userMsg.addProperty("role", "user");
            userMsg.addProperty("content", userMessage);
            messages.add(userMsg);

            requestBody.add("messages", messages);

            Map<String, String> headers = createHeaders();
            String url = Constants.OPENAI_API_BASE_URL + Constants.OPENAI_CHAT_ENDPOINT;

            logger.debug("GPT API request: {}", requestBody);

            HttpResponse<String> response = HttpUtil.post(url, gson.toJson(requestBody), headers);

            if (!HttpUtil.isSuccessful(response.statusCode())) {
                logger.error("GPT API error response: {}", response.body());
                throw new ApiException(
                        ApiProvider.OPENAI_GPT,
                        "Chat completion failed: " + response.body(),
                        response.statusCode()
                );
            }

            JsonObject responseJson = gson.fromJson(response.body(), JsonObject.class);
            String content = responseJson.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString();

            logger.debug("GPT response: {}", content);
            return content.trim();

        } catch (Exception e) {
            if (e instanceof ApiException) {
                throw (ApiException) e;
            }
            throw new ApiException(ApiProvider.OPENAI_GPT, "Error in chat completion", e);
        }
    }

    /**
     * Creates HTTP headers with authorization
     */
    private Map<String, String> createHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + apiKey);
        headers.put("Content-Type", "application/json");
        return headers;
    }

    /**
     * Validates API key
     */
    public boolean validateApiKey() {
        try {
            String url = Constants.OPENAI_API_BASE_URL + "/models";
            Map<String, String> headers = createHeaders();
            HttpResponse<String> response = HttpUtil.get(url, headers);
            return HttpUtil.isSuccessful(response.statusCode());
        } catch (Exception e) {
            logger.error("API key validation failed", e);
            return false;
        }
    }
}
