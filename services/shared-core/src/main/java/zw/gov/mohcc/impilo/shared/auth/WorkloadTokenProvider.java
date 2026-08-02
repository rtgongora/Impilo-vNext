package zw.gov.mohcc.impilo.shared.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Acquires and caches this workload's own OAuth2 client-credentials token.
 *
 * <p>Why this exists: the estate's peer services call each other with a bare
 * {@code new RestTemplate()} and no credential at all. Only {@code experience-bff} could mint a
 * service token, so a service that turned its resource server on would immediately reject every
 * peer — which is precisely what blocked the first enforcement cohort. Trust headers are context,
 * not authentication; without a token there is nothing for a callee to verify.</p>
 *
 * <h2>Caching, and why it is not optional</h2>
 *
 * <p>A token acquired per request would put Keycloak on the synchronous path of every east-west
 * call, turning the identity provider into a latency and availability dependency of the whole
 * estate. The token is cached until {@code refreshSkew} before expiry, so the steady state is zero
 * Keycloak calls per request.</p>
 *
 * <p>Refresh carries <strong>jitter</strong>. Without it, every workload that started together
 * holds a token that expires together and they stampede the token endpoint in lockstep — the
 * refresh storm the programme's performance gates forbid. The jitter is subtracted, never added,
 * so a jittered refresh is always <em>earlier</em> than the deadline and can never serve an
 * expired token.</p>
 *
 * <h2>Failure behaviour</h2>
 *
 * <p>Acquisition failure returns {@link Optional#empty()} and is <strong>not</strong> retried per
 * request: a short backoff prevents a failing identity provider from being hammered by every
 * in-flight call. Empty means the caller sends no {@code Authorization} header, so the callee's
 * resource server rejects it. That is deliberate — the request fails closed at the callee rather
 * than the caller inventing an unauthenticated fallback.</p>
 */
public class WorkloadTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(WorkloadTokenProvider.class);

    /** Logged once per failure window so a broken credential is visible without flooding. */
    public static final String MINT_FAILED_MARKER = "WORKLOAD_TOKEN_MINT_FAILED";

    private final RestTemplate restTemplate;
    private final WorkloadTokenProperties properties;
    private final AtomicReference<CachedToken> cache = new AtomicReference<>(null);
    private final AtomicReference<Instant> backoffUntil = new AtomicReference<>(Instant.EPOCH);

    public WorkloadTokenProvider(RestTemplate restTemplate, WorkloadTokenProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    private record CachedToken(String token, Instant refreshAt) {
        boolean usable(Instant now) {
            return now.isBefore(refreshAt);
        }
    }

    /** This workload's bearer token, or empty when one cannot currently be obtained. */
    public Optional<String> getAccessToken() {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }
        Instant now = Instant.now();

        CachedToken cached = cache.get();
        if (cached != null && cached.usable(now)) {
            return Optional.of(cached.token());
        }
        if (now.isBefore(backoffUntil.get())) {
            // Still inside a failure window; do not re-hammer the identity provider.
            return Optional.empty();
        }
        synchronized (this) {
            cached = cache.get();
            if (cached != null && cached.usable(Instant.now())) {
                return Optional.of(cached.token());
            }
            return acquire();
        }
    }

    private Optional<String> acquire() {
        if (properties.getClientId() == null || properties.getClientId().isBlank()
                || properties.getClientSecret() == null || properties.getClientSecret().isBlank()) {
            log.warn("{}: client-id/client-secret not configured for this workload", MINT_FAILED_MARKER);
            backoffUntil.set(Instant.now().plusSeconds(properties.getFailureBackoffSeconds()));
            return Optional.empty();
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.getClientId());
        form.add("client_secret", properties.getClientSecret());
        if (properties.getScope() != null && !properties.getScope().isBlank()) {
            form.add("scope", properties.getScope());
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    properties.getTokenUri(), new HttpEntity<>(form, headers), Map.class);
            Map<?, ?> body = response.getBody();
            Object token = body == null ? null : body.get("access_token");
            if (token == null) {
                throw new IllegalStateException("token endpoint returned no access_token");
            }
            long expiresIn = body.get("expires_in") instanceof Number n
                    ? n.longValue() : properties.getDefaultLifetimeSeconds();

            // Refresh early by the skew, then earlier again by a random jitter, so workloads that
            // started together do not expire together and stampede the token endpoint.
            long jitter = properties.getRefreshJitterSeconds() > 0
                    ? ThreadLocalRandom.current().nextLong(properties.getRefreshJitterSeconds() + 1)
                    : 0L;
            long refreshInSeconds = expiresIn - properties.getRefreshSkewSeconds() - jitter;
            // Never schedule a refresh in the past, and never hold a token past its own lifetime.
            refreshInSeconds = Math.max(1L, Math.min(refreshInSeconds, expiresIn - 1));

            cache.set(new CachedToken(token.toString(), Instant.now().plus(Duration.ofSeconds(refreshInSeconds))));
            backoffUntil.set(Instant.EPOCH);
            log.debug("Workload token acquired for {}, refreshing in {}s", properties.getClientId(), refreshInSeconds);
            return Optional.of(token.toString());
        } catch (Exception e) {
            // The message may name the client but never the secret; the secret is only ever in the
            // form body, which is not logged.
            log.warn("{}: {} ({})", MINT_FAILED_MARKER, e.getClass().getSimpleName(), e.getMessage());
            backoffUntil.set(Instant.now().plusSeconds(properties.getFailureBackoffSeconds()));
            cache.set(null);
            return Optional.empty();
        }
    }

    /** Testing seam: forget any cached token without touching backoff state. */
    void evictForTest() {
        cache.set(null);
    }
}
