package i5.las2peer.services.hyeYouTubeRecommendations.recommendations;

import org.apache.spark.mllib.recommendation.Rating;

import java.util.HashMap;

public class RatingWrapper {
    // This is kind of a bad way to map String IDs to int IDs, but it's so stupid that I have to that I don't care...
    private HashMap<String, Integer> userIds;
    private HashMap<String, Integer> videoIds;
    private HashMap<String, Double> ratingMappings;

    public RatingWrapper(HashMap<String, Integer> userIds, HashMap<String, Integer> videoIds, HashMap<String,
            Double> ratingMappings) {
        this.userIds = userIds;
        this.videoIds = videoIds;
        this.ratingMappings = ratingMappings;
    }

    public int userIdToInt(String userId) {
        if (userIds.containsKey(userId))
            return userIds.get(userId);
        int mappedId = userIds.size();
        userIds.put(userId, mappedId);
        return mappedId;
    }

    public int videoIdToInt(String videoId) {
        if (videoIds.containsKey(videoId))
            return videoIds.get(videoId);
        int mappedId = videoIds.size();
        videoIds.put(videoId, mappedId);
        return mappedId;
    }

    public double ratingToDouble(String rating) { return ratingMappings.get(rating); }

    public HashMap<String, Double> setRatings(Double dislike, Double subscribe, Double playlist, Double like) {
        if (dislike != null)
            ratingMappings.put("dislike", dislike);
        if (subscribe != null)
            ratingMappings.put("subscribe", subscribe);
        if (playlist != null)
            ratingMappings.put("playlist", playlist);
        if (like != null)
            ratingMappings.put("like", like);
        return ratingMappings;
    }

    public Rating toSparkRating(String userId, String videoId, String rating) {
        return new Rating(userIdToInt(userId), videoIdToInt(videoId), ratingToDouble(rating));
    }
}
