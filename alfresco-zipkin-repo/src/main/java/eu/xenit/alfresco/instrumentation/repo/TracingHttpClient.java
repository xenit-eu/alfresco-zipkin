package eu.xenit.alfresco.instrumentation.repo;

import brave.Clock;
import brave.Span;
import brave.Tracer;
import brave.http.HttpClientHandler;
import brave.http.HttpTracing;
import brave.propagation.TraceContext;
import eu.xenit.alfresco.instrumentation.solr.VirtualSolrSpanFactory;
import eu.xenit.alfresco.instrumentation.solr.representations.SolrRequest;
import org.apache.commons.httpclient.*;

import java.io.IOException;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Http Client with tracing functionality
 */
public class TracingHttpClient extends HttpClient {

    private static final Logger logger = LoggerFactory.getLogger(TracingHttpClient.class);

    private HttpTracing httpTracing;
    private Tracer tracer;
    private HttpClientHandler<HttpMethod, Integer> httpClientHandler;
    private TraceContext.Injector<HttpMethod> injector;
    private VirtualSolrSpanFactory virtualSolrSpanFactory;

    public TracingHttpClient(HttpTracing httpTracing, HttpConnectionManager httpConnectionManager) {
        super(httpConnectionManager);
        this.httpTracing = httpTracing;
        init();
    }

    private void init() {
        tracer = httpTracing.tracing().tracer();
        httpClientHandler = HttpClientHandler.create(httpTracing, new TracingHttpClientAdapter());
        injector = httpTracing.tracing().propagation().injector(HttpMethod::setRequestHeader);
        virtualSolrSpanFactory = new VirtualSolrSpanFactory();
    }

    @Override
    public int executeMethod(HostConfiguration hostconfig, HttpMethod method, HttpState state) throws IOException {
        int responseStatus = 0;
        Span span = httpClientHandler.handleSend(injector, method);
        Clock clock = httpTracing.tracing().clock(span.context());
        long startTime = clock.currentTimeMicroseconds();
        Throwable error = null;

        try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
            String q = method.getQueryString();
            String debugQ = q + "&debugQuery=on";
            method.setQueryString(debugQ);
            responseStatus = super.executeMethod(hostconfig, method, state);
        } catch (RuntimeException | Error e) {
            error = e;
            span.error(e);
            throw e;
        } finally {
            String responseBodyAsString = method.getResponseBodyAsString();
            try {
                JSONObject jsonResponse = new JSONObject(responseBodyAsString);
                SolrRequest mainRequest = virtualSolrSpanFactory.parseDebugInformationIntoSolrRequest(jsonResponse, method.getPath());
                long endTime = clock.currentTimeMicroseconds();
                mainRequest.applyToSpan(tracer, span, startTime, endTime);
            } catch (Exception e) {
                logger.error("Failed to parse response JSON from solr", e);
            } finally {
                httpClientHandler.handleReceive(responseStatus, error, span);
            }
        }
        return responseStatus;
    }

    public void setHttpTracing(HttpTracing httpTracing) {
        this.httpTracing = httpTracing;
    }

}
