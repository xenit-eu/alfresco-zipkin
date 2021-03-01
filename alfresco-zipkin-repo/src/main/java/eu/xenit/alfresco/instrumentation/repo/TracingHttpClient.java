package eu.xenit.alfresco.instrumentation.repo;

import brave.Span;
import brave.Tracer;
import brave.http.HttpClientHandler;
import brave.http.HttpClientRequest;
import brave.http.HttpClientResponse;
import brave.http.HttpTracing;
import brave.propagation.TraceContext;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpConnectionManager;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpState;

import java.io.IOException;

/**
 * Http Client with tracing functionality
 */
public class TracingHttpClient extends HttpClient {

    private HttpTracing httpTracing;
    private Tracer tracer;
    private HttpClientHandler<HttpClientRequest, HttpClientResponse> httpClientHandler;
    private TraceContext.Injector<HttpMethod> injector;

    public TracingHttpClient(HttpTracing httpTracing, HttpConnectionManager httpConnectionManager) {
        super(httpConnectionManager);
        this.httpTracing = httpTracing;
        init();
    }

    private void init(){
        tracer = httpTracing.tracing().tracer();
        httpClientHandler = HttpClientHandler.create(httpTracing);
        injector = httpTracing.tracing().propagation().injector(HttpMethod::setRequestHeader);
    }

    @Override
    public int executeMethod(HostConfiguration hostConfig, HttpMethod method, HttpState state) throws IOException {
        int responseStatus = 0;

        Span span = httpClientHandler.handleSend(injector, method);
        Throwable error = null;
        try (Tracer.SpanInScope ignored = tracer.withSpanInScope(span)) {
            responseStatus = super.executeMethod(hostConfig, method, state);
        } catch (RuntimeException | Error e) {
            error = e;
            throw e;
        } finally {
            httpClientHandler.handleReceive(state., span);
        }
        return responseStatus;
    }

    public void setHttpTracing(HttpTracing httpTracing) {
        this.httpTracing = httpTracing;
    }

}
