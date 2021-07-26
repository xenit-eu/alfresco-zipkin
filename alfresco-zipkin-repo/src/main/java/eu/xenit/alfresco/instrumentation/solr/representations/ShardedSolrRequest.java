package eu.xenit.alfresco.instrumentation.solr.representations;

import brave.Span;
import brave.Tracer;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

public class ShardedSolrRequest extends SolrRequest {

    public String shardName;
    public long elapsedTime;
    public HashMap<String, ShardedSolrSubRequest> subRequestHashMap;

    public ShardedSolrRequest(int numFound, long qTime, JSONObject timing, String query, String shardName, long elapsedTime) {
        super(numFound, qTime, query, timing);
        this.shardName = shardName;
        this.elapsedTime = elapsedTime;
    }

    public void setSubRequestHashMap(HashMap<String, ShardedSolrSubRequest> subRequestHashMap) {
        this.subRequestHashMap = subRequestHashMap;
    }

    @Override
    public Span applyToSpan(Span solrSpan, long startTime) {
        solrSpan.tag("WARNING", "To avoid the need of instrumenting solr, spans are generated based on debug" +
                " information returned by solr. This means that sharded solr span starting times do not reflect " +
                "reality exactly (span durations are correct). The start time of spans are guessed based on " +
                "Alfresco - Solr and internal Solr transmission time estimations. " +
                "Similarly annotations found on this span are not necessary in the displayed order.");
        solrSpan = super.applyToSpan(solrSpan, startTime);
        solrSpan.name(shardName);

        return solrSpan;
    }

    public void completeSpan(Span solrSpan, long startTime) {
        solrSpan.start(startTime);
        applyToSpan(solrSpan, startTime);
        solrSpan.finish(startTime + elapsedTime*1000);
    }

    public void createAndCompleteSubSpans(Tracer tracer, Span solrSpan, long startTime) {
        long spanStartTime = startTime;
        for (String subPhase : orderPhaseKeys(subRequestHashMap.keySet())) {
            //Phase keys are ordered because of order of execution
            ShardedSolrSubRequest subRequest = subRequestHashMap.get(subPhase);
            // Create SubSpans for SubRequests and finish them based on their elapsed time
            Span subSpan = tracer.newChild(solrSpan.context());
            subRequest.completeSpan(subSpan, spanStartTime);
            //Next Sub Span will start after previous is finished
            spanStartTime += subRequest.elapsedTime*1000;
        }
    }

    private List<String> orderPhaseKeys(Set<String> keyset) {
        //EXECUTE_QUERY always happens before GET_FIELDS Phase
        ArrayList<String> orderedKeys = new ArrayList<>();
        if (keyset.contains("EXECUTE_QUERY")) {
            orderedKeys.add("EXECUTE_QUERY");
        }
        if (keyset.contains("GET_FIELDS")) {
            orderedKeys.add("GET_FIELDS");
        }
        for (String key : keyset) {
            if (!orderedKeys.contains(key)) {
                orderedKeys.add(key);
            }
        }
        return orderedKeys;
    }
}
