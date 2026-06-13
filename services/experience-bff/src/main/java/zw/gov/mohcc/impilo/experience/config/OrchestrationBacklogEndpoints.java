package zw.gov.mohcc.impilo.experience.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * PCW-1/PCW-2 downstream env vars mapped in preview Helm but without dedicated BFF RestTemplate clients yet.
 * Binding ensures cluster DNS URLs are available when orchestration clients are implemented.
 */
@ConfigurationProperties(prefix = "impilo.services.orchestration-backlog")
public class OrchestrationBacklogEndpoints {

    private String auditLedgerBaseUrl;
    private String butanoFhirBaseUrl;
    private String cardPrintAgentBaseUrl;
    private String connectorFhirBaseUrl;
    private String developerPortalBaseUrl;
    private String identityAssuranceBaseUrl;
    private String jobsServiceBaseUrl;
    private String observabilityBaseUrl;
    private String offlineEdgeBaseUrl;
    private String offlineSyncBaseUrl;
    private String pharmacyElmisBaseUrl;
    private String productRegistryBaseUrl;
    private String referralServiceBaseUrl;
    private String schemaRegistryBaseUrl;
    private String securityHardeningBaseUrl;
    private String shareSlipBaseUrl;

    public String getAuditLedgerBaseUrl() {
        return auditLedgerBaseUrl;
    }

    public void setAuditLedgerBaseUrl(String auditLedgerBaseUrl) {
        this.auditLedgerBaseUrl = auditLedgerBaseUrl;
    }

    public String getButanoFhirBaseUrl() {
        return butanoFhirBaseUrl;
    }

    public void setButanoFhirBaseUrl(String butanoFhirBaseUrl) {
        this.butanoFhirBaseUrl = butanoFhirBaseUrl;
    }

    public String getCardPrintAgentBaseUrl() {
        return cardPrintAgentBaseUrl;
    }

    public void setCardPrintAgentBaseUrl(String cardPrintAgentBaseUrl) {
        this.cardPrintAgentBaseUrl = cardPrintAgentBaseUrl;
    }

    public String getConnectorFhirBaseUrl() {
        return connectorFhirBaseUrl;
    }

    public void setConnectorFhirBaseUrl(String connectorFhirBaseUrl) {
        this.connectorFhirBaseUrl = connectorFhirBaseUrl;
    }

    public String getDeveloperPortalBaseUrl() {
        return developerPortalBaseUrl;
    }

    public void setDeveloperPortalBaseUrl(String developerPortalBaseUrl) {
        this.developerPortalBaseUrl = developerPortalBaseUrl;
    }

    public String getIdentityAssuranceBaseUrl() {
        return identityAssuranceBaseUrl;
    }

    public void setIdentityAssuranceBaseUrl(String identityAssuranceBaseUrl) {
        this.identityAssuranceBaseUrl = identityAssuranceBaseUrl;
    }

    public String getJobsServiceBaseUrl() {
        return jobsServiceBaseUrl;
    }

    public void setJobsServiceBaseUrl(String jobsServiceBaseUrl) {
        this.jobsServiceBaseUrl = jobsServiceBaseUrl;
    }

    public String getObservabilityBaseUrl() {
        return observabilityBaseUrl;
    }

    public void setObservabilityBaseUrl(String observabilityBaseUrl) {
        this.observabilityBaseUrl = observabilityBaseUrl;
    }

    public String getOfflineEdgeBaseUrl() {
        return offlineEdgeBaseUrl;
    }

    public void setOfflineEdgeBaseUrl(String offlineEdgeBaseUrl) {
        this.offlineEdgeBaseUrl = offlineEdgeBaseUrl;
    }

    public String getOfflineSyncBaseUrl() {
        return offlineSyncBaseUrl;
    }

    public void setOfflineSyncBaseUrl(String offlineSyncBaseUrl) {
        this.offlineSyncBaseUrl = offlineSyncBaseUrl;
    }

    public String getPharmacyElmisBaseUrl() {
        return pharmacyElmisBaseUrl;
    }

    public void setPharmacyElmisBaseUrl(String pharmacyElmisBaseUrl) {
        this.pharmacyElmisBaseUrl = pharmacyElmisBaseUrl;
    }

    public String getProductRegistryBaseUrl() {
        return productRegistryBaseUrl;
    }

    public void setProductRegistryBaseUrl(String productRegistryBaseUrl) {
        this.productRegistryBaseUrl = productRegistryBaseUrl;
    }

    public String getReferralServiceBaseUrl() {
        return referralServiceBaseUrl;
    }

    public void setReferralServiceBaseUrl(String referralServiceBaseUrl) {
        this.referralServiceBaseUrl = referralServiceBaseUrl;
    }

    public String getSchemaRegistryBaseUrl() {
        return schemaRegistryBaseUrl;
    }

    public void setSchemaRegistryBaseUrl(String schemaRegistryBaseUrl) {
        this.schemaRegistryBaseUrl = schemaRegistryBaseUrl;
    }

    public String getSecurityHardeningBaseUrl() {
        return securityHardeningBaseUrl;
    }

    public void setSecurityHardeningBaseUrl(String securityHardeningBaseUrl) {
        this.securityHardeningBaseUrl = securityHardeningBaseUrl;
    }

    public String getShareSlipBaseUrl() {
        return shareSlipBaseUrl;
    }

    public void setShareSlipBaseUrl(String shareSlipBaseUrl) {
        this.shareSlipBaseUrl = shareSlipBaseUrl;
    }
}
