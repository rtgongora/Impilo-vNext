package zw.gov.mohcc.impilo.pacs.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * Upsert request for a facility's imaging capability / deployment-mode record.
 * Only {@code deploymentMode} is mandatory — everything else is honest optional truth.
 */
public class FacilityImagingCapabilityRequest {

    @NotBlank
    private String deploymentMode;

    private String facilityName;
    private Boolean pacsVnaAvailable;
    private Boolean localGatewayAvailable;
    private Boolean dicomwebAvailable;
    private Boolean dimseAvailable;
    private Boolean mwlSupported;
    private Boolean cstoreReceiveSupported;
    private Boolean mppsSupported;
    private Boolean storeForwardSupported;
    private Boolean localCacheSupported;
    private Boolean manualImportSupported;
    private Boolean digitisationBridgeSupported;
    private Boolean viewerAvailable;
    private Boolean reportingAvailable;
    private String internetQuality;
    private String operationalStatus;
    private String defaultArchiveTarget;
    private String defaultWorklistTarget;
    private List<String> supportedModalities;
    private String configurationStatus;
    private String goLiveReadiness;
    private String notes;

    public String getDeploymentMode() { return deploymentMode; }
    public void setDeploymentMode(String deploymentMode) { this.deploymentMode = deploymentMode; }

    public String getFacilityName() { return facilityName; }
    public void setFacilityName(String facilityName) { this.facilityName = facilityName; }

    public Boolean getPacsVnaAvailable() { return pacsVnaAvailable; }
    public void setPacsVnaAvailable(Boolean pacsVnaAvailable) { this.pacsVnaAvailable = pacsVnaAvailable; }

    public Boolean getLocalGatewayAvailable() { return localGatewayAvailable; }
    public void setLocalGatewayAvailable(Boolean localGatewayAvailable) { this.localGatewayAvailable = localGatewayAvailable; }

    public Boolean getDicomwebAvailable() { return dicomwebAvailable; }
    public void setDicomwebAvailable(Boolean dicomwebAvailable) { this.dicomwebAvailable = dicomwebAvailable; }

    public Boolean getDimseAvailable() { return dimseAvailable; }
    public void setDimseAvailable(Boolean dimseAvailable) { this.dimseAvailable = dimseAvailable; }

    public Boolean getMwlSupported() { return mwlSupported; }
    public void setMwlSupported(Boolean mwlSupported) { this.mwlSupported = mwlSupported; }

    public Boolean getCstoreReceiveSupported() { return cstoreReceiveSupported; }
    public void setCstoreReceiveSupported(Boolean cstoreReceiveSupported) { this.cstoreReceiveSupported = cstoreReceiveSupported; }

    public Boolean getMppsSupported() { return mppsSupported; }
    public void setMppsSupported(Boolean mppsSupported) { this.mppsSupported = mppsSupported; }

    public Boolean getStoreForwardSupported() { return storeForwardSupported; }
    public void setStoreForwardSupported(Boolean storeForwardSupported) { this.storeForwardSupported = storeForwardSupported; }

    public Boolean getLocalCacheSupported() { return localCacheSupported; }
    public void setLocalCacheSupported(Boolean localCacheSupported) { this.localCacheSupported = localCacheSupported; }

    public Boolean getManualImportSupported() { return manualImportSupported; }
    public void setManualImportSupported(Boolean manualImportSupported) { this.manualImportSupported = manualImportSupported; }

    public Boolean getDigitisationBridgeSupported() { return digitisationBridgeSupported; }
    public void setDigitisationBridgeSupported(Boolean digitisationBridgeSupported) { this.digitisationBridgeSupported = digitisationBridgeSupported; }

    public Boolean getViewerAvailable() { return viewerAvailable; }
    public void setViewerAvailable(Boolean viewerAvailable) { this.viewerAvailable = viewerAvailable; }

    public Boolean getReportingAvailable() { return reportingAvailable; }
    public void setReportingAvailable(Boolean reportingAvailable) { this.reportingAvailable = reportingAvailable; }

    public String getInternetQuality() { return internetQuality; }
    public void setInternetQuality(String internetQuality) { this.internetQuality = internetQuality; }

    public String getOperationalStatus() { return operationalStatus; }
    public void setOperationalStatus(String operationalStatus) { this.operationalStatus = operationalStatus; }

    public String getDefaultArchiveTarget() { return defaultArchiveTarget; }
    public void setDefaultArchiveTarget(String defaultArchiveTarget) { this.defaultArchiveTarget = defaultArchiveTarget; }

    public String getDefaultWorklistTarget() { return defaultWorklistTarget; }
    public void setDefaultWorklistTarget(String defaultWorklistTarget) { this.defaultWorklistTarget = defaultWorklistTarget; }

    public List<String> getSupportedModalities() { return supportedModalities; }
    public void setSupportedModalities(List<String> supportedModalities) { this.supportedModalities = supportedModalities; }

    public String getConfigurationStatus() { return configurationStatus; }
    public void setConfigurationStatus(String configurationStatus) { this.configurationStatus = configurationStatus; }

    public String getGoLiveReadiness() { return goLiveReadiness; }
    public void setGoLiveReadiness(String goLiveReadiness) { this.goLiveReadiness = goLiveReadiness; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
