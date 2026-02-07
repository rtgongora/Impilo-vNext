package zw.gov.mohcc.impilo.vito.core.qr;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.vito.config.VitoProperties;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class QrSigningServiceTest {

    private QrSigningService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new QrSigningService(new VitoProperties());
        service.init();
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
    void publicKeyIsExposed() {
        String jwk = service.getPublicKeyJwk();
        assertNotNull(jwk);
        assertTrue(jwk.contains("\"kty\":\"OKP\""));
        assertTrue(jwk.contains("\"crv\":\"Ed25519\""));
    }
}
