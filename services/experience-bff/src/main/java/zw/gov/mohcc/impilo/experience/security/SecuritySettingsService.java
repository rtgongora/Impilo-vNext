package zw.gov.mohcc.impilo.experience.security;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.experience.client.KeycloakAdminClient;

/** Account security composition over Keycloak-native credentials and sessions. */
@Service
public class SecuritySettingsService {
    private static final Set<String> NON_WORKFORCE = Set.of("CITIZEN", "CAREGIVER", "CARE_PARTNER");
    private static final Set<String> PRIVILEGED = Set.of(
            "SYSTEM_ADMIN", "SUPER_ADMIN", "DEVELOPER", "HIE_ADMIN", "REGISTRY_ADMIN");
    private static final Set<String> MFA_TYPES = Set.of(
            "otp", "webauthn", "webauthn-passwordless", "recovery-authn-codes");

    private final KeycloakAdminClient keycloak;

    public SecuritySettingsService(KeycloakAdminClient keycloak) {
        this.keycloak = keycloak;
    }

    public SecuritySnapshot snapshot(Jwt jwt) {
        String userId = jwt.getSubject();
        Set<String> roles = realmRoles(jwt);
        boolean workforce = roles.stream().anyMatch(role -> !NON_WORKFORCE.contains(role));
        boolean privileged = roles.stream().anyMatch(PRIVILEGED::contains);
        List<Credential> credentials = keycloak.credentials(userId).stream()
                .filter(value -> !"password".equals(value.type()))
                .map(value -> new Credential(value.id(), method(value.type()), value.userLabel(),
                        value.createdDate() == null ? null : Instant.ofEpochMilli(value.createdDate()),
                        removable(value.type())))
                .toList();
        String currentSid = jwt.getClaimAsString("sid");
        List<Session> sessions = keycloak.sessions(userId).stream()
                .map(value -> new Session(value.id(), value.ipAddress(),
                        epoch(value.startedAt()), epoch(value.lastAccessAt()),
                        value.id().equals(currentSid), clientNames(value.clients())))
                .toList();
        long recoveryCredentials = credentials.stream().filter(c -> "recovery".equals(c.method())).count();
        String requiredAal = privileged ? "urn:impilo:aal3" : workforce ? "urn:impilo:aal2" : "urn:impilo:aal1";
        return new SecuritySnapshot(credentials, sessions, requiredAal, workforce, privileged,
                jwt.getClaimAsString("acr"), amr(jwt), recoveryCredentials > 0,
                null, List.of("UPDATE_PASSWORD", "CONFIGURE_TOTP", "webauthn-register",
                        "webauthn-register-passwordless", "CONFIGURE_RECOVERY_AUTHN_CODES"));
    }

    public void removeCredential(Jwt jwt, String credentialId) {
        // Constrained recovery may never delete credentials: an attacker holding only a
        // recovery code could otherwise strip the account's last real factor. Enrolling a
        // REPLACEMENT factor (CREATE) is the only credential mutation recovery permits.
        requireNotConstrainedRecovery(jwt);
        requireFreshAal2(jwt);
        List<KeycloakAdminClient.CredentialMetadata> credentials = keycloak.credentials(jwt.getSubject());
        KeycloakAdminClient.CredentialMetadata target = credentials.stream()
                .filter(value -> value.id().equals(credentialId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "CREDENTIAL_NOT_FOUND"));
        if (!MFA_TYPES.contains(target.type())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CREDENTIAL_NOT_REMOVABLE");
        }
        boolean workforce = realmRoles(jwt).stream().anyMatch(role -> !NON_WORKFORCE.contains(role));
        long remainingMfa = credentials.stream()
                .filter(value -> MFA_TYPES.contains(value.type()) && !value.id().equals(credentialId)).count();
        if (workforce && remainingMfa == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "WORKFORCE_MFA_REQUIRED");
        }
        if (!keycloak.removeCredential(jwt.getSubject(), credentialId)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "CREDENTIAL_REMOVAL_NOT_CONFIRMED");
        }
    }

    public void terminateSession(Jwt jwt, String sessionId) {
        requireFreshAal2OrConstrainedRecovery(jwt);
        boolean owned = keycloak.sessions(jwt.getSubject()).stream().anyMatch(value -> value.id().equals(sessionId));
        if (!owned) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND");
        if (!keycloak.terminateSession(sessionId)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "SESSION_REVOCATION_NOT_CONFIRMED");
        }
    }

    public int terminateOtherSessions(Jwt jwt) {
        requireFreshAal2OrConstrainedRecovery(jwt);
        String current = jwt.getClaimAsString("sid");
        List<KeycloakAdminClient.SessionMetadata> others = keycloak.sessions(jwt.getSubject()).stream()
                .filter(value -> current == null || !current.equals(value.id())).toList();
        int revoked = 0;
        for (KeycloakAdminClient.SessionMetadata session : others) {
            if (keycloak.terminateSession(session.id())) revoked++;
        }
        if (revoked != others.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "SESSION_REVOCATION_INCOMPLETE");
        }
        return revoked;
    }

    private static void requireFreshAal2(Jwt jwt) {
        if (aalRank(jwt.getClaimAsString("acr")) < 2) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "STEP_UP_AAL2_REQUIRED");
        }
        Instant authTime = claimInstant(jwt, "auth_time");
        if (authTime == null || authTime.plusSeconds(300).isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "FRESH_AUTHENTICATION_REQUIRED");
        }
    }

    /**
     * Session termination is access-REDUCING, so the Tshepo recovery doctrine explicitly
     * permits it during CONSTRAINED_RECOVERY (a recovery session is AAL1 by construction and
     * could otherwise never terminate the sessions it is required to be able to terminate).
     * Ordinary sessions keep the strict fresh-AAL2 requirement unchanged.
     */
    private static void requireFreshAal2OrConstrainedRecovery(Jwt jwt) {
        if (isConstrainedRecovery(jwt)) return;
        requireFreshAal2(jwt);
    }

    /** Recovery may not delete credentials — fail with the canonical recovery decision. */
    private static void requireNotConstrainedRecovery(Jwt jwt) {
        if (isConstrainedRecovery(jwt)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "CONSTRAINED_RECOVERY_SESSION");
        }
    }

    /** Canonical recovery classification — same v1 contract markers the trust plane uses. */
    static boolean isConstrainedRecovery(Jwt jwt) {
        return zw.gov.mohcc.impilo.tshepo.contracts.v1.AuthenticationAssurance.recoveryStateFromAmr(amr(jwt))
                == zw.gov.mohcc.impilo.tshepo.contracts.v1.RecoveryAuthenticationState.CONSTRAINED_RECOVERY;
    }

    @SuppressWarnings("unchecked")
    static Set<String> realmRoles(Jwt jwt) {
        Object realm = jwt.getClaims().get("realm_access");
        if (!(realm instanceof Map<?, ?> map) || !(map.get("roles") instanceof List<?> roles)) return Set.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        roles.forEach(role -> result.add(String.valueOf(role)));
        return Set.copyOf(result);
    }

    private static String method(String type) {
        return switch (type) {
            case "otp" -> "totp";
            case "webauthn", "webauthn-passwordless" -> "passkey";
            case "recovery-authn-codes" -> "recovery";
            default -> type;
        };
    }

    private static boolean removable(String type) { return MFA_TYPES.contains(type); }

    private static int aalRank(String value) {
        if (value == null) return 0;
        if (value.endsWith("aal3")) return 3;
        if (value.endsWith("aal2")) return 2;
        if (value.endsWith("aal1")) return 1;
        return 0;
    }

    private static Instant claimInstant(Jwt jwt, String name) {
        Object value = jwt.getClaims().get(name);
        return value instanceof Number number ? Instant.ofEpochSecond(number.longValue()) : null;
    }

    private static Instant epoch(long millis) { return millis <= 0 ? null : Instant.ofEpochMilli(millis); }

    private static List<String> clientNames(com.fasterxml.jackson.databind.JsonNode clients) {
        if (clients == null || !clients.isObject()) return List.of();
        List<String> values = new java.util.ArrayList<>();
        clients.fields().forEachRemaining(entry -> values.add(entry.getValue().asText(entry.getKey())));
        return List.copyOf(values);
    }

    @SuppressWarnings("unchecked")
    private static List<String> amr(Jwt jwt) {
        Object value = jwt.getClaims().get("amr");
        if (value instanceof List<?> list) return list.stream().map(String::valueOf).toList();
        return value == null ? List.of() : List.of(String.valueOf(value));
    }

    public record Credential(String id, String method, String label, Instant createdAt, boolean removable) {}
    public record Session(String id, String ipAddress, Instant startedAt, Instant lastActiveAt,
                          boolean current, List<String> clients) {}
    public record SecuritySnapshot(List<Credential> methods, List<Session> sessions, String requiredAal,
                                   boolean workforce, boolean privileged, String currentAal,
                                   List<String> currentMethods, boolean recoveryCodesConfigured,
                                   Integer recoveryCodesRemaining, List<String> availableActions) {}
}
