package eu.xenit.alfresco.instrumentation.solr;

import brave.Clock;
import brave.Span;
import brave.Tracer;
import brave.http.HttpClientHandler;
import brave.http.HttpTracing;
import brave.propagation.TraceContext;
import eu.xenit.alfresco.instrumentation.solr.VirtualSolrSpanFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;

import eu.xenit.alfresco.instrumentation.solr.representations.SolrRequest;
import org.alfresco.repo.management.subsystems.SubsystemProxyFactory;
import org.alfresco.repo.search.impl.lucene.SolrJSONResultSet;
import org.alfresco.service.cmr.search.ResultSet;
import org.alfresco.service.cmr.search.SearchParameters;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.json.JSONObject;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.util.ReflectionUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SolrQueryHTTPClientAdvisor {

    private static final Logger logger = LoggerFactory.getLogger(SolrQueryHTTPClientAdvisor.class);
    private final VirtualSolrSpanFactory virtualSolrSpanFactory = new VirtualSolrSpanFactory();

    public SolrQueryHTTPClientAdvisor(final SubsystemProxyFactory bean, final HttpTracing httpTracing) {

        final Method debugMethod = ReflectionUtils.findMethod(SolrJSONResultSet.class, "getResponseBodyAsJSONObject");
        if (debugMethod != null) {

            bean.addAdvisor(0, new DefaultPointcutAdvisor(new MethodInterceptor() {
                public Object invoke(MethodInvocation mi) throws Throwable {
                    if ("query".equals(mi.getMethod().getName())) {
                        Object[] arguments = mi.getArguments();

                        SearchParameters params = (SearchParameters) arguments[0];
                        //params.addExtraParameter("debug", "track");
                        if (!params.getExtraParameters().containsKey("debug")) {
                            params.addExtraParameter("debug", "all");
                        }

                        Tracer tracer = httpTracing.tracing().tracer();
                        Span span = tracer.currentSpan();

                        Throwable error = null;
                        try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
                            Span solrSpan = tracer.newChild(span.context());

                            //Span span = httpClientHandler.handleSend(injector, method);
                            Clock clock = httpTracing.tracing().clock(solrSpan.context());
                            long startTime = clock.currentTimeMicroseconds();

                            ResultSet resultSet = (ResultSet) mi.proceed();

                            long endTime = clock.currentTimeMicroseconds();

                            JSONObject jsonResponse = (JSONObject) ReflectionUtils.invokeMethod(debugMethod, resultSet);
                            SolrRequest mainRequest = virtualSolrSpanFactory.parseDebugInformationIntoSolrRequest(jsonResponse, "unkown");
                            mainRequest.applyToSpan(tracer, solrSpan, startTime, endTime);
                            return resultSet;
                        }
                    }

                    Object ret = mi.proceed();
                    return ret;
                }
            }));
        }
    }

}
