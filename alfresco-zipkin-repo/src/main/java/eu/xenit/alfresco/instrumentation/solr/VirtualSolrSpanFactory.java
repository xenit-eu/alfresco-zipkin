package eu.xenit.alfresco.instrumentation.solr;

import eu.xenit.alfresco.instrumentation.solr.representations.ShardedSolrRequest;
import eu.xenit.alfresco.instrumentation.solr.representations.ShardedSolrSubRequest;
import eu.xenit.alfresco.instrumentation.solr.representations.SolrRequest;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VirtualSolrSpanFactory {
    private static final Logger logger = LoggerFactory.getLogger(VirtualSolrSpanFactory.class);

    private static final String NUMFOUND_KEY = "numFound";
    private static final String SUB_NUMFOUND_KEY = "NumFound";
    private static final String QTIME_KEY = "QTime";
    private static final String ELAPSEDTIME_KEY = "ElapsedTime";
    private static final String RESPONSE_KEY = "response";
    private static final String RESPONSE_HEADER_KEY = "responseHeader";
    private static final String ORIGINAL_PARAMS_KEY = "_original_parameters_";
    private static final String SHARDS_KEY = "shards";
    private static final String DEBUG_KEY = "debug";
    private static final String TRACK_KEY = "track";
    private static final String RID_KEY = "rid";
    private static final String TIMINGS_KEY = "timing";
    private static final String REQUEST_PURPOSE_KEY = "RequestPurpose";
    private static final String HIGHLIGHT_QUERY_KEY = "hl.q";
    private static final String PARSED_QUERY_KEY = "parsedquery_toString";
    private static final String SUB_RESPONSE_KEY = "Response";

    /**
     * Parses debugInformation returned by solr into SOLR request Representations.
     * Debug information from solr is in JSON format (see resources/DebugInfoSharded.json for an example)
     * This factories function is to create Solr request representations and add information to them based on the debug information.
     * In case of a nonsharded setup, only one representation will be made: a SolrRequest with no subrequests.
     * In case of a sharded setup, a main representation will be made with several subrequests
     * This one main SolrRequest will have multiple shardedRequests (one for each shard) and these ShardedRequests
     * will have a ShardedSubRequest for each solr phase (EXECUTE_QUERY, GET_FIELDS)
     *
     * @param jsonResponse Response, with included JSON debug information, returned by Solr
     * @param path         endpoint to which the query was sent (is one of the shards in case of a sharded setup)
     * @return SolrRequest enriched with debug information and possibly containing subrequest ( based on the solr setup sharded / non-sharded)
     */
    public SolrRequest parseDebugInformationIntoSolrRequest(JSONObject jsonResponse, String path) {
        //QTime and Number of documents and Query found of main request
        int numFound = -1;
        long qTime = -1;
        String query = "Query not found in response";

        //Sharded configuration (depends on shards param in the original params
        boolean sharded = false;

        //Timing information if available
        JSONObject mainTimings;

        if (jsonResponse.has(RESPONSE_KEY)) {
            JSONObject responseSection = jsonResponse.getJSONObject(RESPONSE_KEY);
            numFound = Integer.parseInt(String.valueOf(responseSection.opt(NUMFOUND_KEY)));
        }

        if (jsonResponse.has(RESPONSE_HEADER_KEY)) {
            JSONObject responseHeader = jsonResponse.getJSONObject(RESPONSE_HEADER_KEY);
            qTime = Long.parseLong(String.valueOf(responseHeader.opt(QTIME_KEY)));
        }

        HashMap<String, ShardedSolrRequest> shardedSolrRequestHashMap = null;
        if (jsonResponse.has(ORIGINAL_PARAMS_KEY)) {
            JSONObject originalParams = jsonResponse.optJSONObject(ORIGINAL_PARAMS_KEY);

            if (originalParams != null) {
                // Find query out of highlighting param
                if (originalParams.has(HIGHLIGHT_QUERY_KEY)) {
                    query = originalParams.getString(HIGHLIGHT_QUERY_KEY);
                }

                if (originalParams.has(SHARDS_KEY)) {
                    //Sharded setup
                    sharded = true;
                    shardedSolrRequestHashMap = createShardedRequests(jsonResponse, originalParams, query);
                }

            } else {
                if (jsonResponse.has(DEBUG_KEY) && jsonResponse.optJSONObject(DEBUG_KEY).has(PARSED_QUERY_KEY)) {
                    query = jsonResponse.optJSONObject(DEBUG_KEY).opt(PARSED_QUERY_KEY).toString();
                }
            }
        }

        //Timings of the main request can only be used for annotations for non sharded requests
        //check https://lucene.472066.n3.nabble.com/Confusing-debug-timing-parameter-td4310214.html
        mainTimings = getTimingsFromResponseJson(jsonResponse);
        SolrRequest solrRequest = new SolrRequest(numFound, qTime, query, mainTimings, sharded);

        if (shardedSolrRequestHashMap != null) {
            solrRequest.setShardedRequests(shardedSolrRequestHashMap);
        }

        if (sharded == true) {
            solrRequest.setReceivingSolrShard(getShardInstanceIdentifier(path, 2));
        }

        return solrRequest;
    }

    /**
     * Creates ShardedSolrRequest and ShardedSolrSubRequests that will be added to the main SolrRequest.
     * The ShardedSolrRequests are not requests but rather virtual parents for the different ShardedSolrSubRequests.
     * These ShardedSolrRequests help for representation.
     * Their duration is calculated by summing the timings of child ShardedSolrSubRequests.
     * The ShardedSolrSubRequests are generated by the tracking fields of the debug information.
     */
    private HashMap<String, ShardedSolrRequest> createShardedRequests(JSONObject jsonResponse, JSONObject originalParams, String query) {
        String[] shards = originalParams.getString(SHARDS_KEY).split(",");
        HashMap<String, ShardedSolrRequest> shardedRequests = new HashMap<>();

        // Loop over shards and add debug/tracking information to each request representation
        for (int shardIndex = 0; shardIndex < shards.length; shardIndex++) {
            String shardName = shards[shardIndex];
            String shardShortName = getShardInstanceIdentifier(shardName, -1);

            //Add tracking information to underlying sharded requests
            if (jsonResponse.has(DEBUG_KEY) && jsonResponse.getJSONObject(DEBUG_KEY).has(TRACK_KEY)) {
                JSONObject trackingDebug = jsonResponse.getJSONObject(DEBUG_KEY).getJSONObject(TRACK_KEY);

                HashMap<String, ShardedSolrSubRequest> subRequestHashMap = new HashMap<>();
                Iterator<String> keys = trackingDebug.keys();

                long cummulativeQTime = 0;
                long totalElapsed = 0;
                Set<Integer> numFoundSet = new HashSet<>();

                while (keys.hasNext()) {
                    String key = keys.next();
                    JSONObject value = trackingDebug.optJSONObject(key);

                    //Check if the debug information has a key with the respective shard url / name
                    if (!key.equals(RID_KEY) && value.has(shardName)) {
                        //In a ShardedSolrSubRequest
                        JSONObject subRequestTrackingInfo = value.getJSONObject(shardName);

                        long srQTime = subRequestTrackingInfo.optLong(QTIME_KEY);
                        int srNumFound = subRequestTrackingInfo.optInt(SUB_NUMFOUND_KEY);
                        long srElapsedTime = subRequestTrackingInfo.optLong(ELAPSEDTIME_KEY);
                        String requestPurpose = subRequestTrackingInfo.optString(REQUEST_PURPOSE_KEY);
                        String subResponse = subRequestTrackingInfo.optString(SUB_RESPONSE_KEY);

                        JSONObject timings = null;
                        String timingString = "";
                        try {
                            // Response objects contained in the tracking information is a string representation of a JSONObject
                            // it is not gauranteed to be in correct JSON format (can contain illegal chars due to shard urls)
                            // Use Regex instead to get the timing information for the subrequests
                            timingString = findTimings(subResponse);
                            timings = new JSONObject(timingString.replaceAll("=", ":"));
                        } catch (JSONException e) {
                            //do nothing and proceed without timings
                            logger.debug("Searching of shardedRequest timings have failed: " + e.getMessage());
                            logger.debug("timingString: " + timingString);
                        }

                        ShardedSolrSubRequest subRequest = new ShardedSolrSubRequest(srNumFound, srQTime, timings,
                                query, shardName, srElapsedTime, key, requestPurpose);

                        cummulativeQTime += srQTime;
                        numFoundSet.add(srNumFound);
                        totalElapsed += srElapsedTime;

                        subRequestHashMap.put(key, subRequest);
                    }
                }

                //Create ShardedSolrRequest and add its subRequests
                ShardedSolrRequest shardedSolrRequest = new ShardedSolrRequest(Collections.max(numFoundSet), cummulativeQTime,
                        null, query, shardName, totalElapsed);
                shardedSolrRequest.setSubRequestHashMap(subRequestHashMap);
                shardedRequests.put(shardShortName, shardedSolrRequest);
            }
        }
        return shardedRequests;
    }

    private String getShardInstanceIdentifier(String in, int index) {
        String[] segments = in.split("/");
        if (index == -1) {
            return segments[segments.length - 1];
        } else {
            return segments[index];
        }
    }

    private JSONObject getTimingsFromResponseJson(JSONObject jsonResponse) {
        if (jsonResponse != null) {
            return jsonResponse.optJSONObject(DEBUG_KEY).optJSONObject(TIMINGS_KEY);
        } else {
            return null;
        }
    }

    private static String findTimings(String s) {
        Pattern timingsRegex = Pattern.compile("timing=(.*),processedDenies");
        Matcher matcher = timingsRegex.matcher(s);
        matcher.find();
        return matcher.group(1);
    }

}