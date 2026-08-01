package zw.gov.mohcc.impilo.tshepo.contracts.v1;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Safe trust challenge response. Exposes only the approved field set — never policy
 * expressions, credentials, tokens, clinical data, or confidential denial details.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = false)
public record TrustChallengeOutcome(
        @JsonProperty("contract_version") String contractVersion,
        TrustChallengeDecision decision,
        @JsonProperty("reason_code") String reasonCode,
        @JsonProperty("user_message_key") String userMessageKey,
        @JsonProperty("required_action") String requiredAction,
        @JsonProperty("required_assurance") Integer requiredAssurance,
        @JsonProperty("allowed_authentication_methods") List<String> allowedAuthenticationMethods,
        @JsonProperty("context_options") List<String> contextOptions,
        @JsonProperty("consent_request_reference") String consentRequestReference,
        @JsonProperty("approval_reference") String approvalReference,
        @JsonProperty("continuation_reference") String continuationReference,
        @JsonProperty("decision_id") String decisionId,
        @JsonProperty("policy_version") String policyVersion,
        @JsonProperty("expires_at") Instant expiresAt,
        @JsonProperty("support_reference") String supportReference
) {
    private static final Pattern FORBIDDEN = Pattern.compile(
            "(?i)(eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+|bearer\\s+|password|client_secret|"
                    + "private[_-]?key|recovery[_-]?code|policy_expr|rego\\b|deny_detail)");

    public TrustChallengeOutcome {
        TrustContractVersion.requireV1(contractVersion);
        if (decision == null) {
            throw new IllegalArgumentException("decision is required");
        }
        allowedAuthenticationMethods = allowedAuthenticationMethods == null
                ? List.of() : List.copyOf(allowedAuthenticationMethods);
        contextOptions = contextOptions == null ? List.of() : List.copyOf(contextOptions);
        rejectSensitive(reasonCode);
        rejectSensitive(userMessageKey);
        rejectSensitive(requiredAction);
        rejectSensitive(consentRequestReference);
        rejectSensitive(approvalReference);
        rejectSensitive(continuationReference);
        rejectSensitive(decisionId);
        rejectSensitive(policyVersion);
        rejectSensitive(supportReference);
        for (String method : allowedAuthenticationMethods) {
            rejectSensitive(method);
        }
        for (String option : contextOptions) {
            rejectSensitive(option);
        }
    }

    public static TrustChallengeOutcome of(
            TrustChallengeDecision decision,
            String reasonCode,
            String userMessageKey,
            String requiredAction,
            Integer requiredAssurance,
            List<String> allowedAuthenticationMethods,
            List<String> contextOptions,
            String consentRequestReference,
            String approvalReference,
            String continuationReference,
            String decisionId,
            String policyVersion,
            Instant expiresAt,
            String supportReference) {
        return new TrustChallengeOutcome(
                TrustContractVersion.V1,
                decision,
                reasonCode,
                userMessageKey,
                requiredAction,
                requiredAssurance,
                allowedAuthenticationMethods,
                contextOptions,
                consentRequestReference,
                approvalReference,
                continuationReference,
                decisionId,
                policyVersion,
                expiresAt,
                supportReference);
    }

    public static TrustChallengeOutcome allow(String decisionId, String policyVersion) {
        return of(TrustChallengeDecision.ALLOW, null, null, null, null, List.of(), List.of(),
                null, null, null, decisionId, policyVersion, null, null);
    }

    public static TrustChallengeOutcome deny(String reasonCode, String userMessageKey, String decisionId) {
        return of(TrustChallengeDecision.DENY, reasonCode, userMessageKey, null, null, List.of(), List.of(),
                null, null, null, decisionId, null, null, null);
    }

    private static void rejectSensitive(String value) {
        if (value != null && FORBIDDEN.matcher(value).find()) {
            throw new IllegalArgumentException("TrustChallengeOutcome must not expose sensitive material");
        }
    }
}
