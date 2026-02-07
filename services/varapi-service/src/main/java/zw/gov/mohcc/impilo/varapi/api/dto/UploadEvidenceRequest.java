package zw.gov.mohcc.impilo.varapi.api.dto;

public record UploadEvidenceRequest(
        Long documentId,
        String evidenceType,
        String notes
) {}
