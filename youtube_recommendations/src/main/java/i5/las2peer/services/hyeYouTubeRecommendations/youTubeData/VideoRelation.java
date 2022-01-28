package i5.las2peer.services.hyeYouTubeRecommendations.youTubeData;

/**
 * VideoRelation
 *
 * This Class is a relation connecting a YouTube video to a las2peer User Agent by a rating relation (like, dislike,
 * subscription, or playlist)
 *
 */

enum YT_RATING {LIKE, DISLIKE, SUBSCRIPTION, PLAYLIST}

public class VideoRelation {
    YouTubeVideo ytVideo;
    String las2peerAgentId;
    YT_RATING rating;
}
