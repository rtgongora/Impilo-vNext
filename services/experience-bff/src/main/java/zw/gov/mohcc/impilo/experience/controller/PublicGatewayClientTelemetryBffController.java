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
import zw.gov.mohcc.impilo.experience.service.ClientTelemetryIntakeService;

import java.util.Map;

/**
 * Public gateway lane — anonymous client-telemetry ingest (gateway ADR
 * {@code gateway-client-telemetry}; a new anonymous WRITE in the public lane).
 *
 * <p>The web/mobile shell posts a small batch of client-side request outcomes
 * ({@code route/code/correlationId/requestId/status/at}). The BFF is the disclosure/abuse
 * boundary: it enforces fail-CLOSED per-IP + global rate windows, caps the batch size and
 * per-field lengths, and allow-lists the payload fields SERVER-SIDE before forwarding to
 * observability best-effort. The client always receives a {@code 202} (even if the downstream
 * POST fails) so a telemetry hiccup can never trigger a retry storm. PII-free by construction.
 * Abuse controls live in {@link ClientTelemetryIntakeService}. Governed by
 * docs/architecture/gateway-public-lane-security-adr.md.</p>
 */
@RestController
@RequestMapping("/internal/v1/public/gateway/client-telemetry")
public class PublicGatewayClientTelemetryBffController {

    private static final Logger log = LoggerFactory.getLogger(PublicGatewayClientTelemetryBffController.class);

    private final ClientTelemetryIntakeService intakeService;

    public PublicGatewayClientTelemetryBffController(ClientTelemetryIntakeService intakeService) {
        this.intakeService = intakeService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> ingest(
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest servletRequest) {
        Map<String, Object> safeBody = body != null ? body : Map.of();
        try {
            int accepted = intakeService.accept(safeBody, clientIp(servletRequest));
            return ResponseEntity.accepted().body(Map.of("accepted", accepted));
        } catch (ClientTelemetryIntakeService.TelemetryValidationException e) {
            return error(HttpStatus.BAD_REQUEST, "VALIDATION", e.getMessage());
        } catch (ClientTelemetryIntakeService.TelemetryRateLimitedException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.retryAfterSeconds()))
                    .body(Map.of("error", Map.of("code", "RATE_LIMITED", "message", e.getMessage())));
        } catch (Exception e) {
            log.warn("Client telemetry intake failed: {}", e.getMessage());
            return error(HttpStatus.BAD_GATEWAY, "TELEMETRY_UNAVAILABLE",
                    "We could not record telemetry just now.");
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
