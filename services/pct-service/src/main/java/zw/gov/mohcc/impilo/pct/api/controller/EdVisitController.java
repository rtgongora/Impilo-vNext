package zw.gov.mohcc.impilo.pct.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.pct.core.EdVisitService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/ed")
public class EdVisitController {

    private final EdVisitService edVisitService;
    private final zw.gov.mohcc.impilo.pct.core.EdTraumaTeamService traumaTeamService;

    public EdVisitController(EdVisitService edVisitService,
                            zw.gov.mohcc.impilo.pct.core.EdTraumaTeamService traumaTeamService) {
        this.edVisitService = edVisitService;
        this.traumaTeamService = traumaTeamService;
    }

    @PostMapping("/visits")
    public ResponseEntity<ApiResponse<Map<String, Object>>> openVisit(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = CompanionHeaders.TRAUMA_EPISODE_ID, required = false) String traumaEpisodeId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(edVisitService.openVisit(withTraumaEpisode(body, traumaEpisodeId)), correlationId));
    }

    /** Fold the X-Trauma-Episode-ID header into the payload (body value wins if already present). */
    private static Map<String, Object> withTraumaEpisode(Map<String, Object> body, String traumaEpisodeId) {
        if (traumaEpisodeId == null || traumaEpisodeId.isBlank()) return body;
        Map<String, Object> merged = new LinkedHashMap<>(body != null ? body : Map.of());
        merged.putIfAbsent("traumaEpisodeId", traumaEpisodeId);
        return merged;
    }

    /** ED pre-arrival projection upsert — DAIDZAI EMS pushes the prehospital ePCR snapshot here. */
    @PostMapping("/pre-arrival")
    public ResponseEntity<ApiResponse<Map<String, Object>>> upsertPreArrival(@RequestBody Map<String, Object> body) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(edVisitService.upsertPreArrival(body), correlationId));
    }

    /** ED arrival/handover from EMS — transitions the existing PRE_ARRIVAL visit to active (no dup). */
    @PostMapping("/arrival")
    public ResponseEntity<ApiResponse<Map<String, Object>>> arrival(@RequestBody Map<String, Object> body) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(edVisitService.arriveFromEms(body), correlationId));
    }

    /** ED pre-arrival board: incoming EMS patients not yet physically arrived. */
    @GetMapping("/pre-arrival")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listPreArrival(
            @RequestParam(required = false) UUID facilityId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(edVisitService.listPreArrival(facilityId), correlationId));
    }

    @GetMapping("/visits")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listVisits(
            @RequestParam(required = false) UUID facilityId,
            @RequestParam(required = false) String status) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(edVisitService.listVisits(facilityId, status), correlationId));
    }

    @GetMapping("/visits/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getVisit(@PathVariable UUID id) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(edVisitService.getVisit(id), correlationId));
    }

    @GetMapping("/triage/discriminators")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> triageDiscriminators() {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(edVisitService.triageDiscriminatorCatalog(), correlationId));
    }

    @PostMapping("/triage/score")
    public ResponseEntity<ApiResponse<Map<String, Object>>> scoreTriage(@RequestBody Map<String, Object> body) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(edVisitService.scoreTriage(body), correlationId));
    }

    @PostMapping("/visits/{id}/triage")
    public ResponseEntity<ApiResponse<Map<String, Object>>> recordTriage(
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(edVisitService.recordStructuredTriage(id, body), correlationId));
    }

    @PostMapping("/visits/{id}/zone")
    public ResponseEntity<ApiResponse<Map<String, Object>>> assignZone(
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(edVisitService.assignZone(id, body), correlationId));
    }

    @PostMapping("/visits/{id}/encounter")
    public ResponseEntity<ApiResponse<Map<String, Object>>> startEncounter(
            @PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(
                edVisitService.startEdEncounter(id, body != null ? body : Map.of()), correlationId));
    }

    @PostMapping("/visits/{id}/protocol")
    public ResponseEntity<ApiResponse<Map<String, Object>>> bindProtocol(
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(edVisitService.bindProtocol(id, body), correlationId));
    }

    @GetMapping("/visits/{id}/protocol-suggestions")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> protocolSuggestions(@PathVariable UUID id) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(edVisitService.protocolSuggestions(id), correlationId));
    }

    @PostMapping("/visits/{id}/trauma/activate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> activateTrauma(
            @PathVariable UUID id, @RequestBody Map<String, Object> body,
            @RequestHeader(value = CompanionHeaders.TRAUMA_EPISODE_ID, required = false) String traumaEpisodeId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(
                edVisitService.activateTrauma(id, withTraumaEpisode(body, traumaEpisodeId)), correlationId));
    }

    // ── Trauma-team roster → ack → escalate (G1.7) ──────────────────────────
    @GetMapping("/trauma/{traumaId}/team")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> traumaTeam(@PathVariable UUID traumaId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        List<Map<String, Object>> team = traumaTeamService.listTeam(traumaId).stream().map(this::memberRow).toList();
        return ResponseEntity.ok(ApiResponse.ok(team, correlationId));
    }

    @PostMapping("/trauma/{traumaId}/team/{memberId}/ack")
    public ResponseEntity<ApiResponse<Map<String, Object>>> ackTraumaMember(
            @PathVariable UUID traumaId, @PathVariable UUID memberId,
            @RequestBody(required = false) Map<String, Object> body) {
        var ctx = TrustContextHolder.require();
        String ackBy = body != null && body.get("ackBy") != null ? body.get("ackBy").toString() : ctx.actorId();
        var m = traumaTeamService.acknowledge(ctx.tenantId(), memberId, ackBy);
        return ResponseEntity.ok(ApiResponse.ok(memberRow(m), ctx.correlationId().toString()));
    }

    @PostMapping("/trauma/{traumaId}/team/escalate")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> escalateTraumaTeam(
            @PathVariable UUID traumaId, @RequestBody(required = false) Map<String, Object> body) {
        var ctx = TrustContextHolder.require();
        long timeout = 0L;
        if (body != null && body.get("timeoutSeconds") != null) {
            timeout = Long.parseLong(body.get("timeoutSeconds").toString());
        }
        var escalated = traumaTeamService.escalateNoAck(ctx.tenantId(), traumaId, timeout)
                .stream().map(this::memberRow).toList();
        return ResponseEntity.ok(ApiResponse.ok(escalated, ctx.correlationId().toString()));
    }

    private Map<String, Object> memberRow(zw.gov.mohcc.impilo.pct.persistence.entity.EdTraumaTeamMemberEntity m) {
        Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("id", m.getId().toString());
        row.put("workforce_profile_id", m.getWorkforceProfileId());
        row.put("role", m.getRole());
        row.put("ack_status", m.getAckStatus());
        row.put("notification_ref", m.getNotificationRef());
        row.put("escalation_ref", m.getEscalationRef());
        return row;
    }

    @PostMapping("/visits/{id}/trauma/survey")
    public ResponseEntity<ApiResponse<Map<String, Object>>> recordTraumaSurvey(
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(edVisitService.recordTraumaSurvey(id, body), correlationId));
    }

    @PostMapping("/visits/{id}/trauma/end")
    public ResponseEntity<ApiResponse<Map<String, Object>>> endTrauma(
            @PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(
                edVisitService.endTrauma(id, body != null ? body : Map.of()), correlationId));
    }

    @PostMapping("/visits/{id}/page")
    public ResponseEntity<ApiResponse<Map<String, Object>>> requestPage(
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(edVisitService.requestClinicalPage(id, body), correlationId));
    }

    @PostMapping("/pages/{pageId}/delivered")
    public ResponseEntity<ApiResponse<Map<String, Object>>> markPageDelivered(
            @PathVariable UUID pageId, @RequestBody Map<String, Object> body) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(edVisitService.markPageDelivered(pageId, body), correlationId));
    }

    @PostMapping("/visits/{id}/disposition")
    public ResponseEntity<ApiResponse<Map<String, Object>>> recordDisposition(
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(edVisitService.recordDisposition(id, body), correlationId));
    }

    @PostMapping("/emergency-cases")
    public ResponseEntity<ApiResponse<Map<String, Object>>> openEmergencyCase(@RequestBody Map<String, Object> body) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(edVisitService.openEmergencyCase(body), correlationId));
    }

    @PostMapping("/emergency-cases/{caseId}/reconcile")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reconcileEmergencyCase(
            @PathVariable UUID caseId, @RequestBody Map<String, Object> body) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(edVisitService.reconcileEmergencyCase(caseId, body), correlationId));
    }
}
