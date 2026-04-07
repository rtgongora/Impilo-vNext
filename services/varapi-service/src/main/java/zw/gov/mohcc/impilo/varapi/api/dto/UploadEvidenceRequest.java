package zw.gov.mohcc.impilo.varapi.api.dto;

public record UploadEvidenceRequest(
        Long documentId,
        Long eventId,
        String eventType,
        String evidenceType,
        String notes,
        java.time.Instant updatedAt
) {}
