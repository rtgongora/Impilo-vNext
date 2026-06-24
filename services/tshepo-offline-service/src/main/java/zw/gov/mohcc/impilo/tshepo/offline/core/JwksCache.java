package zw.gov.mohcc.impilo.tshepo.offline.core;

import com.nimbusds.jose.jwk.JWKSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.tshepo.offline.client.KeysServiceClient;
import zw.gov.mohcc.impilo.tshepo.offline.config.OfflineProperties;

import java.time.Clock;
import java.time.Instant;

/**
 * Locally-cached JWKS for offline capability-token verification.
 *
 * <p>Token verification must work offline: it cannot fetch the JWKS from the keys-service
 * on every verify. This cache holds the parsed key set for a configurable TTL, so within
 * that window verification uses cached keys and never touches the network. On a refresh
 * failure the <b>last-known-good</b> key set is retained, so a keys-service outage (or a
 * genuinely offline device) does not break verification of previously-issued tokens.</p>
 *
 * <p>{@link #forceRefresh()} bypasses the TTL so a verifier that sees an unknown {@code kid}
 * can pick up a rotated key once before failing closed.</p>
 */
@Component
public class JwksCache {

    private static final Logger log = LoggerFactory.getLogger(JwksCache.class);

    private final KeysServiceClient keysClient;
    private final long ttlSeconds;
    private final Clock clock;

    private volatile JWKSet cached;
    private volatile Instant fetchedAt;

    public JwksCache(KeysServiceClient keysClient, OfflineProperties properties) {
        this(keysClient, properties, Clock.systemUTC());
    }

    // Package-visible for deterministic TTL testing.
    JwksCache(KeysServiceClient keysClient, OfflineProperties properties, Clock clock) {
        this.keysClient = keysClient;
        this.ttlSeconds = properties.jwksCacheTtlSeconds();
        this.clock = clock;
    }

    /**
     * The cached JWKS, refreshed when older than the TTL. Offline-safe: if the refresh
     * fails but a previous set is cached, the cached set is returned.
     *
     * @throws JwksUnavailableException only when no cache exists and the fetch fails.
     */
    public synchronized JWKSet get() {
        if (cached != null && fetchedAt != null
                && clock.instant().isBefore(fetchedAt.plusSeconds(ttlSeconds))) {
            return cached;
        }
        return refresh();
    }

    /**
     * Force a fetch bypassing the TTL (e.g. to pick up a rotated key). The last-known-good
     * set is retained on failure.
     */
    public synchronized JWKSet forceRefresh() {
        return refresh();
    }

    private JWKSet refresh() {
        try {
            JWKSet fresh = JWKSet.parse(keysClient.fetchJwks());
            cached = fresh;
            fetchedAt = clock.instant();
            return fresh;
        } catch (Exception e) {
            if (cached != null) {
                log.warn("JWKS refresh failed; serving last-known-good cached keys (offline mode)", e);
                return cached;
            }
            throw new JwksUnavailableException("JWKS unavailable and no cached keys to fall back on", e);
        }
    }
}
