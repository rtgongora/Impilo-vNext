package zw.gov.mohcc.impilo.patientsafety.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Attach a document (already stored in document-service) to a report. Only the document-service
 * objectId is referenced here — patient-safety does not store binaries.
 */
public record AddAttachmentRequest(
        @NotBlank String documentObjectId,
        String filename,
        String mimeType,
        String description,
        Boolean consentObtained
) {}
