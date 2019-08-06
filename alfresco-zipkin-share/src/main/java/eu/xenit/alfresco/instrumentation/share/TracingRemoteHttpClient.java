package eu.xenit.alfresco.instrumentation.share;

import org.alfresco.web.scripts.SlingshotRemoteClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.protocol.HttpContext;
import org.springframework.extensions.webscripts.connector.RemoteClient;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;

/**
 * RemoteClient with zipkin-instrumented tracing.
 *
 * This subclass can be configured to have a specific HttpClientBuilder.
 */
public class TracingRemoteHttpClient extends SlingshotRemoteClient {

    private static Log logger = LogFactory.getLog(TracingRemoteHttpClient.class);

    private static final HttpHost s_httpProxyHost;
    private static final HttpHost s_httpsProxyHost;

    private HttpClientConnectionManager connectionManager;

    private boolean allowHttpProxy = true;
    private boolean allowHttpsProxy = true;
    private boolean httpConnectionStalecheck = true;
    private int connectTimeout = DEFAULT_CONNECT_TIMEOUT;
    private int readTimeout = DEFAULT_READ_TIMEOUT;

    private HttpClientBuilder httpClientBuilder;

    /**
     * Initialise the static HTTP objects - Proxy Hosts
     */
    static {
        // Create an HTTP Proxy Host if appropriate system property set
        s_httpProxyHost = createProxyHost("http.proxyHost", "http.proxyPort", 80);

        // Create an HTTPS Proxy Host if appropriate system property set
        s_httpsProxyHost = createProxyHost("https.proxyHost", "https.proxyPort", 443);
    }

    public TracingRemoteHttpClient(HttpClientBuilder httpClientBuilder) {
        this.httpClientBuilder = httpClientBuilder;
    }

    @Override
    public void init() {
        super.init();
        // load connection manager from private super class
        this.connectionManager = loadPrivateFieldFromSuper("connectionManager", HttpClientConnectionManager.class);
    }

    private <T> T loadPrivateFieldFromSuper(String fieldname, Class<T> type) {
        try {
            Field superField = RemoteClient.class.getDeclaredField(fieldname);
            superField.setAccessible(true);
            return type.cast(superField.get(this));
        } catch (NoSuchFieldException | IllegalAccessException  e) {
            throw new RuntimeException(e);
        }
    }

    // copied from super class org.springframework.extensions.webscripts.connector.RemoteClient,
    // but hardcoded HttpClientBuilder replaced by the builder specified at construction time.
    @Override
    protected HttpClient createHttpClient(URL url)
    {
        // use the appropriate HTTP proxy host if required
        HttpRoutePlanner routePlanner = null;
        if (s_httpProxyHost != null && this.allowHttpProxy &&
                url.getProtocol().equals("http") && requiresProxy(url.getHost()))
        {
            routePlanner = new DefaultProxyRoutePlanner(s_httpProxyHost);
            if (logger.isDebugEnabled()) logger.debug(" - using HTTP proxy host for: " + url);
        }
        else if (s_httpsProxyHost != null && this.allowHttpsProxy &&
                url.getProtocol().equals("https") && requiresProxy(url.getHost()))
        {
            routePlanner = new DefaultProxyRoutePlanner(s_httpsProxyHost);
            if (logger.isDebugEnabled()) logger.debug(" - using HTTPS proxy host for: " + url);
        }

        return this.httpClientBuilder
                .setConnectionManager(connectionManager)
                .setRoutePlanner(routePlanner)
                .setRedirectStrategy(new RedirectStrategy() {
                    // Switch off automatic redirect handling as we want to process them ourselves and maintain cookies
                    public boolean isRedirected(HttpRequest request, HttpResponse response, HttpContext context)
                            throws ProtocolException
                    {
                        return false;
                    }
                    public HttpUriRequest getRedirect(HttpRequest request, HttpResponse response, HttpContext context)
                            throws ProtocolException
                    {
                        return null;
                    }
                }).setDefaultRequestConfig(RequestConfig.custom()
                        .setStaleConnectionCheckEnabled(httpConnectionStalecheck)
                        .setConnectTimeout(connectTimeout)
                        .setSocketTimeout(readTimeout)
                        .build())
                .build();

        // TODO: this appears to have vanished from the config that can be set since httpclient 3.1->4.3
        //params.setBooleanParameter("http.tcp.nodelay", httpTcpNodelay);
    }

    // retrieves private info from super class
    protected boolean requiresProxy(final String targetHost)
    {
        // we could add memoization on the targetHost to avoid
        // the reflection overhead on very call to createHttpClient ?

        try {
            Method requiresProxySuper = RemoteClient.class.getDeclaredMethod("requiresProxy", String.class);
            requiresProxySuper.setAccessible(true);
            return (boolean) requiresProxySuper.invoke(this, targetHost);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException  e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public void setAllowHttpProxy(boolean allowHttpProxy) {
        this.allowHttpProxy = allowHttpProxy;
        super.setAllowHttpProxy(allowHttpProxy);
    }

    @Override
    public void setAllowHttpsProxy(boolean allowHttpsProxy) {
        this.allowHttpsProxy = allowHttpsProxy;
        super.setAllowHttpsProxy(allowHttpsProxy);
    }

    @Override
    public void setHttpConnectionStalecheck(boolean httpConnectionStalecheck) {
        this.httpConnectionStalecheck = httpConnectionStalecheck;
        super.setHttpConnectionStalecheck(httpConnectionStalecheck);
    }

    @Override
    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
        super.setConnectTimeout(connectTimeout);
    }

    @Override
    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
        super.setReadTimeout(readTimeout);
    }
}