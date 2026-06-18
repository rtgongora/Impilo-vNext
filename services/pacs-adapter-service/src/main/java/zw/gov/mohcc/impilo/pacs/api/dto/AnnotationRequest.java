package zw.gov.mohcc.impilo.pacs.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public class AnnotationRequest {

    @NotBlank
    private String annotationType = "REVIEW_NOTE";

    private String note;

    private Long seriesId;

    private Long instanceId;

    private Map<String, Object> measurement;

    private Map<String, Object> body;

    private String facilityId;

    public String getAnnotationType() {
        return annotationType;
    }

    public void setAnnotationType(String annotationType) {
        this.annotationType = annotationType;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Long getSeriesId() {
        return seriesId;
    }

    public void setSeriesId(Long seriesId) {
        this.seriesId = seriesId;
    }

    public Long getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(Long instanceId) {
        this.instanceId = instanceId;
    }

    public Map<String, Object> getMeasurement() {
        return measurement;
    }

    public void setMeasurement(Map<String, Object> measurement) {
        this.measurement = measurement;
    }

    public Map<String, Object> getBody() {
        return body;
    }

    public void setBody(Map<String, Object> body) {
        this.body = body;
    }

    public String getFacilityId() {
        return facilityId;
    }

    public void setFacilityId(String facilityId) {
        this.facilityId = facilityId;
    }
}
