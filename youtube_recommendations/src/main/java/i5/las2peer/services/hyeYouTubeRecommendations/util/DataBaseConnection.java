package i5.las2peer.services.hyeYouTubeRecommendations.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.stream.JsonReader;
import i5.las2peer.logging.L2pLogger;
import i5.las2peer.services.hyeYouTubeRecommendations.YouTubeRecommendations;
import i5.las2peer.services.hyeYouTubeRecommendations.youTubeData.YouTubeComment;
import i5.las2peer.services.hyeYouTubeRecommendations.youTubeData.YouTubeVideo;
import rice.p2p.util.tuples.Tuple;

import java.io.FileReader;
import java.sql.*;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * DataBaseConnection
 *
 * This Class is used for communication with a MySQL database where the data YouTube obtained from the API is stored
 *
 */

public class DataBaseConnection {

    private static final L2pLogger log = L2pLogger.getInstance(YouTubeRecommendations.class.getName());
    Connection connection;
    boolean healthy;

    /**
     * Constructor, establishes connection
     *
     * @param host Domain under which the database is available
     * @param database Name of the database where data is stored
     * @param username Name of database user
     * @param password Password for given user
     */
    public DataBaseConnection(String host, String database, String username, String password) {
        // TODO add variable for DB port, I suppose
        try {
            connection = DriverManager.getConnection("jdbc:mysql://" + host + '/' + database +
                    "?useSSL=false", username, password);
            healthy = true;
            log.info("Successfully connected to MySQL database");
        } catch (Exception e) {
            log.printStackTrace(e);
            healthy = false;
        }
    }

    /**
     * Function to check MySQL connection health
     *
     * @return Whether connection is healthy
     */
    public boolean isHealthy() {
        return healthy;
    }

    /**
     * Helper function to execute the given statement
     *
     * @param statement The SQL statement to execute
     * @return Whether query execution was successful
     */
    private boolean executeStatement(PreparedStatement statement) {
        try {
            statement.execute();
            return true;
        } catch (Exception e) {
            log.printStackTrace(e);
            return false;
        }
    }

    /**
     * Helper function to execute the given statement
     *
     * @param statement The SQL statement to execute given as String
     * @return Whether query execution was successful
     */
    private boolean executeStatement(String statement) {
        try {
            connection.prepareCall(statement).execute();
            return true;
        } catch (Exception e) {
            log.printStackTrace(e);
            return false;
        }
    }

    /**
     * Create database tables
     *
     * @return Whether table creation was successful
     */
    public boolean init() {
        if (!healthy)
            return false;

        // Try to create tables
        if (!executeStatement("create table ytVideos (" +
                "id varchar(20) not null primary key," +
                "channelId varchar(40)," +
                "title varchar(100)," +
                "description text," +
                "thumbnailUrl varchar(128)," +
                "categoryId int," +
                "uploadDate char(20))"))
            log.warning("Failed to create table ytVideos");
        if (!executeStatement("create table ytComments (" +
                "id varchar(40) not null primary key," +
                "videoId varchar(20) references ytVideos(id)," +
                "content text," +
                "channelId varchar(40)," +
                "publishDate char(20)," +
                "likeCount int)"))
            log.warning("Failed to create table ytComments");
        if (!executeStatement("create table ytRatings (" +
                "videoId varchar(20) not null references ytVideos(id)," +
                "userId char(128) not null," +
                "rating varchar(20) not null)"))
            log.warning("Failed to create table ytRatings");
        if (!executeStatement("create table ytTags (tag varchar (64) not null primary key)"))
            log.warning("Failed to create table ytTags");
        if (!executeStatement("create table tagRelations (" +
                "id int not null primary key auto_increment," +
                "videoId varchar(20) not null references ytVideos(id)," +
                "tag varchar(64) not null references ytTags(tag))"))
            log.warning("Failed to create table tagRelations");
        // If this fails, assume it's because they are already there and healthy (yes, this is a bad idea TODO fix it)

        return true;
    }

    /**
     * Add YouTube video data to MySQL database
     *
     * @param video YouTube video data information
     * @return Whether insertion was successful
     */
    public boolean addVideo(YouTubeVideo video) {
        if (!healthy)
            return false;
        try {
            // Add video data ...
            PreparedStatement statement = connection.prepareStatement(
                    "insert into ytVideos values (?, ?, ? , ?, ?, ?, ?)");
            statement.setString(1, video.getVideoId());
            statement.setString(2, video.getChannelId());
            statement.setString(3, video.getTitle());
            statement.setString(4, video.getDescription());
            statement.setString(5, video.getThumbnailUrl());
            statement.setInt(6, video.getCategoryId());
            statement.setString(7, video.getUploadDate());
            statement.execute();
        } catch (Exception e) {
            log.printStackTrace(e);
            return false;
        }
        // ... and tags if there are any
        String[] tags = video.getTags();
        if (tags == null || tags.length == 0)
            return true;
        for (int i = 0; i < tags.length; ++i) {
            try {
                PreparedStatement statement = connection.prepareStatement("insert ignore into ytTags values (?)");
                statement.setString(1, tags[i]);
                statement.execute();

                statement = connection.prepareStatement("insert into tagRelations (videoId, tag) values (?, ?)");
                statement.setString(1, video.getVideoId());
                statement.setString(2, tags[i]);
                statement.execute();
            } catch (Exception e) {
                log.printStackTrace(e);
            }
        }
        // Incomplete set of tags might be added without caller learning about it TODO somehow address this
        return true;
    }

    /**
     * Add relation between YouTube video and las2peer user to MySQL database
     *
     * @param videoId YouTube video ID of rated video
     * @param userId las2peer ID of rating User Agent
     * @return Whether insertion was successful
     */
    public boolean addRating(String videoId, String userId, String rating) {
        if (!healthy)
            return false;
        try {
            PreparedStatement statement = connection.prepareStatement("insert into ytRatings values (?, ?, ?)");
            statement.setString(1, videoId);
            statement.setString(2, userId);
            statement.setString(3, rating);
            statement.execute();
            return true;
        } catch (Exception e) {
            log.printStackTrace(e);
            return false;
        }
    }

    /**
     * Add YouTubeComment to MuSQL database
     *
     * @param comment YouTube comment data
     * @return Whether insertion was successful
     */
    public boolean addComment(YouTubeComment comment) {
        if (!healthy)
            return false;
        try {
            PreparedStatement statement = connection.prepareStatement(
                    "insert into ytComments values (?, ?, ?, ?, ?, ?)");
            statement.setString(1, comment.getCommentId());
            statement.setString(2, comment.getVideoId());
            statement.setString(3, comment.getContent());
            statement.setString(4, comment.getAuthorId());
            statement.setString(5, comment.getPublishDate());
            statement.setInt(6, comment.getLikeCount());
            statement.execute();
            return true;
        } catch (Exception e) {
            log.printStackTrace(e);
            return false;
        }
    }

    /**
     * Retrieve YouTubeVideo data for specified ID from MySQL database
     *
     * @param videoId YouTube video ID
     * @return Relevant video data
     */
    public YouTubeVideo getVideoById(String videoId) {
        if (!healthy)
            return null;

        String channelId = null;
        String title = null;
        String description = null;
        String thumbnailUrl = null;
        int categoryId = -1;
        String uploadDate = null;
        ArrayList<String> tags = new ArrayList<String>();

        ResultSet resultSet;
        try {
            // Get video information
            PreparedStatement statement = connection.prepareStatement(
                    "select * from ytVideos where id = ?");
            statement.setString(1, videoId);
            resultSet = statement.executeQuery();
            resultSet.next();
            channelId = resultSet.getString("channelId");
            title = resultSet.getString("title");
            description = resultSet.getString("description");
            thumbnailUrl = resultSet.getString("thumbnailUrl");
            categoryId = resultSet.getInt("categoryId");
            uploadDate = resultSet.getString("uploadDate");

            // Get video tags
            statement = connection.prepareStatement(
                    "select * from tagRelations where videoId = ?");
            statement.setString(1, videoId);
            resultSet = statement.executeQuery();
            while (resultSet.next())
                tags.add(resultSet.getString("tag"));
        } catch (Exception e) {
            log.printStackTrace(e);
            return null;
        }

        return new YouTubeVideo(videoId, channelId, title, description, thumbnailUrl,
                tags.toArray(new String[tags.size()]), categoryId, uploadDate);
    }

    /**
     * Retrieve all stored ratings for specified video ID from MySQL database
     *
     * @param videoId YouTube video ID
     * @return YouTube rating as ArrayList of String-String tuples containing las2peer User ID and rating
     */
    public ArrayList<Tuple<String, String>> getRatingsByVideoId(String videoId) {
        if (!healthy)
            return null;

        ArrayList<Tuple<String, String>> result = new ArrayList<Tuple<String, String>>();
        try {
            PreparedStatement statement = connection.prepareStatement("select * from ytRatings where videoId = ?");
            statement.setString(1, videoId);
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next())
                result.add(new Tuple<String, String>(
                        resultSet.getString("userId"), resultSet.getString("rating")));
        } catch (Exception e) {
            log.printStackTrace(e);
            return null;
        }

        return result;
    }

    /**
     * Retrieve all stored ratings for specified user ID from MySQL database
     *
     * @param userId las2peer User Agent ID
     * @return YouTube rating as ArrayList of String-String tuples containing YouTube video ID and rating
     */
    public ArrayList<Tuple<String, String>> getRatingsByUserId(String userId) {
        if (!healthy)
            return null;

        ArrayList<Tuple<String, String>> result = new ArrayList<Tuple<String, String>>();
        try {
            PreparedStatement statement = connection.prepareStatement(
                    "select * from ytRatings where userId = ?");
            statement.setString(1, userId);
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next())
                result.add(new Tuple<String, String>(
                        resultSet.getString("videoId"), resultSet.getString("rating")));
        } catch (Exception e) {
            log.printStackTrace(e);
            return null;
        }

        return result;
    }

    /**
     * Retrieve all YouTube comments for specified video ID
     *
     * @param videoId YouTube video ID
     * @return ArrayList of relevant YouTube comment data
     */
    public ArrayList<YouTubeComment> getCommentsByVideoId(String videoId) {
        if (!healthy)
            return null;

        ArrayList<YouTubeComment> result = new ArrayList<YouTubeComment>();
        try {
            PreparedStatement statement = connection.prepareStatement("select * from ytComments where videoId = ?");
            statement.setString(1, videoId);
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next())
                result.add(new YouTubeComment(
                        resultSet.getString("id"),
                        videoId,
                        resultSet.getString("content"),
                        resultSet.getString("channelId"),
                        resultSet.getString("publishedDate"),
                        resultSet.getInt("likeCount")));
        } catch (Exception e) {
            log.printStackTrace(e);
            return null;
        }

        return result;
    }
}