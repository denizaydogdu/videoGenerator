package com.videogenerator.model;

/**
 * Publication state of one language variant on one platform.
 */
public class Publication {
    private String platform;
    private String status;
    private String url;

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
