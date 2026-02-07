package zw.gov.mohcc.impilo.shareslip.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.util.List;
import java.util.UUID;

/**
 * Request to create a new share link for delegated document pickup.
 *
 * @param subjectType          the type of subject (e.g., PATIENT, PRESCRIPTION, LAB_RESULT)
 * @param subjectId            the unique identifier of the subject
 * @param subjectName          optional human-readable subject name (for display on slip)
 * @param documentIds          list of document IDs from landela-adapter to share
 * @param purpose              optional free-text purpose of the share
 * @param verificationMethod   OTP or QR_SCAN (defaults to OTP if not specified)
 * @param expiryHours          hours until link expires (defaults to 24, max 168)
 * @param maxClaims            maximum number of times the link can be claimed (defaults to 1)
 */
public record CreateShareLinkRequest(
        @NotBlank(message = "subjectType is required")
        String subjectType,

        @NotBlank(message = "subjectId is required")
        String subjectId,

        String subjectName,

        @NotEmpty(message = "At least one documentId is required")
        List<UUID> documentIds,

        String purpose,

        @Pattern(regexp = "^(OTP|QR_SCAN)$", message = "verificationMethod must be OTP or QR_SCAN")
        String verificationMethod,

        @Min(value = 1, message = "expiryHours must be at least 1")
        Integer expiryHours,

        @Min(value = 1, message = "maxClaims must be at least 1")
        Integer maxClaims
) {
}
