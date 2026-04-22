package zw.gov.mohcc.impilo.varapi.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.varapi.config.VarapiProperties;

import java.util.UUID;

/**
 * REST client that queries TUSO (Facility Registry) for facility and workspace
 * information used during privilege validation.
 *
 * <p>All calls gracefully handle connection failures by logging a warning and
 * returning {@code false} or {@code null}, so that TUSO unavailability does not
 * cascade into hard failures within VARAPI.</p>
 */
@Service
public class TusoClient {

    private static final Logger log = LoggerFactory.getLogger(TusoClient.class);

    private final RestTemplate restTemplate;
    private final VarapiProperties varapiProperties;

    public TusoClient(RestTemplate restTemplate, VarapiProperties varapiProperties) {
        this.restTemplate = restTemplate;
        this.varapiProperties = varapiProperties;
    }

    /**
     * Retrieve the human-readable facility name from TUSO.
     *
     * @param facilityId the internal facility identifier
     * @return the facility name, or {@code null} if the facility was not found
     *         or the call failed
     */
    public String getFacilityName(Long facilityId) {
        if (facilityId == null) {
            return null;
        }
        try {
            String url = varapiProperties.getTuso().getBaseUrl()
                    + "/v1/internal/facilities/" + facilityId + "/name";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return response.getBody();
        } catch (Exception e) {
            log.warn("Failed to retrieve facility name for facilityId={}: {}", facilityId, e.getMessage());
            return null;
        }
    }

    /**
     * Validate that a facility exists in the TUSO registry.
     *
     * @param facilityId the internal facility identifier
     * @return {@code true} if the facility exists, {@code false} on lookup failure
     *         or if the facility was not found
     */
    public boolean validateFacilityExists(Long facilityId) {
        if (facilityId == null) {
            return false;
        }
        try {
            String url = varapiProperties.getTuso().getBaseUrl()
                    + "/v1/internal/facilities/" + facilityId;
            restTemplate.getForEntity(url, String.class);
            return true;
        } catch (Exception e) {
            log.warn("Failed to validate facility {}: {}", facilityId, e.getMessage());
            return false;
        }
    }

    /**
     * Retrieve facility status summary for legitimacy checks.
     * Returns null on lookup failure.
     */
    public String getFacilityStatusSummary(Long facilityId) {
        if (facilityId == null) return null;
        try {
            String url = varapiProperties.getTuso().getBaseUrl()
                    + "/v1/internal/facilities/" + facilityId + "/status-summary";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return response.getBody();
        } catch (Exception e) {
            log.warn("Failed to retrieve facility status summary facilityId={}: {}", facilityId, e.getMessage());
            return null;
        }
    }

    /**
     * Validate that a workspace exists in the TUSO registry.
     *
     * @param workspaceId the workspace UUID
     * @return {@code true} if the workspace exists, {@code false} on lookup failure
     *         or if the workspace was not found
     */
    public boolean validateWorkspaceExists(UUID workspaceId) {
        if (workspaceId == null) {
            return false;
        }
        try {
            String url = varapiProperties.getTuso().getBaseUrl()
                    + "/v1/internal/workspaces/" + workspaceId;
            restTemplate.getForEntity(url, String.class);
            return true;
        } catch (Exception e) {
            log.warn("Failed to validate workspace {}: {}", workspaceId, e.getMessage());
            return false;
        }
    }
}
