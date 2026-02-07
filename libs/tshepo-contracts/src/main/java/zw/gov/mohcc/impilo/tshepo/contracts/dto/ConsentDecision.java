package zw.gov.mohcc.impilo.tshepo.contracts.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Result of consent evaluation.
 *
 * <p>Used by the policy engine to determine whether a specific actor
 * may access a specific patient's data for a given purpose.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConsentDecision(
        boolean permitted,
        String consentId,
        String provision,
        List<String> allowedScopes,
        List<String> deniedScopes,
        String reason
) {
    public static ConsentDecision permit(String consentId, List<String> allowedScopes) {
        return new ConsentDecision(true, consentId, "permit", allowedScopes, null, null);
    }

    public static ConsentDecision deny(String reason) {
        return new ConsentDecision(false, null, "deny", null, null, reason);
    }

    public static ConsentDecision noConsentFound() {
        return new ConsentDecision(false, null, null, null, null, "NO_ACTIVE_CONSENT");
    }
}
