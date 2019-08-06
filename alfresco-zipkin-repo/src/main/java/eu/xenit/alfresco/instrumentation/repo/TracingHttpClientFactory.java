package eu.xenit.alfresco.instrumentation.repo;

import brave.http.HttpTracing;
import org.alfresco.httpclient.HttpClientFactory;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.apache.commons.httpclient.params.HttpConnectionParams;

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
    protected HttpClient constructHttpClient()
    {
        MultiThreadedHttpConnectionManager connectionManager = new MultiThreadedHttpConnectionManager();
//        httpTracing.tracing().
        TracingHttpClient tracingHttpClient = new TracingHttpClient(httpTracing, connectionManager);
        HttpClientParams params = tracingHttpClient.getParams();
        params.setBooleanParameter(HttpConnectionParams.TCP_NODELAY, true);
        params.setBooleanParameter(HttpConnectionParams.STALE_CONNECTION_CHECK, true);
        if (socketTimeout != null)
        {
            params.setSoTimeout(socketTimeout);
        }
        HttpConnectionManagerParams connectionManagerParams = tracingHttpClient.getHttpConnectionManager().getParams();
        connectionManagerParams.setMaxTotalConnections(maxTotalConnections);
        connectionManagerParams.setDefaultMaxConnectionsPerHost(maxHostConnections);
        connectionManagerParams.setConnectionTimeout(connectionTimeout);

        return tracingHttpClient;
    }

    public void init(){
        super.init();
    }

    public void setHttpTracing(HttpTracing httpTracing) {
        this.httpTracing = httpTracing;
    }
}
