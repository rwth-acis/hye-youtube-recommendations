package i5.las2peer.services.hyeYouTubeRecommendations.youTubeData;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.apache.v2.ApacheHttpTransport;
import com.google.api.client.json.Json;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import i5.las2peer.logging.L2pLogger;
import i5.las2peer.services.hyeYouTubeRecommendations.YouTubeRecommendations;

import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
    private static FileWriter backup;
    private static String apiKey;

    // Temporary debug function
    public static void init() {
        try {
            backup = new FileWriter("ytDataBackup.json");
        } catch (Exception e) {
            log.severe("##############################################################");
            log.severe("###### NO YOUTUBE BACKUP! STOP THE SERVICE IMMEDIATELY! ######");
            log.severe("##############################################################");
        }
    }

    /**
     * Retrieves the details of the given YouTube video
     * Does not use user specific credentials, but a static API key instead
     * KNOWN ISSUE: The fact that this function is static and uses the API key causes problems for unlisted videos
     * which may only be accessible to certain users.
     *
     * @param videoIds The IDs of the YouTube videos which we are interested in
     * @return Json Array of video information of the YouTube videos identified by the specified IDs
     */
    public static JsonArray getVideoDetails(String[] videoIds, HttpRequestInitializer initializer) {
        if (apiKey == null || videoIds == null || videoIds.length == 0)
            return null;
        String videoIdString = videoIds[0];
        for (int i = 1; i < videoIds.length; ++i) { videoIdString += "," + videoIds[i] ; }
        try {
            YouTube ytConnection = new YouTube.Builder(new ApacheHttpTransport(), new GsonFactory(), initializer)
                    .setApplicationName("How's your Experience").build();
            HttpRequest request = ytConnection.videos().list("snippet").setId(videoIdString)
                    .setFields("items(snippet(title,description,tags,channelId,categoryId,publishedAt,thumbnails))")
                    .setMaxResults(Integer.toUnsignedLong(500)).setKey(apiKey).buildHttpRequest();
            log.info("Sending request: " + request.getUrl().toString());
            JsonObject response = new Gson().fromJson(request.execute().parseAsString(), JsonObject.class);
            backup.write(response.get("items").getAsJsonArray().toString());
            backup.flush();
            return response.get("items").getAsJsonArray();
        } catch (Exception e) {
            log.printStackTrace(e);
            return null;
        }
    }

    /**
     * Retrieves the comments of the given YouTube video
     * Does not use user specific credentials, but a static API key instead
     *
     * @param videoId The ID of the YouTube video from which we retrieve the comments
     * @return Json Array of YouTube comments left under the YouTube video identified by the specified ID
     */
    public static ArrayList<YouTubeComment> getComments(String videoId, HttpRequestInitializer initializer) {
        if (apiKey == null)
            return null;
        JsonArray comments = new JsonArray();
        try {
            YouTube ytConnection = new YouTube.Builder(new ApacheHttpTransport(), new GsonFactory(), initializer)
                    .setApplicationName("How's your Experience").build();
            HttpRequest request = ytConnection.commentThreads().list("snippet").set("videoId", videoId)
                    .setMaxResults(Integer.toUnsignedLong(500)).setKey(apiKey).buildHttpRequest();
            log.info("Sending request: " + request.getUrl().toString());
            JsonObject response = new Gson().fromJson(request.execute().parseAsString(), JsonObject.class);
            comments = response.get("items").getAsJsonArray();
            backup.write(comments.toString());
            backup.flush();
            // Retrieving all comments might deplete quota too quickly
//			while (response.has("nextPageToken")) {
//				request = ytConnection.comments().list("snippet").set("videoId", videoId)
//						.setMaxResults(Integer.toUnsignedLong(500))
//						.setPageToken(response.get("nextPageToken").getAsString()).buildHttpRequest();
//				response = new Gson().fromJson(request.execute().parseAsString(), JsonObject.class);
//				comments.addAll(response.get("items").getAsJsonArray());
//			}
        } catch (Exception e) {
            log.printStackTrace(e);
            return null;
        }
        return parseCommentArray(comments);
    }

    /**
     * Helper function to parse the information from the YouTube data API response to a video's comments
     *
     * @param commentArray The array returned by the getComments function
     * @return YouTubeComments array with all relevant comment data
     */
    private static ArrayList<YouTubeComment> parseCommentArray(JsonArray commentArray) {
        ArrayList<YouTubeComment> comments = new ArrayList<YouTubeComment>();
        Iterator<JsonElement> it = commentArray.iterator();
        while (it.hasNext())
            comments.add(parseYtComment(it.next().getAsJsonObject()));
        return comments;
    }

    /**
     * Helper function to parse the information from a single comment returned by the YouTube data API
     *
     * @param commentObj The comment data as Json
     * @return YouTubeComment object with all relevant data that was available
     */
    public static YouTubeComment parseYtComment(JsonObject commentObj) {
        String id = null;
        String videoId = null;
        String content = null;
        String channelId = null;
        String publishDate = null;
        int likeCount = -1;
        if (commentObj.has("id"))
            id = commentObj.get("id").getAsString();
        JsonObject snippet = null;
        if (commentObj.has("snippet"))
            snippet = commentObj.get("snippet").getAsJsonObject();
        else
            return new YouTubeComment(id, null, null, null, null, -1);

        if (snippet.has("videoId"))
            videoId = snippet.get("videoId").getAsString();

        if (snippet.has("topLevelComment") &&
                snippet.get("topLevelComment").getAsJsonObject().has("snippet"))
            snippet = snippet.get("topLevelComment").getAsJsonObject().get("snippet").getAsJsonObject();
        else
            return new YouTubeComment(id, videoId, null, null, null, -1);

        if (snippet.has("textOriginal"))
            content = snippet.get("textOriginal").getAsString();
        if (snippet.has("authorChannelId") &&
                snippet.get("authorChannelId").getAsJsonObject().has("value"))
            channelId = snippet.get("authorChannelId").getAsJsonObject().get("value").getAsString();
        if (snippet.has("publishedAt"))
            publishDate = snippet.get("publishedAt").getAsString();
        if (snippet.has("likeCount"))
            likeCount = snippet.get("likeCount").getAsInt();
        return new YouTubeComment(id, videoId, content, channelId, publishDate, likeCount);
    }

    public static JsonArray getVideoDetails(String videoId, HttpRequestInitializer initializer) {
        return getVideoDetails(new String[] {videoId}, initializer);
    }

    public YouTubeApiWrapper(Credential credential) { refreshConnection(credential); }

    public boolean intactConnection() { return this.ytConnection != null; }

    public Credential getCredential() { return this.credential; }

    public static void setApiKey(String newApiKey) { apiKey = newApiKey; }

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
     * Retrieves the YouTube videos rated by the current user.
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
                log.info("Sending request: " + request.getUrl().toString());
                response = new Gson().fromJson(request.execute().parseAsString(), JsonObject.class);
                videos.addAll(response.get("items").getAsJsonArray());
            }
            backup.write(videos.toString());
            backup.flush();
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
            log.info("Sending request: " + request.getUrl().toString());
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
                request = ytConnection.search().list("id")
                        .set("channelId",it.next().getAsJsonObject().get("snippet").getAsJsonObject().get("resourceId")
                                .getAsJsonObject().get("channelId").getAsString())
                        .setMaxResults(Integer.toUnsignedLong(500)).buildHttpRequest();
                log.info("Sending request: " + request.getUrl().toString());
                response = new Gson().fromJson(request.execute().parseAsString(), JsonObject.class);
                videos.addAll(response.get("items").getAsJsonArray());
                backup.write(response.get("items").getAsJsonArray().toString());
                backup.flush();
                // Getting all videos might deplete quota too quickly
//				while (response.has("nextPageToken")) {
//				request = ytConnection.videos().list("snippet,id")
//						.set("channelId", it.next().getAsJsonObject().get("snippet").getAsJsonObject().get("channelId").getAsString())
//						.setMaxResults(Integer.toUnsignedLong(500)).buildHttpRequest();
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
            log.info("Sending request: " + request.getUrl().toString());
            JsonObject response = new Gson().fromJson(request.execute().parseAsString(), JsonObject.class);
            JsonArray playlists = response.get("items").getAsJsonArray();
            while (response.has("nextPageToken")) {
                request = ytConnection.subscriptions().list("snippet").setMine(true)
                        .setMaxResults(Integer.toUnsignedLong(500))
                        .setPageToken(response.get("nextPageToken").getAsString()).buildHttpRequest();
                log.info("Sending request: " + request.getUrl().toString());
                response = new Gson().fromJson(request.execute().parseAsString(), JsonObject.class);
                playlists.addAll(response.get("items").getAsJsonArray());
            }

            // Get videos from playlists
            Iterator<JsonElement> it = playlists.iterator();
            JsonArray videos = new JsonArray();
            while (it.hasNext()) {
                request = ytConnection.playlistItems().list("contentDetails")
                        .setPlaylistId(it.next().getAsJsonObject().get("id").getAsString())
                        .setMaxResults(Integer.toUnsignedLong(500)).buildHttpRequest();
                response = new Gson().fromJson(request.execute().parseAsString(), JsonObject.class);
                videos.addAll(response.get("items").getAsJsonArray());
                backup.write(response.get("items").getAsJsonArray().toString());
                backup.flush();
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

    /**
     * Helper function to parse the information from a single video object returned by the YouTube data API
     *
     * @param videoObj The video data as Json
     * @return YouTubeVideo object with all relevant video data that was available
     */
    public static YouTubeVideo parseYtVideo(JsonObject videoObj) {
        String id = null;
        String channelId = null;
        String title = null;
        String description = null;
        String thumbnailUrl = null;
        String[] tags = null;
        int categoryId = -1;
        String publishedAt = null;
        if (videoObj.has("id"))
            id = videoObj.get("id").getAsString();
        JsonObject snippet = null;
        if (videoObj.has("snippet"))
            snippet = videoObj.get("snippet").getAsJsonObject();
        else
            return new YouTubeVideo(id, null, null, null, null, null,
                    -1, null);

        if (snippet.has("channelId"))
            channelId = snippet.get("channelId").getAsString();
        if (snippet.has("title"))
            title = snippet.get("title").getAsString();
        if (snippet.has("description"))
            description = snippet.get("description").getAsString();
        if (snippet.has("thumbnails") &&
            snippet.get("thumbnails").getAsJsonObject().has("default") &&
            snippet.get("thumbnails").getAsJsonObject().get("default").getAsJsonObject().has("url"))
                thumbnailUrl = snippet.get("thumbnails").getAsJsonObject().get("default").getAsJsonObject().get("url")
                        .getAsString();
        if (snippet.has("tags"))
            tags = snippet.get("tags").getAsJsonArray().toString().replaceAll("\\s", "")
                    .replaceAll("\"", "").replaceAll("\\[", "")
                    .replaceAll("]", "").split(",");
        if (snippet.has("categoryId"))
            categoryId = snippet.get("categoryId").getAsInt();
        if (snippet.has("publishedAt"))
            publishedAt = snippet.get("publishedAt").getAsString();

        // Second chance at getting videoId
        if (id == null && thumbnailUrl != null) {
            try {
                id = thumbnailUrl.split("/")[4];
            } catch (Exception e) {
                log.warning("No ID for video YouTubeVideo (" + id + ", " + channelId + ", " + title + ", " +
                        description + ", " + thumbnailUrl + ", " + tags + ", " + categoryId + ", " + publishedAt);
            }
        }
        return new YouTubeVideo(id, channelId, title, description, thumbnailUrl, tags, categoryId, publishedAt);
    }

    /**
     * Helper function to parse the information from the YouTube data API response to likes and dislikes
     *
     * @param videoArray The array returned by the getRatedVideo function
     * @return YouTubeVideo array with all relevant video data of liked or disliked YouTube videos
     */
    private ArrayList<YouTubeVideo> parseVideoArray(JsonArray videoArray) {
        if (videoArray == null) {
            log.warning("Video array is null");
            return null;
        }
        ArrayList<YouTubeVideo> videoList = new ArrayList<YouTubeVideo>();
        Iterator<JsonElement> it = videoArray.iterator();
        while (it.hasNext()) {
            YouTubeVideo video = parseYtVideo(it.next().getAsJsonObject());
            if (video != null)
                videoList.add(video);
        }
        return videoList;
    }

    /**
     * Helper function to parse the information from the YouTube data API response to subscriptions
     *
     * @param videoArray The array returned by the getSubscriptions function
     * @return YouTubeVideo array with all relevant video data of subscribed to YouTube videos
     */
    private ArrayList<YouTubeVideo> parseSubscriptionsArray(JsonArray videoArray) {
        if (videoArray == null) {
            log.warning("Subscription array is null");
            return null;
        }
        ArrayList<YouTubeVideo> videoList = new ArrayList<YouTubeVideo>();
        Iterator<JsonElement> it = videoArray.iterator();
        HashSet<String> videoIds = new HashSet<String>();
        while (it.hasNext()) {
            try {
                // This kind of sucks and is super inefficient, but we need the additional video information
                JsonObject searchResult = it.next().getAsJsonObject();
                if (!searchResult.has("id") || !searchResult.get("id").getAsJsonObject().has("videoId"))
                    continue;
                videoIds.add(it.next().getAsJsonObject().get("id").getAsJsonObject().get("videoId").getAsString());
                // At least we can send requests for multiple videos at once, don't know how many work so let's keep it
                // down to 10 for now
                if (videoIds.size() >= 10 || !it.hasNext()) {
                    String[] videoIdArray = videoIds.toArray(new String[videoIds.size()]);
                    videoIds.clear();
                    // TODO if this request fails, it fails for up to 10 videos... that's kind of a waste of precious video data
                    videoList.addAll(parseVideoArray(getVideoDetails(videoIdArray,
                            ytConnection.getRequestFactory().getInitializer())));
                }
            } catch (Exception e) {
                log.printStackTrace(e);
                continue;
            }
        }
        return videoList;
    }

    /**
     * Helper function to parse the information from the YouTube data API response to playlists
     *
     * @param videoArray The array returned by the getPlaylists function
     * @return YouTubeVideo array with all relevant video data of YouTube videos in playlists
     */
    private ArrayList<YouTubeVideo> parsePlaylistsArray(JsonArray videoArray) {
        if (videoArray == null) {
            log.warning("Playlist array is null");
            return null;
        }
        ArrayList<YouTubeVideo> videoList = new ArrayList<YouTubeVideo>();
        Iterator<JsonElement> it = videoArray.iterator();
        ArrayList<String> videoIds = new ArrayList<String>();
        while (it.hasNext()) {
            try {
                videoIds.add(it.next().getAsJsonObject().get("contentDetails").getAsJsonObject().get("videoId").getAsString());
                if (videoIds.size() >= 10 || !it.hasNext()) {
                    videoList.addAll(parseVideoArray(getVideoDetails(videoIds.toArray(new String[videoArray.size()]),
                            ytConnection.getRequestFactory().getInitializer())));
                    videoIds.clear();
                }
            } catch (Exception e) {
                log.printStackTrace(e);
                continue;
            }
        }
        return videoList;
    }

    /**
     * Helper function to retrieve YouTube watch data via established connection
     *
     * @return YouTubeVideo array with all relevant video data
     */
    public HashMap<String, ArrayList<YouTubeVideo>> getYouTubeWatchData() {
        HashMap<String, ArrayList<YouTubeVideo>> videoData = new HashMap<String, ArrayList<YouTubeVideo>>();

         videoData.put("likes", parseVideoArray(getLikedVideos()));
         videoData.put("dislikes", parseVideoArray(getDisikedVideos()));
         videoData.put("subscriptions", parseSubscriptionsArray(getSubscriptions()));
        // TODO research YouTube playlists, because if they're just used for music, it doesn't make that much sense to include them here
        // TODO also we currently, don't consider uploads which is probably not a huge deal, but maybe still relevant
        videoData.put("playlists", parsePlaylistsArray(getPlaylists()));
        return videoData;
    }
}
