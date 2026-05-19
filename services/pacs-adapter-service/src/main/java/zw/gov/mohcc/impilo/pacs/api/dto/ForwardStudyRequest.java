package zw.gov.mohcc.impilo.pacs.api.dto;

/**
 * Request body for forwarding an imaging study to Orthanc.
 */
public class ForwardStudyRequest {

    /**
     * Optional override hint retained for backwards compatibility.
     * Runtime forwarding uses configured {@code impilo.orthanc.base-url}.
     */
    private String orthancUrl;

    private String priority;

    public String getOrthancUrl() { return orthancUrl; }
    public void setOrthancUrl(String orthancUrl) { this.orthancUrl = orthancUrl; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
}
