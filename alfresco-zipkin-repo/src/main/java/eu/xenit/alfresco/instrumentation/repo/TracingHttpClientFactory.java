package eu.xenit.alfresco.instrumentation.repo;

import brave.http.HttpTracing;
import org.alfresco.httpclient.HttpClientFactory;
import org.apache.commons.httpclient.HttpClient;

/**
 * A factory for creating Http Clients with tracing functionality
 */
public class TracingHttpClientFactory extends HttpClientFactory{

    private HttpTracing httpTracing;

    // uninherited variables
    private int maxTotalConnections = 40;
    private int maxHostConnections = 40;
    private Integer socketTimeout = null;
    private int connectionTimeout = 0;

    /**
     * Creates a Http Client with tracing functionality
     * @return
     */
    @Override
    protected HttpClient constructHttpClient() {
        // return new TracingHttpClient(httpTracing);   // TODO incompatible http client libraries here
        return null;                                    // TODO This null will break Alfresco's HttpClient creation for the time being
    }

    public void init(){
        super.init();
    }

    public void setHttpTracing(HttpTracing httpTracing) {
        this.httpTracing = httpTracing;
    }
}
