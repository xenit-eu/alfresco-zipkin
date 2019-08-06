package eu.xenit.alfresco.instrumentation.repo;

import brave.http.HttpClientAdapter;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.URIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

public class TracingHttpClientAdapter extends HttpClientAdapter<HttpMethod, Integer> {

    private static final Logger log = LoggerFactory.getLogger(TracingHttpClientAdapter.class);


    @Nullable
    @Override
    public String method(HttpMethod request) {
        return request.getName();
    }

    @Nullable
    @Override
    public String url(HttpMethod request){
        try {
            return request.getURI().toString();
        } catch (URIException e) {
            log.error("Invalid URI in request: "+e.getMessage(),e);
        }
        return null;
    }

    @Nullable
    @Override
    public String requestHeader(HttpMethod request, String name) {
        return request.getRequestHeader(name).getValue();
    }

    @Nullable
    @Override
    public Integer statusCode(Integer response) {
        return response;
    }
}
