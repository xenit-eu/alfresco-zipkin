package eu.xenit.alfresco.instrumentation.solradmin;

public class SolrAdminClientException extends Exception {
    public SolrAdminClientException(String msgId, Throwable cause) {
        super(msgId, cause);
    }

    public SolrAdminClientException(String errorMessage) {
        super(errorMessage);
    }
}
