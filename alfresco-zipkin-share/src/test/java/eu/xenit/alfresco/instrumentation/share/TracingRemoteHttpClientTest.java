package eu.xenit.alfresco.instrumentation.share;

import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.Test;
import org.springframework.extensions.webscripts.connector.RemoteClient;


import java.net.MalformedURLException;
import java.net.URL;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class TracingRemoteHttpClientTest {

    @Test
    public void createHttpClient() throws MalformedURLException {
        HttpClientBuilder builder = mock(HttpClientBuilder.class);
        TracingRemoteHttpClient client = new TracingRemoteHttpClient(builder);

        client.createHttpClient(new URL("http://localhost:8080/alfresco"));

        verify(builder, times(1)).build();

    }

    @Test
    public void testPrivateMethodRequiresProxy() {
        TracingRemoteHttpClient client = new TracingRemoteHttpClient(HttpClientBuilder.create());

        client.requiresProxy("localhost");
    }

    @Test
    public void testOverriddenSetters() {
        RemoteClient client = new TracingRemoteHttpClient(HttpClientBuilder.create());

        client.setAllowHttpProxy(false);
        client.setAllowHttpsProxy(false);
        client.setHttpConnectionStalecheck(false);
        client.setConnectTimeout(1000);
        client.setReadTimeout(1000);
    }
}