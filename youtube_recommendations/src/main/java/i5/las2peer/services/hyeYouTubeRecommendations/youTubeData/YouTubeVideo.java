package i5.las2peer.services.hyeYouTubeRecommendations.youTubeData;

import com.google.gson.JsonObject;

import java.util.Arrays;

/**
 * YouTubeVideo
 *
 * This Class is used as a wrapper for relevant YouTube video data used by this service.
 *
 */

public class YouTubeVideo {
    private String videoId;
    private String channelId;
    private String title;
    private String description;
    private String thumbnailUrl;
    private String[] tags;
    private int categoryId;
    private String uploadDate;

    public YouTubeVideo(String videoId, String channelId, String title, String description, String thumbnailUrl,
                        String[] tags, int categoryId, String uploadDate) {
        this.videoId = videoId;
        this.channelId = channelId;
        this.title = title;
        this.description = description;
        this.thumbnailUrl = thumbnailUrl;
        this.tags = tags;
        this.categoryId = categoryId;
        this.uploadDate = uploadDate;
    }

    public String getVideoId() {
        return videoId;
    }

    public void setVideoId(String videoId) {
        this.videoId = videoId;
    }

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public String[] getTags() {
        return tags;
    }

    public void setTags(String[] tags) {
        this.tags = tags;
    }

    public int getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(int categoryId) {
        this.categoryId = categoryId;
    }

    public String getUploadDate() {
        return uploadDate;
    }

    public void setUploadDate(String uploadDate) {
        this.uploadDate = uploadDate;
    }

    public boolean isComplete() {
        return videoId != null && channelId != null && title != null && description != null && thumbnailUrl != null &&
                tags != null && categoryId != -1 && uploadDate != null;
    }

    @Override
    public String toString() {
        return "{\"videoId\":\"" + videoId + "\"," +
            "\"channelId\":\"" + channelId + "\"," +
            "\"title\":\"" + title + "\"," +
            "\"description\":\"" + description + "\"," +
            "\"thumbnailUrl\":\"" + thumbnailUrl + "\"," +
            "\"tags\":" + Arrays.toString(tags) + "," +
            "\"categoryId\":\"" + categoryId + "\"," +
            "\"uploadDate\":\"" + uploadDate + "\"";
    }
}
