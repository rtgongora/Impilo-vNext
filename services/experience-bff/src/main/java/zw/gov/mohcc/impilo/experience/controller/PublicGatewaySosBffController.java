package zw.gov.mohcc.impilo.experience.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.experience.service.PublicSosIntakeService;
import zw.gov.mohcc.impilo.experience.service.PublicSosIntakeService.SosReceipt;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public gateway lane — anonymous emergency SOS intake (no authentication).
 *
 * <p>The one anonymous WRITE in the gateway public lane besides feedback (gateway ADR §5, PD-3):
 * an ordinary person in an emergency can request help without a Health ID, an account, or payment.
 * The request is captured immediately and never blocked; a callback number is REQUIRED so a
 * responder can call back to confirm before any resource moves (the emergency service holds the
 * request pending that callback).</p>
 *
 * <p>Abuse controls (per-IP + global rate limits, callback normalization, body caps) live in
 * {@link PublicSosIntakeService}. The 202 response carries only a reference and a reassurance
 * message — never internal service names.</p>
 */
@RestController
@RequestMapping("/internal/v1/public/gateway/sos")
public class PublicGatewaySosBffController {

    private static final Logger log = LoggerFactory.getLogger(PublicGatewaySosBffController.class);

    private final PublicSosIntakeService sosIntakeService;

    public PublicGatewaySosBffController(PublicSosIntakeService sosIntakeService) {
        this.sosIntakeService = sosIntakeService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> submit(
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest servletRequest) {
        Map<String, Object> safeBody = body != null ? body : Map.of();
        try {
            SosReceipt receipt = sosIntakeService.submit(safeBody, clientIp(servletRequest));
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("requestReference", receipt.requestReference());
            attributes.put("message", receipt.message());
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(attributes);
        } catch (PublicSosIntakeService.SosValidationException e) {
            return error(HttpStatus.BAD_REQUEST, "VALIDATION", e.getMessage());
        } catch (PublicSosIntakeService.SosRateLimitedException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.retryAfterSeconds()))
                    .body(Map.of("error", Map.of("code", "RATE_LIMITED", "message", e.getMessage())));
        } catch (Exception e) {
            // Never expose downstream detail on the public lane; keep the caller pointed at the
            // emergency numbers rather than a bare failure.
            log.warn("Public SOS intake failed: {}", e.getMessage());
            return error(HttpStatus.BAD_GATEWAY, "SOS_UNAVAILABLE",
                    "We could not capture your request just now. If this is an emergency, call the numbers shown.");
        }
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "error", Map.of("code", code, "message", message)));
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
