package eu.xenit.alfresco.instrumentation;

import com.sun.org.apache.xpath.internal.operations.Bool;
import eu.xenit.alfresco.instrumentation.solradmin.SolrAdminClientException;
import eu.xenit.alfresco.instrumentation.solradmin.SolrAdminHttpClient;
import io.restassured.authentication.FormAuthConfig;
import org.apache.commons.codec.net.URLCodec;
import org.apache.commons.httpclient.methods.GetMethod;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;

public class IntegrationTestUtil {
    public static final String ALFRESCO_USERNAME = "admin";
    public static final String ALFRESCO_PASSWORD = "admin";
    private int maxTries = 20;

    public static final FormAuthConfig FORM_AUTH_CONFIG_SHARE =
            new FormAuthConfig("/share/page/dologin", "username", "password");

    public static String getAlfrescoServiceUrl() {
        return "http://" + System.getProperty("alfresco.host", "localhost")
                + ":" + System.getProperty("alfresco.tcp.8080", "8000");
    }

    public static String getShareServiceUrl() {
        return "http://" + System.getProperty("share.host", "localhost")
                + ":" + System.getProperty("share.tcp.8080", "8082");
    }

    public static String getZipkinServiceUrl() {
        return "http://" + System.getProperty("zipkin.host", "localhost")
                + ":" + System.getProperty("zipkin.tcp.9411", "9411");
    }

    public static String getSolrAdminUrl() {
        return "http://" + System.getProperty("solr.host", "localhost")
                + ":" + System.getProperty("solr.tcp.8081", "8081") + "/solr/admin/cores";
    }

}
