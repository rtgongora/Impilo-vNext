package zw.gov.mohcc.impilo.tshepo.contracts.v1;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Authentication strength (AAL/AMR/times). Separate from {@link IdentityAssurance}.
 *
 * <p>Recovery authentication is represented as {@link RecoveryAuthenticationState#CONSTRAINED_RECOVERY}
 * and must never be treated as ordinary workforce AAL2 authority.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthenticationAssurance(
        String contractVersion,
        int aal,
        List<String> amr,
        Instant authenticationTime,
        Instant stepUpTime,
        boolean phishingResistant,
        String sessionId,
        String flowId,
        RecoveryAuthenticationState recoveryState
) {
    private static final List<String> RECOVERY_AMR_MARKERS = List.of(
            "auth-recovery-authn-code-form",
            "recovery",
            "recovery-code",
            "recovery_code"
    );

    public AuthenticationAssurance {
        TrustContractVersion.requireV1(contractVersion);
        if (aal < 0 || aal > 3) {
            throw new IllegalArgumentException("aal must be between 0 and 3");
        }
        amr = amr == null ? List.of() : List.copyOf(amr);
        recoveryState = recoveryState == null ? RecoveryAuthenticationState.NONE : recoveryState;
        if (recoveryState == RecoveryAuthenticationState.CONSTRAINED_RECOVERY
                && aal >= 2
                && !containsRecoveryMarker(amr)) {
            // Allow explicit constrained state even if AMR list was redacted, but never
            // invent ordinary AAL2 from recovery without the constrained flag.
        }
    }

    public static AuthenticationAssurance of(
            int aal,
            List<String> amr,
            Instant authenticationTime,
            Instant stepUpTime,
            boolean phishingResistant,
            String sessionId,
            String flowId,
            RecoveryAuthenticationState recoveryState) {
        return new AuthenticationAssurance(
                TrustContractVersion.V1,
                aal,
                amr,
                authenticationTime,
                stepUpTime,
                phishingResistant,
                sessionId,
                flowId,
                recoveryState);
    }

    public static AuthenticationAssurance none() {
        return of(0, List.of(), null, null, false, null, null, RecoveryAuthenticationState.NONE);
    }

    /**
     * Derive recovery state from AMR. Callers that only have ACR must not invent ordinary AAL2
     * for recovery methods — use this after AMR is known.
     */
    public static RecoveryAuthenticationState recoveryStateFromAmr(List<String> amr) {
        return containsRecoveryMarker(amr)
                ? RecoveryAuthenticationState.CONSTRAINED_RECOVERY
                : RecoveryAuthenticationState.NONE;
    }

    /** Ordinary workforce AAL2 is false when recovery is constrained, regardless of numeric AAL. */
    public boolean grantsOrdinaryWorkforceAal2() {
        return aal >= 2 && recoveryState == RecoveryAuthenticationState.NONE;
    }

    public boolean isConstrainedRecovery() {
        return recoveryState == RecoveryAuthenticationState.CONSTRAINED_RECOVERY;
    }

    private static boolean containsRecoveryMarker(List<String> methods) {
        if (methods == null) {
            return false;
        }
        for (String method : methods) {
            if (method == null) {
                continue;
            }
            String normalised = method.toLowerCase(Locale.ROOT);
            for (String marker : RECOVERY_AMR_MARKERS) {
                if (normalised.equals(marker) || normalised.contains("recovery")) {
                    return true;
                }
            }
        }
        return false;
    }
}
