package com.videogenerator.velzon;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VelzonInstagramApiClientTest {

    static class FakeHttp implements VelzonInstagramApiClient.Http {
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

    private VelzonInstagramApiClient client(FakeHttp http) {
        return new VelzonInstagramApiClient(http, "IGUSER1", "TOKEN1");
    }

    @Test
    void createMediaContainerPostsImageUrlAndCaptionAndReturnsId() throws Exception {
        FakeHttp http = new FakeHttp();
        http.postFormResponse = "{\"id\":\"CONTAINER1\"}";
        VelzonInstagramApiClient c = client(http);

        String id = c.createMediaContainer("https://shorts.velzon.tr/img.png", "Merhaba dünya");

        assertEquals("CONTAINER1", id);
        assertTrue(http.calls.get(0).contains("/IGUSER1/media"));
        assertTrue(http.calls.get(0).contains("image_url=https://shorts.velzon.tr/img.png"));
        assertTrue(http.calls.get(0).contains("caption=Merhaba dünya"));
        assertTrue(http.calls.get(0).contains("access_token=TOKEN1"));
    }

    @Test
    void createMediaContainerThrowsOnMissingId() {
        FakeHttp http = new FakeHttp();
        http.postFormResponse = "{\"error\":{\"message\":\"bad request\"}}";
        VelzonInstagramApiClient c = client(http);

        assertThrows(IllegalStateException.class,
                () -> c.createMediaContainer("https://x/y.png", "cap"));
    }

    @Test
    void createMediaContainerThrowsOnMalformedJson() {
        FakeHttp http = new FakeHttp();
        http.postFormResponse = "not json{{{";
        VelzonInstagramApiClient c = client(http);

        assertThrows(RuntimeException.class,
                () -> c.createMediaContainer("https://x/y.png", "cap"));
    }

    @Test
    void createMediaContainerPropagatesHttpFailure() {
        FakeHttp http = new FakeHttp();
        http.postFormError = new IllegalStateException("Instagram HTTP 400: bad request");
        VelzonInstagramApiClient c = client(http);

        assertThrows(IllegalStateException.class,
                () -> c.createMediaContainer("https://x/y.png", "cap"));
    }

    @Test
    void publishContainerPostsCreationIdAndReturnsMediaId() throws Exception {
        FakeHttp http = new FakeHttp();
        http.postFormResponse = "{\"id\":\"MEDIA1\"}";
        VelzonInstagramApiClient c = client(http);

        String mediaId = c.publishContainer("CONTAINER1");

        assertEquals("MEDIA1", mediaId);
        assertTrue(http.calls.get(0).contains("/IGUSER1/media_publish"));
        assertTrue(http.calls.get(0).contains("creation_id=CONTAINER1"));
    }

    @Test
    void publishContainerThrowsOnMissingId() {
        FakeHttp http = new FakeHttp();
        http.postFormResponse = "{}";
        VelzonInstagramApiClient c = client(http);

        assertThrows(IllegalStateException.class, () -> c.publishContainer("CONTAINER1"));
    }

    @Test
    void publishContainerPropagatesHttpFailure() {
        FakeHttp http = new FakeHttp();
        http.postFormError = new IllegalStateException("Instagram HTTP 500: server error");
        VelzonInstagramApiClient c = client(http);

        assertThrows(IllegalStateException.class, () -> c.publishContainer("CONTAINER1"));
    }

    @Test
    void getPermalinkReturnsUrl() throws Exception {
        FakeHttp http = new FakeHttp();
        http.getResponse = "{\"permalink\":\"https://www.instagram.com/p/ABC123/\"}";
        VelzonInstagramApiClient c = client(http);

        String url = c.getPermalink("MEDIA1");

        assertEquals("https://www.instagram.com/p/ABC123/", url);
        assertTrue(http.calls.get(0).contains("/MEDIA1"));
        assertTrue(http.calls.get(0).contains("fields=permalink"));
        assertTrue(http.calls.get(0).contains("access_token=TOKEN1"));
    }

    @Test
    void getPermalinkThrowsOnMissingField() {
        FakeHttp http = new FakeHttp();
        http.getResponse = "{}";
        VelzonInstagramApiClient c = client(http);

        assertThrows(IllegalStateException.class, () -> c.getPermalink("MEDIA1"));
    }

    @Test
    void getPermalinkThrowsOnMalformedJson() {
        FakeHttp http = new FakeHttp();
        http.getResponse = "{{not json";
        VelzonInstagramApiClient c = client(http);

        assertThrows(RuntimeException.class, () -> c.getPermalink("MEDIA1"));
    }

    @Test
    void getPermalinkPropagatesHttpFailure() {
        FakeHttp http = new FakeHttp();
        http.getError = new IllegalStateException("Instagram HTTP 404: not found");
        VelzonInstagramApiClient c = client(http);

        assertThrows(IllegalStateException.class, () -> c.getPermalink("MEDIA1"));
    }
}
