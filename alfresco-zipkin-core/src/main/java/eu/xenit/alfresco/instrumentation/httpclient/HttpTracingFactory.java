package eu.xenit.alfresco.instrumentation.httpclient;

import brave.Tracing;
import brave.http.HttpClientParser;
import brave.http.HttpSampler;
import brave.http.HttpServerParser;
import brave.http.HttpTracing;


public class HttpTracingFactory {

    private HttpClientParser clientParser;
    private HttpSampler clientSampler;
    private HttpServerParser serverParser;
    private HttpSampler serverSampler;

    public HttpTracingFactory(){
    }

    public HttpTracing createInstance(Tracing tracing) {
        HttpTracing.Builder builder = HttpTracing.newBuilder(tracing);
        if (clientParser != null)
            builder.clientParser(clientParser);
        if (clientSampler != null)
            builder.clientSampler(clientSampler);
        if (serverParser != null)
            builder.serverParser(serverParser);
        if (serverSampler != null)
            builder.serverSampler(serverSampler);

        return builder.build();
    }

    public void setClientParser(HttpClientParser clientParser) {
        this.clientParser = clientParser;
    }
    public void setClientSampler(HttpSampler clientSampler) {
        this.clientSampler = clientSampler;
    }
    public void setServerParser(HttpServerParser serverParser) {
        this.serverParser = serverParser;
    }
    public void setServerSampler(HttpSampler serverSampler) {
        this.serverSampler = serverSampler;
    }
}
