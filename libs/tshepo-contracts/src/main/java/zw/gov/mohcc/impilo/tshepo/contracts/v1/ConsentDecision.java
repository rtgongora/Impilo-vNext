package zw.gov.mohcc.impilo.tshepo.contracts.v1;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Canonical consent decision — distinct from {@link LawfulBasisDecision}.
 *
 * <p>Does not resolve Mvumo vs tshepo-consent-service ownership. See Checkpoint 1
 * {@code CONSENT_CONTRACT_INCOMPATIBILITY.md}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConsentDecision(
        String contractVersion,
        boolean permitted,
        String consentId,
        String provision,
        List<String> allowedScopes,
        List<String> deniedScopes,
        String reasonCode,
        String subjectRef,
        String purpose
) {
    public ConsentDecision {
        TrustContractVersion.requireV1(contractVersion);
        allowedScopes = allowedScopes == null ? List.of() : List.copyOf(allowedScopes);
        deniedScopes = deniedScopes == null ? List.of() : List.copyOf(deniedScopes);
    }

    public static ConsentDecision permit(String consentId, List<String> allowedScopes, String subjectRef, String purpose) {
        return new ConsentDecision(
                TrustContractVersion.V1, true, consentId, "permit", allowedScopes, List.of(), null, subjectRef, purpose);
    }

    public static ConsentDecision deny(String reasonCode, String subjectRef, String purpose) {
        return new ConsentDecision(
                TrustContractVersion.V1, false, null, "deny", List.of(), List.of(), reasonCode, subjectRef, purpose);
    }
}
