package zw.gov.mohcc.impilo.madi.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.madi.core.MhpService;
import zw.gov.mohcc.impilo.madi.persistence.entity.MhpActivationEntity;
import zw.gov.mohcc.impilo.madi.persistence.entity.MhpPackEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Massive Haemorrhage Protocol activation + pack tracking (madi V200, W8a). */
@RestController
@RequestMapping("/v1/mhp")
public class MhpController {

    private final MhpService mhpService;

    public MhpController(MhpService mhpService) {
        this.mhpService = mhpService;
    }

    @PostMapping("/activations")
    public ResponseEntity<Map<String, Object>> activate(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @RequestHeader(value = "X-Escalation-Grant-Id", required = false) String escalationGrantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> b = body != null ? body : Map.of();
        try {
            String activatedBy = str(b, "activatedBy");
            MhpActivationEntity a = mhpService.activate(tenantId, uuid(b, "facilityId"),
                    str(b, "patientCpid"), uuid(b, "traumaEpisodeId"), uuid(b, "emergencyEpisodeId"),
                    activatedBy != null ? activatedBy : actorId, purposeOfUse, escalationGrantId);
            return ResponseEntity.status(HttpStatus.CREATED).body(activationRow(a));
        } catch (zw.gov.mohcc.impilo.sharedkernel.security.EmergencyAccessGuard.EmergencyAccessDeniedException e) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        }
    }

    @GetMapping("/activations/{activationId}")
    public ResponseEntity<Map<String, Object>> get(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID activationId) {
        MhpActivationEntity a = mhpService.getActivation(activationId, tenantId);
        Map<String, Object> out = activationRow(a);
        out.put("packs", mhpService.packs(activationId, tenantId).stream().map(this::packRow).toList());
        out.put("ratio_summary", mhpService.ratioSummary(activationId, tenantId));
        return ResponseEntity.ok(out);
    }

    @PostMapping("/activations/{activationId}/packs")
    public ResponseEntity<Map<String, Object>> issuePack(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @RequestHeader(value = "X-Escalation-Grant-Id", required = false) String escalationGrantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @PathVariable UUID activationId,
            @RequestBody Map<String, Object> body) {
        try {
            String issuedBy = str(body, "issuedBy");
            MhpPackEntity pack = mhpService.issuePack(activationId, tenantId,
                    intVal(body, "packNumber"), str(body, "componentType"), intVal(body, "unitsIssued"),
                    str(body, "bloodGroup"), issuedBy != null ? issuedBy : actorId, purposeOfUse, escalationGrantId);
            return ResponseEntity.status(HttpStatus.CREATED).body(packRow(pack));
        } catch (zw.gov.mohcc.impilo.sharedkernel.security.EmergencyAccessGuard.EmergencyAccessDeniedException e) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        }
    }

    @PostMapping("/activations/{activationId}/stand-down")
    public ResponseEntity<Map<String, Object>> standDown(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @PathVariable UUID activationId,
            @RequestBody Map<String, Object> body) {
        String stoodDownBy = str(body, "stoodDownBy");
        MhpActivationEntity a = mhpService.standDown(activationId, tenantId,
                stoodDownBy != null ? stoodDownBy : actorId, str(body, "reason"));
        return ResponseEntity.ok(activationRow(a));
    }

    private Map<String, Object> activationRow(MhpActivationEntity a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("activation_id", a.getActivationId().toString());
        m.put("patient_cpid", a.getPatientCpid());
        m.put("trauma_episode_id", a.getTraumaEpisodeId() != null ? a.getTraumaEpisodeId().toString() : null);
        m.put("emergency_episode_id", a.getEmergencyEpisodeId() != null ? a.getEmergencyEpisodeId().toString() : null);
        m.put("status", a.getStatus());
        m.put("activated_by", a.getActivatedBy());
        m.put("activated_at", a.getActivatedAt() != null ? a.getActivatedAt().toString() : null);
        m.put("stood_down_at", a.getStoodDownAt() != null ? a.getStoodDownAt().toString() : null);
        m.put("stand_down_reason", a.getStandDownReason());
        return m;
    }

    private Map<String, Object> packRow(MhpPackEntity p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pack_id", p.getPackId().toString());
        m.put("pack_number", p.getPackNumber());
        m.put("component_type", p.getComponentType());
        m.put("units_issued", p.getUnitsIssued());
        m.put("blood_group", p.getBloodGroup());
        m.put("issued_by", p.getIssuedBy());
        m.put("issued_at", p.getIssuedAt() != null ? p.getIssuedAt().toString() : null);
        return m;
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body != null ? body.get(key) : null;
        return v != null && !v.toString().isBlank() ? v.toString() : null;
    }

    private static UUID uuid(Map<String, Object> body, String key) {
        String s = str(body, key);
        if (s == null) return null;
        try { return UUID.fromString(s.trim()); } catch (IllegalArgumentException e) { return null; }
    }

    private static int intVal(Map<String, Object> body, String key) {
        Object v = body != null ? body.get(key) : null;
        if (v instanceof Number n) return n.intValue();
        if (v != null) {
            try { return Integer.parseInt(v.toString().trim()); } catch (NumberFormatException ignored) { }
        }
        throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, key + " is required");
    }
}
