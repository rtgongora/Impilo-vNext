package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.experience.client.GuidanceServiceClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public Nompilo Service Advisory lane for the gateway (no authentication) — thin proxy over
 * the guidance service's advisory resolve API. Advisories are DATA (never hard-coded in the
 * frontend); the public lane resolves only PUBLISHED, in-window, audience='public' advisories,
 * with allow-listed fields and NO PII. Dismissals carry only an opaque, caller-supplied key.
 *
 * <p>Mounted under the single public gateway namespace {@code /internal/v1/public/gateway/**}
 * (permitAll owned by the gateway lane's SecurityConfig slice).</p>
 *
 * <ul>
 *   <li>GET  /advisory/resolve     — active public advisories for the caller context</li>
 *   <li>POST /advisory/impression  — record a VIEW/FEEDBACK impression (opaque anon key)</li>
 *   <li>POST /advisory/dismiss     — record a dismissal (opaque anon key)</li>
 * </ul>
 */
@RestController
@RequestMapping("/internal/v1/public/gateway/advisory")
public class PublicGatewayAdvisoryBffController {

    private static final Logger log = LoggerFactory.getLogger(PublicGatewayAdvisoryBffController.class);

    private final GuidanceServiceClient guidanceClient;

    public PublicGatewayAdvisoryBffController(GuidanceServiceClient guidanceClient) {
        this.guidanceClient = guidanceClient;
    }

    @GetMapping("/resolve")
    public ResponseEntity<JsonNode> resolve(
            @RequestParam(required = false) String deviceType,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) String serviceKey,
            @RequestParam(required = false) String appKey,
            @RequestParam(required = false) String journeyStage) {
        try {
            return ResponseEntity.ok(guidanceClient.resolvePublicAdvisories(
                    deviceType, language, province, district, serviceKey, appKey, journeyStage));
        } catch (Exception e) {
            log.warn("Public gateway advisory resolve failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    @PostMapping("/impression")
    public ResponseEntity<JsonNode> impression(@RequestBody Map<String, Object> body) {
        try {
            return ResponseEntity.ok(guidanceClient.recordAdvisoryImpression(publicPayload(body)));
        } catch (Exception e) {
            log.warn("Public gateway advisory impression failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    @PostMapping("/dismiss")
    public ResponseEntity<JsonNode> dismiss(@RequestBody Map<String, Object> body) {
        try {
            return ResponseEntity.ok(guidanceClient.dismissAdvisory(publicPayload(body)));
        } catch (Exception e) {
            log.warn("Public gateway advisory dismiss failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    /**
     * Allow-list the anonymous payload forwarded downstream: only the advisory id, the
     * interaction event and an opaque dismissal key — never any caller-identifying field.
     */
    private static Map<String, Object> publicPayload(Map<String, Object> body) {
        Map<String, Object> b = body == null ? Map.of() : body;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("advisoryId", b.get("advisoryId"));
        if (b.get("event") != null) out.put("event", b.get("event"));
        if (b.get("anonDismissalKey") != null) out.put("anonDismissalKey", b.get("anonDismissalKey"));
        return out;
    }
}
