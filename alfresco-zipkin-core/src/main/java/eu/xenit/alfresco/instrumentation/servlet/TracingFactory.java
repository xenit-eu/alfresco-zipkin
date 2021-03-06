package eu.xenit.alfresco.instrumentation.servlet;

import brave.Tracing;
import brave.context.log4j12.MDCScopeDecorator;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.sampler.Sampler;
import zipkin2.Span;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.Reporter;
import zipkin2.reporter.urlconnection.URLConnectionSender;

// Note: it looks like we could remove this whole class
// and use Tracing.newBuilder() in spring directly ?
public class TracingFactory {

    private String serviceName;
    private String url;

    // never sample a request spontaneously by default
    // sampling is only done when there is upstream a decision to sample
    // and this decision is propagated !
    private Sampler sampler = Sampler.NEVER_SAMPLE;

    public TracingFactory() {
        // no action
    }

    public Tracing createInstance() {
        Tracing.Builder builder = Tracing.newBuilder()
                .currentTraceContext(
                        ThreadLocalCurrentTraceContext.newBuilder()
                                    .addScopeDecorator(MDCScopeDecorator.create())
                                    .build())
                ;

        builder.spanReporter(this.getReporter());

        if (serviceName != null)
                builder.localServiceName(serviceName);

        if (sampler != null)
            builder.sampler(sampler);

        return builder.build();
    }

    public Reporter<Span> getReporter() {
        if (url == null || url.trim().isEmpty())
            return Reporter.NOOP;

        return AsyncReporter.create(URLConnectionSender.create(url));
    }

    public TracingFactory setURL(String url) {
        this.url = url;
        return this;
    }

    public TracingFactory setServiceName(String serviceName) {
        this.serviceName = serviceName;
        return this;
    }

    public TracingFactory setSampler(Sampler sampler) {
        this.sampler = sampler;
        return this;
    }

    public Sampler getSampler()
    {
        return this.sampler;
    }
}
