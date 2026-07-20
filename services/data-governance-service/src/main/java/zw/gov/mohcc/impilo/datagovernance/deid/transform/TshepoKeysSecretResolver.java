package zw.gov.mohcc.impilo.datagovernance.deid.transform;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.datagovernance.core.TenantKeys;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Real key-custody {@link SecretResolver}: resolves {@code deid_dataset.secret_ref}
 * against the tshepo-keys managed-secret store
 * ({@code GET /v1/secrets/{ref}?tenantId=<uuid>}, W4a). Key material never lives in
 * this service's configuration or database — only the reference does.
 *
 * <p>Active only when {@code impilo.deid.secret-source=tshepo-keys}; it is
 * {@link Primary} so the {@link DatasetTokeniser} binds to it in preference to the
 * config-backed {@link ConfigSecretResolver}, which remains the fallback for local
 * dev / test. Resolution is scoped to the current request's tenant (tshepo-keys
 * partitions secrets by tenant) and cached per {@code tenant::ref}.</p>
 *
 * <p>Fails closed: any unresolvable reference, missing value, or transport failure
 * throws {@link IllegalStateException} so the run is REJECTED
 * ({@code SECRET_UNAVAILABLE}) and never tokenises with a missing secret.</p>
 */
@Component
@Primary
@ConditionalOnProperty(name = "impilo.deid.secret-source", havingValue = "tshepo-keys")
public class TshepoKeysSecretResolver implements SecretResolver {

    private static final Logger log = LoggerFactory.getLogger(TshepoKeysSecretResolver.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final ConcurrentMap<String, String> cache = new ConcurrentHashMap<>();

    public TshepoKeysSecretResolver(
            @Value("${impilo.deid.tshepo-keys.base-url:http://localhost:8184}") String baseUrl) {
        this(baseUrl, new RestTemplate());
    }

    // Package-private seam for tests (MockRestServiceServer).
    TshepoKeysSecretResolver(String baseUrl, RestTemplate restTemplate) {
        this.baseUrl = baseUrl;
        this.restTemplate = restTemplate;
    }

    @Override
    public String resolve(String secretRef) {
        if (secretRef == null || secretRef.isBlank()) {
            throw new IllegalStateException("De-identification secret reference is required");
        }
        UUID tenantId = currentTenant();
        String cacheKey = tenantId + "::" + secretRef;
        String cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        String value = fetch(secretRef, tenantId);
        cache.put(cacheKey, value);
        return value;
    }

    private UUID currentTenant() {
        RequestContext ctx = RequestContextHolder.get();
        if (ctx == null || ctx.tenantId() == null || ctx.tenantId().isBlank()) {
            throw new IllegalStateException(
                    "Cannot resolve tshepo-keys secret without a bound request tenant");
        }
        return TenantKeys.tenantUuid(ctx.tenantId());
    }

    private String fetch(String secretRef, UUID tenantId) {
        try {
            ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                    baseUrl + "/v1/secrets/{ref}?tenantId={tenant}", JsonNode.class,
                    secretRef, tenantId.toString());
            JsonNode body = response.getBody();
            String value = body != null && body.hasNonNull("value") ? body.get("value").asText() : null;
            if (value == null || value.isBlank()) {
                throw new IllegalStateException(
                        "tshepo-keys returned no material for secret reference '" + secretRef + "'");
            }
            log.debug("Resolved deid secret via tshepo-keys [ref={}, tenant={}]", secretRef, tenantId);
            return value;
        } catch (RestClientException e) {
            // Fail closed — never tokenise when custody is unavailable.
            throw new IllegalStateException(
                    "De-identification secret not available for reference '" + secretRef
                            + "' (custody: tshepo-keys unreachable at " + baseUrl + ")", e);
        }
    }
}
