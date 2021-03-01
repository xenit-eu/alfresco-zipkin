package eu.xenit.alfresco.instrumentation.httpclient;

import brave.Tracing;
import brave.http.HttpRequest;
import brave.http.HttpRequestParser;
import brave.http.HttpTracing;
import brave.sampler.SamplerFunction;


public class HttpTracingFactory {

    private HttpRequestParser clientRequestParser;
    private SamplerFunction<HttpRequest> clientSampler;
    private HttpRequestParser serverRequestParser;
    private SamplerFunction<HttpRequest> serverSampler;

    public HttpTracingFactory(){
    }

    public HttpTracing createInstance(Tracing tracing) {
        HttpTracing.Builder builder = HttpTracing.newBuilder(tracing);
        if (clientRequestParser != null)
            builder.clientRequestParser(clientRequestParser);
        if (clientSampler != null)
            builder.clientSampler(clientSampler);
        if (serverRequestParser != null)
            builder.serverRequestParser(serverRequestParser);
        if (serverSampler != null)
            builder.serverSampler(serverSampler);

        return builder.build();
    }

    public void setClientRequestParser(HttpRequestParser clientRequestParser) {
        this.clientRequestParser = clientRequestParser;
    }
    public void setClientSampler(SamplerFunction<HttpRequest> clientSampler) {
        this.clientSampler = clientSampler;
    }
    public void setServerRequestParser(HttpRequestParser serverRequestParser) {
        this.serverRequestParser = serverRequestParser;
    }
    public void setServerSampler(SamplerFunction<HttpRequest> serverSampler) {
        this.serverSampler = serverSampler;
    }
}
