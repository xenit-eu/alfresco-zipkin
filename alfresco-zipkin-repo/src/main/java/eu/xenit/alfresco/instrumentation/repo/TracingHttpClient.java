package eu.xenit.alfresco.instrumentation.repo;

import brave.Span;
import brave.Tracer;
import brave.http.HttpClientHandler;
import brave.http.HttpTracing;
import brave.propagation.TraceContext;
import org.apache.commons.httpclient.*;

import java.io.IOException;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.util.EncodingUtil;

/**
 * Http Client with tracing functionality
 */
public class TracingHttpClient extends HttpClient {

    private HttpTracing httpTracing;
    private Tracer tracer;
    private HttpClientHandler<HttpMethod, Integer> httpClientHandler;
    private TraceContext.Injector<HttpMethod> injector;

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
        span.tag("custom tag key","custom tag value");
        if (method instanceof PostMethod) {
            PostMethod postMethod = (PostMethod) method;
            String s = EncodingUtil.formUrlEncode(postMethod.getParameters(), postMethod.getRequestCharSet());
            span.tag("possibly a big content string", s);
        }
        Throwable error = null;
        try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
            responseStatus = super.executeMethod(hostconfig, method, state);
        } catch (RuntimeException | Error e) {
            error = e;
            throw e;
        } finally {
            httpClientHandler.handleReceive(responseStatus, error, span);
        }
        return responseStatus;
    }

    public void setHttpTracing(HttpTracing httpTracing) {
        this.httpTracing = httpTracing;
    }

}
