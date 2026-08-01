package com.videogenerator.model;

import com.google.gson.annotations.SerializedName;

/**
 * Result of a YouTube video upload
 */
public class UploadResult {
    @SerializedName("video_id")
    private String videoId;

    private String url;
    private String title;
    private String status;

    @SerializedName("uploaded_at")
    private long uploadedAt;

    @SerializedName("file_path")
    private String filePath;

    public UploadResult() {
        this.uploadedAt = System.currentTimeMillis();
    }

    public UploadResult(String videoId, String title) {
        this();
        this.videoId = videoId;
        this.title = title;
        this.url = "https://www.youtube.com/watch?v=" + videoId;
        this.status = "uploaded";
    }

    // Getters and Setters
    public String getVideoId() {
        return videoId;
    }

    public void setVideoId(String videoId) {
        this.videoId = videoId;
        this.url = "https://www.youtube.com/watch?v=" + videoId;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(long uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getShortsUrl() {
        return "https://www.youtube.com/shorts/" + videoId;
    }

    @Override
    public String toString() {
        return "UploadResult{" +
                "videoId='" + videoId + '\'' +
                ", url='" + url + '\'' +
                ", title='" + title + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
