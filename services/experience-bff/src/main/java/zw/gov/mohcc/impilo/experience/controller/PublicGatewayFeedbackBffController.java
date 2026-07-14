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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import zw.gov.mohcc.impilo.experience.service.PublicFeedbackIntakeService;
import zw.gov.mohcc.impilo.experience.service.PublicFeedbackIntakeService.FeedbackReceipt;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public gateway lane — anonymous feedback/safety-concern intake with claim-code tracking
 * (gateway ADR W4 {@code gateway-feedback-claim}; the second anonymous WRITE in the public
 * lane besides SOS).
 *
 * <p>An ordinary person can report unsafe care or a complaint without an account and
 * receive a one-time claim code to check the outcome. Abuse controls (fail-closed rate
 * windows, allow-listed case types, body caps) live in {@link PublicFeedbackIntakeService}.
 * Responses never carry internal service names.</p>
 */
@RestController
@RequestMapping("/internal/v1/public/gateway/feedback")
public class PublicGatewayFeedbackBffController {

    private static final Logger log = LoggerFactory.getLogger(PublicGatewayFeedbackBffController.class);

    private final PublicFeedbackIntakeService intakeService;

    public PublicGatewayFeedbackBffController(PublicFeedbackIntakeService intakeService) {
        this.intakeService = intakeService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> submit(
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest servletRequest) {
        Map<String, Object> safeBody = body != null ? body : Map.of();
        try {
            FeedbackReceipt receipt = intakeService.submit(safeBody, clientIp(servletRequest));
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("caseReference", receipt.caseReference());
            attributes.put("claimCode", receipt.claimCode());
            attributes.put("status", receipt.status());
            attributes.put("message",
                    "Your report has been received. Keep your claim code safe — it is the only way "
                            + "to check the outcome and it will not be shown again.");
            return ResponseEntity.status(HttpStatus.CREATED).body(attributes);
        } catch (PublicFeedbackIntakeService.FeedbackValidationException e) {
            return error(HttpStatus.BAD_REQUEST, "VALIDATION", e.getMessage());
        } catch (PublicFeedbackIntakeService.FeedbackRateLimitedException e) {
            return rateLimited(e);
        } catch (Exception e) {
            log.warn("Public feedback intake failed: {}", e.getMessage());
            return error(HttpStatus.BAD_GATEWAY, "FEEDBACK_UNAVAILABLE",
                    "We could not capture your report just now. Please try again later or sign in to submit.");
        }
    }

    @GetMapping("/{claimCode}")
    public ResponseEntity<Map<String, Object>> status(
            @PathVariable String claimCode, HttpServletRequest servletRequest) {
        try {
            JsonNode status = intakeService.statusByClaimCode(claimCode, clientIp(servletRequest));
            if (status == null) {
                return error(HttpStatus.NOT_FOUND, "UNKNOWN_CLAIM_CODE", "That claim code was not found.");
            }
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("caseReference", status.path("caseReference").asText(null));
            attributes.put("caseType", status.path("caseType").asText(null));
            attributes.put("status", status.path("status").asText(null));
            attributes.put("createdAt", status.path("createdAt").asText(null));
            attributes.put("updatedAt", status.path("updatedAt").asText(null));
            return ResponseEntity.ok(attributes);
        } catch (PublicFeedbackIntakeService.FeedbackValidationException e) {
            return error(HttpStatus.BAD_REQUEST, "VALIDATION", e.getMessage());
        } catch (PublicFeedbackIntakeService.FeedbackRateLimitedException e) {
            return rateLimited(e);
        } catch (HttpClientErrorException.NotFound e) {
            return error(HttpStatus.NOT_FOUND, "UNKNOWN_CLAIM_CODE", "That claim code was not found.");
        } catch (Exception e) {
            log.warn("Public feedback status failed: {}", e.getMessage());
            return error(HttpStatus.BAD_GATEWAY, "FEEDBACK_UNAVAILABLE",
                    "We could not check that report just now. Please try again later.");
        }
    }

    private static ResponseEntity<Map<String, Object>> rateLimited(
            PublicFeedbackIntakeService.FeedbackRateLimitedException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.retryAfterSeconds()))
                .body(Map.of("error", Map.of("code", "RATE_LIMITED", "message", e.getMessage())));
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
