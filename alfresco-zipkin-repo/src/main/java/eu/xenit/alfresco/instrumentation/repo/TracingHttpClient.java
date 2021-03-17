package eu.xenit.alfresco.instrumentation.repo;

import brave.Span;
import brave.Tracer;
import brave.http.HttpClientHandler;
import brave.http.HttpClientRequest;
import brave.http.HttpClientResponse;
import brave.http.HttpRequestParser;
import brave.http.HttpTags;
import brave.http.HttpTracing;
import eu.xenit.alfresco.instrumentation.repo.HttpWrappers.HttpRequestWrapper;
import eu.xenit.alfresco.instrumentation.repo.HttpWrappers.HttpResponseWrapper;
import java.io.IOException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;

/**
 * Http Client with tracing functionality
 */
public class TracingHttpClient extends CloseableHttpClient {

    private HttpTracing httpTracing;
    private Tracer tracer;
    private HttpClientHandler<HttpClientRequest, HttpClientResponse> httpClientHandler;

    public TracingHttpClient(HttpTracing httpTracing) {
        this.httpTracing = httpTracing;
        init();
    }

    private void init(){
        httpTracing = httpTracing.toBuilder().clientRequestParser((req, ctx, span) -> {
            HttpRequestParser.DEFAULT.parse(req, ctx, span);
            HttpTags.URL.tag(req, ctx, span);
        }).build();;
        tracer = httpTracing.tracing().tracer();
        httpClientHandler = HttpClientHandler.create(httpTracing);
    }

    @Override
    protected CloseableHttpResponse doExecute(HttpHost target, HttpRequest request, HttpContext context)
            throws IOException {
        CloseableHttpResponse response = null;
        Throwable error = null;
        HttpRequestWrapper requestWrapper = new HttpRequestWrapper(request);
        Span span = httpClientHandler.handleSend(requestWrapper);
        try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
            response = super.execute(target, request, context);
        } catch (RuntimeException | Error e) {
            error = e;
            throw e;
        } finally {
            HttpResponseWrapper responseWrapper = new HttpResponseWrapper(requestWrapper, response, error);
            httpClientHandler.handleReceive(responseWrapper, span);
        }
        return response;
    }

    @Override
    public void close() {

    }

    @Override
    public HttpParams getParams() {
        return null;
    }

    @Override
    public ClientConnectionManager getConnectionManager() {
        return null;
    }

}
