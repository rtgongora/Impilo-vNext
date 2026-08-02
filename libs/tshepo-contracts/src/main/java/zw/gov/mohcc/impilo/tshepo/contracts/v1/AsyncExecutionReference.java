package zw.gov.mohcc.impilo.tshepo.contracts.v1;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Opaque, revocable task-delegation reference for asynchronous work.
 *
 * <p>Must not contain a reusable human access token. Workers authenticate with their own
 * {@link WorkloadSubject} and revalidate scope/authority/expiry/revocation at execution.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AsyncExecutionReference(
        String contractVersion,
        String referenceId,
        WorkloadSubject workerWorkload,
        HumanSubjectRef originalHumanSubject,
        List<String> allowedActions,
        List<String> allowedScopes,
        String audience,
        String purposeOfUse,
        int delegationDepth,
        Instant expiresAt,
        boolean revoked,
        String correlationId
) {
    private static final Pattern TOKEN_LIKE = Pattern.compile(
            "(?i)(eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}|bearer\\s+|access_token|refresh_token)");

    public AsyncExecutionReference {
        TrustContractVersion.requireV1(contractVersion);
        if (referenceId == null || referenceId.isBlank()) {
            throw new IllegalArgumentException("referenceId is required");
        }
        if (TOKEN_LIKE.matcher(referenceId).find()) {
            throw new IllegalArgumentException("referenceId must not contain an access token");
        }
        rejectTokenMaterial(referenceId);
        allowedActions = allowedActions == null ? List.of() : List.copyOf(allowedActions);
        allowedScopes = allowedScopes == null ? List.of() : List.copyOf(allowedScopes);
        for (String scope : allowedScopes) {
            rejectTokenMaterial(scope);
        }
        for (String action : allowedActions) {
            rejectTokenMaterial(action);
        }
        if (delegationDepth < 0) {
            throw new IllegalArgumentException("delegationDepth must be >= 0");
        }
    }

    public static AsyncExecutionReference of(
            String referenceId,
            WorkloadSubject workerWorkload,
            HumanSubjectRef originalHumanSubject,
            List<String> allowedActions,
            List<String> allowedScopes,
            String audience,
            String purposeOfUse,
            int delegationDepth,
            Instant expiresAt,
            boolean revoked,
            String correlationId) {
        return new AsyncExecutionReference(
                TrustContractVersion.V1,
                referenceId,
                workerWorkload,
                originalHumanSubject,
                allowedActions,
                allowedScopes,
                audience,
                purposeOfUse,
                delegationDepth,
                expiresAt,
                revoked,
                correlationId);
    }

    public boolean isExecutable(Instant now) {
        return !revoked && (expiresAt == null || now == null || !expiresAt.isBefore(now));
    }

    @JsonIgnore
    public boolean containsAccessTokenMaterial() {
        return TOKEN_LIKE.matcher(referenceId).find();
    }

    private static void rejectTokenMaterial(String value) {
        if (value != null && TOKEN_LIKE.matcher(value).find()) {
            throw new IllegalArgumentException("async execution reference must not carry access-token material");
        }
        if (value != null && value.toLowerCase(Locale.ROOT).contains("access_token=")) {
            throw new IllegalArgumentException("async execution reference must not carry access-token material");
        }
    }
}
