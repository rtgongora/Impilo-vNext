package zw.gov.mohcc.impilo.tshepo.sdk.envelope;

import com.nimbusds.jose.jwk.JWKSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

/**
 * The PDP's public keys, fetched once and reused.
 *
 * <p>Key rotation means a token can legitimately arrive signed by a key this service has
 * never seen, so an unrecognised {@code kid} triggers a refresh rather than a rejection.
 * That is also an amplifier if left unbounded — a caller inventing a kid per request would
 * turn each one into an outbound fetch — so refreshes are rate limited by a cooldown, and a
 * caller who defeats the cache gets a rejection instead of a stampede at keys-service.</p>
 *
 * <p>A failed refresh keeps the previous key set. Keys do not stop being valid because the
 * service serving them is briefly unreachable, and discarding them would convert a
 * keys-service blip into a total rejection of otherwise good traffic.</p>
 */
public class JwksSource {

    private static final Logger log = LoggerFactory.getLogger(JwksSource.class);

    private final String jwksUri;
    private final Duration cacheFor;
    private final Duration refreshCooldown;
    private final HttpClient httpClient;

    private volatile JWKSet cached;
    private volatile Instant fetchedAt = Instant.EPOCH;
    private volatile Instant lastRefreshAttempt = Instant.EPOCH;

    public JwksSource(String jwksUri, Duration cacheFor, Duration refreshCooldown) {
        this(jwksUri, cacheFor, refreshCooldown,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
    }

    JwksSource(String jwksUri, Duration cacheFor, Duration refreshCooldown, HttpClient httpClient) {
        this.jwksUri = jwksUri;
        this.cacheFor = cacheFor;
        this.refreshCooldown = refreshCooldown;
        this.httpClient = httpClient;
    }

    /** The current key set, refreshed if stale. Null when it has never been fetched. */
    public JWKSet current() {
        if (cached == null || Instant.now().isAfter(fetchedAt.plus(cacheFor))) {
            refresh();
        }
        return cached;
    }

    /**
     * Fetch again because a token named a key we do not hold — unless the cooldown has not
     * elapsed, in which case the caller verifies against what we already have and fails.
     *
     * @return true if a fetch was actually attempted
     */
    public boolean refreshForUnknownKid() {
        Instant now = Instant.now();
        if (now.isBefore(lastRefreshAttempt.plus(refreshCooldown))) {
            return false;
        }
        refresh();
        return true;
    }

    private synchronized void refresh() {
        lastRefreshAttempt = Instant.now();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(jwksUri))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("JWKS fetch from {} returned {} — keeping the previous key set", jwksUri,
                        response.statusCode());
                return;
            }
            cached = JWKSet.parse(response.body());
            fetchedAt = Instant.now();
        } catch (Exception e) {
            // Deliberately keeps `cached`: a brief outage at keys-service must not invalidate
            // keys that are still perfectly good.
            log.warn("JWKS fetch from {} failed ({}) — keeping the previous key set",
                    jwksUri, e.getMessage());
        }
    }
}
