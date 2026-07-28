package zw.gov.mohcc.impilo.pct.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.pct.core.EmergencyAlertService;
import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyAlertEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The responder-facing HTTP surface for {@code emergency_alert} (pct V203, W5) — the six alerts the
 * sweep job raises had no way for a human to acknowledge, respond to, or close until this wave (W5b).
 */
@RestController
@RequestMapping("/v1/emergency")
public class EmergencyAlertController {

    private final EmergencyAlertService alertService;

    public EmergencyAlertController(EmergencyAlertService alertService) {
        this.alertService = alertService;
    }

    /** The responder board: open alerts for a facility, most urgent clock first. */
    @GetMapping("/alerts")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> board(
            @RequestParam(required = false) UUID facilityId) {
        TrustContext ctx = TrustContextHolder.require();
        var alerts = alertService.board(ctx.tenantId(), facilityId);
        return ResponseEntity.ok(ApiResponse.ok(alerts.stream().map(this::row).toList(), ctx.correlationId().toString()));
    }

    @GetMapping("/episodes/{episodeId}/alerts")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> forEpisode(@PathVariable UUID episodeId) {
        TrustContext ctx = TrustContextHolder.require();
        var alerts = alertService.forEpisode(episodeId, ctx.tenantId());
        return ResponseEntity.ok(ApiResponse.ok(alerts.stream().map(this::row).toList(), ctx.correlationId().toString()));
    }

    @PostMapping("/alerts/{alertId}/acknowledge")
    public ResponseEntity<ApiResponse<Map<String, Object>>> acknowledge(@PathVariable UUID alertId,
                                                                          @RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        var alert = alertService.acknowledge(alertId, ctx.tenantId(), withActor(body, ctx));
        return ResponseEntity.ok(ApiResponse.ok(row(alert), ctx.correlationId().toString()));
    }

    @PostMapping("/alerts/{alertId}/respond")
    public ResponseEntity<ApiResponse<Map<String, Object>>> respond(@PathVariable UUID alertId,
                                                                      @RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        var alert = alertService.respond(alertId, ctx.tenantId(), withActor(body, ctx));
        return ResponseEntity.ok(ApiResponse.ok(row(alert), ctx.correlationId().toString()));
    }

    @PostMapping("/alerts/{alertId}/close")
    public ResponseEntity<ApiResponse<Map<String, Object>>> close(@PathVariable UUID alertId,
                                                                    @RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        var alert = alertService.close(alertId, ctx.tenantId(), body);
        return ResponseEntity.ok(ApiResponse.ok(row(alert), ctx.correlationId().toString()));
    }

    /** Defaults acknowledged_by/responded_by to the caller's own actor id when the body omits it. */
    private Map<String, Object> withActor(Map<String, Object> body, TrustContext ctx) {
        Map<String, Object> b = body != null ? new LinkedHashMap<>(body) : new LinkedHashMap<>();
        b.putIfAbsent("acknowledgedBy", ctx.actorId());
        b.putIfAbsent("respondedBy", ctx.actorId());
        return b;
    }

    private Map<String, Object> row(EmergencyAlertEntity a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("alert_id", a.getAlertId().toString());
        m.put("episode_id", a.getEpisodeId().toString());
        m.put("subject_cpid", a.getSubjectCpid());
        m.put("alert_type", a.getAlertType());
        m.put("severity", a.getSeverity());
        m.put("status", a.getStatus());
        m.put("reason", a.getReason());
        m.put("detected_value_at", a.getDetectedValueAt() != null ? a.getDetectedValueAt().toString() : null);
        m.put("raised_at", a.getRaisedAt() != null ? a.getRaisedAt().toString() : null);
        m.put("raised_by", a.getRaisedBy());
        m.put("response_due_at", a.getResponseDueAt() != null ? a.getResponseDueAt().toString() : null);
        m.put("acknowledged_by", a.getAcknowledgedBy());
        m.put("acknowledged_at", a.getAcknowledgedAt() != null ? a.getAcknowledgedAt().toString() : null);
        m.put("responded_by", a.getRespondedBy());
        m.put("responded_at", a.getRespondedAt() != null ? a.getRespondedAt().toString() : null);
        m.put("response_note", a.getResponseNote());
        m.put("closed_at", a.getClosedAt() != null ? a.getClosedAt().toString() : null);
        m.put("close_reason", a.getCloseReason());
        m.put("rito_case_ref", a.getRitoCaseRef());
        return m;
    }
}
