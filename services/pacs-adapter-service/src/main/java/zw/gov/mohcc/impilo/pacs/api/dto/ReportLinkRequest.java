package zw.gov.mohcc.impilo.pacs.api.dto;

import jakarta.validation.constraints.NotBlank;

public class ReportLinkRequest {

    @NotBlank
    private String reportRef;

    private String reportingProviderRef;

    @NotBlank
    private String reportStatus = "AVAILABLE";

    public String getReportRef() {
        return reportRef;
    }

    public void setReportRef(String reportRef) {
        this.reportRef = reportRef;
    }

    public String getReportingProviderRef() {
        return reportingProviderRef;
    }

    public void setReportingProviderRef(String reportingProviderRef) {
        this.reportingProviderRef = reportingProviderRef;
    }

    public String getReportStatus() {
        return reportStatus;
    }

    public void setReportStatus(String reportStatus) {
        this.reportStatus = reportStatus;
    }
}
