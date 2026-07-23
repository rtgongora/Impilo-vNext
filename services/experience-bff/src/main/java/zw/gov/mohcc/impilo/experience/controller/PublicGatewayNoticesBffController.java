package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.experience.client.GuidanceServiceClient;

/**
 * Public Notices &amp; Bulletins archive for the gateway (no authentication) — a browsable
 * list of currently-published public notices across categories (public-health campaigns,
 * safety recalls, disaster alerts, regulatory notices, procurement notices, service status).
 *
 * <p>Thin proxy over the guidance service's {@code PublicGuidanceController} notices lane,
 * which returns only PUBLISHED, in-window, audience='public' notices with allow-listed
 * fields and NO PII. Regulatory/recall notices are surfaced only when a named authority has
 * approved them (guidance {@code AdvisoryAdminService}); there is no path from internal
 * disciplinary/case data to this lane.</p>
 *
 * <ul>
 *   <li>GET /notices?category=&amp;province=&amp;page=&amp;size= — the public notices archive</li>
 * </ul>
 */
@RestController
@RequestMapping("/internal/v1/public/gateway/notices")
public class PublicGatewayNoticesBffController {

    private static final Logger log = LoggerFactory.getLogger(PublicGatewayNoticesBffController.class);

    private final GuidanceServiceClient guidanceClient;

    public PublicGatewayNoticesBffController(GuidanceServiceClient guidanceClient) {
        this.guidanceClient = guidanceClient;
    }

    @GetMapping
    public ResponseEntity<JsonNode> notices(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String province,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        try {
            return ResponseEntity.ok(guidanceClient.listPublicNotices(category, province, page, size));
        } catch (Exception e) {
            log.warn("Public gateway notices list failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }
}
