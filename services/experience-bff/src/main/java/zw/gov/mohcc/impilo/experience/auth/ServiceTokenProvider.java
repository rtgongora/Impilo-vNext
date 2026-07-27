package zw.gov.mohcc.impilo.experience.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Mints and caches this workload's OWN OAuth2 credential (client_credentials grant against
 * Keycloak) for service-originated downstream calls.
 *
 * <p>Why: trust headers (X-Tenant-ID, X-Actor-ID, X-Actor-Type: SERVICE…) are context, NOT
 * authentication — a confused deputy was demonstrated on 2026-07-22 by forging X-Actor-ID from
 * another pod, and NetworkPolicies were empirically proven non-enforcing on the preview k3s, so
 * network segmentation is not a compensating control. Services that already enforce OAuth
 * (clinical-knowledge-platform, ndila) 401 unauthenticated S2S calls and the caller degrades
 * silently (e.g. distance-matrix falling back to haversine forever). This provider gives the
 * BFF a real credential for those calls; per-service providers are follow-up lanes.</p>
 *
 * <p>Behaviour:</p>
 * <ul>
 *   <li>Token cached until ~30s before its {@code expires_in} expiry; thread-safe
 *       double-checked refresh.</li>
 *   <li>Honest failure: if minting fails (secret absent, Keycloak down, non-2xx, bad body),
 *       logs WARN with the {@value #MINT_FAILED_MARKER} marker and yields empty so the call
 *       proceeds WITHOUT a token — preview, where the secret may be absent, keeps working.</li>
 *   <li>A failure is never cached longer than 30 seconds.</li>
 * </ul>
 *
 * <p>Uses its own plain {@link RestTemplate}: routing the token call through
 * {@code serviceRestTemplate} would re-enter the trust-header interceptor and recurse into
 * this provider.</p>
 */
@Component
public class ServiceTokenProvider {

    /** Distinctive, grep-able marker for every mint failure. */
    public static final String MINT_FAILED_MARKER = "SERVICE_TOKEN_MINT_FAILED";

    private static final Logger log = LoggerFactory.getLogger(ServiceTokenProvider.class);

    /** Refresh this long before the token's real expiry. */
    private static final Duration EXPIRY_SKEW = Duration.ofSeconds(30);
    /** Never remember a mint failure longer than this. */
    private static final Duration FAILURE_BACKOFF = Duration.ofSeconds(30);

    private final ServiceTokenProperties props;
    private final ObjectMapper objectMapper;
    private final RestTemplate tokenClient;
    private final Clock clock;

    private volatile String cached;
    private volatile Instant cacheExpiry = Instant.EPOCH;
    private volatile Instant failureBackoffUntil = Instant.EPOCH;

    @org.springframework.beans.factory.annotation.Autowired
    public ServiceTokenProvider(ServiceTokenProperties props, ObjectMapper objectMapper) {
        this(props, objectMapper, defaultTokenClient(), Clock.systemUTC());
    }

    /** Test seam: inject the token-endpoint client and the clock. */
    public ServiceTokenProvider(ServiceTokenProperties props, ObjectMapper objectMapper,
                                RestTemplate tokenClient, Clock clock) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.tokenClient = tokenClient;
        this.clock = clock;
    }

    private static RestTemplate defaultTokenClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(3));
        return new RestTemplate(factory);
    }

    /**
     * Returns a bearer token for this workload, or empty when disabled or minting currently
     * fails. Callers MUST treat empty as "proceed without Authorization", never as an error.
     */
    public Optional<String> getAccessToken() {
        if (!props.enabled()) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        String token = cached;
        if (token != null && now.isBefore(cacheExpiry)) {
            return Optional.of(token);
        }
        if (now.isBefore(failureBackoffUntil)) {
            return Optional.empty();
        }
        synchronized (this) {
            now = clock.instant();
            token = cached;
            if (token != null && now.isBefore(cacheExpiry)) {
                return Optional.of(token);
            }
            if (now.isBefore(failureBackoffUntil)) {
                return Optional.empty();
            }
            return mint(now);
        }
    }

    private Optional<String> mint(Instant now) {
        if (!props.hasSecret()) {
            return mintFailed(now,
                    "client secret absent (KEYCLOAK_BACKEND_SECRET / impilo.auth.service-token.client-secret unset)");
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "client_credentials");
            form.add("client_id", props.clientId());
            form.add("client_secret", props.clientSecret());
            if (props.scope() != null && !props.scope().isBlank()) {
                form.add("scope", props.scope().trim());
            }
            ResponseEntity<String> response = tokenClient.postForEntity(
                    props.tokenUri(), new HttpEntity<>(form, headers), String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return mintFailed(now, "token endpoint returned status=" + response.getStatusCode());
            }
            JsonNode body = objectMapper.readTree(response.getBody());
            String accessToken = body.path("access_token").asText(null);
            if (accessToken == null || accessToken.isBlank()) {
                return mintFailed(now, "token response carried no access_token");
            }
            long ttlSeconds = body.path("expires_in").asLong(300);
            cached = accessToken;
            cacheExpiry = now.plusSeconds(Math.max(EXPIRY_SKEW.toSeconds(),
                    ttlSeconds - EXPIRY_SKEW.toSeconds()));
            failureBackoffUntil = Instant.EPOCH;
            log.debug("Minted service token for client '{}' (ttl={}s)", props.clientId(), ttlSeconds);
            return Optional.of(accessToken);
        } catch (Exception e) {
            return mintFailed(now, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private Optional<String> mintFailed(Instant now, String reason) {
        failureBackoffUntil = now.plus(FAILURE_BACKOFF);
        log.warn("{}: client_credentials mint for '{}' at {} failed ({}) — proceeding WITHOUT a service token; "
                        + "retry in {}s",
                MINT_FAILED_MARKER, props.clientId(), props.tokenUri(), reason, FAILURE_BACKOFF.toSeconds());
        return Optional.empty();
    }
}
