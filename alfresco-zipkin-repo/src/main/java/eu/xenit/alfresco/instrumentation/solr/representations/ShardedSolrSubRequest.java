package eu.xenit.alfresco.instrumentation.solr.representations;

import brave.Span;
import org.json.JSONObject;

public class ShardedSolrSubRequest extends ShardedSolrRequest {
    public String phaseName;
    public String requestPurpose;

    public ShardedSolrSubRequest(int numFound, long qTime, JSONObject timing, String query, String shardName, long elapsedTime, String phaseName, String requestPurpose) {
        super(numFound, qTime, timing, query, shardName, elapsedTime);
        this.phaseName = phaseName;
        this.requestPurpose = requestPurpose;
    }

    @Override
    public void addInfoToSpan(Span solrSpan, long startTime) {
        super.addInfoToSpan(solrSpan, startTime);
        solrSpan.name(phaseName);
        solrSpan.tag("Request Purpose", requestPurpose);
    }

}
