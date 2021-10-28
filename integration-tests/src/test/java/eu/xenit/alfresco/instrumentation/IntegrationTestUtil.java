package eu.xenit.alfresco.instrumentation;

import io.restassured.authentication.FormAuthConfig;

public class IntegrationTestUtil {
    public static final String ALFRESCO_USERNAME = "admin";
    public static final String ALFRESCO_PASSWORD = "admin";

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
}
