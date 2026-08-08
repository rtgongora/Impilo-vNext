package zw.gov.mohcc.impilo.shared.auth;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the duty shadow: the lane split, what it would refuse, and that SHADOW changes nothing.
 *
 * <p>The lane split is the whole design. Counting machine callers as would-deny would drown the
 * census in false positives — a {@code client_credentials} token has no roles by construction — and
 * a would-deny rate that is mostly schedulers tells you nothing about whether enforcing is safe.</p>
 */
class DutyShadowTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    /** A user token as experience-ui actually mints it: realm_access.roles present. */
    private void authenticatedUser(String username, String... roles) {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .subject("user-sub")
                .claim("preferred_username", username)
                .claim("realm_access", Map.of("roles", List.of(roles)))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of()));
    }

    /** A workload token as impilo-bff actually mints it: NO realm_access at all. */
    private void authenticatedWorkload() {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .subject("sa-sub")
                .claim("preferred_username", "service-account-impilo-bff")
                .claim("scope", "profile email openid impilo-tenant")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of()));
    }

    @Test
    @DisplayName("a workload token asserting SYSTEM is the MACHINE lane — not a would-deny")
    void machineLaneIsNotCounted() {
        authenticatedWorkload();
        var o = DutyShadow.observe(DutyShadow.Mode.SHADOW, "closing a period", "/gl/periods", "SYSTEM");
        assertEquals("MACHINE", o.lane());
        assertTrue(o.wouldPermit(), "machine callers must not be counted as duty failures");
    }

    /**
     * The finding the census exists to surface: human authority asserted by a forgeable header on a
     * token that carries no duty at all. This is exactly what a workload token with
     * {@code X-Actor-Type: OPERATOR} does, and it produced real writes across the estate.
     */
    @Test
    @DisplayName("a workload token asserting OPERATOR is HUMAN lane and would be DENIED")
    void forgedHumanActorTypeIsTheFinding() {
        authenticatedWorkload();
        var o = DutyShadow.observe(DutyShadow.Mode.SHADOW, "approving a waiver", "/costa/v1/waivers", "OPERATOR");
        assertEquals("HUMAN", o.lane());
        assertFalse(o.wouldPermit(), "a token with no duty role must not silently pass as OPERATOR");
        assertTrue(o.roles().isEmpty());
    }

    @Test
    @DisplayName("a real finance user would be permitted")
    void financeUserWouldPass() {
        authenticatedUser("thandi.finance", "FINANCE", "default-roles-impilo");
        var o = DutyShadow.observe(DutyShadow.Mode.SHADOW, "approving a waiver", "/costa/v1/waivers", "OPERATOR");
        assertEquals("HUMAN", o.lane());
        assertTrue(o.wouldPermit());
        assertTrue(o.roles().contains("FINANCE"));
        assertFalse(o.roles().contains("default-roles-impilo"), "keycloak's composite is census noise");
    }

    @Test
    @DisplayName("a clinician with no finance duty would be DENIED — the separation P0 wanted")
    void clinicianWouldBeDenied() {
        authenticatedUser("dr.gwena", "CLINICIAN");
        var o = DutyShadow.observe(DutyShadow.Mode.SHADOW, "approving a waiver", "/costa/v1/waivers", "OPERATOR");
        assertFalse(o.wouldPermit());
        assertTrue(o.roles().contains("CLINICIAN"));
    }

    @Test
    @DisplayName("SHADOW changes no response; ENFORCE refuses with 403")
    void shadowObservesAndEnforceRefuses() {
        authenticatedUser("dr.gwena", "CLINICIAN");
        assertDoesNotThrow(() -> DutyShadow.observe(
                DutyShadow.Mode.SHADOW, "approving a waiver", "/costa/v1/waivers", "OPERATOR"),
                "SHADOW must observe, never refuse");

        var ex = assertThrows(ResponseStatusException.class, () -> DutyShadow.observe(
                DutyShadow.Mode.ENFORCE, "approving a waiver", "/costa/v1/waivers", "OPERATOR"));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("OFF does nothing at all")
    void offDoesNothing() {
        authenticatedUser("dr.gwena", "CLINICIAN");
        assertNull(DutyShadow.observe(DutyShadow.Mode.OFF, "x", "/y", "OPERATOR"));
    }

    /**
     * A machine claim must be corroborated by the token. Trusting the header alone would let the
     * forgeable X-Actor-Type move a caller into the lane that is never questioned — the hole this
     * class exists to measure.
     */
    @Test
    @DisplayName("a ROLE-BEARING user claiming SYSTEM is still HUMAN — the header alone cannot relane")
    void headerAloneCannotMoveAUserIntoTheMachineLane() {
        authenticatedUser("dr.gwena", "CLINICIAN");
        var o = DutyShadow.observe(DutyShadow.Mode.SHADOW, "approving a waiver", "/costa/v1/waivers", "SYSTEM");
        assertEquals("HUMAN", o.lane());
        assertFalse(o.wouldPermit());
    }

    @Test
    @DisplayName("an unreadable mode defaults to SHADOW, never to OFF")
    void unreadableModeFailsSafe() {
        assertEquals(DutyShadow.Mode.SHADOW, DutyShadow.Mode.parse("nonsense"));
        assertEquals(DutyShadow.Mode.SHADOW, DutyShadow.Mode.parse(null));
        assertEquals(DutyShadow.Mode.ENFORCE, DutyShadow.Mode.parse("enforce"));
        assertEquals(DutyShadow.Mode.OFF, DutyShadow.Mode.parse("OFF"));
    }

    @Test
    @DisplayName("a non-JWT authentication is handled without throwing")
    void nonJwtAuthentication() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("someone", "n/a", List.of()));
        var o = DutyShadow.observe(DutyShadow.Mode.SHADOW, "x", "/y", "OPERATOR");
        assertEquals("HUMAN", o.lane());
        assertFalse(o.wouldPermit());
    }
}
