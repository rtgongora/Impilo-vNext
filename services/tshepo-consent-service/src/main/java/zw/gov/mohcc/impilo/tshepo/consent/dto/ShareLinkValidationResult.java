package zw.gov.mohcc.impilo.tshepo.consent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Result of validating and consuming a share link token.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ShareLinkValidationResult(
        boolean valid,
        String patientRef,
        String scope,
        String purpose,
        int remainingUses,
        String reason
) {
    public static ShareLinkValidationResult success(String patientRef, String scope,
                                                     String purpose, int remainingUses) {
        return new ShareLinkValidationResult(true, patientRef, scope, purpose, remainingUses, null);
    }

    public static ShareLinkValidationResult failure(String reason) {
        return new ShareLinkValidationResult(false, null, null, null, 0, reason);
    }
}
