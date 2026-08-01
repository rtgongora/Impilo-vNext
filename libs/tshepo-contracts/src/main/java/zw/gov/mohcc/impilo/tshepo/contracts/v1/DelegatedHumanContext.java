package zw.gov.mohcc.impilo.tshepo.contracts.v1;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Human context carried through a workload-delegated request.
 * Downstream representations must be equal to or less privileged than the source.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DelegatedHumanContext(
        String contractVersion,
        HumanSubjectRef humanSubject,
        ActiveContext activeContext,
        AuthorityBinding authority,
        String purposeOfUse,
        String audience,
        List<String> allowedActions,
        List<String> allowedScopes,
        AuthenticationAssurance authenticationAssurance,
        int delegationDepth,
        Instant expiresAt,
        String correlationId,
        String requestId
) {
    public DelegatedHumanContext {
        TrustContractVersion.requireV1(contractVersion);
        Objects.requireNonNull(humanSubject, "humanSubject");
        allowedActions = allowedActions == null ? List.of() : List.copyOf(allowedActions);
        allowedScopes = allowedScopes == null ? List.of() : List.copyOf(allowedScopes);
        if (delegationDepth < 0) {
            throw new IllegalArgumentException("delegationDepth must be >= 0");
        }
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && now != null && expiresAt.isBefore(now);
    }

    /**
     * True when {@code other} does not increase privilege relative to this context.
     */
    public boolean permitsDownstream(DelegatedHumanContext other) {
        if (other == null) {
            return false;
        }
        if (other.delegationDepth() < this.delegationDepth()) {
            return false;
        }
        if (other.delegationDepth() > this.delegationDepth() + 1) {
            return false;
        }
        if (!subset(other.allowedActions(), this.allowedActions())) {
            return false;
        }
        if (!subset(other.allowedScopes(), this.allowedScopes())) {
            return false;
        }
        if (this.expiresAt != null && other.expiresAt != null && other.expiresAt.isAfter(this.expiresAt)) {
            return false;
        }
        if (this.authenticationAssurance != null && other.authenticationAssurance != null) {
            if (other.authenticationAssurance.aal() > this.authenticationAssurance.aal()) {
                return false;
            }
            if (this.authenticationAssurance.isConstrainedRecovery()
                    && !other.authenticationAssurance.isConstrainedRecovery()) {
                return false;
            }
        }
        return true;
    }

    private static boolean subset(List<String> candidate, List<String> parent) {
        if (candidate == null || candidate.isEmpty()) {
            return true;
        }
        if (parent == null || parent.isEmpty()) {
            return false;
        }
        return parent.containsAll(candidate);
    }
}
