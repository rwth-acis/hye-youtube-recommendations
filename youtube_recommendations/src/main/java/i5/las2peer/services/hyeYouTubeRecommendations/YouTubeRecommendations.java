package i5.las2peer.services.hyeYouTubeRecommendations;

// import java.io.FileReader;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.*;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.http.*;
import com.google.api.services.youtube.YouTube;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import i5.las2peer.api.Context;
import i5.las2peer.api.persistency.Envelope;
import i5.las2peer.api.persistency.EnvelopeNotFoundException;
import i5.las2peer.api.security.AgentNotFoundException;
import i5.las2peer.api.security.ServiceAgent;
import i5.las2peer.logging.L2pLogger;
import i5.las2peer.api.ManualDeployment;
import i5.las2peer.api.security.UserAgent;
import i5.las2peer.restMapper.RESTService;
import i5.las2peer.restMapper.annotations.ServicePath;

import i5.las2peer.services.hyeYouTubeRecommendations.recommendations.MlLibWrapper;
import i5.las2peer.services.hyeYouTubeRecommendations.recommendations.Word2VecWrapper;
import i5.las2peer.services.hyeYouTubeRecommendations.util.DataBaseConnection;
import i5.las2peer.services.hyeYouTubeRecommendations.util.TokenWrapper;
import i5.las2peer.services.hyeYouTubeRecommendations.youTubeData.YouTubeApiWrapper;
import i5.las2peer.services.hyeYouTubeRecommendations.youTubeData.YouTubeComment;
import i5.las2peer.services.hyeYouTubeRecommendations.youTubeData.YouTubeVideo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Contact;
import io.swagger.annotations.Info;
import io.swagger.annotations.License;
import io.swagger.annotations.SwaggerDefinition;

import com.google.api.client.auth.oauth2.*;
import com.google.api.client.http.apache.v2.ApacheHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import rice.p2p.util.tuples.Tuple;

/**
 * HyE - YouTube Recommendations
 * 
 * This service is used to obtain user data from YouTube via the YouTube Data API,
 * which are used to generate custom video recommendations.
 * To this end, it stores las2peer identities and associates them with YouTube IDs
 * which are used to obtain YouTube watch data.
 * 
 */

@Api
@SwaggerDefinition(
		info = @Info(
				title = "Custom YouTube Recommender System",
				version = "0.1",
				description = "Part of How's your Experience. Used to obtain data from YouTube and generate custom content recommendations.",
				termsOfService = "http://your-terms-of-service-url.com",
				contact = @Contact(
						name = "Michael Kretschmer",
						email = "kretschmer@dbis.rwth-aachen.de"),
				license = @License(
						name = "your software license name",
						url = "http://your-software-license-url.com")))

@ManualDeployment
@ServicePath("/hye-recommendations")
public class YouTubeRecommendations extends RESTService {

	private String clientId;
	private String clientSecret;
	private String apiKey;
	private String mysqlHost;
	private String mysqlDatabase;
	private String mysqlUser;
	private String mysqlPassword;
	private String mlLibUrl;
	private String modelName;
	private String serviceAgentName;
	private String serviceAgentPw;

	// TODO change user based word2vec storage to service based
	private final String ROOT_URI = "http://localhost:8080/hye-recommendations";
	private final String LOGIN_URI = ROOT_URI + "/login";
	private final String AUTH_URI = ROOT_URI + "/auth";
	private final String MF_MODEL_SUFFIX = "_MF-Model";
	private final String W2V_MODEL_SUFFIX = "_W2V-Model";
	private final String ALPHA_SUFFIX = "_Alpha";
	private static final L2pLogger log = L2pLogger.getInstance(YouTubeRecommendations.class.getName());

	private final AuthorizationCodeFlow flow;
	private final HttpTransport transport;
	private final GsonFactory json;
	private URL mlUrl;
	// Store access token in frontend instead
	private static HashMap<String, YouTubeApiWrapper> ytConnections;
	private static DataBaseConnection db;

	/**
	 * Class constructor, initializes member variables
	 */
	HttpRequestInitializer httpRequestInitializer;
	public YouTubeRecommendations() {
		setFieldValues();
		log.info("Using API key " + apiKey + " with client id " + clientId + " and secret " + clientSecret +
				" and connecting to jdbc:mysql://" + mysqlHost + '/' + mysqlDatabase + " as " + mysqlUser +
				" and obtaining model " + modelName + " from server running at " + mlLibUrl +
				" using service agent " + serviceAgentName + " with password " + serviceAgentPw);
		transport = new ApacheHttpTransport();
		json = new GsonFactory();
		flow = new GoogleAuthorizationCodeFlow.Builder(transport, json, clientId, clientSecret,
				Arrays.asList("https://www.googleapis.com/auth/youtube")).setAccessType("offline").build();
		if (ytConnections == null)
			ytConnections = new HashMap<String, YouTubeApiWrapper>();
		YouTubeApiWrapper.setApiKey(apiKey);
		if (db == null) {
			db = new DataBaseConnection(mysqlHost, mysqlDatabase, mysqlUser, mysqlPassword);
			if (db.isHealthy())
				db.init();
			else
				log.severe("!!! No database connection. The functionality of the service is severely limited !!!");
		}
		try {
			mlUrl = new URL(this.mlLibUrl);
		} catch (Exception e) {
			log.severe("!!! Provided mlLibUrl is invalid. The functionality of the service is severely limited !!!");
			mlUrl = null;
		}
		// Try to unlock provided user
		try {
			Context context = Context.getCurrent();
			UserAgent serviceAgent = (UserAgent) context.fetchAgent(
					context.getUserAgentIdentifierByLoginName(serviceAgentName));
			serviceAgent.unlock(serviceAgentPw);
		} catch (Exception e) {
			log.severe("!!! Provided service agent credentials are invalid. Prepare for many errors !!!");
		}
	}

	private Response buildResponse(int status, String msg) {
		JsonObject response = new JsonObject();
		response.addProperty("status", status);
		response.addProperty("msg", msg);
		return Response.status(status).entity(response.toString()).build();
	}

	private Response buildResponse(int status, JsonObject msg) {
		JsonObject response = new JsonObject();
		response.addProperty("status", status);
		response.add("msg", msg);
		return Response.status(status).entity(response.toString()).build();
	}

	/**
	 * Helper function to retrieve a user's YouTube ID
	 *
	 * @param user The User Agent whose YouTube ID we are interested in
	 * @return The YouTube ID linked to the given user
	 */
	private String getUserId(UserAgent user) { return user.getIdentifier(); }

	/**
	 * Helper function to retrieve YouTube authorization data for the given user
	 *
	 * @param userId A las2peer User Agent ID
	 * @return A valid authorization token belonging to that user
	 */
	private Credential getCredentials(String userId) {
		if (!ytConnections.containsKey(userId))
			return null;
		return ytConnections.get(userId).getCredential();
	}

	private Credential getCredentials(UserAgent user) { return getCredentials(getUserId(user)); }

	/**
	 * Helper function to store the given authorization code for the given user
	 *
	 * @param userId A las2peer User Agent ID
	 * @param credential Google Authorization data
	 */
	private void storeConnection(String userId, Credential credential) {
		if (ytConnections.containsKey(userId)) {
			ytConnections.get(userId).refreshConnection(credential);
			log.info("Authorization code updated for user " + userId);
		} else {
			ytConnections.put(userId, new YouTubeApiWrapper(credential));
			log.info("Authorization code stored for user " + userId);
		}
	}

	/**
	 * Helper function to retrieve the YouTube connection stored for the given user
	 *
	 * @param userId The las2peer Agent ID of the user we are interested in
	 * @return YouTube Data API connection if one exists
	 */
	private YouTubeApiWrapper getConnection(String userId) {
		if (ytConnections.containsKey(userId))
			return ytConnections.get(userId);
		return null;
	}

	private YouTubeApiWrapper getConnection(UserAgent user) { return getConnection(getUserId(user)); }

	/**
	 * Helper function to retrieve an access token with the given authorization code
	 *
	 * @param code A Google OAuth authorization code
	 * @return Either a valid access token or an appropriate error message
	 */
	private TokenResponse getAccessToken(String code) {
		TokenResponse token;
		log.info("Sending token request");

		try {
			token = flow.newTokenRequest(code).setRedirectUri(AUTH_URI).execute();
		} catch (Exception e) {
			log.printStackTrace(e);
			return null;
		}
		return token;
	}

	/**
	 * Helper function to update local data storage with relevant YouTube watch data a given user
	 *
	 * @param ytConnection The YouTube connection stored for the user in question
	 * @return Amount of video data objects updated
	 */
	private int synchronizeYouTubeData(String userId, YouTubeApiWrapper ytConnection) {
		if (!db.isHealthy()) {
			log.severe("Database connection not healthy! Aborting YouTube synchronization.");
			return -1;
		}

		// TODO this seems terribly inefficient... if you have any ideas, let me know
		HashMap<String, ArrayList<YouTubeVideo>> videoData = ytConnection.getYouTubeWatchData();
		int dbInsertions = 0;
		for (String rating : videoData.keySet()) {
			if (videoData.get(rating) == null)
				continue;
			ArrayList<YouTubeVideo> videoRatings = videoData.get(rating);
			Iterator<YouTubeVideo> videoIt = videoRatings.iterator();
			// Not sure how many videos can be requested at once, so let's stick to 10 for now
			String[] videoIdList = new String[10];
			int itemCount = 0;
			while (videoIt.hasNext()) {
				YouTubeVideo ytVideo = videoIt.next();
				// Check whether video data is complete
				if (!ytVideo.isComplete()) {
					// First, check database
					YouTubeVideo dbVideo = db.getVideoById(ytVideo.getVideoId());
					if (dbVideo != null)
						ytVideo = dbVideo;
					else {
						// Otherwise, request data from YouTube Data API, but bundle requests to save quota tokens
						videoIdList[itemCount] = ytVideo.getVideoId();
						itemCount++;
						if (itemCount == 10 || !videoIt.hasNext()) {
							// Just append results to current list
							videoRatings.addAll(YouTubeApiWrapper.getVideoDetails(videoIdList,
									flow.getRequestInitializer()));
							itemCount = 0;
						}
					}
				}
				// Assume that video insertion failing means the video had already been added before
				// Which also means, once recorded, comments don't get updated
				if (db.addVideo(ytVideo)) {
					dbInsertions++;
					ArrayList<YouTubeComment> comments =
							YouTubeApiWrapper.getComments(ytVideo.getVideoId(), flow.getRequestInitializer());
					if (comments == null)
					    continue;
					Iterator<YouTubeComment> commentIt = comments.iterator();
					while (commentIt.hasNext()){
						db.addComment(commentIt.next());
						dbInsertions++;
					}
				}
				db.addRating(ytVideo.getVideoId(), userId, rating);
			}
		}
		// Lastly store alpha value to signify that user synchronized DB
		try {
			Context context = Context.getCurrent();
			Envelope env = context.createEnvelope(getAlphaHandle(userId));
			env.setContent(0.5);
			context.storeEnvelope(env);
		} catch(Exception e) {
			log.printStackTrace(e);
		}
		return dbInsertions;
	}

	/**
	 * Helper function to split the given data set into two data sets with the given proportions
	 *
	 * @param data The entire data set
	 * @param testDataPercentage The percentage of the total data that is not included to train the model
	 * @return A tuple containing the (usually larger) training set in a and the test data set in b
	 */
	private Tuple<ArrayList<Tuple<String, String>>, ArrayList<Tuple<String, String>>> splitData(
			ArrayList<Tuple<String, String>> data, int testDataPercentage) {
		ArrayList<Tuple<String, String>> trainData = new ArrayList<Tuple<String, String>>();
		ArrayList<Tuple<String, String>> testData = new ArrayList<Tuple<String, String>>();

		for (Tuple<String, String> tuple : data) {
			if (Math.random() < testDataPercentage)
				testData.add(tuple);
			else
				trainData.add(tuple);
		}

		return new Tuple<ArrayList<Tuple<String, String>>, ArrayList<Tuple<String, String>>>(trainData, testData);
	}

	/**
	 * Helper function to retrieve handle used to store matrix-factorization user features
	 *
	 * @return Handle for envelope
	 */
	private String getMatrixHandle() {
		try {
			return Context.getCurrent().getUserAgentIdentifierByLoginName(serviceAgentName) + MF_MODEL_SUFFIX;
		} catch (Exception e) {
			log.printStackTrace(e);
			return null;
		}
	}

	/**
	 * Helper function to retrieve handle used to store word2vec user vectors
	 *
	 * @return Handle for envelope
	 */
	private String getVectorHandle() {
		try {
			return Context.getCurrent().getUserAgentIdentifierByLoginName(serviceAgentName) + W2V_MODEL_SUFFIX;
		} catch (Exception e) {
			log.printStackTrace(e);
			return null;
		}
	}

	/**
	 * Helper function to retrieve handle used to store alpha value for given user
	 *
	 * @param userId las2peer Agent ID of storing user
	 * @return Handle for envelope
	 */
	private String getAlphaHandle(String userId) { return userId + ALPHA_SUFFIX; }

	/**
	 * Helper function to store user feature or word2vec vectors in las2peer network
	 *
	 * @param context Current execution context from which function is called
	 * @param envHandle Handle used to store envelope
	 * @param content Map with user IDs mapping to ArrayLists of double values
	 * @return True if storing succeeded, False otherwise
	 */
	private boolean storeEnvelope(Context context, String envHandle, Serializable content) {
		Envelope env;
		UserAgent serviceAgent;
		try {
			serviceAgent = (UserAgent) context.fetchAgent(
					context.getUserAgentIdentifierByLoginName(serviceAgentName));
			serviceAgent.unlock(serviceAgentPw);
		} catch (Exception e) {
			log.severe("Invalid service account credentials!");
			return false;
		}

		// See whether envelope already exists
		try {
			env = context.requestEnvelope(envHandle, serviceAgent);
		} catch (EnvelopeNotFoundException e) {
			// Envelope does not exist
			env = null;
		} catch (Exception e) {
			log.printStackTrace(e);
			return false;
		}

		// Else create envelope
		if (env == null) {
			try {
				env = context.createEnvelope(envHandle, serviceAgent);
			} catch (Exception e) {
				log.printStackTrace(e);
				return false;
			}
		}

		// Store content
		try {
			env.setContent(content);
			context.storeEnvelope(env, serviceAgent);
		} catch (Exception e) {
			log.printStackTrace(e);
			return false;
		}
		return true;
	}

	/**
	 * Loads envelope content using provided service account credentials
	 *
	 * @param context Current execution context from which function is called
	 * @param envHandle Handle under which envelope is stored in network
	 * @return content Envelope content
	 */
	private Serializable getEnvelopeContent(Context context, String envHandle) {
		UserAgent serviceAgent;
		try {
			serviceAgent = (UserAgent) context.fetchAgent(
					context.getUserAgentIdentifierByLoginName(serviceAgentName));
			serviceAgent.unlock(serviceAgentPw);
		} catch (Exception e) {
			log.severe("Invalid service account credentials!");
			return null;
		}
		try {
			return context.requestEnvelope(envHandle, serviceAgent).getContent();
		} catch(Exception e) {
			log.printStackTrace(e);
			return null;
		}
	}

	/**
	 * Remove punctuation and split string by whitespace
	 *
	 * @param string The String of which we want to extract the words
	 * @return Set of words contained in given string as HashSet
	 */
	private HashSet<String> getWordsFromString(String string) {
		// Remove punctuation
		String cleanedDescription = string.replaceAll("\\p{Punct}", "");
		// Split by whitespace
		String[] wordArray = cleanedDescription.split("\\s+");
		// Return as HashSet
		HashSet wordSet = new HashSet<String>();
		wordSet.addAll(Arrays.asList(wordArray));
		return wordSet;
	}

	/**
	 * Gets all words from string associated with the YouTube video
	 *
	 * @param detail Might be title or description of YouTube Video given as String
	 * @return Set of words contained in given detail as HashSet
	 */
	private HashSet<String> parseVideoDetail(String detail) {
		try {
			return getWordsFromString(detail);
		} catch (Exception e) {
			log.printStackTrace(e);
			return null;
		}
	}

	/**
	 * Gets all words from the video tags
	 *
	 * @param videoTags Strings tagged to video as ArrayList
	 * @return Strings tagged to video as HashSet
	 */
	private HashSet<String> parseTags(String[] videoTags) {
		try {
			HashSet<String> tagSet = new HashSet<String>();
			tagSet.addAll(Arrays.asList(videoTags));
			return tagSet;
		} catch (Exception e) {
			log.printStackTrace(e);
			return null;
		}
	}

	/**
	 * Gets all words from the video comments
	 *
	 * @param videoComments Comments associated with YouTube Video as ArrayList
	 * @return All words from given list of YouTube Comments as HashSet
	 */
	private HashSet<String> parseComments(ArrayList<YouTubeComment> videoComments) {
		if (videoComments == null)
			return null;
		HashSet<String> wordSet = new HashSet<String>();
		for (YouTubeComment comment : videoComments) {
			try {
				wordSet.addAll(getWordsFromString(comment.getContent()));
			} catch (Exception e) {
				log.printStackTrace(e);
				continue;
			}
		}
		return wordSet;
	}

	/**
	 * Finds the best match out of the given users for the requesting user based on the previously computed machine-
	 * learning models
	 *
	 * @param userIds HashSet of las2peer User Agent IDs who should be considered as possbile matches
	 * @param request The YouTube request for which the users are matched (e.g., specific video, or search query)
	 * @return The las2peer Agent ID which optimizes both inverted collaborative filtering and semantic closeness
	 */
	public String findMatch(HashSet<String> userIds, String request) {
		// TODO include request data
		Context context = Context.getCurrent();
		HashMap<String, ArrayList<Double>> mfModel =
				(HashMap<String, ArrayList<Double>>) getEnvelopeContent(context, getMatrixHandle());
		HashMap<String, ArrayList<Double>> w2vModel =
				(HashMap<String, ArrayList<Double>>) getEnvelopeContent(context, getVectorHandle());
		if (mfModel == null || w2vModel == null) {
			log.severe("Cannot compute match without models!");
			return null;
		}

		String userId = getUserId((UserAgent) context.getMainAgent());
		ArrayList<Double> userMfVec = mfModel.get(userId);
		ArrayList<Double> userW2vVec = w2vModel.get(userId);
		if (userMfVec == null || w2vModel == null) {
			log.severe("Missing machine-learning model for user!");
			return null;
		}

		// Compute collaborative filtering and word2vec similarity
		HashMap<String, Tuple<Double, Double>> matchVals = new HashMap<String, Tuple<Double, Double>>();
		double maxCfVal = 0;
		double maxW2vVal = 0;
		for (String matchId : userIds) {
			if (userId.equals(matchId))
				continue;
			// Compute similarity based on collaborative filtering
			double cfVal = 0;
			ArrayList<Double> matchMfVector = mfModel.get(matchId);
			for (int i = 0; i < userMfVec.size(); i++)
				cfVal += Math.pow(userMfVec.get(i) - matchMfVector.get(i), 2);
			cfVal = Math.sqrt(cfVal);
			if (cfVal > maxCfVal)
				maxCfVal = cfVal;
			// Compute similarity based on word2vec vectors
			double w2vVal = 0;
			ArrayList<Double> matchW2vVec = w2vModel.get(matchId);
			for (int i = 0; i < userW2vVec.size(); i++)
				w2vVal += Math.pow(userW2vVec.get(i) - matchW2vVec.get(i), 2);
			w2vVal = Math.sqrt(w2vVal);
			if (w2vVal > maxW2vVal)
				maxW2vVal = w2vVal;
			matchVals.put(matchId, new Tuple<Double, Double>(cfVal, w2vVal));
			
		}
		// Get alpha value for user (if non was ever set, set to neutral)
		double alpha = 0.5;
		try {
			alpha = (Double) context.requestEnvelope(getAlphaHandle(userId)).getContent();
		} catch (Exception e) {
			log.printStackTrace(e);
			log.info("No alpha value stored for user " + userId + " using 0.5");
		}
		// Since we later divide by these values, they should not be zero
		if (maxCfVal == 0)
			maxCfVal = 1;
		if (maxW2vVal == 0)
			maxW2vVal = 1;
		// Compute best matching value (cfVal should be as high as possible, w2vVal as low as possible)
		String bestMatchId = null;
		double bestMatchVal = 0;
		for (String matchId : matchVals.keySet()) {
			if (userId.equals(matchId))
				continue;
			// Note, that cfVal and w2vVal are normalized, due to (likely) differences in dimensionality
			Tuple<Double, Double> matchT = matchVals.get(matchId);
			double matchVal = ((1-alpha) * (matchT.a()/maxCfVal)) - (alpha * (matchT.b()/maxW2vVal));
			// debug info
			log.info("Computed match value " + String.valueOf(matchVal) + " for user pair " + userId + ", " + matchId);
			if (matchVal > bestMatchVal) {
				bestMatchVal = matchVal;
				bestMatchId = matchId;
			}
		}
		return bestMatchId;
	}

	/**
	 * Returns all data stored for requesting user in database
	 *
	 * @return YouTube data of requesting user
	 */
	@GET
	@Path("/")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(
			value = "YouTube",
			notes = "Returns YouTube watch data")
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "OK") })
	public Response getYouTubeDate() {
		UserAgent user;
		String userId;
		try {
			user = (UserAgent) Context.getCurrent().getMainAgent();
			userId = getUserId(user);
		} catch (Exception e) {
			log.printStackTrace(e);
			return buildResponse(401, "Unable to get user agent. Are you logged in?");
		}

		if (!db.isHealthy())
			return buildResponse(500, "No database connection!");

		HashMap<String, String> watchData = db.getRatingsByUserId(userId);
		return buildResponse(200, new Gson().toJson(watchData, HashMap.class));
	}

	/**
	 * Sends requests to YouTube Data API in order to get relevant YouTube watch data for current user
	 *
	 * @return Status code indicating whether synchronization was successful
	 */
	@POST
	@Path("/")
	@Produces(MediaType.TEXT_PLAIN)
	@ApiOperation(
			value = "YouTube",
			notes = "Updates YouTube watch data")
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "OK") })
	public Response updateYouTubeData() {
		// TODO add constraint so that this function can only be called once per week/twice per month/etc. for same user (due to quota restraints)
		// Check for access token of current user in memory
		String userId;
		try {
			userId = getUserId((UserAgent) Context.getCurrent().getMainAgent());
		} catch (Exception e) {
			log.printStackTrace(e);
			return Response.status(401).entity("Unable to get user agent. Are you logged in?").build();
		}

		YouTubeApiWrapper ytConnection = getConnection(userId);
		if (ytConnection == null || !ytConnection.intactConnection()) {
			return Response.status(403).entity("No valid login data stored for user! Please login to YouTube and come" +
					" back afterwards.").build();
		}
		try {
			synchronizeYouTubeData(userId, ytConnection);
			return Response.ok().entity("YouTube Data successfully synchronized.").build();
		} catch (Exception e) {
			log.printStackTrace(e);
			return Response.status(500).entity("Unspecified server error").build();
		}
	}

	/**
	 * Removes all rating data stored for requesting user
	 *
	 * @return Message indicating whether deletion was successful
	 */
	@DELETE
	@Path("/")
	@Produces(MediaType.TEXT_PLAIN)
	@ApiOperation(
			value = "YouTube",
			notes = "Deletes YouTube watch data")
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "OK") })
	public Response deleteYouTubeDate() {
		UserAgent user;
		String userId;
		try {
			user = (UserAgent) Context.getCurrent().getMainAgent();
			userId = getUserId(user);
		} catch (Exception e) {
			log.printStackTrace(e);
			return Response.status(401).entity("Unable to get user agent. Are you logged in?").build();
		}

		if (!db.isHealthy())
			return Response.serverError().entity("No database connection!").build();
		if (db.deleteRatingsByUserId(userId))
			return Response.ok().build();
		else
			return Response.serverError().entity("Unspecified server error").build();
	}

	/**
	 * Return alpha value stored for requesting user
	 *
	 * @return Alpha value stored for user or error message
	 */
	@GET
	@Path("/alpha")
	@Produces(MediaType.TEXT_PLAIN)
	@ApiOperation(
			value = "YouTube",
			notes = "Returns user's alpha value")
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "OK") })
	public Response getAlpha() {
		// Get requesting user
		Context context;
		UserAgent user;
		String userId;
		try {
			context = Context.getCurrent();
			user = (UserAgent) context.getMainAgent();
			userId = getUserId(user);
		} catch (Exception e) {
			log.printStackTrace(e);
			return Response.status(401).entity("Unable to get user agent. Are you logged in?").build();
		}

		// Get alpha value and return it
		Double alpha;
		try {
			alpha = (Double) context.requestEnvelope(getAlphaHandle(userId)).getContent();
		} catch (EnvelopeNotFoundException e) {
			return Response.status(404).entity("No alpha value stored for you.").build();
		} catch (Exception e) {
			log.printStackTrace(e);
			return Response.serverError().entity("Unspecified server error.").build();
		}
		return Response.ok().entity(alpha).build();
	}

	/**
	 * Update alpha value used to balance recommendations between serendipity and topical similarity
	 *
	 * @alphaVal New value for alpha
	 * @return Status code indicating whether update was successful
	 */
	@POST
	@Path("/alpha")
	@Consumes(MediaType.TEXT_PLAIN)
	@Produces(MediaType.TEXT_PLAIN)
	@ApiOperation(
			value = "YouTube",
			notes = "Updates YouTube watch data")
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "OK") })
	public Response updateAlpha(String alphaVal) {
		// Get requesting user
		Context context;
		UserAgent user;
		String userId;
		try {
			context = Context.getCurrent();
			user = (UserAgent) context.getMainAgent();
			userId = getUserId(user);
		} catch (Exception e) {
			log.printStackTrace(e);
			return Response.status(401).entity("Unable to get user agent. Are you logged in?").build();
		}

		// First check, if alpha value has been created
		Envelope env;
		try {
			env = context.requestEnvelope(getAlphaHandle(userId));
		} catch (EnvelopeNotFoundException e) {
			return Response.status(403).entity("Please synchronize your YouTube data first.").build();
		} catch (Exception e) {
			log.printStackTrace(e);
			return Response.serverError().entity("Unspecified server error.").build();
		}
		// Check given alpha value
		Double alpha;
		try {
			if (alphaVal == null)
				throw new NullPointerException("Alpha is null.");
			alpha = Double.valueOf(alphaVal);
		} catch (Exception e) {
			return Response.status(400).entity("Unable to turn given value " + alphaVal + " into number.").build();
		}
		if (alpha < 0 || alpha > 1)
			return Response.status(400).entity("Alpha must be a value between 0 and 1.").build();
		try {
			env.setContent(alpha);
			context.storeEnvelope(env);
		} catch(Exception e) {
			log.printStackTrace(e);
			return Response.serverError().entity("Unable to store value.").build();
		}
			
		return Response.ok().build();
	}

	/**
	 * Login function used to perform OAuth login.
	 *
	 * @return A redirect to a Google OAuth page
	 */
	@GET
	@Path("/login")
	@Produces(MediaType.TEXT_PLAIN)
	@ApiOperation(
			value = "YouTube - Login",
			notes = "Sends user to Google login page")
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "OK") })
	public Response login() {
		return Response.temporaryRedirect(URI.create(flow.newAuthorizationUrl().setRedirectUri(AUTH_URI).build()))
				.build();
	}

	/**
	 * Authentication function used to handle YouTube OAuth logins.
	 * TODO Since the auth code is sent as get parameter I think it is written to the server log, which would be like a massive security issue, wouldn't it? Check that
	 *
	 * @param code The authentication code returned by Google.
	 * @return A valid YouTube API Access Token
	 */
	@GET
	@Path("/auth")
	@Produces(MediaType.TEXT_PLAIN)
	@ApiOperation(
			value = "YouTube - Authentication",
			notes = "Handles the OAuth login to Google")
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "OK") })
	public Response auth(@QueryParam("code") String code) {
		// Test authorization code
		TokenResponse token = getAccessToken(code);
		if (token == null) {
			return Response.serverError().entity("Could not get access token with authorization code " + code).build();
		}

		UserAgent user = (UserAgent) Context.getCurrent().getMainAgent();
		Credential credential = null;
		TokenWrapper tokenWrapper = null;
		try {
			String userId = getUserId(user);
			credential = flow.createAndStoreCredential(token, userId);
			storeConnection(userId, credential);
		} catch (Exception e) {
			log.printStackTrace(e);
			return Response.serverError().entity("Error storing access token.").build();
		}

		return Response.ok().entity("Login successful.").build();
	}

	/**
	 * Computes a Matrix Factorization model from the rating data stored in the linked database
	 *
	 * @return The user's feature vectors computed based on the given rating data
	 */
	@GET
	@Path("/matrix-factorization")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(
			value = "Create MF model",
			notes = "Creates a Matrix Factorization model based on stored data and returns user features")
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "OK") })
	public Response createMfModel() {
		// Check if service is set up correctly
		if (!db.isHealthy())
			return buildResponse(500, "No database connection!");
		if (mlUrl == null)
			return buildResponse(500, "No connection to remote machine learning implementation!");
		// Only node/service admins are allowed to call this function
		try {
			Context context = Context.getCurrent();
			if (!context.getMainAgent().getIdentifier().equals(
					context.getUserAgentIdentifierByLoginName(serviceAgentName)))
				return buildResponse(403, "This function can only be called by service agent!");
		} catch (Exception e) {
			return buildResponse(401, "Could not get execution context. Are you logged in?");
		}
		// Retrieve rating data from MySQL database
		HashMap<String, HashMap<String, String>> ratingData = db.getAllRatings();
		if (ratingData == null)
			return buildResponse(500, "There was an error retrieving watch data from the database.");
		if (ratingData.isEmpty())
			return buildResponse(400, "There is currently no YouTube watch data stored in the database.");

		// TODO outsource this somewhere
		HashMap<String, Double> ratingMappings = new HashMap<String, Double>();
		ratingMappings.put("dislike", -1.0);
		ratingMappings.put("subscribe", 1.0);
		ratingMappings.put("watch", 2.0);
		ratingMappings.put("playlist", 2.0);
		ratingMappings.put("like", 3.0);

		MlLibWrapper mlLib = new MlLibWrapper(mlUrl, ratingMappings);
		JsonObject remoteServiceResponse = mlLib.trainModel(
				modelName, ratingData);
		if (remoteServiceResponse == null)
			return buildResponse(500, "Internal server error while trying to train model.");
		if (remoteServiceResponse.get("status").getAsInt() != 200)
			return buildResponse(remoteServiceResponse.get("status").getAsInt(),
					remoteServiceResponse.get("msg").getAsString());
		if (!storeEnvelope(Context.getCurrent(),
				getMatrixHandle(),  mlLib.parseFeatureVectors(remoteServiceResponse.get("msg").getAsString()))) {
			log.warning("Could not store matrix!");
		}
		return buildResponse(200, remoteServiceResponse.get("msg").getAsString());
	}

	/**
	 * Sends requests to the remote word2vec implementation to compute the center of the textual video data stored in
	 * the database for the requesting user
	 * TODO restrict access, normal users should not be able to trigger this function (due to computational overhead)
	 *
	 * @return The user's word vector computed based on the given video text data
	 */
	@GET
	@Path("/word2vec")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(
			value = "Find Word2Vec center",
			notes = "Uses the textual video data stored in the database for requesting user and returns the center in" +
					"the Word2Vec model")
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "OK") })
	public Response computeW2V() {
		// Check if service is set up correctly
		if (!db.isHealthy())
			return buildResponse(500, "No database connection!");
		if (mlUrl == null)
			return buildResponse(500, "No connection to remote machine learning implementation!");
		// Only node/service admins are allowed to call this function
		try {
			Context context = Context.getCurrent();
			if (!context.getMainAgent().getIdentifier().equals(
					context.getUserAgentIdentifierByLoginName(serviceAgentName)))
				return buildResponse(403, "This function can only be called by service agent!");
		} catch (Exception e) {
			return buildResponse(401, "Could not get execution context. Are you logged in?");
		}

		// Get ratings data from database
		HashMap<String, HashMap<String, String>> ratingData = db.getAllRatings();
		if (ratingData == null) {
			return buildResponse(500, "Unable to get video data from database.");
		}
		HashMap<String, ArrayList<Double>> w2vVectors = new HashMap<String, ArrayList<Double>>();
		int vectorSize = 0;
		// Set up word2vec implementation for usage
		Word2VecWrapper w2v = new Word2VecWrapper(mlUrl);
		for (String userId : ratingData.keySet()) {
			// Initialize user vector
			ArrayList<Double> userVector = new ArrayList<Double>();
			// Add up semantic centers computed for videos rated by user
			int noVideos = 0;
			HashMap<String, String> userRatings = ratingData.get(userId);
			for (String videoId : userRatings.keySet()) {
				// Check database for video center
				ArrayList<Double> videoVector = db.getVideoCenter(videoId);
				if (videoVector == null || videoVector.isEmpty()) {
					// Else compute
					HashSet<String> bagOfWords = new HashSet<String>();
					// Load model
					if (!(w2v.isModelLoaded() || w2v.loadModel()))
						return buildResponse(500, "Could not load word2vec model");
					log.info("Computing center of video " + videoId);
					YouTubeVideo videoData = db.getVideoById(videoId);
					if (videoData == null)
						continue;
					bagOfWords.addAll(parseVideoDetail(videoData.getTitle()));
					bagOfWords.addAll(parseVideoDetail(videoData.getDescription()));
					bagOfWords.addAll(parseTags(videoData.getTags()));
					// Including comments seems to make result worse
					// bagOfWords.addAll(parseComments(db.getCommentsByVideoId(videoId)));
					log.info(bagOfWords.toString());
					videoVector = w2v.computeWordCenter(bagOfWords);
					if (videoVector == null)
						continue;
					db.addVideoCenter(videoId, videoVector);
				}

				// Use first result to determine size of vectors
				if (vectorSize == 0)
					vectorSize = videoVector.size();
				else if (videoVector.size() != vectorSize) {
					log.warning("Invalid vector size (" + videoVector.size() + ") for video " + videoId);
					continue;
				}
				for (int i = 0; i < vectorSize; i++) {
					if (userVector.size() < vectorSize) {
						userVector.add(videoVector.get(i));
					} else {
						try {
							userVector.set(i, userVector.get(i) + videoVector.get(i));
						} catch (Exception e) {
							log.warning("Malformed word vector " + videoVector.toString());
							noVideos--;
							break;
						}
					}
				}
				noVideos++;
			}
			// Compute average (center)
			for (int i = 0; i < userVector.size(); i++)
				userVector.set(i, userVector.get(i) / noVideos);
			w2vVectors.put(userId, userVector);
		}
		if (w2v.isModelLoaded() && !w2v.freeModel())
			log.warning("Could not free model!");
		// Store vectors
		if (!storeEnvelope(Context.getCurrent(),
				getVectorHandle(), w2vVectors))
			log.warning("Could not store user vector!");
		return buildResponse(200, new Gson().toJson(w2vVectors, HashMap.class));
	}

	/**
	 * Gets video information of YouTube video associated with given ID from database or via YouTube Data API
	 *
	 * @param videoId YouTube Video ID of video in question
	 * @return Video information or error message
	 */
	@GET
	@Path("/video")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(
			value = "YouTube",
			notes = "Gets YouTube video data")
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "OK") })
	public Response getVideoInformation(@QueryParam("id") String videoId) {
		if (videoId == null || videoId.length() < 1) {
			return buildResponse(400, "Missing video ID!");
		}
		String userId;
		try {
			userId = getUserId((UserAgent) Context.getCurrent().getMainAgent());
		} catch (Exception e) {
			log.printStackTrace(e);
			return buildResponse(401, "Unable to get user agent. Are you logged in?");
		}

		try {
			// Check database
			YouTubeVideo videoData = null;
			if (db.isHealthy())
				videoData = db.getVideoById(videoId);
			// No success? Use Data API
			if (videoData == null) {
				videoData = YouTubeApiWrapper.getVideoDetails(videoId, flow.getRequestInitializer());
				// Put requested video information into database so we can use this next time
				if (db.isHealthy() && videoData != null)
					db.addVideo(videoData);
			}
			// Still nothing, video must not exist
			if (videoData == null)
				return buildResponse(404, "No video found for ID " + videoId);
			// Record the information that user has watched the requested video
			if (db.isHealthy())
				db.addRating(videoId, userId, "watch");
			return buildResponse(200, videoData.toString());
		} catch (Exception e) {
			log.printStackTrace(e);
			return Response.status(500).entity("Unspecified server error").build();
		}
	}

	/**
	 * TODO remove, just here for debugging
	 *
	 * @return nothin'
	 */
	@GET
	@Path("/match")
	@Produces(MediaType.TEXT_PLAIN)
	@ApiOperation(
			value = "YouTube",
			notes = "Calls find match function")
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "OK") })
	public Response findMatch() {
		try {
			String userId = getUserId((UserAgent) Context.getCurrent().getMainAgent());
		} catch (Exception e) {
			log.printStackTrace(e);
			return buildResponse(401, "Unable to get user agent. Are you logged in?");
		}

		try {
			findMatch(db.getUserIds(), "");
			return Response.ok().build();
		} catch (Exception e) {
			log.printStackTrace(e);
			return Response.serverError().build();
		}
	}
}
