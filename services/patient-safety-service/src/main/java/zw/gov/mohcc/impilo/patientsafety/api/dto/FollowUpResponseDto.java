package zw.gov.mohcc.impilo.patientsafety.api.dto;

import jakarta.validation.constraints.NotBlank;

/** A reporter's response to a follow-up request; attaches to the case. */
public record FollowUpResponseDto(
        @NotBlank String responseText,
        String responderId,
        String responderKind,       // PROVIDER | CITIZEN | CAREGIVER
        String attachmentObjectId   // optional document-service objectId
) {}
