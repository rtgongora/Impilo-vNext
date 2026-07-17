package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.experience.client.ObservabilityServiceClient;
import zw.gov.mohcc.impilo.experience.service.PublicServiceStatusProjectionService;

import java.util.Map;

/**
 * Public gateway lane — citizen service-status board (no authentication).
 *
 * <p>Projects the platform heartbeat feed into a handful of citizen-facing service groups
 * with coarse, honest states. It NEVER fabricates "operational": a group is green only when
 * every internal member service is present and up in the feed; absent members degrade the
 * group to {@code PARTIAL}/{@code UNKNOWN}. If the heartbeat source is unreachable the board
 * returns {@code live:false} with all groups {@code UNKNOWN} (200, not 500) so the UI falls
 * back to its indicative static board. No internal service names appear in the response
 * (the taxonomy lives in {@link PublicServiceStatusProjectionService}). PII-free.</p>
 *
 * <p>Covered by the SecurityConfig {@code GET /internal/v1/public/gateway/**} permitAll —
 * no new SecurityConfig or Envoy route is required for this read. Governed by
 * docs/architecture/gateway-public-lane-security-adr.md.</p>
 */
@RestController
@RequestMapping("/internal/v1/public/gateway/service-status")
public class PublicGatewayServiceStatusBffController {

    private static final Logger log = LoggerFactory.getLogger(PublicGatewayServiceStatusBffController.class);

    private final ObservabilityServiceClient observability;
    private final PublicServiceStatusProjectionService projection;

    public PublicGatewayServiceStatusBffController(ObservabilityServiceClient observability,
                                                   PublicServiceStatusProjectionService projection) {
        this.observability = observability;
        this.projection = projection;
    }

    @GetMapping("")
    public ResponseEntity<Map<String, Object>> status() {
        JsonNode feed;
        try {
            feed = observability.publicServiceStatus();
        } catch (Exception e) {
            // Honesty gate: source down -> live:false, all groups UNKNOWN. Never fabricate green.
            log.debug("Public service-status feed unavailable, returning indicative board: {}", e.getMessage());
            feed = null;
        }
        return ResponseEntity.ok(projection.project(feed));
    }
}
