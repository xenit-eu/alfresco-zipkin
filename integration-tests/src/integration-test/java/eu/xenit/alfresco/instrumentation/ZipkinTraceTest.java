package eu.xenit.alfresco.instrumentation;

import io.restassured.filter.log.LogDetail;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasValue;

public class ZipkinTraceTest {

    // Components buffer zipkin-spans before sending them in
    // batch to the zipkin-api, default timeout = 1 second.
    private static final long SLEEP_MILLIS = 1 * 1000L;

    @Test
    public void traceAlfresco() throws InterruptedException {

        String traceId = randomTraceId();
        Map<String, String> b3Headers = createB3Headers(traceId);

        // Make a call to Alfresco with B3-headers
        given()
                .auth().basic(IntegrationTestUtil.ALFRESCO_USERNAME, IntegrationTestUtil.ALFRESCO_PASSWORD)
                .headers(b3Headers)
                .log().uri()
                .log().headers()
                .get(IntegrationTestUtil.getAlfrescoServiceUrl() + "/alfresco/webdav")
            .then()
                .log().status()
                .statusCode(is(200));

        TimeUnit.MILLISECONDS.sleep(SLEEP_MILLIS);

        // Verify trace is recorded
        given()
                .log().uri()
                .pathParam("trace", traceId)
                .get(IntegrationTestUtil.getZipkinServiceUrl() + "/zipkin/api/v2/trace/{trace}")
            .then()
                .log().status()
                .log().ifValidationFails(LogDetail.BODY)
                .statusCode(is(200))
                // check if this trace contains at least 1 span with serviceName 'alfresco'
                .body("findAll { it.localEndpoint.serviceName == 'alfresco' }.size()", greaterThan(1))
                .body ( "localEndpoint.serviceName.flatten().unique()", containsInAnyOrder("alfresco"))
                .body ( "remoteEndpoint.serviceName.flatten().unique().findAll{ it != '' }", hasItems("db"))
                ;

    }

    @Test
    public void traceShare() throws InterruptedException {
        String traceId = randomTraceId();
        Map<String, String> b3Headers = createB3Headers(traceId);


        given()
                .log().all()
                // Share login
                .auth().form(IntegrationTestUtil.ALFRESCO_USERNAME, IntegrationTestUtil.ALFRESCO_PASSWORD, IntegrationTestUtil.FORM_AUTH_CONFIG_SHARE)
                .headers(b3Headers)
                .queryParam("term", "test")
                .get(IntegrationTestUtil.getShareServiceUrl() + "/share/proxy/alfresco/slingshot/search/")
            .then()
                .log().status()
                .log().ifValidationFails(LogDetail.BODY)
                // Do not validate search-results: solr might not have indexed alfresco yet
                // .body("totalRecords", is(1))
                .statusCode(is(200));

        TimeUnit.MILLISECONDS.sleep(SLEEP_MILLIS);

        // Verify trace is recorded
        given()
                .log().uri()
                .pathParam("trace", traceId)
                .get(IntegrationTestUtil.getZipkinServiceUrl() + "/zipkin/api/v2/trace/{trace}")
            .then()
                .log().status()
                .log().ifValidationFails()
                .statusCode(is(200))
                .body("findAll { it.localEndpoint.serviceName == 'share' }.size()", greaterThan(1))
                .body("findAll { it.localEndpoint.serviceName == 'alfresco' }.size()", greaterThan(1))
                .body ( "localEndpoint.serviceName.flatten().unique()", containsInAnyOrder("alfresco", "share", "solr"))
                .body ( "localEndpoint.serviceName.flatten().unique().findAll{ it != '' }", hasItems("solr"))
                .body ( "remoteEndpoint.serviceName.flatten().unique().findAll{ it != '' }", hasItems("db"))


                ;

    }

    private Map<String, String> createB3Headers(String traceId) {
        Map<String, String> b3Headers = new HashMap<>();
        b3Headers.put("X-B3-TraceId", traceId);
        b3Headers.put("X-B3-ParentSpanId", traceId);
        b3Headers.put("X-B3-SpanId", randomTraceId());
        b3Headers.put("X-B3-Sampled", "1");
        return b3Headers;
    }

    private String randomTraceId() {
        long longTraceId = java.util.concurrent.ThreadLocalRandom.current().nextLong();
        return Long.toHexString(longTraceId);
    }
}
