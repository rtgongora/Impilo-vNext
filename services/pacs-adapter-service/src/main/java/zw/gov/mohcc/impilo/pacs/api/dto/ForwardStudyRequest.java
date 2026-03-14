package zw.gov.mohcc.impilo.pacs.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for forwarding an imaging study to Orthanc.
 */
public class ForwardStudyRequest {

    @NotBlank
    private String orthancUrl;

    private String priority;

    public String getOrthancUrl() { return orthancUrl; }
    public void setOrthancUrl(String orthancUrl) { this.orthancUrl = orthancUrl; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
}
