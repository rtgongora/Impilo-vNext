package zw.gov.mohcc.impilo.tshepo.core;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Service
public class WorkspaceValidationService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceValidationService.class);

    private final RestTemplate restTemplate;
    private final String tusoBaseUrl;

    public WorkspaceValidationService(
            @org.springframework.beans.annotation.Qualifier("tusoRestTemplate") RestTemplate tusoRestTemplate,
            @Value("${impilo.services.tuso.base-url:http://localhost:8084}") String tusoBaseUrl) {
        this.restTemplate = tusoRestTemplate;
        this.tusoBaseUrl = tusoBaseUrl;
    }

    public record WorkspaceValidationResult(
            boolean valid,
            UUID workspaceId,
            boolean active,
            String reason,
            boolean degradedMode,
            Long facilityNumericId
    ) {}

    public WorkspaceValidationResult validate(UUID tenantId, UUID workspaceId) {
        if (workspaceId == null) {
            return new WorkspaceValidationResult(false, null, false, "No workspace ID provided", false, null);
        }

        try {
            String url = tusoBaseUrl + "/v1/internal/workspaces/" + workspaceId;
            log.debug("Validating workspace {} via TUSO", workspaceId);

            ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("TUSO returned non-success status: {}", response.getStatusCode());
                return new WorkspaceValidationResult(false, workspaceId, false,
                        "TUSO returned error", true, null);
            }

            JsonNode body = response.getBody();
            if (body == null || !body.has("data")) {
                log.warn("TUSO returned empty body for workspace {}", workspaceId);
                return new WorkspaceValidationResult(false, workspaceId, false,
                        "Empty response", false, null);
            }

            JsonNode data = body.get("data");
            boolean active = data.has("active") && data.get("active").asBoolean();
            Long facilityNumericId = null;
            if (data.has("facilityId") && data.get("facilityId").canConvertToLong()) {
                facilityNumericId = data.get("facilityId").longValue();
            }

            log.debug("Workspace {} validation: active={}", workspaceId, active);

            return new WorkspaceValidationResult(
                    active,
                    workspaceId,
                    active,
                    active ? "Active" : "Workspace not active",
                    false,
                    facilityNumericId
            );

        } catch (RestClientException e) {
            log.warn("Failed to validate workspace {} via TUSO: {}", workspaceId, e.getMessage());
            return new WorkspaceValidationResult(false, workspaceId, false,
                    "TUSO call failed", true, null);
        } catch (Exception e) {
            log.error("Unexpected error validating workspace {}: {}", workspaceId, e.getMessage(), e);
            return new WorkspaceValidationResult(false, workspaceId, false,
                    "Validation error", true, null);
        }
    }
}