package zw.gov.mohcc.impilo.tshepo.offline.core;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.tshepo.offline.client.KeysServiceClient;
import zw.gov.mohcc.impilo.tshepo.offline.config.OfflineProperties;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves the JWKS cache (G057): verification stays offline within the TTL (one fetch),
 * refreshes after the TTL, and falls back to the last-known-good key set when the
 * keys-service is unreachable — only failing closed when there is no cache at all.
 */
@ExtendWith(MockitoExtension.class)
class JwksCacheTest {

    @Mock private KeysServiceClient keysClient;

    private Instant now = Instant.parse("2026-01-01T00:00:00Z");
    private final Clock clock = new Clock() {
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    };

    private String jwksA;
    private String jwksB;
    private JwksCache cache;

    private static OfflineProperties propsWithTtl(int ttlSeconds) {
        return new OfflineProperties(0, 0, 0, null, 0, 0, null, null, ttlSeconds);
    }

    @BeforeEach
    void setUp() throws Exception {
        OctetKeyPair a = new OctetKeyPairGenerator(Curve.Ed25519).keyID("kid-A").generate();
        OctetKeyPair b = new OctetKeyPairGenerator(Curve.Ed25519).keyID("kid-B").generate();
        jwksA = new JWKSet(a.toPublicJWK()).toString();
        jwksB = new JWKSet(b.toPublicJWK()).toString();
        cache = new JwksCache(keysClient, propsWithTtl(60), clock);
    }

    @Test
    void withinTtl_servesFromCache_singleFetch() {
        when(keysClient.fetchJwks()).thenReturn(jwksA);

        assertThat(cache.get().getKeyByKeyId("kid-A")).isNotNull();
        now = now.plusSeconds(30); // still within the 60s TTL
        assertThat(cache.get().getKeyByKeyId("kid-A")).isNotNull();

        verify(keysClient, times(1)).fetchJwks();
    }

    @Test
    void afterTtl_refetches() {
        when(keysClient.fetchJwks()).thenReturn(jwksA);

        cache.get();
        now = now.plusSeconds(61); // past TTL
        cache.get();

        verify(keysClient, times(2)).fetchJwks();
    }

    @Test
    void refreshFailure_servesLastKnownGood() {
        when(keysClient.fetchJwks()).thenReturn(jwksA).thenThrow(new RuntimeException("keys-service down"));

        assertThat(cache.get().getKeyByKeyId("kid-A")).isNotNull(); // primes the cache
        now = now.plusSeconds(61); // force a refresh attempt, which now fails

        // Offline fallback: the cached key set is still served, no exception.
        assertThat(cache.get().getKeyByKeyId("kid-A")).isNotNull();
    }

    @Test
    void noCacheAndFetchFails_failsClosed() {
        when(keysClient.fetchJwks()).thenThrow(new RuntimeException("keys-service down"));

        assertThatThrownBy(() -> cache.get()).isInstanceOf(JwksUnavailableException.class);
    }

    @Test
    void forceRefresh_picksUpRotatedKey_bypassingTtl() {
        when(keysClient.fetchJwks()).thenReturn(jwksA).thenReturn(jwksB);

        assertThat(cache.get().getKeyByKeyId("kid-A")).isNotNull();
        // Still within TTL, but a rotated key (kid-B) must be reachable via forceRefresh.
        assertThat(cache.forceRefresh().getKeyByKeyId("kid-B")).isNotNull();
        verify(keysClient, times(2)).fetchJwks();
    }
}
