package eu.xenit.alfresco.instrumentation;

import eu.xenit.alfresco.instrumentation.solradmin.SolrAdminClientException;
import eu.xenit.alfresco.instrumentation.solradmin.SolrAdminHttpClient;
import org.json.JSONObject;

public class SolrTestHelper {
    private String solrCoreName = "alfresco";
    private final int maxTries = 20;
    protected SolrAdminHttpClient solrAdminHttpClient;


    public SolrTestHelper() {
        solrAdminHttpClient = new SolrAdminHttpClient(IntegrationTestUtil.getSolrAdminUrl());
    }

    public void setSolrCoreName(String coreName) {
        solrCoreName = coreName;
    }

    public void waitForTransactionSync() throws InterruptedException, SolrAdminClientException {
        for (int i = 0; i < maxTries; i++) {
            if (areSolrTransactionsSynced()) {
                return;
            }
            // These prints are here to send data over the wire while waiting.
            // This prevents any http proxy from closing the connection due to timeouts
            System.out.print("Waiting 5 seconds for Solr to index transactions");
            for (int j = 0; j < 5; j++) {
                System.out.print("..." + ((i * 5) + j + 1));
                Thread.sleep(1000);
            }
            System.out.println();
        }
    }

    public Boolean areSolrTransactionsSynced() throws SolrAdminClientException {
        return areSolrTransactionsSynced(solrCoreName);
    }

    public Boolean areSolrTransactionsSynced(String coreName) throws SolrAdminClientException {
        JSONObject summary = solrAdminHttpClient.getSolrSummaryJson();
        int lastTxInIndex = summary.getJSONObject(coreName).getInt("Id for last TX in index");
        int remainingTransactions = summary.getJSONObject(coreName).getInt("Approx transactions remaining");
        System.out.println("LastTx:" + lastTxInIndex + " Remaining tx = " + remainingTransactions);
        if (lastTxInIndex > 0 && remainingTransactions == 0) {
            return true;
        } else {
            return false;
        }
    }
}
