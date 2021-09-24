package eu.xenit.alfresco.instrumentation;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

public class AlfrescoTest {

    @Test
    public void alfrescoWebdavUp() {
        System.out.println("TEST TEST TEST TEST TEST TEST TEST TEST TEST");
        given()
                .auth().basic(IntegrationTestUtil.ALFRESCO_USERNAME, IntegrationTestUtil.ALFRESCO_PASSWORD)
                .log().uri()
                .get(IntegrationTestUtil.getAlfrescoServiceUrl() + "/alfresco/webdav")
                .then()
                .log().status()
                .statusCode(is(200));
    }

}
