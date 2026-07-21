package zw.gov.mohcc.impilo.pct.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.pct.core.TelemedicineOrchestrationService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1")
public class TelemedicineController {

    private final TelemedicineOrchestrationService telemedicineService;

    public TelemedicineController(TelemedicineOrchestrationService telemedicineService) {
        this.telemedicineService = telemedicineService;
    }

    @GetMapping("/referrals")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listReferrals(
            @RequestParam String patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        List<Map<String, Object>> rows = telemedicineService.listPatientReferrals(patientId);
        return ResponseEntity.ok(ApiResponse.ok(page(rows, page, size), correlationId));
    }

    @GetMapping("/referrals/incoming")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listIncomingReferrals(
            @RequestParam String facilityId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        List<Map<String, Object>> rows = telemedicineService.listIncomingReferrals(facilityId, status);
        return ResponseEntity.ok(ApiResponse.ok(page(rows, page, size), correlationId));
    }

    /**
     * Pool-scoped queue (Stage 3 routing): referrals routed to a specialty pool, oldest
     * first. Default status filter is SUBMITTED; {@code status} accepts a comma-separated
     * status-in list.
     */
    @GetMapping("/referrals/pool/{poolId}")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listPoolReferrals(
            @PathVariable String poolId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        List<Map<String, Object>> rows = telemedicineService.listPoolReferrals(poolId, status);
        return ResponseEntity.ok(ApiResponse.ok(page(rows, page, size), correlationId));
    }

    @GetMapping("/referrals/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getReferral(@PathVariable String id) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(telemedicineService.getReferral(id), correlationId));
    }

    @PostMapping("/referrals")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createReferral(@RequestBody Map<String, Object> request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(telemedicineService.createReferral(request), correlationId));
    }

    @PutMapping("/referrals/{id}/stage")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateReferralStage(
            @PathVariable String id,
            @RequestBody Map<String, Object> request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(telemedicineService.updateReferralStage(id, request), correlationId));
    }

    @PostMapping("/referrals/{id}/consent")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateReferralConsent(
            @PathVariable String id,
            @RequestBody Map<String, Object> request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(telemedicineService.updateReferralConsent(id, request), correlationId));
    }

    @PostMapping("/referrals/{id}/submit")
    public ResponseEntity<ApiResponse<Map<String, Object>>> submitReferral(@PathVariable String id) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(telemedicineService.submitReferral(id), correlationId));
    }

    @PostMapping("/referrals/{id}/accept")
    public ResponseEntity<ApiResponse<Map<String, Object>>> acceptReferral(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(telemedicineService.acceptReferral(id, request == null ? Map.of() : request), correlationId));
    }

    @PostMapping("/referrals/{id}/respond")
    public ResponseEntity<ApiResponse<Map<String, Object>>> respondReferral(
            @PathVariable String id,
            @RequestBody Map<String, Object> request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(telemedicineService.respondReferral(id, request), correlationId));
    }

    /** Stage 6: structured clinical response (C7) — diagnosis / action plan / red flags / follow-up. */
    @PostMapping("/referrals/{id}/respond-structured")
    public ResponseEntity<ApiResponse<Map<String, Object>>> respondReferralStructured(
            @PathVariable String id,
            @RequestBody Map<String, Object> request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(telemedicineService.respondReferralStructured(id, request), correlationId));
    }

    /** Stage 3: route to a team/pool/on-call roster (not only a single named provider). */
    @PostMapping("/referrals/{id}/route")
    public ResponseEntity<ApiResponse<Map<String, Object>>> routeReferral(
            @PathVariable String id,
            @RequestBody Map<String, Object> request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(telemedicineService.routeReferral(id, request), correlationId));
    }

    @PostMapping("/referrals/{id}/complete")
    public ResponseEntity<ApiResponse<Map<String, Object>>> completeReferral(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(telemedicineService.completeReferral(id, request == null ? Map.of() : request), correlationId));
    }

    /** Guard-permitted next actions for the referral's current state (TM-B1). */
    @GetMapping("/referrals/{id}/allowed-actions")
    public ResponseEntity<ApiResponse<Map<String, Object>>> allowedActions(@PathVariable String id) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(telemedicineService.allowedActions(id), correlationId));
    }

    @PostMapping("/referrals/{id}/cancel")
    public ResponseEntity<ApiResponse<Map<String, Object>>> cancelReferral(
            @PathVariable String id, @RequestBody(required = false) Map<String, Object> request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(telemedicineService.cancelReferral(id, request == null ? Map.of() : request), correlationId));
    }

    @PostMapping("/referrals/{id}/reopen")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reopenReferral(
            @PathVariable String id, @RequestBody(required = false) Map<String, Object> request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(telemedicineService.reopenReferral(id, request == null ? Map.of() : request), correlationId));
    }

    @PostMapping("/referrals/{id}/error-mark")
    public ResponseEntity<ApiResponse<Map<String, Object>>> markEnteredInError(
            @PathVariable String id, @RequestBody(required = false) Map<String, Object> request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(telemedicineService.markEnteredInError(id, request == null ? Map.of() : request), correlationId));
    }

    @PostMapping("/referrals/{id}/escalate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> escalateReferral(
            @PathVariable String id, @RequestBody(required = false) Map<String, Object> request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(telemedicineService.escalateReferral(id, request == null ? Map.of() : request), correlationId));
    }

    @PostMapping("/referrals/{id}/transfer")
    public ResponseEntity<ApiResponse<Map<String, Object>>> transferReferral(
            @PathVariable String id, @RequestBody(required = false) Map<String, Object> request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(telemedicineService.transferReferral(id, request == null ? Map.of() : request), correlationId));
    }

    /** List tasks linked to a teleconsult referral (TM-B7 execution loop). */
    @GetMapping("/referrals/{id}/tasks")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listReferralTasks(@PathVariable String id) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(telemedicineService.listReferralTasks(id), correlationId));
    }

    /** Create a follow-up / execution task scoped to a teleconsult referral (TM-B7). */
    @PostMapping("/referrals/{id}/tasks")
    public ResponseEntity<ApiResponse<Map<String, Object>>> addReferralTask(
            @PathVariable String id, @RequestBody(required = false) Map<String, Object> request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(
                telemedicineService.addReferralTask(id, request == null ? Map.of() : request), correlationId));
    }

    @GetMapping("/patient/{cpid}/telehealth")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listPatientTelehealthSessions(
            @PathVariable String cpid,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        List<Map<String, Object>> rows = telemedicineService.listPatientTelehealthSessions(cpid, status);
        return ResponseEntity.ok(ApiResponse.ok(page(rows, page, size), correlationId));
    }

    @GetMapping("/telehealth")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listTelehealthSessions(
            @RequestParam String facilityId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        List<Map<String, Object>> rows = telemedicineService.listFacilityTelehealthSessions(facilityId, status);
        return ResponseEntity.ok(ApiResponse.ok(page(rows, page, size), correlationId));
    }

    @GetMapping("/telehealth/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTelehealthSession(@PathVariable String id) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(telemedicineService.getTelehealthSession(id), correlationId));
    }

    @PostMapping("/telehealth")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createTelehealthSession(
            @RequestBody Map<String, Object> request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(telemedicineService.createTelehealthSession(request), correlationId));
    }

    @PostMapping("/telehealth/{id}/join")
    public ResponseEntity<ApiResponse<Map<String, Object>>> joinTelehealthSession(@PathVariable String id) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(telemedicineService.joinTelehealthSession(id), correlationId));
    }

    @PostMapping("/telehealth/{id}/end")
    public ResponseEntity<ApiResponse<Map<String, Object>>> endTelehealthSession(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(telemedicineService.endTelehealthSession(id, request == null ? Map.of() : request), correlationId));
    }

    @GetMapping("/ops/telemedicine")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTelemedicineOps(
            @RequestParam String facilityId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(telemedicineService.telemedicineOpsSnapshot(facilityId), correlationId));
    }

    private List<Map<String, Object>> page(List<Map<String, Object>> input, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, 200));
        int start = Math.min(safePage * safeSize, input.size());
        int end = Math.min(start + safeSize, input.size());
        return input.subList(start, end);
    }
}
