package i5.las2peer.services.hyeYouTubeRecommendations.youTubeData;

/**
 * YouTubeComment
 *
 * This Class is used as a wrapper for relevant YouTube comment data used by this service.
 *
 */

public class YouTubeComment {
    private String commentId;
    private String videoId;
    private String content;
    private String channelId;
    private String publishDate;
    private int likeCount;

    public YouTubeComment(String id, String videoId, String content, String authorId, String publishDate,
                          int likeCount) {
        this.commentId = id;
        this.videoId = videoId;
        this.content = content;
        this.channelId = authorId;
        this.publishDate = publishDate;
        this.likeCount = likeCount;
    }

    public String getCommentId() {
        return commentId;
    }

    public void setCommentId(String commentId) {
        this.commentId = commentId;
    }

    public String getVideoId() {
        return videoId;
    }

    public void setVideoId(String videoId) {
        this.videoId = videoId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getAuthorId() {
        return channelId;
    }

    public void setAuthorId(String authorId) {
        this.channelId = authorId;
    }

    public String getPublishDate() {
        return publishDate;
    }

    public void setPublishDate(String publishDate) {
        this.publishDate = publishDate;
    }

    public int getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(int likeCount) {
        this.likeCount = likeCount;
    }

    @Override
    public String toString() {
        return "{\"commentId\":\"" + commentId + "\"," +
                "\"videoId\":\"" + videoId + "\"," +
                "\"content\":\"" + content + "\"," +
                "\"authorId\":\"" + channelId + "\"," +
                "\"publishDate\":\"" + publishDate + "\"," +
                "\"likeCount\":" + likeCount;
    }
}
