package eu.xenit.alfresco.instrumentation.repo;

import brave.Span;
import brave.Tracer;
import brave.http.HttpClientHandler;
import brave.http.HttpTracing;
import brave.propagation.TraceContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.httpclient.*;

import java.io.IOException;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Http Client with tracing functionality
 */
public class TracingHttpClient extends HttpClient {

    private static final Logger logger = LoggerFactory.getLogger(TracingHttpClient.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private HttpTracing httpTracing;
    private Tracer tracer;
    private HttpClientHandler<HttpMethod, Integer> httpClientHandler;
    private TraceContext.Injector<HttpMethod> injector;

    private static final Pattern queryPattern = Pattern.compile("spellcheck.q=([^&]*)&");

    public TracingHttpClient(HttpTracing httpTracing, HttpConnectionManager httpConnectionManager) {
        super(httpConnectionManager);
        this.httpTracing = httpTracing;
        init();
    }

    private void init(){
        tracer = httpTracing.tracing().tracer();
        httpClientHandler = HttpClientHandler.create(httpTracing, new TracingHttpClientAdapter());
        injector = httpTracing.tracing().propagation().injector(HttpMethod::setRequestHeader);
    }

    @Override
    public int executeMethod(HostConfiguration hostconfig, HttpMethod method, HttpState state) throws IOException {
        int responseStatus = 0;
        Span span = httpClientHandler.handleSend(injector, method);
        Throwable error = null;
        span.annotate("Solr post Query");

        try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
            HttpMethodParams params = method.getParams();
            params.setParameter("solrDebug", "on");
            method.setParams(params);
            responseStatus = super.executeMethod(hostconfig, method, state);
        } catch (RuntimeException | Error e) {
            error = e;
            throw e;
        } finally {
            String responseBodyAsString = method.getResponseBodyAsString();
            Matcher queryMatcher = queryPattern.matcher(responseBodyAsString);
            //  logger.info("number found:" + queryMatcher.());
            logger.info(String.format("found a match for the query: %s", queryMatcher.matches()));
            if (queryMatcher.find()) {
                logger.info("match found through group:" + queryMatcher.group(0));
                span.tag("query_group", queryMatcher.group(0));
                String query = responseBodyAsString.subSequence(queryMatcher.start(), queryMatcher.end()).toString();
                logger.info("query found: " + query);
                span.tag("query", query);
            }

            JSONObject jsonResponse = new JSONObject(responseBodyAsString);
            span.tag("Number of results found",((JSONObject)jsonResponse.get("response")).get("numFound").toString());

            String Qtime = ((JSONObject)jsonResponse.get("response")).get("numFound").toString();
            span.tag("Qtime", Qtime);

            span.tag("body", method.getResponseBodyAsString());
            httpClientHandler.handleReceive(responseStatus, error, span);
        }
        return responseStatus;
    }

    public void setHttpTracing(HttpTracing httpTracing) {
        this.httpTracing = httpTracing;
    }

}
