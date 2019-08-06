package eu.xenit.alfresco.instrumentation.share;

import brave.Span;
import brave.Tracer;
import brave.http.HttpServerHandler;
import brave.http.HttpTracing;
import brave.propagation.TraceContext;
import brave.servlet.HttpServletAdapter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class TracingInterceptor extends HandlerInterceptorAdapter implements InitializingBean {

    private static final Log log = LogFactory.getLog(TracingInterceptor.class);

    private static final String SPAN_NAME = Span.class.getName();
    private static final String SPAN_IN_SCOPE_NAME = Tracer.SpanInScope.class.getName();

    private HttpTracing httpTracing;
    private Tracer tracer;
    private HttpServerHandler httpServerHandler;
    private TraceContext.Extractor extractor;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (log.isDebugEnabled())
            log.debug("Prehandle "+request.getRequestURL());

        try {
            Span span = httpServerHandler.handleReceive(extractor, request);
            Tracer.SpanInScope ws = tracer.withSpanInScope(span);
            request.setAttribute(SPAN_NAME,span);
            request.setAttribute(SPAN_IN_SCOPE_NAME,ws);
        } catch(Exception e) {
            log.error("Problem with brave instrumentation: "+e.getMessage(),e);
        }
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView){
        if (log.isDebugEnabled())
            log.error("PostHandle "+request.getRequestURL());

        try {
            Span span = (Span) request.getAttribute(SPAN_NAME);
            request.removeAttribute(SPAN_NAME);
            if (span != null)
                httpServerHandler.handleSend(response, null, span);
        } finally {
            Tracer.SpanInScope ws = (Tracer.SpanInScope) request.getAttribute(SPAN_IN_SCOPE_NAME);
            request.removeAttribute(SPAN_IN_SCOPE_NAME);
            if (ws != null)
                ws.close();
        }

    }

    @Override
    public void afterPropertiesSet()  {
        tracer = httpTracing.tracing().tracer();
        httpServerHandler = HttpServerHandler.create(httpTracing, new HttpServletAdapter());
        extractor = httpTracing.tracing().propagation().extractor(HttpServletRequest::getHeader);
    }

    public HttpTracing getHttpTracing() {
        return httpTracing;
    }

    public void setHttpTracing(HttpTracing httpTracing) {
        this.httpTracing = httpTracing;
    }
}
