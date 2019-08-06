package eu.xenit.alfresco.instrumentation.share;

import org.junit.Test;
import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.GenericXmlApplicationContext;

import java.util.Properties;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

public class TestShareConfig {

    private static void assertProperty(Properties props, String key, String value)
    {
        assertThat(
                String.format("Property '%s' does not contain expected value", key),
                props.getProperty(key),
                is(value)
        );
    }

    @Test
    public void testDefaultZipkinProperties() {
        assertThat(true, is(true));

        AbstractApplicationContext context = new GenericXmlApplicationContext(
                "org/springframework/extensions/webscripts/spring-webscripts-application-context.xml",
                "config/alfresco/web-extension/zipkin-share-context.xml"
        );


        Properties zipkinProps = context.getBean("zipkin-properties", Properties.class);

        assertProperty(zipkinProps,"zipkin.collector", "");

        assertProperty(zipkinProps,"zipkin.service.share.name", "share");
        assertProperty(zipkinProps,"zipkin.service.share.sampler.rate", "0.0");
    }
}
