package i5.las2peer.services.hyeYouTubeRecommendations.util;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.apache.v2.ApacheHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import i5.las2peer.logging.L2pLogger;
import i5.las2peer.services.hyeYouTubeRecommendations.YouTubeRecommendations;

import java.util.Iterator;

/**
 * YouTubeApiWrapper
 *
 * This Class is used as a wrapper for the YouTube Data API.
 * As such, it sends the requests and is created for one specific set of credentials (i.e., one specific user)
 *
 */

public class YouTubeApiWrapper {

    private YouTube ytConnection;
    private Credential credential;
    private static final L2pLogger log = L2pLogger.getInstance(YouTubeRecommendations.class.getName());

    /**
     * Retrieves the comments of the given YouTube video.
     * Does not use user specific credentials, but a static API key instead.
     *
     * @param videoId The ID of the YouTube video from which we retrieve the comments
     * @return Json Array of YouTube comments left under the YouTube video identified by the specified ID
     */
    public static JsonArray getComments(String videoId, HttpRequestInitializer initializer, String apiKey) {
        try {
            YouTube ytConnection = new YouTube.Builder(new ApacheHttpTransport(), new GsonFactory(), initializer)
                    .setApplicationName("How's your Experience").build();
            HttpRequest request = ytConnection.comments().list("snippet").set("videoId", videoId)
                    .setMaxResults(Integer.toUnsignedLong(500)).setKey(apiKey).buildHttpRequest();
            JsonObject response = new Gson().fromJson(request.execute().parseAsString(), JsonObject.class);
            JsonArray comments = response.get("items").getAsJsonArray();
            // Retrieving all comments might deplete quota too quickly
//			while (response.has("nextPageToken")) {
//				request = ytConnection.comments().list("snippet").set("videoId", videoId)
//						.setMaxResults(Integer.toUnsignedLong(500))
//						.setPageToken(response.get("nextPageToken").getAsString()).buildHttpRequest();
//				response = new Gson().fromJson(request.execute().parseAsString(), JsonObject.class);
//				comments.addAll(response.get("items").getAsJsonArray());
//			}
            return comments;
        } catch (Exception e) {
            log.printStackTrace(e);
            return null;
        }
    }

    /**
     * Retrieves the details of the given YouTube video.
     * Does not use user specific credentials, but a static API key instead.
     *
     * @param videoId The ID of the YouTube video about which we want to learn
     * @return Json Object of video information of the YouTube video identified by the specified ID
     */
    public static JsonObject getVideoDetails(String videoId, HttpRequestInitializer initializer, String apiKey) {
        try {
            YouTube ytConnection = new YouTube.Builder(new ApacheHttpTransport(), new GsonFactory(), initializer)
                    .setApplicationName("How's your Experience").build();
            HttpRequest request = ytConnection.comments().list("snippet").setId(videoId)
                    .setMaxResults(Integer.toUnsignedLong(500)).setKey(apiKey).buildHttpRequest();
            JsonObject response = new Gson().fromJson(request.execute().parseAsString(), JsonObject.class);
            return response.get("items").getAsJsonArray().get(0).getAsJsonObject();
        } catch (Exception e) {
            log.printStackTrace(e);
            return null;
        }
    }

    public YouTubeApiWrapper(Credential credential) { refreshConnection(credential); }

    public boolean intactConnection() { return this.ytConnection != null; }

    public Credential getCredential() { return this.credential; }

    /**
     * Creates the YouTube Data API connection with the given credentials
     *
     * @param credential Access credentials for the YouTube Data API
     */
    public void refreshConnection(Credential credential) {
        this.credential = credential;
        try {
            ytConnection = new YouTube.Builder(new ApacheHttpTransport(), new GsonFactory(), credential)
                    .setApplicationName("How's your Experience").build();
        } catch (Exception e) {
            log.printStackTrace(e);
            ytConnection = null;
        }
    }

    /**
     * Retrieves the YouTube videos rated by the current user
     *
     * @param rating A String either like or dislike indicating the rating
     * @return Json Array of YouTube videos liked by requesting user
     */
    private JsonArray getRatedVideos(String rating) {
        if (ytConnection == null)
            return null;
        try {
            HttpRequest request = ytConnection.videos().list("snippet").setMyRating(rating)
                    .setMaxResults(Integer.toUnsignedLong(500)).buildHttpRequest();
            JsonObject response = new Gson().fromJson(request.execute().parseAsString(), JsonObject.class);
            JsonArray videos = response.get("items").getAsJsonArray();
            while (response.has("nextPageToken")) {
                request = ytConnection.videos().list("snippet").setMyRating(rating)
                        .setMaxResults(Integer.toUnsignedLong(500))
                        .setPageToken(response.get("nextPageToken").getAsString()).buildHttpRequest();
                response = new Gson().fromJson(request.execute().parseAsString(), JsonObject.class);
                videos.addAll(response.get("items").getAsJsonArray());
            }
            return videos;
        } catch (Exception e) {
            // TODO improve error handling and only set ytConnection as invalid, if exception is actually related to it
            ytConnection = null;
            log.printStackTrace(e);
            return null;
        }
    }

    /**
     * Retrieves the YouTube videos liked by the current user
     *
     * @return Json Array of YouTube videos liked by requesting user
     */
    private JsonArray getLikedVideos() { return getRatedVideos("like"); }

    /**
     * Retrieves the YouTube videos disliked by the current user
     *
     * @return Json Array of YouTube videos disliked by requesting user
     */
    private JsonArray getDisikedVideos() { return getRatedVideos("dislike"); }

    /**
     * Retrieves the YouTube videos by channels the current user is subscribed to
     *
     * @return Json Array of YouTube videos uploaded by channels the requesting user is subscribed to
     */
    private JsonArray getSubscriptions() {
        if (ytConnection == null)
            return null;
        try {
            // Get Subscribed channels
            HttpRequest request = ytConnection.subscriptions().list("snippet").setMine(true)
                    .setMaxResults(Integer.toUnsignedLong(500)).buildHttpRequest();
            JsonObject response = new Gson().fromJson(request.execute().parseAsString(), JsonObject.class);
            JsonArray subscriptions = response.get("items").getAsJsonArray();
            while (response.has("nextPageToken")) {
                request = ytConnection.subscriptions().list("snippet").setMine(true)
                        .setMaxResults(Integer.toUnsignedLong(500))
                        .setPageToken(response.get("nextPageToken").getAsString()).buildHttpRequest();
                response = new Gson().fromJson(request.execute().parseAsString(), JsonObject.class);
                subscriptions.addAll(response.get("items").getAsJsonArray());
            }

            // Get videos
            JsonArray videos = new JsonArray();
            Iterator<JsonElement> it = subscriptions.iterator();
            while (it.hasNext()) {
                request = ytConnection.videos().list("snippet,id")
                        .set("channelId", it.next().getAsJsonObject().get("snippet").getAsJsonObject().get("channelId").getAsString())
                        .setMaxResults(Integer.toUnsignedLong(500)).buildHttpRequest();
                response = new Gson().fromJson(request.execute().parseAsString(), JsonObject.class);
                videos.addAll(response.get("items").getAsJsonArray());
                // Getting all videos might deplete quota too quickly
//				while (response.has("nextPageToken")) {
//				request = ytConnection.videos().list("snippet,id")
//						.set("channelId", it.next().getAsJsonObject().get("snippet").getAsJsonObject().get("channelId").getAsString())
//						.setMaxResults(Integer.toUnsignedLong(500)).buildHttpRequest();
//					response = new Gson().fromJson(request.execute().parseAsString(), JsonObject.class);
//					videos.addAll(response.get("items").getAsJsonArray());
//				}
            }
            return subscriptions;
        } catch (Exception e) {
            // TODO improve error handling and only set ytConnection as invalid, if exception is actually related to it
            ytConnection = null;
            log.printStackTrace(e);
            return null;
        }
    }

    /**
     * Retrieves the videos from YouTube playlists of the current user
     *
     * @return Json Array of YouTube videos contained in playlists created by the requesting user
     */
    private JsonArray getPlaylists() {
        if (ytConnection == null)
            return null;
        try {
            // Get Playlists
            HttpRequest request = ytConnection.playlists().list("snippet").setMine(true)
                    .setMaxResults(Integer.toUnsignedLong(500)).buildHttpRequest();
            JsonObject response = new Gson().fromJson(request.execute().parseAsString(), JsonObject.class);
            JsonArray playlists = response.get("items").getAsJsonArray();
            while (response.has("nextPageToken")) {
                request = ytConnection.subscriptions().list("snippet").setMine(true)
                        .setMaxResults(Integer.toUnsignedLong(500))
                        .setPageToken(response.get("nextPageToken").getAsString()).buildHttpRequest();
                response = new Gson().fromJson(request.execute().parseAsString(), JsonObject.class);
                playlists.addAll(response.get("items").getAsJsonArray());
            }

            // Get videos from playlists
            Iterator<JsonElement> it = playlists.iterator();
            JsonArray videos = new JsonArray();
            while (it.hasNext()) {
                request = ytConnection.playlistItems().list("snippet")
                        .setPlaylistId(it.next().getAsJsonObject().get("id").getAsString())
                        .setMaxResults(Integer.toUnsignedLong(500)).buildHttpRequest();
                response = new Gson().fromJson(request.execute().parseAsString(), JsonObject.class);
                videos.addAll(response.get("items").getAsJsonArray());
                // Getting all videos might deplete quota too quickly
//				while (response.has("nextPageToken")) {
//					request = ytConnection.subscriptions().list("snippet").setMine(true)
//							.setMaxResults(Integer.toUnsignedLong(500))
//							.setPageToken(response.get("nextPageToken").getAsString()).buildHttpRequest();
//					response = new Gson().fromJson(request.execute().parseAsString(), JsonObject.class);
//					videos.addAll(response.get("items").getAsJsonArray());
//				}
            }
            return videos;
        } catch (Exception e) {
            // TODO improve error handling and only set ytConnection as invalid, if exception is actually related to it
            ytConnection = null;
            log.printStackTrace(e);
            return null;
        }
    }

    public JsonArray parseVideoData(JsonArray likes, JsonArray dislikes, JsonArray playlists, JsonArray subscriptions) {
        return new JsonArray();
    }
}
