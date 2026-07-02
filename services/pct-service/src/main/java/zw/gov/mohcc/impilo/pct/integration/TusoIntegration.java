package zw.gov.mohcc.impilo.pct.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Integration service for the TUSO Facility Configuration Service.
 *
 * <p>TUSO is the authoritative source for facility configuration data
 * including workspace (service point) catalogs, queue definitions, ward
 * layouts, and bed inventories. PCT queries TUSO to discover the available
 * workspaces and queues when setting up shift contexts and routing
 * patients.</p>
 *
 * <p>All external calls degrade gracefully: if TUSO is unavailable, the
 * failure is logged and an empty list/map is returned. Future versions
 * should implement local caching with TTL-based invalidation.</p>
 */
@Service
public class TusoIntegration {

    private static final Logger log = LoggerFactory.getLogger(TusoIntegration.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public TusoIntegration(RestTemplate restTemplate,
                           @Value("${pct.integration.tuso.base-url:http://localhost:8084}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    /**
     * Retrieve the workspace (service point) catalog for a facility.
     *
     * <p>Returns all configured workspaces including their type, name,
     * capacity, and operational hours. This data is used to populate
     * shift-start selectors and routing options.</p>
     *
     * @param facilityId the facility to query
     * @return list of workspace definitions, or empty list on failure
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getWorkspaceCatalog(UUID facilityId) {
        try {
            String url = baseUrl + "/v1/facilities/" + facilityId + "/workspaces";
            ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("TUSO workspace catalog retrieved for facility {} ({} workspaces)",
                        facilityId, response.getBody().size());
                return response.getBody();
            }

            log.warn("TUSO returned non-success status {} when retrieving workspaces for facility {}",
                    response.getStatusCode(), facilityId);
            return Collections.emptyList();

        } catch (RestClientException e) {
            log.warn("TUSO unavailable when retrieving workspaces for facility {}: {}",
                    facilityId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Retrieve TUSO's queue definitions for a facility (derived from its service points).
     *
     * <p>TUSO owns queue definitions via facility service-point/workspace configuration; PCT materialises
     * them. The facility is addressed by its canonical facility UUID (the identity downstream services
     * key off). Each definition carries a stable {@code sourceRef} (TUSO service-point id), {@code name},
     * {@code queueType}, {@code active}, and optional {@code workspaceId}.</p>
     *
     * <p>Returns {@code null} on failure/unavailability (distinct from an empty list, which means the
     * facility genuinely has no queues) so materialisation can stay failure-safe and never wipe queues
     * when TUSO is unreachable.</p>
     *
     * @param facilityUuid the canonical facility UUID to query
     * @return list of queue definitions, an empty list if none, or {@code null} on failure
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getQueueDefinitions(UUID facilityUuid) {
        try {
            String url = baseUrl + "/v1/internal/facilities/by-uuid/" + facilityUuid + "/queue-definitions";
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object data = response.getBody().getOrDefault("data", response.getBody().get("definitions"));
                if (data instanceof List<?> list) {
                    log.info("TUSO queue definitions retrieved for facility {} ({} definitions)",
                            facilityUuid, list.size());
                    return (List<Map<String, Object>>) list;
                }
                log.warn("TUSO queue-definitions response for facility {} had no data array", facilityUuid);
                return null;
            }

            log.warn("TUSO returned non-success status {} when retrieving queue definitions for facility {}",
                    response.getStatusCode(), facilityUuid);
            return null;

        } catch (RestClientException e) {
            log.warn("TUSO unavailable when retrieving queue definitions for facility {}: {}",
                    facilityUuid, e.getMessage());
            return null;
        }
    }

    /**
     * Retrieve the bed list for a specific ward.
     *
     * <p>Returns all beds in the ward including their number, type,
     * and current availability status. This data is used by the
     * admission workflow for bed assignment.</p>
     *
     * @param wardId the ward to query
     * @return list of bed records, or empty list on failure
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getBedList(UUID wardId) {
        try {
            String url = baseUrl + "/v1/wards/" + wardId + "/beds";
            ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("TUSO bed list retrieved for ward {} ({} beds)",
                        wardId, response.getBody().size());
                return response.getBody();
            }

            log.warn("TUSO returned non-success status {} when retrieving beds for ward {}",
                    response.getStatusCode(), wardId);
            return Collections.emptyList();

        } catch (RestClientException e) {
            log.warn("TUSO unavailable when retrieving beds for ward {}: {}", wardId, e.getMessage());
            return Collections.emptyList();
        }
    }
}
