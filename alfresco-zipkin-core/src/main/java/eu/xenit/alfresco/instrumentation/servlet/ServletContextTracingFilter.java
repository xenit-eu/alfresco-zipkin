package eu.xenit.alfresco.instrumentation.servlet;

import brave.Tracing;
import brave.servlet.TracingFilter;
import org.springframework.beans.factory.InitializingBean;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;

/**
 * Created by toon on 7/6/17.
 */
public class ServletContextTracingFilter implements Filter, InitializingBean {

    private Tracing tracing;
    private Filter tracingFilter;

    public ServletContextTracingFilter(){
        // no action
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (tracingFilter == null) {
            chain.doFilter(request, response);
        } else {
            tracingFilter.doFilter(request, response, chain);
        }
    }

    @Override
    public void afterPropertiesSet() {
        tracingFilter = TracingFilter.create(tracing);
    }

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override public void destroy() {
        tracing = null;
        tracingFilter = null;
    }

    public void setTracing(Tracing tracing) {
        this.tracing = tracing;
    }
}