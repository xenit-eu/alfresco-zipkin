package eu.xenit.alfresco.instrumentation.solr.representations;

import brave.Span;
import org.json.JSONObject;

import java.util.Iterator;

public class SolrRequest {
    public int numFound;
    public long qTime;
    public String query;
    public JSONObject timings;

    public SolrRequest(int numFound, long qTime, String query, JSONObject timings) {
        this.numFound = numFound;
        this.qTime = qTime;
        this.query = query;
        this.timings = timings;
    }

    public Span applyToSpan(Span solrSpan, long startTime) {
        solrSpan.tag("Query", query);
        solrSpan.tag("Qtime [ms]" , String.valueOf(qTime));
        solrSpan.tag("Number results found: ", String.valueOf(numFound));
        if (timings != null) {
            annotateSpanWithSolrTimings(solrSpan, startTime);
        }
        return solrSpan;
    }

    public Span annotateSpanWithSolrTimings(Span solrSpan, long startTime) {
        Iterator<String> phaseKeyIterator = timings.keys();
        long dt = 0;
        while (phaseKeyIterator.hasNext()){
            String phase = phaseKeyIterator.next();
            if (timings.optJSONObject(phase) != null) {
                //Loop over the different timings for each phase (mostly prepare and process)
                JSONObject phaseTimings = timings.getJSONObject(phase);
                Iterator<String> phaseTimingsKeyIterator = phaseTimings.keys();
                //Loop over the different subphases (these are the different steps in a standard solr search query : facet timing, spellcorrection, highlighting, ...)
                while (phaseTimingsKeyIterator.hasNext()) {
                    String subPhase= phaseTimingsKeyIterator.next();
                    if (phaseTimings.optJSONObject(subPhase) != null && phaseTimings.optJSONObject(subPhase).has("time")) {
                        long subPhaseTime = phaseTimings.getJSONObject(subPhase).optLong("time");
                        if (subPhaseTime != 0) {
                            String annotationName = phase + " - " + subPhase + " [" + subPhaseTime + "ms]";
                            dt += subPhaseTime*1000;
                            solrSpan.annotate(startTime + dt, annotationName);
                        }
                    }
                }
            }
        }
        return solrSpan;
    }

}
