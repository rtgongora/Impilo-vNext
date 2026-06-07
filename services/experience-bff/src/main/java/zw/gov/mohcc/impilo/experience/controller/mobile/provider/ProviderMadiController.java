package zw.gov.mohcc.impilo.experience.controller.mobile.provider;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.MadiServiceClient;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Provider mobile surface for MADI operational workflows — orders, transfusions, drives, haemovigilance.
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/madi")
public class ProviderMadiController {

    private static final Logger log = LoggerFactory.getLogger(ProviderMadiController.class);

    private final MadiServiceClient client;

    public ProviderMadiController(MadiServiceClient client) {
        this.client = client;
    }

    // ── Orders ───────────────────────────────────────────────────────

    @PostMapping("/orders")
    public ResponseEntity<Map<String, Object>> createOrder(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardPost(() -> client.createOrder(body), requestId, correlationId);
    }

    @PostMapping("/orders/{orderId}/submit")
    public ResponseEntity<Map<String, Object>> submitOrder(
            @PathVariable String orderId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardPost(() -> client.submitOrder(orderId), requestId, correlationId);
    }

    @PostMapping("/orders/{orderId}/samples")
    public ResponseEntity<Map<String, Object>> collectSample(
            @PathVariable String orderId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardPost(() -> client.collectSample(orderId, body), requestId, correlationId);
    }

    @PostMapping("/orders/{orderId}/crossmatch")
    public ResponseEntity<Map<String, Object>> crossmatch(
            @PathVariable String orderId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardPost(() -> client.crossmatchOrder(orderId, body), requestId, correlationId);
    }

    @PostMapping("/orders/{orderId}/reserve")
    public ResponseEntity<Map<String, Object>> reserve(
            @PathVariable String orderId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardPost(() -> client.reserveOrder(orderId, body), requestId, correlationId);
    }

    @PostMapping("/orders/{orderId}/issue")
    public ResponseEntity<Map<String, Object>> issue(
            @PathVariable String orderId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardPost(() -> client.issueOrder(orderId, body), requestId, correlationId);
    }

    // ── Transfusions ─────────────────────────────────────────────────

    @PostMapping("/transfusions")
    public ResponseEntity<Map<String, Object>> startTransfusion(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardPost(() -> client.startTransfusion(body), requestId, correlationId);
    }

    @PostMapping("/transfusions/{episodeId}/observations")
    public ResponseEntity<Map<String, Object>> observation(
            @PathVariable String episodeId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardPost(() -> client.transfusionObservation(episodeId, body), requestId, correlationId);
    }

    @PostMapping("/transfusions/{episodeId}/complete")
    public ResponseEntity<Map<String, Object>> complete(
            @PathVariable String episodeId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> client.completeTransfusion(episodeId, body), requestId, correlationId);
    }

    @PostMapping("/transfusions/{episodeId}/verify")
    public ResponseEntity<Map<String, Object>> verify(
            @PathVariable String episodeId,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> client.verifyTransfusion(episodeId, body), requestId, correlationId);
    }

    // ── Donation drives (operational) ────────────────────────────────

    @PostMapping("/drives")
    public ResponseEntity<Map<String, Object>> createDrive(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardPost(() -> client.createDrive(body), requestId, correlationId);
    }

    @GetMapping("/drives")
    public ResponseEntity<Map<String, Object>> listDrives(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ResponseEntity.ok(envelope(client.listDrives(), requestId, correlationId));
        } catch (Exception e) {
            log.warn("provider madi list drives failed: {}", e.getMessage());
            return ResponseEntity.ok(envelope(Collections.emptyList(), requestId, correlationId));
        }
    }

    @PostMapping("/drives/{driveId}/publish")
    public ResponseEntity<Map<String, Object>> publishDrive(
            @PathVariable String driveId,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> client.publishDrive(driveId, body), requestId, correlationId);
    }

    @PostMapping("/drives/{driveId}/register")
    public ResponseEntity<Map<String, Object>> registerAtDrive(
            @PathVariable String driveId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardPost(() -> client.registerAtDrive(driveId, body), requestId, correlationId);
    }

    @PostMapping("/drives/{driveId}/screen")
    public ResponseEntity<Map<String, Object>> screenAtDrive(
            @PathVariable String driveId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> client.screenAtDrive(driveId, body), requestId, correlationId);
    }

    @PostMapping("/drives/{driveId}/donations")
    public ResponseEntity<Map<String, Object>> recordDonation(
            @PathVariable String driveId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardPost(() -> client.recordDonation(driveId, body), requestId, correlationId);
    }

    @PostMapping("/drives/{driveId}/close")
    public ResponseEntity<Map<String, Object>> closeDrive(
            @PathVariable String driveId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> client.closeDrive(driveId), requestId, correlationId);
    }

    // ── Haemovigilance ─────────────────────────────────────────────

    @PostMapping("/haemovigilance/reactions")
    public ResponseEntity<Map<String, Object>> reportReaction(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardPost(() -> client.reportReaction(body), requestId, correlationId);
    }

    @PostMapping("/haemovigilance/cases")
    public ResponseEntity<Map<String, Object>> openCase(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardPost(() -> client.openHaemovigilanceCase(body), requestId, correlationId);
    }

    @PostMapping("/haemovigilance/cases/{caseId}/investigate")
    public ResponseEntity<Map<String, Object>> investigate(
            @PathVariable String caseId,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> client.investigateCase(caseId, body), requestId, correlationId);
    }

    @PostMapping("/haemovigilance/cases/{caseId}/close")
    public ResponseEntity<Map<String, Object>> closeCase(
            @PathVariable String caseId,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> client.closeCase(caseId, body), requestId, correlationId);
    }

    @FunctionalInterface
    private interface MadiCall {
        JsonNode call() throws Exception;
    }

    private ResponseEntity<Map<String, Object>> forward(MadiCall call, String requestId, String correlationId) {
        try {
            return ResponseEntity.ok(envelope(call.call(), requestId, correlationId));
        } catch (Exception e) {
            return upstreamFailure(requestId, correlationId);
        }
    }

    private ResponseEntity<Map<String, Object>> forwardPost(MadiCall call, String requestId, String correlationId) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(envelope(call.call(), requestId, correlationId));
        } catch (Exception e) {
            return upstreamFailure(requestId, correlationId);
        }
    }

    private static Map<String, Object> envelope(Object data, String requestId, String correlationId) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("data", data != null ? data : Collections.emptyList());
        envelope.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return envelope;
    }

    private static ResponseEntity<Map<String, Object>> upstreamFailure(String requestId, String correlationId) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("error", Map.of("code", "MADI_UNAVAILABLE", "message", "Blood bank service is temporarily unavailable."));
        err.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(err);
    }
}
