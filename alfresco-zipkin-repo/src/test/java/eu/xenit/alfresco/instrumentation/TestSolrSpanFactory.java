package eu.xenit.alfresco.instrumentation;

import eu.xenit.alfresco.instrumentation.solr.VirtualSolrSpanFactory;
import eu.xenit.alfresco.instrumentation.solr.representations.ShardedSolrRequest;
import eu.xenit.alfresco.instrumentation.solr.representations.ShardedSolrSubRequest;
import eu.xenit.alfresco.instrumentation.solr.representations.SolrRequest;
import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.stream.Stream;

import static org.junit.Assert.*;

public class TestSolrSpanFactory {


    private JSONObject loadTestDebugInformation(String fileName) throws IOException {
        ClassLoader classLoader = getClass().getClassLoader();
        URL resource = classLoader.getResource(fileName);
        if (resource == null) {
            throw new IllegalArgumentException("File not found: " + fileName);
        } else {
            JSONObject json;
            try {
                String responseBodyAsString = readFileIntoString(resource.getFile());
                json = new JSONObject(responseBodyAsString);
            } catch (Exception e) {
                throw e;
            }
            return json;
        }
    }

    private static String readFileIntoString(String filePath) {
        StringBuilder contentBuilder = new StringBuilder();

        try (Stream<String> stream = Files.lines(Paths.get(filePath), StandardCharsets.UTF_8)) {
            stream.forEach(s -> contentBuilder.append(s));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return contentBuilder.toString();
    }

    public void assertSolrRequest(SolrRequest solrRequest, VirtualSolrSpanFactory virtualSolrSpanFactory, boolean shouldBeSharded) {
        assertNotNull(solrRequest.qTime);
        assertNotNull(solrRequest.query);
        assertNotNull(solrRequest.numFound);
        HashMap<String, ShardedSolrRequest> shardedRequests = solrRequest.getShardedRequests();

        assertEquals(shouldBeSharded, solrRequest.sharded);

        if (shouldBeSharded) {
            assertNotNull(solrRequest.getShardedRequests());
            for (ShardedSolrRequest shardedSolrRequest : shardedRequests.values()) {
                assertNotNull(shardedSolrRequest.shardName);
                assertNotNull(shardedSolrRequest.query);
                assertNotNull(shardedSolrRequest.elapsedTime);
                assertNotNull(shardedSolrRequest.qTime);
                assertNotNull(shardedSolrRequest.subRequestHashMap);

                for (ShardedSolrSubRequest shardedSolrSubRequest : shardedSolrRequest.subRequestHashMap.values()) {
                    assertNotNull(shardedSolrSubRequest.phaseName);
                    assertNotNull(shardedSolrSubRequest.requestPurpose);
                    assertNotNull(shardedSolrSubRequest.timings);
                }
            }
        }
    }

    @Test
    public void testShardedDebugInfoParsing() throws IOException {
        String path = "http://shard1:8080/solr/shard1";
        JSONObject debugInfo = loadTestDebugInformation("debugInfoText.txt");
        VirtualSolrSpanFactory virtualSolrSpanFactory = new VirtualSolrSpanFactory();
        SolrRequest solrRequest = virtualSolrSpanFactory.parseDebugInformationIntoSolrRequest(debugInfo, path);
        assertSolrRequest(solrRequest, virtualSolrSpanFactory, true);
    }


}
