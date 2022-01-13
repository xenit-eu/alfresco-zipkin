package eu.xenit.alfresco.instrumentation.solradmin;

import eu.xenit.alfresco.instrumentation.IntegrationTestUtil;
import org.apache.commons.codec.net.URLCodec;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.GetMethod;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.HashMap;

public class SolrAdminHttpClient {
    private String adminUrl;
    private HttpClient httpClient;
    private int maxTries = 20;

    public SolrAdminHttpClient(String url) {
        httpClient = new HttpClient();
        adminUrl = url;
    }

    public void setAdminUrl(String url) {
        adminUrl = url;
    }

    public JSONObject execute(HashMap<String, String> args) throws SolrAdminClientException {
        return execute(adminUrl, args);
    }

    public JSONObject execute(String adminUrl, HashMap<String, String> args) throws SolrAdminClientException {
        try {
            URLCodec encoder = new URLCodec();
            StringBuilder url = new StringBuilder();

            for (String key : args.keySet()) {
                String value = args.get(key);
                if (url.length() == 0) {
                    url.append(adminUrl);
                    url.append("?");
                    url.append(encoder.encode(key, "UTF-8"));
                    url.append("=");
                    url.append(encoder.encode(value, "UTF-8"));
                } else {
                    url.append("&");
                    url.append(encoder.encode(key, "UTF-8"));
                    url.append("=");
                    url.append(encoder.encode(value, "UTF-8"));
                }

            }

            GetMethod get = new GetMethod(url.toString());

            try {
                httpClient.executeMethod(get);

                if (get.getStatusCode() == HttpStatus.SC_MOVED_PERMANENTLY || get.getStatusCode() == HttpStatus.SC_MOVED_TEMPORARILY) {
                    Header locationHeader = get.getResponseHeader("location");
                    if (locationHeader != null) {
                        String redirectLocation = locationHeader.getValue();
                        get.setURI(new URI(redirectLocation, true));
                        httpClient.executeMethod(get);
                    }
                }

                if (get.getStatusCode() != HttpServletResponse.SC_OK) {
                    throw new SolrAdminClientException("Request failed " + get.getStatusCode() + " " + url.toString());
                }

                Reader reader = new BufferedReader(new InputStreamReader(get.getResponseBodyAsStream()));
                // TODO - replace with streaming-based solution e.g. SimpleJSON ContentHandler
                JSONObject json = new JSONObject(new JSONTokener(reader));
                return json;
            } finally {
                get.releaseConnection();
            }
        } catch (UnsupportedEncodingException e) {
            throw new SolrAdminClientException("", e);
        } catch (HttpException e) {
            throw new SolrAdminClientException("", e);
        } catch (IOException e) {
            throw new SolrAdminClientException("", e);
        } catch (JSONException e) {
            throw new SolrAdminClientException("", e);
        } catch (SolrAdminClientException e) {
            throw new SolrAdminClientException("", e);
        }
    }

    public JSONObject getSolrSummaryJson() throws SolrAdminClientException {
        HashMap<String, String> params = new HashMap<>();
        params.put("wt", "json");
        params.put("action", "summary");
        JSONObject response = execute(params);
        return response.getJSONObject("Summary");
    }

}


