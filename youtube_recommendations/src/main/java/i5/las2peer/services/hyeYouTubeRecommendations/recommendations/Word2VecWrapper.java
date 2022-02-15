package i5.las2peer.services.hyeYouTubeRecommendations.recommendations;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import i5.las2peer.logging.L2pLogger;
import i5.las2peer.services.hyeYouTubeRecommendations.YouTubeRecommendations;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;

/**
 * Word2VecWrapper
 *
 * This Class handles the communication with the Python microservice developed to perform the word2vec based semantic
 * word analysis.
 *
 */

public class Word2VecWrapper {

    private URL w2vUrl;
    private boolean modelLoaded;
    private static final L2pLogger log = L2pLogger.getInstance(YouTubeRecommendations.class.getName());

    /**
     * Constructor - Set remove word2vec implementation Url
     *
     * @param w2vUrl The location where the python server implementing the Word2Vec is running
     */
    public Word2VecWrapper(URL w2vUrl) {
        this.w2vUrl = w2vUrl;
        this.modelLoaded = false;
    }

    /**
     * Check whether model is currently loaded
     *
     * @return True if model is loaded, false otherwise
     */
    public boolean isModelLoaded() { return this.modelLoaded; }

    /**
     * Sets the location of the server running the Word2Vec implementation
     *
     * @param url New location of the server instance
     * @return Updated class instance
     */
    public Word2VecWrapper setW2VUrl(URL url) {
        this.w2vUrl = url;
        return this;
    }

    /**
     * Takes a Set of words and turns it into a Json array
     *
     * @param wordList Set of words associated with YouTube Video
     * @return List of words as Json array
     */
    private JsonArray parseWordList(HashSet<String> wordList) {
        JsonArray wordArray = new JsonArray();
        for (String word : wordList)
            wordArray.add(word);
        return wordArray;
    }

    /**
     * Sends the request to the python server running the word2vec implementation required to load the word2vec model
     *
     * @return Whether model was loaded successfully
     */
    public boolean loadModel() {
        try {
            // Send request to generate new model
            HttpURLConnection con = (HttpURLConnection) new URL(w2vUrl.toString() + "word2vec").openConnection();
            con.setRequestMethod("GET");
            con.connect();

            // Read response
            JsonObject response = new JsonObject();
            if (con.getResponseCode() == 200) {
                this.modelLoaded = true;
                return true;
            }
            return false;
        } catch (Exception e) {
            log.printStackTrace(e);
            return false;
        }
    }

    /**
     * Sends the request to the python server running the word2vec implementation to unload the word2vec model
     *
     * @return Whether model was freed successfully
     */
    public boolean freeModel() {
        try {
            // Send request to generate new model
            HttpURLConnection con = (HttpURLConnection) new URL(w2vUrl.toString() + "word2vec").openConnection();
            con.setRequestMethod("DELETE");
            con.connect();

            // Read response
            JsonObject response = new JsonObject();
            if (con.getResponseCode() == 200) {
                this.modelLoaded = false;
                return true;
            }
            return false;
        } catch (Exception e) {
            log.printStackTrace(e);
            return false;
        }
    }

    /**
     * Takes a json string and turns it into an ArrayList of double values
     *
     * @param jsonString Response string sent by remote Word2Vec implementation
     * @return Vector of word center as an array list of double values
     */
    private ArrayList<Double> parseWordVector(String jsonString) {
        ArrayList<Double> wordVector = new ArrayList<Double>();
        try {
            Gson gson = new Gson();
            wordVector = gson.fromJson(gson.fromJson(jsonString, JsonArray.class), ArrayList.class);
        } catch (Exception e) {
            log.printStackTrace(e);
            return null;
        }
        return wordVector;
    }

    /**
     * Sends the provided list of words as payload to the Word2Vec implementation and returns the computed center
     *
     * @param wordList Set of words associated to a YouTube video
     * @return Average of the Word2Vec representation of the given words as a Json array
     */
    public ArrayList<Double> computeWordCenter(HashSet<String> wordList) {
        try {
            // Send request to generate new model
            HttpURLConnection con = (HttpURLConnection) new URL(w2vUrl.toString() + "word2vec").openConnection();
            con.setRequestMethod("POST");
            // con.setRequestProperty("Content-Type", "application/json");
            con.setDoOutput(true);
            OutputStream os = con.getOutputStream();
            OutputStreamWriter osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);

            // Generate payload
            JsonArray requestBody = parseWordList(wordList);
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
                return parseWordVector(sb.toString());
            }
            return null;
        } catch (Exception e) {
            log.printStackTrace(e);
            return null;
        }
    }
}
