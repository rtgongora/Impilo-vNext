package zw.gov.mohcc.impilo.tuso.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import zw.gov.mohcc.impilo.tuso.config.TusoProperties;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Client for VARAPI Provider Registry.
 *
 * <p>Fetches provider information (actor type, roles) from the VARAPI service.
 * Used by WorkspaceService to check provider eligibility for workspaces.</p>
 *
 * <p>Graceful degradation: if VARAPI is unavailable, returns empty roles
 * (which restricts access rather than granting it).</p>
 */
@Service
public class VarapiClient {

    private static final Logger log = LoggerFactory.getLogger(VarapiClient.class);

    private final TusoProperties tusoProperties;
    private final RestClient restClient;

    public VarapiClient(TusoProperties tusoProperties,
                         RestClient.Builder restClientBuilder) {
        this.tusoProperties = tusoProperties;
        this.restClient = restClientBuilder
                .baseUrl(tusoProperties.getVarapi().getBaseUrl())
                .build();
    }

    /**
     * Fetch the (hpa_institution_id, provider_public_id) pairs for HPA practitioner
     * candidates that VARAPI has materialised to a provider. TUSO consumes these to
     * seed PIC nominations in the HPA-2017 state machine.
     *
     * @return list of pair maps; empty if VARAPI is unavailable
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> fetchHpaNominationPairs() {
        try {
            Map<String, Object> response = restClient.get()
                    .uri("/v1/internal/providers/hpa-practitioner-import/nomination-pairs")
                    .retrieve()
                    .body((Class<Map<String, Object>>) (Class<?>) Map.class);
            if (response == null) {
                return Collections.emptyList();
            }
            Object data = response.get("data");
            return (data instanceof List<?> list) ? (List<Map<String, Object>>) list : Collections.emptyList();
        } catch (RestClientException e) {
            log.warn("Failed to fetch HPA nomination pairs from VARAPI: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Fetch the roles assigned to a provider.
     *
     * @param providerId the provider identifier
     * @return list of role strings; empty list if VARAPI is unavailable
     */
    @SuppressWarnings("unchecked")
    public List<String> getProviderRoles(String providerId) {
        try {
            Map<String, Object> response = restClient.get()
                    .uri("/api/v1/providers/{providerId}", providerId)
                    .retrieve()
                    .body((Class<Map<String, Object>>) (Class<?>) Map.class);

            if (response == null) {
                log.warn("Null response from VARAPI for provider {}", providerId);
                return Collections.emptyList();
            }

            Object rolesObj = response.get("roles");
            if (rolesObj instanceof List<?> list) {
                return list.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .toList();
            }

            return Collections.emptyList();

        } catch (RestClientException e) {
            log.error("Failed to fetch provider roles from VARAPI for {}: {}",
                    providerId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Fetch the actor type for a provider.
     *
     * @param providerId the provider identifier
     * @return the actor type string (e.g. "DOCTOR", "NURSE"), or null if unavailable
     */
    @SuppressWarnings("unchecked")
    public String getProviderType(String providerId) {
        try {
            Map<String, Object> response = restClient.get()
                    .uri("/api/v1/providers/{providerId}", providerId)
                    .retrieve()
                    .body((Class<Map<String, Object>>) (Class<?>) Map.class);

            if (response == null) {
                log.warn("Null response from VARAPI for provider {}", providerId);
                return null;
            }

            Object actorType = response.get("actorType");
            return actorType != null ? actorType.toString() : null;

        } catch (RestClientException e) {
            log.error("Failed to fetch provider type from VARAPI for {}: {}",
                    providerId, e.getMessage());
            return null;
        }
    }

    /**
     * Fetch full provider info (actor type, roles, privileges) in a single call.
     *
     * @param providerId the provider identifier
     * @return provider info record; never null (fields may be empty on failure)
     */
    @SuppressWarnings("unchecked")
    public ProviderInfo getProviderInfo(String providerId) {
        try {
            Map<String, Object> response = restClient.get()
                    .uri("/api/v1/providers/{providerId}", providerId)
                    .retrieve()
                    .body((Class<Map<String, Object>>) (Class<?>) Map.class);

            if (response == null) {
                log.warn("Null response from VARAPI for provider {}", providerId);
                return new ProviderInfo(providerId, null, Collections.emptySet(), Collections.emptySet());
            }

            String actorType = response.get("actorType") != null
                    ? response.get("actorType").toString() : null;

            Set<String> roles = extractStringSet(response, "roles");
            Set<String> privileges = extractStringSet(response, "privileges");

            return new ProviderInfo(providerId, actorType, roles, privileges);

        } catch (RestClientException e) {
            log.error("Failed to fetch provider info from VARAPI for {}: {}", providerId, e.getMessage());
            return new ProviderInfo(providerId, null, Collections.emptySet(), Collections.emptySet());
        }
    }

    private Set<String> extractStringSet(Map<String, Object> map, String key) {
        Object obj = map.get(key);
        if (obj instanceof java.util.Collection<?> c) {
            return Set.copyOf(c.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList());
        }
        return Collections.emptySet();
    }

    /**
     * Request a time-specific PIC eligibility assessment from VARAPI
     * (the professional-registry source of truth). Returns the assessment
     * verbatim so Tuso can snapshot it on the nomination — per-axis evidence,
     * reasons and source-record versions included. Trust headers are
     * synthesized for this service-originated call (MISSING_REQUIRED_HEADER
     * defect family). Throws on failure — nomination must not proceed on an
     * unresolvable provider.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> requestEligibilityAssessment(String providerPublicId,
                                                            String facilityRef,
                                                            String facilityUnitRef,
                                                            String purpose,
                                                            String tenantId) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("providerPublicId", providerPublicId);
        if (facilityRef != null) body.put("facilityRef", facilityRef);
        if (facilityUnitRef != null) body.put("facilityUnitRef", facilityUnitRef);
        body.put("purpose", purpose != null ? purpose : "PIC_NOMINATION");
        Map<String, Object> response = restClient.post()
                .uri("/v1/internal/interop/eligibility/assessments")
                .headers(h -> {
                    if (tenantId != null) h.set("X-Tenant-ID", tenantId);
                    h.set("X-Pod-ID", "national-spine");
                    h.set("X-Request-ID", java.util.UUID.randomUUID().toString());
                    h.set("X-Correlation-ID", java.util.UUID.randomUUID().toString());
                    h.set("X-Actor-ID", "tuso-service");
                    h.set("X-Actor-Type", "SERVICE");
                    h.set("X-Purpose-Of-Use", "OPERATIONS");
                })
                .body(body)
                .retrieve()
                .body((Class<Map<String, Object>>) (Class<?>) Map.class);
        if (response == null) {
            throw new IllegalStateException("VARAPI returned no eligibility assessment for " + providerPublicId);
        }
        Object data = response.get("data");
        return data instanceof Map<?, ?> m ? (Map<String, Object>) m : response;
    }

    /**
     * Aggregated provider information returned by VARAPI.
     */
    public record ProviderInfo(
            String providerId,
            String actorType,
            Set<String> roles,
            Set<String> privileges
    ) {}
}
