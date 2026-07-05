package zw.gov.mohcc.impilo.pacs.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * Create/update request for a facility modality (machine) registry entry.
 */
public class ImagingModalityRequest {

    @NotBlank
    private String modalityType;

    @NotBlank
    private String name;

    private String aeTitle;
    private String host;
    private Integer port;
    private String calledAeTitle;
    private String callingAeTitle;
    private String vendor;
    private String model;
    private String serialNumber;
    private Boolean supportsDicomStorage;
    private Boolean supportsWorklist;
    private Boolean supportsMpps;
    private Boolean supportsDicomweb;
    private Map<String, Object> exportOptions;
    private String operationalStatus;
    private String defaultStorageDestination;
    private String defaultWorklistSource;
    private String notes;

    public String getModalityType() { return modalityType; }
    public void setModalityType(String modalityType) { this.modalityType = modalityType; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAeTitle() { return aeTitle; }
    public void setAeTitle(String aeTitle) { this.aeTitle = aeTitle; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }

    public String getCalledAeTitle() { return calledAeTitle; }
    public void setCalledAeTitle(String calledAeTitle) { this.calledAeTitle = calledAeTitle; }

    public String getCallingAeTitle() { return callingAeTitle; }
    public void setCallingAeTitle(String callingAeTitle) { this.callingAeTitle = callingAeTitle; }

    public String getVendor() { return vendor; }
    public void setVendor(String vendor) { this.vendor = vendor; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

    public Boolean getSupportsDicomStorage() { return supportsDicomStorage; }
    public void setSupportsDicomStorage(Boolean supportsDicomStorage) { this.supportsDicomStorage = supportsDicomStorage; }

    public Boolean getSupportsWorklist() { return supportsWorklist; }
    public void setSupportsWorklist(Boolean supportsWorklist) { this.supportsWorklist = supportsWorklist; }

    public Boolean getSupportsMpps() { return supportsMpps; }
    public void setSupportsMpps(Boolean supportsMpps) { this.supportsMpps = supportsMpps; }

    public Boolean getSupportsDicomweb() { return supportsDicomweb; }
    public void setSupportsDicomweb(Boolean supportsDicomweb) { this.supportsDicomweb = supportsDicomweb; }

    public Map<String, Object> getExportOptions() { return exportOptions; }
    public void setExportOptions(Map<String, Object> exportOptions) { this.exportOptions = exportOptions; }

    public String getOperationalStatus() { return operationalStatus; }
    public void setOperationalStatus(String operationalStatus) { this.operationalStatus = operationalStatus; }

    public String getDefaultStorageDestination() { return defaultStorageDestination; }
    public void setDefaultStorageDestination(String defaultStorageDestination) { this.defaultStorageDestination = defaultStorageDestination; }

    public String getDefaultWorklistSource() { return defaultWorklistSource; }
    public void setDefaultWorklistSource(String defaultWorklistSource) { this.defaultWorklistSource = defaultWorklistSource; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
