package i5.las2peer.services.hyeYouTubeRecommendations.recommendations;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import i5.las2peer.logging.L2pLogger;
import i5.las2peer.services.hyeYouTubeRecommendations.YouTubeRecommendations;
import rice.p2p.util.tuples.Tuple;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;

/**
 * MlLibWrapper
 *
 * This Class handles the communication with the Python microservice developed to perform the Matrix Factorization
 * using the Spark MlLin
 *
 */

public class MlLibWrapper {

    private URL mlLibUrl;
    private int rank;
    private int iterations;
    private double lambda;
    // This is kind of a bad way to map String IDs to int IDs, but it's so stupid that I have to that I don't care...
    // TODO probably this mapping should be resolved by adding a column to the MySQL data table (probably faster)
    private HashMap<String, Integer> userIds;
    private HashMap<String, Integer> itemIds;
    private HashMap<String, Double> ratingMappings;

    private static final L2pLogger log = L2pLogger.getInstance(YouTubeRecommendations.class.getName());

    /**
     * Constructor - Creates the mappings between data types used by the superordinate service and Spark's MlLib
     *
     * @param mlLibUrl The location where the python server implementing the Spark MlLib is running
     * @param ratingMappings A map of ratings given as strings mapping to doubles
     */
    public MlLibWrapper(URL mlLibUrl, HashMap<String, Double> ratingMappings) {
        this.mlLibUrl = mlLibUrl;
        this.userIds = new LinkedHashMap<String, Integer>();
        this.itemIds = new HashMap<String, Integer>();
        this.ratingMappings = ratingMappings;

        // Default values for required Matrix Factorization parameters
        this.rank = 10;
        this.iterations = 10;
        this.lambda = 0.01;
    }

    /**
     * Sets the location of the server running the MlLib implementation
     *
     * @param url New location of the server instance
     * @return Updated class instance
     */
    public MlLibWrapper setMlLibUrl(URL url) {
        this.mlLibUrl = url;
        return this;
    }

    /**
     * Sets the Matrix Factorization rank
     *
     * @param rank Number of features computed through Matrix Factorization
     * @return Updated class instance
     */
    public MlLibWrapper setRank(int rank) {
        this.rank = rank;
        return this;
    }

    /**
     * Sets the Matrix Factorization number of iterations
     *
     * @param iterations Number of ALS (alternating least squares) iterations performed
     * @return Updated class instance
     */
    public MlLibWrapper setIterations(int iterations) {
        this.iterations = iterations;
        return this;
    }

    /**
     * Sets the Matrix Factorization lambda value
     *
     * @param lambda New lambda regularization term
     * @return Updated class instance
     */
    public MlLibWrapper setLambda(double lambda) {
        this.lambda = lambda;
        return this;
    }

    /**
     * Sets the user ID map
     *
     * @param userIds New user ID map
     * @return Updated class instance
     */
    public MlLibWrapper setUserIds(HashMap<String, Integer> userIds) {
        this.userIds = userIds;
        return this;
    }

    /**
     * Sets the item ID map
     *
     * @param itemIds New item ID map
     * @return Updated class instance
     */
    public MlLibWrapper setItemIds(HashMap<String, Integer> itemIds) {
        this.itemIds = itemIds;
        return this;
    }

    /**
     * Returns the value currently associated with the given user ID or creates it if it doesn't exist, yet
     *
     * @param userId las2peer User Agent ID
     * @return Mapped int value
     */
    private int userIdToInt(String userId) {
        if (userIds.containsKey(userId))
            return userIds.get(userId);
        int mappedId = userIds.size();
        userIds.put(userId, mappedId);
        return mappedId;
    }

    /**
     * Returns the value currently associated with the given item ID or creates it if it doesn't exist, yet
     *
     * @param itemId YouTube video ID
     * @return Mapped int value
     */
    private int itemIdToInt(String itemId) {
        if (itemIds.containsKey(itemId))
            return itemIds.get(itemId);
        int mappedId = itemIds.size();
        itemIds.put(itemId, mappedId);
        return mappedId;
    }

    /**
     * Returns the value currently associated with the given rating
     *
     * @param rating YouTube video rating
     * @return The weight assigned to the given rating relation
     */
    private double ratingToDouble(String rating) {
        if (ratingMappings.containsKey(rating))
            return ratingMappings.get(rating);
        log.info("Rating " + rating + " not found in map " + ratingMappings.toString());
        // Fail
        return ratingMappings.get(rating);
    }

    /**
     * Returns the user ID with which the given int is currently associated (lookup by value)
     *
     * @param userAsInt Numeric string which should be stored as a value in the user ID map
     * @return The user ID which maps to the given integer
     */
    private String intToUserId(String userAsInt) {
        try {
            return userIds.keySet().toArray()[Integer.parseInt(userAsInt)].toString();
        } catch (Exception e) {
            log.printStackTrace(e);
            return null;
        }
    }

    /**
     * Updates the current weights associated with the different rating relations
     *
     * @param dislike New value of dislike rating
     * @param subscribe New value of subscribe relation
     * @param watch New value of watch relation
     * @param playlist New value of playlist relation
     * @param like New value of like rating
     * @return Updated class instance
     */
    public MlLibWrapper setRatings(Double dislike, Double subscribe, Double watch, Double playlist, Double like) {
        if (dislike != null)
            ratingMappings.put("dislike", dislike);
        if (subscribe != null)
            ratingMappings.put("subscribe", subscribe);
        if (watch != null)
            ratingMappings.put("watch", watch);
        if (playlist != null)
            ratingMappings.put("playlist", playlist);
        if (like != null)
            ratingMappings.put("like", like);
        return this;
    }

    /**
     * Takes a json string and turns it into a map of Strings and ArrayLists of Doubles
     *
     * @param jsonString Response string sent by remote MlLib implementation
     * @return Vectors of latent user features as a map {user_u: [feature_i, ...], ...}
     */
    public HashMap<String, ArrayList<Double>> parseFeatureVectors(String jsonString) {
        JsonObject userFeaturesJson = new JsonObject();
        Gson gson = new Gson();
        try {
            JsonObject responseJson = gson.fromJson(jsonString, JsonObject.class);
            userFeaturesJson = responseJson.get("userFeatures").getAsJsonObject();
        } catch (Exception e) {
            log.printStackTrace(e);
            return null;
        }
        HashMap<String, ArrayList<Double>> featureVectors = new HashMap<String, ArrayList<Double>>();
        for (String user : userFeaturesJson.keySet()) {
            try {
                JsonArray userFeatures = userFeaturesJson.get(user).getAsJsonArray();
                ArrayList<Double> features = gson.fromJson(userFeatures, ArrayList.class);
                featureVectors.put(intToUserId(user), features);
            } catch (Exception e) {
                log.printStackTrace(e);
                continue;
            }
        }
        return featureVectors;
    }

    /**
     * Takes the map of user rating tuples and turns them into a json object
     *
     * @param ratings Map of user IDs associated with ArrayLists of Tuples representing ratings
     * @return Ratings as json {user_u: {item_i: rating_ui, ...}, ...}
     */
    private JsonObject parseRatingData(HashMap<String, HashMap<String, String>> ratings) {
        JsonObject result = new JsonObject();
        if (ratings == null || ratings.isEmpty())
            return null;
        for (String user : ratings.keySet()) {
            HashMap<String, String> userRatings = ratings.get(user);
            JsonObject jsonRatings = new JsonObject();
            if (userRatings == null || userRatings.isEmpty())
                continue;
            for (String videoId : userRatings.keySet())
                jsonRatings.addProperty(String.valueOf(itemIdToInt(videoId)),
                        String.valueOf(ratingToDouble(userRatings.get(videoId))));
            result.add(String.valueOf(userIdToInt(user)), jsonRatings);
        }
        return result;
    }

    /**
     * Sends the provided ratings as payload to the MlLib implementation and returns the computed user features
     *
     * @param modelName Name under which model is stored at remote location
     * @param ratings Map of user IDs associated with ArrayLists of Tuples representing ratings
     * @return Latent user features computed based on provided ratings indicating user similarities
     */
    public JsonObject trainModel(String modelName, HashMap<String, HashMap<String, String>> ratings) {
        try {
            // TODO handle case where modelName is empty
            // Send request to generate new model
            HttpURLConnection con = (HttpURLConnection) new URL(mlLibUrl.toString() + "matrix-factorization/" +
                    modelName).openConnection();
            con.setRequestMethod("POST");
            // con.setRequestProperty("Content-Type", "application/json");
            con.setDoOutput(true);
            OutputStream os = con.getOutputStream();
            OutputStreamWriter osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);

            // Generate payload
            JsonObject requestBody = new JsonObject();
            requestBody.add("ratings", parseRatingData(ratings));
            requestBody.addProperty("rank", rank);
            requestBody.addProperty("iterations", iterations);
            requestBody.addProperty("lambda", lambda);

            // Write payload to request body
            osw.write(requestBody.toString());
            osw.flush();
            osw.close();
            os.close();
            con.connect();

            // Read response
            JsonObject response = new JsonObject();
            response.addProperty("status", con.getResponseCode());
            if (con.getResponseCode() == 200) {
                // Read response body
                BufferedReader br = new BufferedReader(new InputStreamReader((con.getInputStream())));
                StringBuilder sb = new StringBuilder();
                String payload;
                while ((payload = br.readLine()) != null) {
                    sb.append(payload);
                }
                response.addProperty("msg", sb.toString());
            } else {
                response.addProperty("msg", con.getResponseMessage());
            }
            return response;
        } catch (Exception e) {
            log.printStackTrace(e);
            return null;
        }
    }
}
