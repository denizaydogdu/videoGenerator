package com.videogenerator.service;

import com.videogenerator.api.OpenAiGptClient;
import com.videogenerator.model.ContentIdea;
import com.videogenerator.model.NicheData;
import com.videogenerator.repository.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Generates viral content ideas for YouTube Shorts.
 * Creates SEO-optimized titles, emotional hooks, and engagement-focused concepts.
 */
public class IdeaGenerator {
    private static final Logger logger = LoggerFactory.getLogger(IdeaGenerator.class);
    private final OpenAiGptClient gptClient;
    private final DatabaseManager dbManager;

    public IdeaGenerator(OpenAiGptClient gptClient) {
        this.gptClient = gptClient;
        this.dbManager = DatabaseManager.getInstance();
    }

    /**
     * Generates viral content ideas for a specific niche.
     *
     * @param niche the niche to generate ideas for
     * @param count number of ideas to generate (typically 10)
     * @return list of content ideas
     */
    public List<ContentIdea> generateIdeas(NicheData niche, int count) {
        logger.info("Generating {} viral content ideas for niche: {}", count, niche.getTopic());

        try {
            // Use GPT-4 to generate ideas
            List<ContentIdea> ideas = gptClient.generateViralIdeas(niche, count);

            // Save ideas to database
            for (ContentIdea idea : ideas) {
                idea.setNicheId(niche.getId());
                saveIdea(idea);
            }

            logger.info("Generated {} content ideas for niche: {}", ideas.size(), niche.getTopic());
            return ideas;

        } catch (Exception e) {
            logger.error("Failed to generate ideas for niche: {}", niche.getTopic(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Gets a random content idea from the database.
     * Useful for quick content generation without niche analysis.
     *
     * @return random content idea
     */
    public ContentIdea getRandomIdea() {
        String sql = "SELECT * FROM content_ideas ORDER BY RANDOM() LIMIT 1";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                return mapResultSetToIdea(rs);
            }

        } catch (SQLException e) {
            logger.error("Failed to get random idea", e);
        }

        return null;
    }

    /**
     * Gets top-rated content ideas from the database.
     *
     * @param limit maximum number of ideas to return
     * @return list of top-rated ideas
     */
    public List<ContentIdea> getTopIdeas(int limit) {
        List<ContentIdea> ideas = new ArrayList<>();
        String sql = "SELECT * FROM content_ideas ORDER BY estimated_ctr DESC LIMIT ?";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ideas.add(mapResultSetToIdea(rs));
                }
            }

        } catch (SQLException e) {
            logger.error("Failed to get top ideas", e);
        }

        return ideas;
    }

    /**
     * Gets content ideas for a specific niche.
     *
     * @param nicheId the niche ID
     * @param limit maximum number of ideas
     * @return list of ideas for that niche
     */
    public List<ContentIdea> getIdeasForNiche(int nicheId, int limit) {
        List<ContentIdea> ideas = new ArrayList<>();
        String sql = "SELECT * FROM content_ideas WHERE niche_id = ? " +
                     "ORDER BY estimated_ctr DESC LIMIT ?";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, nicheId);
            stmt.setInt(2, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ideas.add(mapResultSetToIdea(rs));
                }
            }

        } catch (SQLException e) {
            logger.error("Failed to get ideas for niche", e);
        }

        return ideas;
    }

    /**
     * Selects the best idea from a list based on scoring criteria.
     *
     * @param ideas list of ideas to choose from
     * @return the best idea
     */
    public ContentIdea selectBestIdea(List<ContentIdea> ideas) {
        if (ideas == null || ideas.isEmpty()) {
            return null;
        }

        // Sort by estimated CTR (descending)
        ideas.sort((a, b) -> Double.compare(b.getEstimatedCtr(), a.getEstimatedCtr()));

        // Return top idea
        ContentIdea best = ideas.get(0);
        logger.info("Selected best idea: {} (CTR: {:.2f})", best.getTitle(), best.getEstimatedCtr());

        return best;
    }

    /**
     * Validates that a content idea meets quality standards.
     *
     * @param idea the idea to validate
     * @return true if idea is valid
     */
    public boolean validateIdea(ContentIdea idea) {
        if (idea == null) {
            return false;
        }

        // Check title
        if (!idea.isTitleValid()) {
            logger.warn("Invalid title: {}", idea.getTitle());
            return false;
        }

        // Check hook
        if (idea.getHook() == null || idea.getHook().length() < 10) {
            logger.warn("Hook too short or missing");
            return false;
        }

        // Check hashtags
        if (idea.getHashtags() == null || idea.getHashtags().isEmpty()) {
            logger.warn("No hashtags provided");
            return false;
        }

        return true;
    }

    /**
     * Regenerates ideas for a niche (useful if initial ideas weren't good).
     *
     * @param niche the niche
     * @param count number of new ideas
     * @return new list of ideas
     */
    public List<ContentIdea> regenerateIdeas(NicheData niche, int count) {
        logger.info("Regenerating {} ideas for niche: {}", count, niche.getTopic());

        // Delete old ideas for this niche
        deleteIdeasForNiche(niche.getId());

        // Generate fresh ideas
        return generateIdeas(niche, count);
    }

    /**
     * Saves a content idea to the database.
     *
     * @param idea the idea to save
     */
    private void saveIdea(ContentIdea idea) {
        String sql = "INSERT INTO content_ideas (niche_id, title, hook, description, " +
                     "hashtags, estimated_ctr, created_date) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, idea.getNicheId());
            stmt.setString(2, idea.getTitle());
            stmt.setString(3, idea.getHook());
            stmt.setString(4, idea.getDescription());
            stmt.setString(5, idea.getHashtagsAsString());
            stmt.setDouble(6, idea.getEstimatedCtr());
            stmt.setString(7, idea.getCreatedDate().toString());

            stmt.executeUpdate();
            logger.debug("Saved idea: {}", idea.getTitle());

        } catch (SQLException e) {
            logger.error("Failed to save idea", e);
        }
    }

    /**
     * Deletes all ideas for a specific niche.
     *
     * @param nicheId the niche ID
     */
    private void deleteIdeasForNiche(Integer nicheId) {
        if (nicheId == null) {
            return;
        }

        String sql = "DELETE FROM content_ideas WHERE niche_id = ?";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, nicheId);
            int deleted = stmt.executeUpdate();

            logger.info("Deleted {} old ideas for niche ID: {}", deleted, nicheId);

        } catch (SQLException e) {
            logger.error("Failed to delete ideas", e);
        }
    }

    /**
     * Maps SQL ResultSet to ContentIdea object.
     *
     * @param rs the result set
     * @return mapped content idea
     * @throws SQLException if mapping fails
     */
    private ContentIdea mapResultSetToIdea(ResultSet rs) throws SQLException {
        ContentIdea idea = new ContentIdea();
        idea.setId(rs.getInt("id"));
        idea.setNicheId(rs.getObject("niche_id", Integer.class));
        idea.setTitle(rs.getString("title"));
        idea.setHook(rs.getString("hook"));
        idea.setDescription(rs.getString("description"));

        String hashtagsStr = rs.getString("hashtags");
        if (hashtagsStr != null && !hashtagsStr.isEmpty()) {
            idea.setHashtags(Arrays.asList(hashtagsStr.split("\\s+")));
        }

        idea.setEstimatedCtr(rs.getDouble("estimated_ctr"));

        return idea;
    }

    /**
     * Gets statistics about generated ideas.
     *
     * @return statistics string
     */
    public String getStatistics() {
        String sql = "SELECT COUNT(*) as total, AVG(estimated_ctr) as avg_ctr, " +
                     "MAX(estimated_ctr) as max_ctr FROM content_ideas";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                int total = rs.getInt("total");
                double avgCtr = rs.getDouble("avg_ctr");
                double maxCtr = rs.getDouble("max_ctr");

                return String.format("Total Ideas: %d, Avg CTR: %.2f, Max CTR: %.2f",
                        total, avgCtr, maxCtr);
            }

        } catch (SQLException e) {
            logger.error("Failed to get statistics", e);
        }

        return "No statistics available";
    }

    /**
     * Cleans up old ideas (older than specified days).
     *
     * @param daysOld days to keep
     * @return number of deleted ideas
     */
    public int cleanupOldIdeas(int daysOld) {
        String sql = "DELETE FROM content_ideas WHERE created_date < datetime('now', '-' || ? || ' days')";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, daysOld);
            int deleted = stmt.executeUpdate();

            logger.info("Deleted {} old ideas (older than {} days)", deleted, daysOld);
            return deleted;

        } catch (SQLException e) {
            logger.error("Failed to cleanup old ideas", e);
            return 0;
        }
    }
}
