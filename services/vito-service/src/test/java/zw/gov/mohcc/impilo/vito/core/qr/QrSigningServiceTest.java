package zw.gov.mohcc.impilo.vito.core.qr;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class QrSigningServiceTest {

    private static final String SEED = "a-stable-qr-signing-seed-of-32-bytes++";

    private QrSigningService service;

    private QrSigningService instance(String seed) throws Exception {
        return instance(seed, "");
    }

    private QrSigningService instance(String seed, String previousSeed) throws Exception {
        QrSigningService s = new QrSigningService("seed", seed, previousSeed);
        s.init();
        return s;
    }

    @BeforeEach
    void setUp() throws Exception {
        service = instance(SEED);
    }

    @Test
    void tokenVerifiesAcrossInstances_survivesRestart() throws Exception {
        // C3 regression: a token signed before a restart / by another pod must
        // still verify. Seed-derived keys are identical across instances.
        QrSigningService afterRestart = instance(SEED);
        String token = service.sign("tenant1", "PICKUP", "ptr", 3600);
        assertTrue(afterRestart.verify(token).isPresent());
    }

    @Test
    void differentSeedCannotVerify() throws Exception {
        QrSigningService other = instance("a-DIFFERENT-qr-seed-of-32-bytes-long++");
        String token = service.sign("tenant1", "WALLET", "ptr", 3600);
        assertTrue(other.verify(token).isEmpty());
    }

    @Test
    void publicJwkIsStableAndPublicOnly() throws Exception {
        assertEquals(instance(SEED).getPublicKeyJwk(), instance(SEED).getPublicKeyJwk());
        assertFalse(service.getPublicKeyJwk().contains("\"d\""),
                "public JWK must not leak the private scalar");
    }

    @Test
    void signProducesNonEmptyToken() {
        String token = service.sign("tenant1", "HEALTH_ID", "pointer123", 3600);
        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test
    void signedTokenCanBeVerified() {
        String token = service.sign("tenant1", "EMERGENCY", "ptr", 3600);
        Optional<QrSigningService.QrClaims> claims = service.verify(token);

        assertTrue(claims.isPresent());
        assertEquals("tenant1", claims.get().tenantId());
        assertEquals("EMERGENCY", claims.get().purpose());
        assertEquals("ptr", claims.get().pointer());
    }

    @Test
    void expiredTokenIsRejected() {
        // Sign with 0-second TTL (already expired)
        String token = service.sign("tenant1", "HEALTH_ID", "ptr", 0);

        // Wait a tiny bit to ensure expiry
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}

        Optional<QrSigningService.QrClaims> claims = service.verify(token);
        assertTrue(claims.isEmpty());
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = service.sign("tenant1", "HEALTH_ID", "ptr", 3600);
        // Tamper with the signature
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        Optional<QrSigningService.QrClaims> claims = service.verify(tampered);
        assertTrue(claims.isEmpty());
    }

    @Test
    void garbageInputIsRejected() {
        Optional<QrSigningService.QrClaims> claims = service.verify("not.a.valid.jwt");
        assertTrue(claims.isEmpty());
    }

    @Test
    void eachTokenHasUniqueJti() {
        String t1 = service.sign("tenant1", "HEALTH_ID", "ptr", 3600);
        String t2 = service.sign("tenant1", "HEALTH_ID", "ptr", 3600);

        var c1 = service.verify(t1).orElseThrow();
        var c2 = service.verify(t2).orElseThrow();

        assertNotEquals(c1.jti(), c2.jti());
    }

    @Test
    void rotationOverlap_previousSeedTokensStillVerify() throws Exception {
        // X4 K1<->K2 overlap: rotate the seed while retaining the old one
        // verify-only — QRs issued before the rotation stay valid to expiry.
        String newSeed = "a-ROTATED-qr-signing-seed-of-32-bytes+";
        QrSigningService rotated = instance(newSeed, SEED);

        String oldToken = service.sign("tenant1", "PICKUP", "ptr", 3600);
        assertTrue(rotated.verify(oldToken).isPresent(), "pre-rotation QR must verify during overlap");

        String newToken = rotated.sign("tenant1", "PICKUP", "ptr", 3600);
        assertTrue(rotated.verify(newToken).isPresent());

        // Without the overlap configured, the old token is rejected.
        QrSigningService noOverlap = instance(newSeed);
        assertTrue(noOverlap.verify(oldToken).isEmpty());
    }

    @Test
    void kidIsVersionedBySeed() throws Exception {
        String kidA = service.getPublicKeyJwk();
        String kidB = instance("a-DIFFERENT-qr-seed-of-32-bytes-long++").getPublicKeyJwk();
        assertTrue(kidA.contains("\"kid\":\"vito-qr-"), "kid must be versioned, was: " + kidA);
        assertNotEquals(kidA, kidB, "a seed rotation must change the kid");
    }

    @Test
    void publicKeyIsExposed() {
        String jwk = service.getPublicKeyJwk();
        assertNotNull(jwk);
        assertTrue(jwk.contains("\"kty\":\"OKP\""));
        assertTrue(jwk.contains("\"crv\":\"Ed25519\""));
    }

    @Test
    void failsClosedOnBlankOrWeakSeed() {
        assertThrows(IllegalStateException.class,
                () -> new QrSigningService("seed", "", ""));
        assertThrows(IllegalStateException.class,
                () -> new QrSigningService("seed", "too-short", ""));
        assertThrows(IllegalStateException.class,
                () -> new QrSigningService("seed", "vito-qr-signing-dev-seed-change-me-32b", ""));
    }
}
