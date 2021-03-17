package eu.xenit.alfresco.instrumentation.repo;

import brave.http.HttpClientResponse;
import brave.internal.Nullable;
import org.apache.http.HttpRequest;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.springframework.web.client.HttpStatusCodeException;

public class HttpWrappers {

    static final class HttpRequestWrapper extends brave.http.HttpClientRequest {
        final HttpRequest delegate;

        HttpRequestWrapper(HttpRequest delegate) {
            this.delegate = delegate;
        }

        @Override public Object unwrap() {
            return delegate;
        }

        @Override public String method() {
            return delegate.getRequestLine().getMethod();
        }

        @Override public String path() {
            String result = delegate.getRequestLine().getUri(); // per JavaDoc, getURI() is never null
            return result != null && result.isEmpty() ? "/" : result;
        }

        @Override public String url() {
            return delegate.getRequestLine().getUri();
        }

        @Override public String header(String name) {
            Object result = delegate.getHeaders(name);
            return result != null ? result.toString() : null;
        }

        @Override public void header(String name, String value) {
            delegate.setHeader(name, value);
        }
    }

    static final class HttpResponseWrapper extends HttpClientResponse {
        final HttpRequestWrapper request;
        @Nullable
        final CloseableHttpResponse response;
        @Nullable final Throwable error;

        HttpResponseWrapper(HttpRequestWrapper request, @Nullable CloseableHttpResponse response,
                @Nullable Throwable error) {
            this.request = request;
            this.response = response;
            this.error = error;
        }

        @Override public Object unwrap() {
            return response;
        }

        @Override public HttpRequestWrapper request() {
            return request;
        }

        @Override public Throwable error() {
            return error;
        }

        @Override public int statusCode() {
            try {
                int result = response != null ? response.getStatusLine().getStatusCode() : 0;
                if (result <= 0 && error instanceof HttpStatusCodeException) {
                    result = ((HttpStatusCodeException) error).getStatusCode().value();
                }
                return result;
            } catch (Exception e) {
                return 0;
            }
        }
    }
}
