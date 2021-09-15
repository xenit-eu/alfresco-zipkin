package eu.xenit.alfresco.instrumentation.solr.representations;

import brave.Span;
import brave.Tracer;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;

public class SolrRequest {
    public int numFound;
    public long qTime;
    public String query;
    public JSONObject timings;
    public boolean sharded;
    public HashMap<String, ShardedSolrRequest> shardedRequests;
    public String receivingSolrShard;

    public SolrRequest(int numFound, long qTime, String query, JSONObject timings, boolean sharded) {
        this.numFound = numFound;
        this.qTime = qTime;
        this.query = query;
        this.timings = timings;
        this.sharded = sharded;
    }

    /**
     * Tags the solr span with information from properties such as: query, number found, ...
     * Annotates the span with timing information returned by solr.
     *
     * @param solrSpan  Virtual span representing this request
     * @param startTime Start time of the span
     */
    public void addInfoToSpan(Span solrSpan, long startTime) {
        solrSpan.tag("Query", query);
        solrSpan.tag("Qtime [ms]", String.valueOf(qTime));
        solrSpan.tag("Number results found ", String.valueOf(numFound));
        //Add sharded boolean tag
        solrSpan.tag("sharded", String.valueOf(sharded));
        checkAndAnnotateWithSolrTimings(solrSpan, startTime);
    }

    /**
     * In case the solrRequest is nonsharded, the timings on the main request can be used to annotate.
     * In case the solrRequest is sharded, the timing information on the main request cannot be used since it is the
     * cummulative time of the shardedRequests. (the timings of the ShardedSubRequests can be used)
     *
     * @param solrSpan  solr span
     * @param startTime StartTime of the Solr Span
     */
    public void checkAndAnnotateWithSolrTimings(Span solrSpan, long startTime) {
        if (!(this.getClass() == SolrRequest.class && sharded == true)) {
            annotateSpanWithSolrTimings(solrSpan, startTime);
        }
    }

    /**
     * Annotates the given span with the timing phases included in the timing debug information returned by solr.
     *
     * @param solrSpan  solr span
     * @param startTime StartTime of the Solr Span
     */
    public void annotateSpanWithSolrTimings(Span solrSpan, long startTime) {
        if (timings != null) {
            Iterator<String> phaseKeyIterator = timings.keys();
            long dt = 0;
            while (phaseKeyIterator.hasNext()) {
                String phase = phaseKeyIterator.next();
                if (timings.optJSONObject(phase) != null) {
                    //Loop over the different timings for each phase (mostly prepare and process)
                    JSONObject phaseTimings = timings.getJSONObject(phase);
                    Iterator<String> phaseTimingsKeyIterator = phaseTimings.keys();
                    //Loop over the different subphases (these are the different steps in a standard solr search query : facet timing, spellcorrection, highlighting, ...)
                    while (phaseTimingsKeyIterator.hasNext()) {
                        String subPhase = phaseTimingsKeyIterator.next();
                        if (phaseTimings.optJSONObject(subPhase) != null && phaseTimings.optJSONObject(subPhase).has("time")) {
                            long subPhaseTime = phaseTimings.getJSONObject(subPhase).optLong("time");
                            if (subPhaseTime != 0) {
                                String annotationName = phase + " - " + subPhase + " [" + subPhaseTime + "ms]";
                                dt += subPhaseTime * 1000;
                                solrSpan.annotate(startTime + dt, annotationName);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Applies the information contained in the solr request Representations to the provided span.
     * (tags and annotates the span)
     * In case the solrRequest is sharded, it creates virtual spans (childs) representing the requests
     * to the different Shards and tags them with information and annotates them with timing information.
     * The starttime of the subrequest span is approximated by taking the difference of the total elapsed time and
     * the solr search time divided by two.
     *
     * @param tracer    HttpTracer from the TracingHttpClient. Needed for creating child spans.
     * @param solrSpan  The main solrSpan returned by the VirtualSpanFactory
     * @param startTime StartTime of the solr Request
     * @param endTime   EndTime of the solr Request (needed to approx packet transmission time)
     */
    public void applyToSpan(Tracer tracer, Span solrSpan, long startTime, long endTime) {
        //Apply main solr information to span
        addInfoToSpan(solrSpan, startTime);

        //Approximation of time that main solr request was not actively searching -> I/O and package transmissions
        long transmissionTime = ((endTime - startTime) - qTime * 1000) / 2;

        if (sharded && shardedRequests != null) {
            //Find the first solr shard instance to which the request was sent to
            //Example path /solr/shard1/afts
            ShardedSolrRequest firstRequest = shardedRequests.get(receivingSolrShard);

            //Create a new span for the first sharded request
            Span firstShardedSpan = tracer.newChild(solrSpan.context());

            //Start the first span on startTime of main span + transmission time to first shard instance (approximation)
            long firstReceiveTime = startTime + transmissionTime;

            //Apply to newly created span for the first shard instance which propagates the query to the other shards
            firstRequest.completeSpan(firstShardedSpan, firstReceiveTime);

            //Create a child span for the different subrequests
            firstRequest.createAndCompleteSubSpans(tracer, firstShardedSpan, firstReceiveTime);

            for (ShardedSolrRequest shardedSolrRequest : shardedRequests.values()) {
                if (shardedSolrRequest != firstRequest) {
                    //Create a new span for each further shardedRequest
                    Span subsequentSpan = tracer.newChild(solrSpan.context());
                    shardedSolrRequest.completeSpan(subsequentSpan, firstReceiveTime);
                    shardedSolrRequest.createAndCompleteSubSpans(tracer, subsequentSpan, firstReceiveTime);
                }
            }
        }
    }

    public void setShardedRequests(HashMap<String, ShardedSolrRequest> shardedRequests) {
        this.shardedRequests = shardedRequests;
    }

    public HashMap<String, ShardedSolrRequest> getShardedRequests() {
        return shardedRequests;
    }

    public void setReceivingSolrShard(String receivingSolrShard) {
        this.receivingSolrShard = receivingSolrShard;
    }

    public String getReceivingSolrShard() {
        return receivingSolrShard;
    }
}
