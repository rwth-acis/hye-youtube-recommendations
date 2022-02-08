package i5.las2peer.services.hyeYouTubeRecommendations;

import java.io.FileReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.*;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.http.*;
import com.google.api.client.json.Json;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import i5.las2peer.api.Context;
import i5.las2peer.logging.L2pLogger;
import i5.las2peer.api.ManualDeployment;
import i5.las2peer.api.security.UserAgent;
import i5.las2peer.restMapper.RESTService;
import i5.las2peer.restMapper.annotations.ServicePath;

import i5.las2peer.services.hyeYouTubeRecommendations.recommendations.MatrixFactorization;
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
	private final String ROOT_URI = "http://localhost:8080/hye-recommendations";
	private final String LOGIN_URI = ROOT_URI + "/login";
	private final String AUTH_URI = ROOT_URI + "/auth";
	private static final L2pLogger log = L2pLogger.getInstance(YouTubeRecommendations.class.getName());
	private AuthorizationCodeFlow flow;
	private HttpTransport transport;
	private GsonFactory json;
	// Store access token in frontend instead
	private static HashMap<String, YouTubeApiWrapper> ytConnections;
	private static DataBaseConnection db;
	private static MatrixFactorization mf;

	/**
	 * Class constructor, initializes member variables
	 */
	HttpRequestInitializer httpRequestInitializer;
	public YouTubeRecommendations() {
		setFieldValues();
		log.info("Using API key " + apiKey +" with client id " + clientId + " and secret " + clientSecret +
		" and connecting to jdbc:mysql://" + mysqlHost + '/' + mysqlDatabase + " as " + mysqlUser);
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
		if (mf == null)
			mf = new MatrixFactorization();
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

		// TODO this seems terribly inefficient...
		HashMap<String, ArrayList<YouTubeVideo>> videoData = ytConnection.getYouTubeWatchData();
		Iterator<String> ratingsIt = videoData.keySet().iterator();
		int dbInsertions = 0;
		while (ratingsIt.hasNext()) {
			String rating = ratingsIt.next();
			if (videoData.get(rating) == null)
				continue;
			Iterator<YouTubeVideo> videoIt = videoData.get(rating).iterator();
			while (videoIt.hasNext()) {
				YouTubeVideo ytVideo = videoIt.next();
				// Assume that video insertion failing means the video had already been added before
				// Which also means, once recorded, comments don't get updated
				if (db.addVideo(ytVideo)) {
					dbInsertions++;
					ArrayList<YouTubeComment> comments =
							YouTubeApiWrapper.getComments(ytVideo.getVideoId(), flow.getRequestInitializer());
					if (comments == null)
					    continue;
					Iterator<YouTubeComment> commentIt = comments.iterator();
					while (commentIt.hasNext())
                                        {
						db.addComment(commentIt.next());
						dbInsertions++;
					}
				}
				db.addRating(ytVideo.getVideoId(), userId, rating);
			}
		}
		return dbInsertions;
	}

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

	// TODO implement ratings (videos of subscribed channel 1, videos in playlist 2, liked video 3, disliked video -1), "finMatch" method using inverted matrix factorization and semantic similarity based on word embeddings, MySQL data base connection

	/**
	 * Main page showing YouTube watch data of current user
	 *
	 * @return YouTube data of requesting user
	 */
	@GET
	@Path("/")
	@Produces(MediaType.TEXT_PLAIN)
	@ApiOperation(
			value = "YouTube",
			notes = "Returns YouTube watch data")
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "OK") })
	public Response getYouTubeDate() {
		// Check for access token of current user in memory
		UserAgent user;
		try {
			user = (UserAgent) Context.getCurrent().getMainAgent();
		} catch (Exception e) {
			log.printStackTrace(e);
			return Response.status(401).entity("Unable to get user agent. Are you logged in?").build();
		}

		JsonObject response = new JsonObject();
		try {
			// TODO implement data storage
			log.info("Nothing to do...");
			return Response.ok().entity(response.toString()).build();
		} catch (Exception e) {
			log.printStackTrace(e);
			return Response.status(500).entity("Unspecified server error").build();
		}
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
		// TODO just here for debugging
		YouTubeApiWrapper.init();
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
	 * Debug function used to create and evaluate the fitness of the Spark MlLib Matrix Factorization
	 *
	 * @return The MSE of the model created for the requesting user
	 */
	@GET
	@Path("/model")
	@Produces(MediaType.TEXT_PLAIN)
	@ApiOperation(
			value = "YouTube - Authentication",
			notes = "Creates and evaluates the Matrix Factorization model created for the given user")
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "OK") })
	public Response createAndEvaluateModel(@DefaultValue("20")
											   @QueryParam("testDataPercentage") String testDataPercentage) {
		int testSplit;
		try {
			testSplit = Integer.parseInt(testDataPercentage);
		} catch (Exception e) {
			return Response.status(400).entity("Invalid value for testDataSize: " + testDataPercentage).build();
		}
		String userId;
		try {
			userId = Context.getCurrent().getMainAgent().getIdentifier();
			// TODO only for testing
			userId = "MICHI";
		} catch (Exception e) {
			return Response.status(401).entity("Could not get user agent! Are you logged in?").build();
		}

		// Retrieve data stored for user
		ArrayList<Tuple<String, String>> ratingData = db.getRatingsByUserId(userId);
		if (ratingData == null || ratingData.isEmpty())
			return Response.status(400).entity("There is currently no YouTube watch data stored for you.").build();

		// Build and evaluate model
		Tuple<ArrayList<Tuple<String, String>>, ArrayList<Tuple<String, String>>> trainAndTestData =
				splitData(ratingData, testSplit);
		ArrayList<String> userIds = new ArrayList<String>();
		userIds.add(userId);
		ArrayList<ArrayList<Tuple<String, String>>> userRatings = new ArrayList<ArrayList<Tuple<String, String>>>();
		userRatings.add(trainAndTestData.a());
		mf.train(userIds, userRatings);
		return Response.ok().entity(mf.evaluateModel(userId, trainAndTestData.b())).build();
	}
}
