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
import zw.gov.mohcc.impilo.experience.service.PublicGetInvolvedIntakeService;
import zw.gov.mohcc.impilo.experience.service.PublicGetInvolvedIntakeService.ContributionReceipt;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public gateway lane — anonymous citizen "Get Involved" co-design (gateway ADR W4
 * {@code gateway-get-involved-claim}).
 *
 * <p>An ordinary person can propose an idea or describe an experience that could be better,
 * back ideas on a moderated public board, and sign up to help test — all without an account.
 * A submission returns a one-time claim code to check its status later. Abuse controls
 * (fail-closed rate windows, allow-listed types, body caps) live in
 * {@link PublicGetInvolvedIntakeService}. This is the "something could be better" surface,
 * distinct from the feedback lane's "something went wrong". Responses never carry internal
 * service names.</p>
 */
@RestController
@RequestMapping("/internal/v1/public/gateway/get-involved")
public class PublicGatewayGetInvolvedBffController {

    private static final Logger log = LoggerFactory.getLogger(PublicGatewayGetInvolvedBffController.class);

    private final PublicGetInvolvedIntakeService intakeService;

    public PublicGatewayGetInvolvedBffController(PublicGetInvolvedIntakeService intakeService) {
        this.intakeService = intakeService;
    }

    @PostMapping("/contributions")
    public ResponseEntity<Map<String, Object>> submit(
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest servletRequest) {
        Map<String, Object> safeBody = body != null ? body : Map.of();
        try {
            ContributionReceipt receipt = intakeService.submit(safeBody, clientIp(servletRequest));
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("reference", receipt.reference());
            attributes.put("claimCode", receipt.claimCode());
            attributes.put("status", receipt.status());
            attributes.put("message",
                    "Thank you — your idea has been received. Keep your claim code safe: it is the only "
                            + "way to check what happens next, and it will not be shown again.");
            return ResponseEntity.status(HttpStatus.CREATED).body(attributes);
        } catch (PublicGetInvolvedIntakeService.ValidationException e) {
            return error(HttpStatus.BAD_REQUEST, "VALIDATION", e.getMessage());
        } catch (PublicGetInvolvedIntakeService.RateLimitedException e) {
            return rateLimited(e);
        } catch (Exception e) {
            log.warn("Public get-involved submit failed: {}", e.getMessage());
            return error(HttpStatus.BAD_GATEWAY, "GET_INVOLVED_UNAVAILABLE",
                    "We could not capture your idea just now. Please try again later or sign in to submit.");
        }
    }

    @GetMapping("/contributions/{claimCode}")
    public ResponseEntity<Map<String, Object>> status(
            @PathVariable String claimCode, HttpServletRequest servletRequest) {
        try {
            JsonNode status = intakeService.statusByClaimCode(claimCode, clientIp(servletRequest));
            if (status == null) {
                return error(HttpStatus.NOT_FOUND, "UNKNOWN_CLAIM_CODE", "That claim code was not found.");
            }
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("reference", status.path("reference").asText(null));
            attributes.put("type", status.path("type").asText(null));
            attributes.put("status", status.path("status").asText(null));
            attributes.put("moderationState", status.path("moderationState").asText(null));
            attributes.put("supportCount", status.path("supportCount").asInt(0));
            attributes.put("createdAt", status.path("createdAt").asText(null));
            attributes.put("updatedAt", status.path("updatedAt").asText(null));
            return ResponseEntity.ok(attributes);
        } catch (PublicGetInvolvedIntakeService.ValidationException e) {
            return error(HttpStatus.BAD_REQUEST, "VALIDATION", e.getMessage());
        } catch (PublicGetInvolvedIntakeService.RateLimitedException e) {
            return rateLimited(e);
        } catch (HttpClientErrorException.NotFound e) {
            return error(HttpStatus.NOT_FOUND, "UNKNOWN_CLAIM_CODE", "That claim code was not found.");
        } catch (Exception e) {
            log.warn("Public get-involved status failed: {}", e.getMessage());
            return error(HttpStatus.BAD_GATEWAY, "GET_INVOLVED_UNAVAILABLE",
                    "We could not check that idea just now. Please try again later.");
        }
    }

    @GetMapping("/board")
    public ResponseEntity<?> board() {
        try {
            return ResponseEntity.ok(intakeService.board());
        } catch (Exception e) {
            log.warn("Public idea board fetch failed: {}", e.getMessage());
            return error(HttpStatus.BAD_GATEWAY, "GET_INVOLVED_UNAVAILABLE",
                    "We could not load the idea board just now. Please try again later.");
        }
    }

    @PostMapping("/board/{id}/support")
    public ResponseEntity<Map<String, Object>> support(
            @PathVariable String id, HttpServletRequest servletRequest) {
        try {
            JsonNode result = intakeService.support(id, clientIp(servletRequest));
            Map<String, Object> attributes = new LinkedHashMap<>();
            if (result != null) {
                attributes.put("reference", result.path("reference").asText(null));
                attributes.put("supportCount", result.path("supportCount").asInt(0));
            }
            attributes.put("message", "Thanks for backing this idea.");
            return ResponseEntity.ok(attributes);
        } catch (PublicGetInvolvedIntakeService.ValidationException e) {
            return error(HttpStatus.BAD_REQUEST, "VALIDATION", e.getMessage());
        } catch (PublicGetInvolvedIntakeService.RateLimitedException e) {
            return rateLimited(e);
        } catch (HttpClientErrorException.NotFound e) {
            return error(HttpStatus.NOT_FOUND, "UNKNOWN_IDEA", "That idea was not found on the board.");
        } catch (Exception e) {
            log.warn("Public idea support failed: {}", e.getMessage());
            return error(HttpStatus.BAD_GATEWAY, "GET_INVOLVED_UNAVAILABLE",
                    "We could not record your support just now. Please try again later.");
        }
    }

    @GetMapping("/cohorts")
    public ResponseEntity<?> cohorts() {
        try {
            return ResponseEntity.ok(intakeService.cohorts());
        } catch (Exception e) {
            log.warn("Public testing cohorts fetch failed: {}", e.getMessage());
            return error(HttpStatus.BAD_GATEWAY, "GET_INVOLVED_UNAVAILABLE",
                    "We could not load the testing opportunities just now. Please try again later.");
        }
    }

    @PostMapping("/cohorts/{id}/enroll")
    public ResponseEntity<Map<String, Object>> enroll(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest servletRequest) {
        Map<String, Object> safeBody = body != null ? body : Map.of();
        try {
            intakeService.enroll(id, safeBody, clientIp(servletRequest));
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "status", "ENROLLED",
                    "message", "Thank you for signing up to help test. We will be in touch."));
        } catch (PublicGetInvolvedIntakeService.ValidationException e) {
            return error(HttpStatus.BAD_REQUEST, "VALIDATION", e.getMessage());
        } catch (PublicGetInvolvedIntakeService.RateLimitedException e) {
            return rateLimited(e);
        } catch (HttpClientErrorException.NotFound e) {
            return error(HttpStatus.NOT_FOUND, "UNKNOWN_COHORT", "That testing opportunity was not found.");
        } catch (Exception e) {
            log.warn("Public cohort enrol failed: {}", e.getMessage());
            return error(HttpStatus.BAD_GATEWAY, "GET_INVOLVED_UNAVAILABLE",
                    "We could not sign you up just now. Please try again later.");
        }
    }

    private static ResponseEntity<Map<String, Object>> rateLimited(
            PublicGetInvolvedIntakeService.RateLimitedException e) {
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
