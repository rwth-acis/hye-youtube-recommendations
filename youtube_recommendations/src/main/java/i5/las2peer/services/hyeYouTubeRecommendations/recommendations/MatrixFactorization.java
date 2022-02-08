package i5.las2peer.services.hyeYouTubeRecommendations.recommendations;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.*;
import org.apache.spark.mllib.recommendation.ALS;
import org.apache.spark.mllib.recommendation.MatrixFactorizationModel;
import org.apache.spark.mllib.recommendation.Rating;
import rice.p2p.util.tuples.Tuple;

import java.util.ArrayList;
import java.util.HashMap;

public class MatrixFactorization {

    private JavaSparkContext jsc;
    private int rank;
    private int numIterations;
    private double lambda;
    private RatingWrapper ratingsWrapper;
    private MatrixFactorizationModel model;

    public MatrixFactorization() {
        jsc = new JavaSparkContext(new SparkConf().setAppName("HyE - Recommendations").setMaster("local"));
        this.rank = 10;
        this.numIterations = 10;
        this.lambda = 0.01;

        // TODO do some research how to best weight YouTube rating interactions
        HashMap<String, Double> ratingMappings = new HashMap<>();
        ratingMappings.put("dislike", -1.0);
        ratingMappings.put("subscribe", 1.0);
        ratingMappings.put("playlist", 2.0);
        ratingMappings.put("like", 3.0);
        this.ratingsWrapper = new RatingWrapper(new HashMap<>(), new HashMap<>(), ratingMappings);
    }

    public MatrixFactorization setRank(int rank) {
        this.rank = rank;
        return this;
    }

    public MatrixFactorization setNumIterations(int numIterations) {
        this.numIterations = numIterations;
        return this;
    }

    public MatrixFactorization setLambda(double lambda) {
        this.lambda = lambda;
        return this;
    }

    private JavaRDD<Rating> userRatingsToRdd(String userId, ArrayList<Tuple<String, String>> tupleList) {
        ArrayList<Rating> ratingList = new ArrayList<Rating>();
        for (Tuple<String, String> tuple : tupleList)
            ratingList.add(ratingsWrapper.toSparkRating(userId, tuple.a(), tuple.b()));
        return jsc.parallelize(ratingList);
    }

    private JavaRDD<Rating> userRatingsToRdd(ArrayList<String> userIds,
                                             ArrayList<ArrayList<Tuple<String, String>>> userRatings) {
        if (userIds.size() != userRatings.size())
            return null;
        // Convert user ratings to JavaRDD ratings
        JavaRDD<Rating> ratings = jsc.emptyRDD();
        for (int i = 0; i < userIds.size(); ++i)
            ratings = ratings.union(userRatingsToRdd(userIds.get(i), userRatings.get(i)));
        return ratings;
    }

    public MatrixFactorizationModel train(ArrayList<String> userIds,
                                          ArrayList<ArrayList<Tuple<String, String>>> userRatings) {
        // Parse the data
        JavaRDD<Rating> ratings = userRatingsToRdd(userIds, userRatings);
        // Build the recommendation model using ALS
        MatrixFactorizationModel model = ALS.train(JavaRDD.toRDD(ratings), rank, numIterations, lambda);
        return model;
    }

    public double evaluateModel(String userId, ArrayList<Tuple<String, String>> testData) {
        if (model == null)
            return -1;
        JavaRDD<Rating> ratings = userRatingsToRdd(userId, testData);
        return ratings.mapToDouble(
                rating -> Math.pow(rating.rating() - model.predict(rating.user(), rating.product()), 2)).mean();
    }
}
