package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Provider Activation BFF Controller — bridges the experience shell to VARAPI
 * for listing Provider IDs linked to a person's Health ID.
 *
 * <p>Implements the Health OS doctrine's Identity and Role Activation model:
 * a person signs in with their Health ID (who they are), then activates a
 * Provider ID to practice in a regulated professional capacity. Professional
 * execution requires a valid Provider ID with current licensure, organizational
 * affiliation, facility context, and declared purpose of use.</p>
 *
 * <p>This controller supports the role activation flow by retrieving all provider
 * identities attached to a given Health ID, enabling the experience shell to
 * present the "Practice as..." selector when a person has provider credentials.</p>
 *
 * <h3>Endpoints:</h3>
 * <ul>
 *   <li>GET /internal/v1/identity/providers?actorId={healthId} — list Provider IDs for a person</li>
 * </ul>
 *
 * @see <a href="docs/doctrine/health-os-doctrine.md">Health OS Doctrine — Identity and Role Activation</a>
 */
@RestController
@RequestMapping("/internal/v1/identity/providers")
public class ProviderActivationController {

    private static final Logger log = LoggerFactory.getLogger(ProviderActivationController.class);

    private final VarapiServiceClient varapiClient;

    public ProviderActivationController(VarapiServiceClient varapiClient) {
        this.varapiClient = varapiClient;
    }

    /**
     * List all Provider IDs linked to a person's Health ID.
     *
     * <p>Queries VARAPI to retrieve provider identities associated with the given
     * actor (Health ID). Each result includes the provider's display name, cadre,
     * registration number, status, and licensure expiry — everything the experience
     * shell needs to render the role activation selector and enforce graduated friction.</p>
     *
     * @param tenantId      mandatory tenant context (X-Tenant-ID)
     * @param requestId     unique request identifier (X-Request-ID)
     * @param correlationId correlation chain identifier (X-Correlation-ID)
     * @param actorId       the Health ID of the person whose provider identities are requested
     * @return list of provider identity objects with cadre, licensure, and activation status
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listProvidersByActor(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam String actorId) {

        log.info("Listing providers for actor: tenant={}, actorId={}, requestId={}",
                tenantId, actorId, requestId);

        try {
            JsonNode result = varapiClient.getProviderByHealthId(actorId);
            if (result != null) {
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("data", result);
                response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
                return ResponseEntity.ok(response);
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", Map.of("code", "PROVIDER_NOT_FOUND", "message", "No provider profile found for actor"),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("VARAPI lookup failed for actorId={}: {}",
                    actorId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of("code", "VARAPI_UNAVAILABLE", "message", e.getMessage()),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }
}
