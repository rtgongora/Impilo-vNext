package zw.gov.mohcc.impilo.tshepo.authz.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.tshepo.authz.config.AuthzProperties;
import zw.gov.mohcc.impilo.tshepo.contracts.trust.DecisionEnvelopeClaims;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mints the signed decision envelope on ALLOW (Phase D, D2).
 *
 * <h2>Why the private key stays in tshepo-keys-service</h2>
 *
 * <p>Signing locally would be one fewer network hop, and it was tempting because keys-service
 * can export raw private material under software custody. It would also mean the PDP holding
 * a signing key, which the custody seam calls "the residual migration surface for a real HSM"
 * — and a PKCS#11 provider refuses {@code exportPrivate} outright, so an envelope built that
 * way would stop working the day an HSM is wired. Instead the payload goes to
 * {@code POST /v1/sign}, which already picks the export path or the in-module path based on
 * custody, so this works unchanged on an HSM.</p>
 *
 * <p>The cost is one call per allowed request. That is the same class of cost the PDP already
 * pays: every authorize does a synchronous introspection round trip to identity, and sessions
 * validate over the network too. It is one more hop of a kind already on the path, not a new
 * kind of latency — and {@link #averageSignMicros()} exists so that claim can be checked
 * rather than believed.</p>
 *
 * <h2>Why a signing failure still allows</h2>
 *
 * <p>If keys-service is unreachable the request is allowed without an envelope. Denying would
 * turn a keys-service blip into an estate-wide outage, which is the failure the introspection
 * client is explicitly shaped to avoid. The fail-closed half belongs downstream: a service
 * configured to require an envelope rejects a request that arrives without one. That means
 * downstream enforcement must not be switched on while this signer is failing, and
 * {@link #UNSIGNED_MARKER} is in the log line so the operator can see that before flipping it.</p>
 */
@Component
public class DecisionEnvelopeSigner {

    private static final Logger log = LoggerFactory.getLogger(DecisionEnvelopeSigner.class);

    /** Grep target: the PDP allowed a request it could not put an envelope on. */
    public static final String UNSIGNED_MARKER = "DECISION_ENVELOPE_UNSIGNED";

    private final RestTemplate restTemplate;
    private final AuthzProperties properties;
    private final ObjectMapper objectMapper;

    private final AtomicLong signCount = new AtomicLong();
    private final AtomicLong signMicrosTotal = new AtomicLong();
    private final AtomicLong failureCount = new AtomicLong();

    public DecisionEnvelopeSigner(RestTemplate restTemplate,
                                  AuthzProperties properties,
                                  ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public boolean isEnabled() {
        return properties.isDecisionEnvelopeEnabled();
    }

    /**
     * Sign an envelope for an allowed request.
     *
     * @return the compact JWS, or empty when signing is disabled, the request lacks the
     *         identity to bind to, or keys-service could not sign it
     */
    public Optional<String> sign(UUID tenantId,
                                 String actorId,
                                 String actorType,
                                 UUID correlationId,
                                 String method,
                                 String path,
                                 String obligationsJson) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        if (tenantId == null || actorId == null || actorId.isBlank()) {
            // An envelope binds a decision to an actor in a tenant. With neither there is
            // nothing to bind, and an envelope naming nobody would verify against anybody.
            return Optional.empty();
        }

        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(properties.getDecisionEnvelopeTtlSeconds());

        Map<String, Object> claims = DecisionEnvelopeClaims.build(
                actorId,
                actorType,
                tenantId.toString(),
                correlationId == null ? null : correlationId.toString(),
                method,
                path,
                obligationsJson,
                now,
                expiry);

        long startNanos = System.nanoTime();
        try {
            String payload = objectMapper.writeValueAsString(claims);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(Map.of(
                    "tenantId", tenantId.toString(),
                    "payload", payload,
                    "jwsCompact", true,
                    "purpose", "AUTHZ_DECISION"), headers);

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    properties.getKeysServiceUrl() + "/v1/sign",
                    HttpMethod.POST, request, JsonNode.class);

            JsonNode body = response.getBody();
            JsonNode signature = body == null ? null : body.get("signature");
            if (signature == null || signature.asText().isBlank()) {
                recordFailure("keys-service returned no signature", correlationId);
                return Optional.empty();
            }

            signCount.incrementAndGet();
            signMicrosTotal.addAndGet((System.nanoTime() - startNanos) / 1_000);
            return Optional.of(signature.asText());
        } catch (Exception e) {
            recordFailure(e.getMessage(), correlationId);
            return Optional.empty();
        }
    }

    private void recordFailure(String reason, UUID correlationId) {
        failureCount.incrementAndGet();
        log.warn("{}: allowing without a decision envelope — {} (correlation={})",
                UNSIGNED_MARKER, reason, correlationId);
    }

    /** Mean signing latency in microseconds over successful signs, or 0 before any. */
    public long averageSignMicros() {
        long count = signCount.get();
        return count == 0 ? 0 : signMicrosTotal.get() / count;
    }

    public long signedCount() {
        return signCount.get();
    }

    public long failedCount() {
        return failureCount.get();
    }
}
