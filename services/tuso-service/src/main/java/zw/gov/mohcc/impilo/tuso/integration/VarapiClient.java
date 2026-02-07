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
     * Aggregated provider information returned by VARAPI.
     */
    public record ProviderInfo(
            String providerId,
            String actorType,
            Set<String> roles,
            Set<String> privileges
    ) {}
}
