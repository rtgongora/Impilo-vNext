package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.workforceintake.WorkforceIntakeService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Workforce Intake BFF — staged MoHCC staff bootstrap journey
 * (upload → mapping/validation → match/dedupe preview → approve → execute → tracker).
 *
 * <p>Stateless composition over workforce-governance-service (staged batch SoR),
 * tshepo-identity (Health-ID matching), VARAPI (Provider ID issuance), Vashandi
 * (workforce profiles/assignments) and the governance invitation pipeline.</p>
 */
@RestController
@RequestMapping("/internal/v1/workforce-intake")
public class WorkforceIntakeController {

    private final WorkforceIntakeService workforceIntakeService;

    public WorkforceIntakeController(WorkforceIntakeService workforceIntakeService) {
        this.workforceIntakeService = workforceIntakeService;
    }

    public record CreateBatchRequest(String organisationId, String fileName, String csvContent,
                                     List<Map<String, String>> rows) {}

    public record MappingRequest(Map<String, String> columnMapping) {}

    @PostMapping("/batches")
    public ResponseEntity<Map<String, Object>> createBatch(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestBody CreateBatchRequest body) {
        return ok(requestId, correlationId, workforceIntakeService.createBatch(
                actorId, providerId, hasFacility(facilityId),
                body.organisationId(), body.fileName(), body.csvContent(), body.rows()));
    }

    @PostMapping("/batches/{batchId}/mapping")
    public ResponseEntity<Map<String, Object>> applyMapping(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @PathVariable String batchId,
            @RequestBody MappingRequest body) {
        return ok(requestId, correlationId, workforceIntakeService.applyMapping(
                actorId, providerId, hasFacility(facilityId), batchId, body.columnMapping()));
    }

    @PostMapping("/batches/{batchId}/match")
    public ResponseEntity<Map<String, Object>> match(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @PathVariable String batchId) {
        return ok(requestId, correlationId, workforceIntakeService.match(
                actorId, providerId, hasFacility(facilityId), tenantId, batchId));
    }

    @PostMapping("/batches/{batchId}/approve")
    public ResponseEntity<Map<String, Object>> approve(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @PathVariable String batchId) {
        return ok(requestId, correlationId, workforceIntakeService.approve(
                actorId, providerId, hasFacility(facilityId), batchId));
    }

    @PostMapping("/batches/{batchId}/execute")
    public ResponseEntity<Map<String, Object>> execute(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @PathVariable String batchId) {
        return ok(requestId, correlationId, workforceIntakeService.execute(
                actorId, providerId, hasFacility(facilityId), batchId));
    }

    @GetMapping("/batches/{batchId}/status")
    public ResponseEntity<Map<String, Object>> status(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String batchId) {
        return ok(requestId, correlationId, workforceIntakeService.status(batchId));
    }

    @GetMapping("/template")
    public ResponseEntity<Map<String, Object>> template(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return ok(requestId, correlationId, workforceIntakeService.template());
    }

    private static boolean hasFacility(String facilityId) {
        return facilityId != null && !facilityId.isBlank();
    }

    private static ResponseEntity<Map<String, Object>> ok(String requestId, String correlationId, Object attributes) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "type", "workforce-intake-action",
                "attributes", attributes
        ));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }
}
