package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.TshepoAuthzServiceClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Citizen-facing step-up challenge proxy (closes G-CZO-17 / unblocks G-CZO-04).
 *
 * <p>Exposes the tshepo-authz step-up engine to authenticated citizens so a sensitive action
 * that returns {@code 401 STEP_UP_REQUIRED} (from PolicyEngine at the gateway) can be unblocked:
 * the client issues a challenge, verifies it, then retries the original action — PolicyEngine then
 * sees a recently-completed step-up ({@code StepUpService.hasRecentStepUp}) and allows it. No
 * cross-service token is involved; completion is server-side challenge state.</p>
 *
 * <p><strong>Identity is server-authoritative.</strong> The {@code tenantId}/{@code actorId} bound
 * to the challenge are taken from the trust headers ({@code X-Tenant-ID}/{@code X-Actor-ID}),
 * never from the request body — a citizen can only issue/complete a step-up for themselves. The
 * verification credential (TOTP/SMS code, etc.) is still validated by the tshepo-authz engine.</p>
 */
@RestController
@RequestMapping("/internal/v1/step-up")
public class CitizenStepUpController {

    private static final Logger log = LoggerFactory.getLogger(CitizenStepUpController.class);

    private final TshepoAuthzServiceClient authzClient;

    public CitizenStepUpController(TshepoAuthzServiceClient authzClient) {
        this.authzClient = authzClient;
    }

    @PostMapping("/challenge")
    public ResponseEntity<Map<String, Object>> challenge(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {
        Object type = body == null ? null : (body.containsKey("challengeType") ? body.get("challengeType") : body.get("method"));
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("tenantId", tenantId);   // server-authoritative — never from body
        request.put("actorId", actorId);     // server-authoritative — never from body
        request.put("challengeType", type);
        log.info("Citizen step-up challenge requested, type={}, requestId={}", type, requestId);
        try {
            JsonNode resp = authzClient.issueStepUpChallenge(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(envelope(resp, requestId, correlationId));
        } catch (HttpClientErrorException e) {
            return passthroughError(e, requestId, correlationId);
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verify(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("challengeId", body.get("challengeId"));
        request.put("tenantId", tenantId);   // server-authoritative
        request.put("actorId", actorId);     // server-authoritative
        request.put("verificationCode", body.get("verificationCode"));
        log.info("Citizen step-up verify, challengeId={}, requestId={}", body.get("challengeId"), requestId);
        try {
            JsonNode resp = authzClient.verifyStepUpChallenge(request);
            return ResponseEntity.ok(envelope(resp, requestId, correlationId));
        } catch (HttpClientErrorException e) {
            return passthroughError(e, requestId, correlationId);
        }
    }

    @GetMapping("/status/{challengeId}")
    public ResponseEntity<Map<String, Object>> status(
            @PathVariable String challengeId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode resp = authzClient.getStepUpStatus(challengeId);
            return ResponseEntity.ok(envelope(resp, requestId, correlationId));
        } catch (HttpClientErrorException e) {
            return passthroughError(e, requestId, correlationId);
        }
    }

    /** Surface the engine's 4xx (e.g. invalid code 400, actor mismatch / not-supervisor 403) to the client. */
    private ResponseEntity<Map<String, Object>> passthroughError(
            HttpClientErrorException e, String requestId, String correlationId) {
        log.warn("Citizen step-up upstream rejected: status={}", e.getStatusCode());
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("error", Map.of("code", "STEP_UP_FAILED", "status", e.getStatusCode().value()));
        err.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.status(e.getStatusCode()).body(err);
    }

    private static Map<String, Object> envelope(Object data, String requestId, String correlationId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return response;
    }
}
