package eu.xenit.alfresco.instrumentation.solr;

import brave.Span;
import brave.Tracer;
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

    public SolrRequest solrRequest;
    public boolean sharded = false;
    public HashMap<String, ShardedSolrRequest> shardedRequests;
    public String receivingSolrInstance;

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

    public VirtualSolrSpanFactory(JSONObject jsonResponse, String path) {
        receivingSolrInstance = path;

        //QTime and Number of documents and Query found of main request
        int numFound = -1;
        long qTime = -1;
        String query = "Query not found in response";
        //Timing information if available
        JSONObject mainTimings = null;

        if (jsonResponse.has(RESPONSE_KEY)) {
            JSONObject responseSection = jsonResponse.getJSONObject(RESPONSE_KEY);
            numFound = Integer.parseInt(String.valueOf(responseSection.opt(NUMFOUND_KEY)));
        }

        if (jsonResponse.has(RESPONSE_HEADER_KEY)) {
            JSONObject responseHeader = jsonResponse.getJSONObject(RESPONSE_HEADER_KEY);
            qTime = Long.parseLong(String.valueOf(responseHeader.opt(QTIME_KEY)));
        }

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
                    createShardedRequests(jsonResponse, originalParams, query);
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
        solrRequest = new SolrRequest(numFound, qTime, query, mainTimings);
    }

    public Span createVirtualSolrSpans(Tracer tracer, Span solrSpan, long startTime, long endTime) {

        //Apply main solr information to span
        solrRequest.applyToSpan(solrSpan, startTime);

        //Add sharded boolean tag
        solrSpan.tag("sharded", String.valueOf(sharded));

        //Approximation of time that main solr request was not actively searching -> I/O and package transmissions
        long transmissionTime = ((endTime - startTime) - solrRequest.qTime * 1000) / 2;

        if (shardedRequests != null) {
            //Find the first solr shard instance to which the request was sent to
            //Example path /solr/shard1/afts
            String shardInstanceIdentifier = getShardInstanceIdentifier(receivingSolrInstance, 2);
            ShardedSolrRequest firstRequest = shardedRequests.get(shardInstanceIdentifier);

            //Create a new span for the first sharded request
            Span firstShardedSpan = tracer.newChild(solrSpan.context());

            //Start the first span on startTime of main span + transmission time to first shard instance (approximation)
            long firstReceiveTime = startTime + transmissionTime;

            //Apply to newly create span for the first shard instance which propagates the query to the other shards
            firstRequest.completeSpan(firstShardedSpan, firstReceiveTime);

            //Create a child span for the different subrequests
            firstRequest.createAndCompleteSubSpans(tracer, firstShardedSpan, firstReceiveTime);

            //Approximate transmission time between different shard instances (similar approx)
            long shardInstanceTransmissionTime = ((firstRequest.elapsedTime - firstRequest.qTime) / 3) * 1000;

            for (ShardedSolrRequest shardedSolrRequest : shardedRequests.values()) {
                if (shardedSolrRequest != firstRequest) {
                    //Create a new span for each further shardedRequest
                    Span subsequentSpan = tracer.newChild(solrSpan.context());
                    long spanStartTime = firstReceiveTime + shardInstanceTransmissionTime;
                    shardedSolrRequest.completeSpan(subsequentSpan, spanStartTime);
                    shardedSolrRequest.createAndCompleteSubSpans(tracer, subsequentSpan, spanStartTime);
                }
            }
        }

        return solrSpan;
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
        if (jsonResponse != null && sharded == false) {
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

    private void createShardedRequests(JSONObject jsonResponse, JSONObject originalParams, String query) {
        String[] shards = originalParams.getString(SHARDS_KEY).split(",");
        shardedRequests = new HashMap<>();

        //Loop over shards and add debug/tracking information to each request representation
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
                    if (!key.equals(RID_KEY) && value.has(shardName)) {
                        //In a ShardedSolrSubRequest
                        JSONObject subRequestTrackingInfo = value.getJSONObject(shardName);

                        long srQTime = subRequestTrackingInfo.optLong(QTIME_KEY);
                        int srNumFound = subRequestTrackingInfo.optInt(SUB_NUMFOUND_KEY);
                        long srElapsedTime = subRequestTrackingInfo.optLong(ELAPSEDTIME_KEY);
                        String requestPurpose = subRequestTrackingInfo.optString(REQUEST_PURPOSE_KEY);

                        String subResponse = subRequestTrackingInfo.optString("Response");

                        JSONObject timings = null;
                        try {
                            // Timings section in debug information is not necessarily a correctly formatted JSONObject.
                            // (can contain illegal chars due to shard urls)
                            // Use Regex instead to get the timing information for the subrequests
                            String timingString = findTimings(subResponse);
                            timings = new JSONObject(timingString.replaceAll("=", ":"));
                        } catch (JSONException e) {
                            e.printStackTrace();
                            throw e;
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
                this.shardedRequests.put(shardShortName, shardedSolrRequest);
            }
        }
    }

    public SolrRequest getMainSolrRequest() {
        return solrRequest;
    }

    public boolean isSharded() {
        return sharded;
    }

    public HashMap<String, ShardedSolrRequest> getShardedRequests() {
        return shardedRequests;
    }
}