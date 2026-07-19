package zw.gov.mohcc.impilo.varapi.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Applicant-supplied fields for a self-service provider-access request
 * (IATG Journey D). The person anchor is server-authoritative (X-Actor-ID);
 * it is never taken from the body.
 */
public record SubmitProviderAccessRequest(
        @NotBlank String requestType,
        String profession,
        String councilCode,
        String councilNumber,
        String ecNumber,
        String organizationRef,
        String evidenceSummary,
        java.util.UUID facilityId,
        String engagementType,
        java.time.LocalDate accessValidFrom,
        java.time.LocalDate accessValidTo
) {}
