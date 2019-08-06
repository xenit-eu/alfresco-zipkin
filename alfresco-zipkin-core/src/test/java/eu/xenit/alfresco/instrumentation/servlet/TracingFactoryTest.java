package eu.xenit.alfresco.instrumentation.servlet;

import brave.sampler.Sampler;
import org.junit.Test;

import static org.junit.Assert.*;

public class TracingFactoryTest {

    @Test
    public void createDefaultInstance() {
        new TracingFactory().createInstance();
    }

    @Test
    public void createConfiguredInstance() {
        new TracingFactory()
                .setServiceName("alfresco")
                .setURL("http://zipkinhost:9411/api/v2/spans")
                .setSampler(Sampler.ALWAYS_SAMPLE)
                .createInstance();
    }

    @Test
    public void createEmptyUrlInstance() {
        new TracingFactory()
                .setURL("")
                .createInstance();
    }



}