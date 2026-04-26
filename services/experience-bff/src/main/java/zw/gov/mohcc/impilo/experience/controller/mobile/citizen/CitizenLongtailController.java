package zw.gov.mohcc.impilo.experience.controller.mobile.citizen;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.service.citizen.CitizenLongtailDtos;
import zw.gov.mohcc.impilo.experience.service.citizen.CitizenLongtailService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tier-3 wave 2: citizen long-tail endpoints.
 *
 * <p>Implementation is delegated to {@link CitizenLongtailService} which switches between
 * deterministic stubs and sovereign services via {@code impilo.bff.citizen-longtail.*}.</p>
 */
@RestController
@RequestMapping("/internal/v1/mobile/citizen")
public class CitizenLongtailController {

    private final CitizenLongtailService longtailService;

    public CitizenLongtailController(CitizenLongtailService longtailService) {
        this.longtailService = longtailService;
    }

    @PostMapping("/share/issue")
    public ResponseEntity<Map<String, Object>> issueShareCode(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody(required = false) CitizenLongtailDtos.IssueShareCodeRequest request) {
        Map<String, Object> data = longtailService.issueShareData(actorId, request);
        return okEnvelope(data, requestId, correlationId);
    }

    @PostMapping("/share/claim")
    public ResponseEntity<Map<String, Object>> claimShare(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) CitizenLongtailDtos.ClaimShareRequest request) {
        Map<String, Object> data = longtailService.claimShareData(request);
        return okEnvelope(data, requestId, correlationId);
    }

    @PostMapping("/credential/verify")
    public ResponseEntity<Map<String, Object>> verifyCredential(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) CitizenLongtailDtos.VerifyCredentialRequest request) {
        Map<String, Object> data = longtailService.verifyCredentialData(request);
        return okEnvelope(data, requestId, correlationId);
    }

    @PostMapping("/delegated-pickup/issue")
    public ResponseEntity<Map<String, Object>> issueDelegatedPickup(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) CitizenLongtailDtos.IssueDelegatedPickupRequest request) {
        Map<String, Object> data = longtailService.issueDelegatedPickupData(request);
        return okEnvelope(data, requestId, correlationId);
    }

    private static ResponseEntity<Map<String, Object>> okEnvelope(
            Map<String, Object> data, String requestId, String correlationId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", data);
        body.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(body);
    }
}
