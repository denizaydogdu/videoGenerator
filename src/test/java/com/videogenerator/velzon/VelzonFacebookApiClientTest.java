package com.videogenerator.velzon;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VelzonFacebookApiClientTest {

    static class FakeHttp implements VelzonFacebookApiClient.Http {
        final List<String> calls = new ArrayList<>();
        String postFormResponse;
        String getResponse;
        RuntimeException postFormError;
        RuntimeException getError;

        @Override
        public String postForm(String url, Map<String, String> form) {
            calls.add("FORM " + url + " " + new java.util.TreeMap<>(form));
            if (postFormError != null) {
                throw postFormError;
            }
            return postFormResponse;
        }

        @Override
        public String get(String url) {
            calls.add("GET " + url);
            if (getError != null) {
                throw getError;
            }
            return getResponse;
        }
    }

    private VelzonFacebookApiClient client(FakeHttp http) {
        return new VelzonFacebookApiClient(http, "PAGE1", "TOKEN1");
    }

    @Test
    void createPostPostsUrlAndCaptionAndReturnsPostId() throws Exception {
        FakeHttp http = new FakeHttp();
        http.postFormResponse = "{\"id\":\"PHOTO1\",\"post_id\":\"PAGE1_POST1\"}";
        VelzonFacebookApiClient c = client(http);

        String postId = c.createPost("https://shorts.velzon.tr/img.png", "Merhaba dünya");

        assertEquals("PAGE1_POST1", postId);
        assertTrue(http.calls.get(0).contains("/PAGE1/photos"));
        assertTrue(http.calls.get(0).contains("url=https://shorts.velzon.tr/img.png"));
        assertTrue(http.calls.get(0).contains("caption=Merhaba dünya"));
        assertTrue(http.calls.get(0).contains("access_token=TOKEN1"));
    }

    @Test
    void createPostFallsBackToIdWhenPostIdMissing() throws Exception {
        FakeHttp http = new FakeHttp();
        http.postFormResponse = "{\"id\":\"PHOTO1\"}";
        VelzonFacebookApiClient c = client(http);

        String postId = c.createPost("https://x/y.png", "cap");

        assertEquals("PHOTO1", postId);
    }

    @Test
    void createPostThrowsWhenBothIdAndPostIdMissing() {
        FakeHttp http = new FakeHttp();
        http.postFormResponse = "{\"error\":{\"message\":\"bad request\"}}";
        VelzonFacebookApiClient c = client(http);

        assertThrows(IllegalStateException.class,
                () -> c.createPost("https://x/y.png", "cap"));
    }

    @Test
    void createPostThrowsOnMalformedJson() {
        FakeHttp http = new FakeHttp();
        http.postFormResponse = "not json{{{";
        VelzonFacebookApiClient c = client(http);

        assertThrows(RuntimeException.class,
                () -> c.createPost("https://x/y.png", "cap"));
    }

    @Test
    void createPostPropagatesHttpFailure() {
        FakeHttp http = new FakeHttp();
        http.postFormError = new IllegalStateException("Facebook HTTP 400: bad request");
        VelzonFacebookApiClient c = client(http);

        assertThrows(IllegalStateException.class,
                () -> c.createPost("https://x/y.png", "cap"));
    }

    @Test
    void getPermalinkReturnsUrl() throws Exception {
        FakeHttp http = new FakeHttp();
        http.getResponse = "{\"permalink_url\":\"https://www.facebook.com/1287143177812980/posts/123/\"}";
        VelzonFacebookApiClient c = client(http);

        String url = c.getPermalink("PAGE1_POST1");

        assertEquals("https://www.facebook.com/1287143177812980/posts/123/", url);
        assertTrue(http.calls.get(0).contains("/PAGE1_POST1"));
        assertTrue(http.calls.get(0).contains("fields=permalink_url"));
        assertTrue(http.calls.get(0).contains("access_token=TOKEN1"));
    }

    @Test
    void getPermalinkThrowsOnMissingField() {
        FakeHttp http = new FakeHttp();
        http.getResponse = "{}";
        VelzonFacebookApiClient c = client(http);

        assertThrows(IllegalStateException.class, () -> c.getPermalink("PAGE1_POST1"));
    }

    @Test
    void getPermalinkThrowsOnMalformedJson() {
        FakeHttp http = new FakeHttp();
        http.getResponse = "{{not json";
        VelzonFacebookApiClient c = client(http);

        assertThrows(RuntimeException.class, () -> c.getPermalink("PAGE1_POST1"));
    }

    @Test
    void getPermalinkPropagatesHttpFailure() {
        FakeHttp http = new FakeHttp();
        http.getError = new IllegalStateException("Facebook HTTP 404: not found");
        VelzonFacebookApiClient c = client(http);

        assertThrows(IllegalStateException.class, () -> c.getPermalink("PAGE1_POST1"));
    }
}
