package zw.gov.mohcc.impilo.experience.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.experience.service.DiscoveryOrchestrationService;

/**
 * Public unified discovery — one anonymous search across the real per-domain
 * public lanes (care/facilities, pharmacies, diagnostics, virtual services,
 * marketplace goods, coverage plans, wellness screening). Composition only; the
 * internal services stay hidden from the citizen. On the public gateway lane
 * ({@code /internal/v1/public/gateway/**}), covered by the permitAll GET rule.
 */
@RestController
@RequestMapping("/internal/v1/public/gateway/discovery")
public class PublicGatewayDiscoveryBffController {

    private static final Logger log = LoggerFactory.getLogger(PublicGatewayDiscoveryBffController.class);

    private final DiscoveryOrchestrationService discovery;

    public PublicGatewayDiscoveryBffController(DiscoveryOrchestrationService discovery) {
        this.discovery = discovery;
    }

    @GetMapping("/search")
    public ResponseEntity<?> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "all") String category,
            @RequestParam(required = false) String lat,
            @RequestParam(required = false) String lng,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        try {
            return ResponseEntity.ok(discovery.search(q, category, lat, lng, page, size, clientIp(request)));
        } catch (Exception e) {
            log.warn("Public gateway discovery search failed: {}", e.getMessage());
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
