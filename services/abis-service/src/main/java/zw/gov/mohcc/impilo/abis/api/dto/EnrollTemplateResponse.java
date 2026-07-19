package zw.gov.mohcc.impilo.abis.api.dto;

public record EnrollTemplateResponse(
        Long id,
        String subjectRef,
        String modality,
        String position,
        String status) {
}
