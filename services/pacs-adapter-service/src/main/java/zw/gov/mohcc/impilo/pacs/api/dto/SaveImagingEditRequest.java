package zw.gov.mohcc.impilo.pacs.api.dto;

public class SaveImagingEditRequest {
    private String editType = "ANNOTATION";
    private String annotationData;
    private String viewportData;
    private Long triageRecordId;
    private String journeyId;

    public String getEditType() { return editType; }
    public void setEditType(String editType) { this.editType = editType; }
    public String getAnnotationData() { return annotationData; }
    public void setAnnotationData(String annotationData) { this.annotationData = annotationData; }
    public String getViewportData() { return viewportData; }
    public void setViewportData(String viewportData) { this.viewportData = viewportData; }
    public Long getTriageRecordId() { return triageRecordId; }
    public void setTriageRecordId(Long triageRecordId) { this.triageRecordId = triageRecordId; }
    public String getJourneyId() { return journeyId; }
    public void setJourneyId(String journeyId) { this.journeyId = journeyId; }
}
