package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.experience.service.FindCareOrchestrationService;
import zw.gov.mohcc.impilo.experience.service.FindCareOrchestrationService.CareSearchResponse;

import java.util.Map;

/**
 * Public gateway lane — service-aware "find health services" search (no authentication).
 *
 * <p>Turns the anonymous facility directory into an access-to-care search: a person asks in plain
 * language ("maternity", "x-ray", "hiv medicines") and, optionally, shares a location; the lane
 * returns facilities that actually <b>offer</b> the service (TUSO registry truth — an ACTIVE
 * matching capability), ranked by how near they are (Ndila routing). It never fabricates that a
 * facility offers a service, and never presents stale operational status as a live "open now".</p>
 *
 * <p>The lane is GET-only and anonymous (permitAll for {@code GET /internal/v1/public/gateway/**}
 * is owned by {@code SecurityConfig}; the v1.1 companion headers are defaulted by
 * {@code PublicGatewayAnonymousDefaultsFilter}). Abuse controls (rate limits, input caps) live in
 * {@link FindCareOrchestrationService}. Responses are PII-free and carry no internal service
 * names. Governed by docs/architecture/gateway-public-lane-security-adr.md.</p>
 */
@RestController
@RequestMapping("/internal/v1/public/gateway/find-care")
public class PublicGatewayFindCareBffController {

    private static final Logger log = LoggerFactory.getLogger(PublicGatewayFindCareBffController.class);

    private final FindCareOrchestrationService findCareService;

    public PublicGatewayFindCareBffController(FindCareOrchestrationService findCareService) {
        this.findCareService = findCareService;
    }

    @GetMapping("/search")
    public ResponseEntity<?> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String lat,
            @RequestParam(required = false) String lng,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) String facilityType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest servletRequest) {
        try {
            CareSearchResponse response = findCareService.search(
                    q, service, lat, lng, province, district, facilityType, page, size,
                    clientIp(servletRequest));
            return ResponseEntity.ok(response);
        } catch (FindCareOrchestrationService.FindCareValidationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", Map.of("code", "VALIDATION", "message", e.getMessage())));
        } catch (FindCareOrchestrationService.FindCareRateLimitedException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.retryAfterSeconds()))
                    .body(Map.of("error", Map.of("code", "RATE_LIMITED", "message", e.getMessage())));
        } catch (Exception e) {
            log.warn("Public find-care search failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", Map.of("code", "FIND_CARE_UNAVAILABLE",
                            "message", "We could not search for care just now. Please try again shortly.")));
        }
    }

    @GetMapping("/facilities/{id}")
    public ResponseEntity<JsonNode> facilityProfile(@PathVariable long id) {
        try {
            return ResponseEntity.ok(findCareService.facilityProfile(id));
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound nf) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            log.warn("Public find-care facility profile failed for id {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    /**
     * Verified providers at a facility. Composition passthrough of Varapi's public register (Varapi
     * owns the gate + allow-listed fields). Always {@code {"providers":[...]}}; an unknown/empty
     * facility is a 200 with an empty list (no existence oracle).
     */
    @GetMapping("/facilities/{id}/practitioners")
    public ResponseEntity<JsonNode> facilityPractitioners(@PathVariable long id) {
        try {
            return ResponseEntity.ok(findCareService.verifiedProvidersAtFacility(id));
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound nf) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            log.warn("Public find-care facility practitioners failed for id {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
