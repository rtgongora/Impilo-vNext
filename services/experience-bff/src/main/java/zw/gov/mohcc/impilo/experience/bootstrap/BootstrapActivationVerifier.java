package zw.gov.mohcc.impilo.experience.bootstrap;

import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.experience.auth.session.WebAuthSessionStore;
import zw.gov.mohcc.impilo.experience.client.KeycloakAdminClient;

/** Verifies bootstrap activation entirely from server-side session and Keycloak state. */
@Service
public class BootstrapActivationVerifier {
    private final KeycloakAdminClient keycloak;
    private final int maxAuthAgeSeconds;

    public BootstrapActivationVerifier(KeycloakAdminClient keycloak,
            @Value("${impilo.bootstrap.aal3-max-age-seconds:300}") int maxAuthAgeSeconds) {
        this.keycloak = keycloak;
        this.maxAuthAgeSeconds = maxAuthAgeSeconds;
    }

    public Verification verify(String expectedUserId, WebAuthSessionStore.SessionData session) {
        if (session == null) return Verification.denied("An authenticated bootstrap session is required.");
        if (expectedUserId == null || expectedUserId.isBlank()
                || !expectedUserId.equals(session.subject())) {
            return Verification.denied("The authenticated user does not match the nominated bootstrap account.");
        }
        if (aal(session.acr()) < 3) {
            return Verification.denied("Fresh approved hardware authentication at AAL3 is required.");
        }
        List<String> methods = session.amr() == null ? List.of() : session.amr();
        if (methods.stream().noneMatch(BootstrapActivationVerifier::isWebAuthn)) {
            return Verification.denied("The AAL3 session must be authenticated with WebAuthn.");
        }
        Instant assuranceTime = session.stepUpTime() != null ? session.stepUpTime() : session.authTime();
        if (assuranceTime == null || assuranceTime.plusSeconds(maxAuthAgeSeconds).isBefore(Instant.now())) {
            return Verification.denied("The AAL3 authentication is too old; authenticate again.");
        }

        List<KeycloakAdminClient.CredentialMetadata> credentials = keycloak.credentials(expectedUserId);
        boolean hardware = credentials.stream().map(KeycloakAdminClient.CredentialMetadata::type)
                .anyMatch(type -> "webauthn".equalsIgnoreCase(type));
        boolean recovery = credentials.stream().map(KeycloakAdminClient.CredentialMetadata::type)
                .anyMatch(type -> "recovery-authn-codes".equalsIgnoreCase(type));
        if (!hardware) {
            return Verification.denied("An approved hardware WebAuthn credential is required.");
        }
        if (!recovery) {
            return Verification.denied("Recovery authentication codes must be generated and confirmed.");
        }
        return new Verification(true, "AAL3 hardware credential and recovery codes verified.",
                credentials.size());
    }

    static int aal(String acr) {
        if (acr == null) return 0;
        return switch (acr.trim().toLowerCase()) {
            case "3", "urn:impilo:aal3" -> 3;
            case "2", "urn:impilo:aal2" -> 2;
            case "1", "urn:impilo:aal1" -> 1;
            default -> 0;
        };
    }

    private static boolean isWebAuthn(String method) {
        return method != null && ("webauthn".equalsIgnoreCase(method)
                || "hwk".equalsIgnoreCase(method));
    }

    public record Verification(boolean allowed, String message, int credentialCount) {
        static Verification denied(String message) { return new Verification(false, message, 0); }
    }
}
