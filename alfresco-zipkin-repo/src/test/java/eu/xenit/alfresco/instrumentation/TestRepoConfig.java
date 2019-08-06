package eu.xenit.alfresco.instrumentation;

import brave.sampler.Sampler;
import eu.xenit.alfresco.instrumentation.servlet.TracingFactory;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.GenericXmlApplicationContext;
import zipkin2.reporter.Reporter;

import java.util.Properties;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;


public class TestRepoConfig {

    private static void assertProperty(Properties props, String key, String value)
    {
        assertThat(
            String.format("Property '%s' does not contain expected value", key),
            props.getProperty(key),
            is(value)
        );
    }

    private static AbstractApplicationContext createTestApplicationContext()
    {
        return createTestApplicationContext(new Properties());
    }

    private static AbstractApplicationContext createTestApplicationContext(Properties properties) {
        GenericXmlApplicationContext context = new GenericXmlApplicationContext();
        context.load(
                "test-global-properties-context.xml",
                "config/alfresco/module/*/module-context.xml"
        );
        context.getBeanFactory().registerSingleton("test-properties", properties);
        context.refresh();

        return context;
    }

    @Test
    public void testDefaultGlobalProperties() {
        // ALFEDGE-249 zipkin module should still boot unconfigured
        AbstractApplicationContext context = createTestApplicationContext(new Properties());

        Properties globalProps = context.getBean("global-properties", Properties.class);

        assertThat(globalProps, is(not(nullValue())));

        assertProperty(globalProps,"zipkin.collector", "");

        assertProperty(globalProps,"zipkin.service.alfresco.name", "alfresco");
        assertProperty(globalProps,"zipkin.service.alfresco.sampler.rate", "0.0");

        assertProperty(globalProps,"zipkin.service.solr.name", "solr");
        assertProperty(globalProps,"zipkin.service.solr.sampler.rate", "0.0");
    }

    @Test
    public void unconfiguredIsNoop() {
        AbstractApplicationContext context = createTestApplicationContext(new Properties());
        TracingFactory tracingFactory = context.getBean("repoTracingFactory", TracingFactory.class);

        assertThat(tracingFactory.getReporter(), is(Reporter.NOOP));
        assertThat(tracingFactory.getSampler(), is(Sampler.NEVER_SAMPLE));
    }


    @Test
    public void testConfiguredZipkinModule() {
        Properties properties = new Properties();
        properties.setProperty("zipkin.collector", "http://zipkin:9411/api/v2/spans");

        AbstractApplicationContext context = createTestApplicationContext(properties);

        TracingFactory tracingFactory = context.getBean("repoTracingFactory", TracingFactory.class);
        assertThat(tracingFactory, is(not(nullValue())));
    }

}
