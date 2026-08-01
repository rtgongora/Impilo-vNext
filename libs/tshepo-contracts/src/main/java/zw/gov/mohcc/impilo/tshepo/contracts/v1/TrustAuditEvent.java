package zw.gov.mohcc.impilo.tshepo.contracts.v1;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Trust-plane audit event. Never records raw tokens, passwords, OTPs, recovery codes,
 * private keys, or unnecessary clinical payloads.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TrustAuditEvent(
        String contractVersion,
        String eventId,
        Instant timestamp,
        String requestId,
        String traceId,
        String decisionId,
        String callingWorkloadId,
        String destinationWorkloadId,
        String humanActorId,
        String activeContextId,
        String delegationReference,
        String purposeOfUse,
        String action,
        String resourceReference,
        String credentialType,
        Integer identityAssuranceLevel,
        Integer authenticationAal,
        RecoveryAuthenticationState recoveryState,
        String policyVersion,
        String enforcementPoint,
        String result,
        Map<String, String> attributes
) {
    private static final Pattern FORBIDDEN = Pattern.compile(
            "(?i)(eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+|bearer\\s+|password|client_secret|private[_-]?key|"
                    + "recovery[_-]?code|otp\\b|refresh_token|access_token|-----BEGIN)");

    public TrustAuditEvent {
        TrustContractVersion.requireV1(contractVersion);
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId is required");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("timestamp is required");
        }
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        rejectSensitive(eventId);
        rejectSensitive(requestId);
        rejectSensitive(traceId);
        rejectSensitive(decisionId);
        rejectSensitive(resourceReference);
        rejectSensitive(delegationReference);
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            rejectSensitive(entry.getKey());
            rejectSensitive(entry.getValue());
            rejectClinicalPayloadKey(entry.getKey());
        }
    }

    private static void rejectSensitive(String value) {
        if (value != null && FORBIDDEN.matcher(value).find()) {
            throw new IllegalArgumentException("TrustAuditEvent must not contain secrets or credentials");
        }
    }

    private static void rejectClinicalPayloadKey(String key) {
        if (key == null) {
            return;
        }
        String k = key.toLowerCase(Locale.ROOT);
        if (k.contains("clinical") || k.contains("observation") || k.contains("fhir")
                || k.contains("diagnosis") || k.contains("medication")) {
            throw new IllegalArgumentException("TrustAuditEvent must not carry clinical payloads");
        }
    }
}
