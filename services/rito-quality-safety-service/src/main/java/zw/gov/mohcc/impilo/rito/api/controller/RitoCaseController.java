package zw.gov.mohcc.impilo.rito.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.rito.core.CaseService;
import zw.gov.mohcc.impilo.rito.persistence.entity.CaseAttachmentEntity;
import zw.gov.mohcc.impilo.rito.persistence.entity.CaseEntity;
import zw.gov.mohcc.impilo.rito.persistence.entity.CaseEventEntity;
import zw.gov.mohcc.impilo.rito.persistence.entity.CaseLinkEntity;
import zw.gov.mohcc.impilo.rito.persistence.entity.CaseMessageEntity;
import zw.gov.mohcc.impilo.rito.persistence.entity.CasePartyEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/rito/cases")
public class RitoCaseController {

    private final CaseService caseService;

    public RitoCaseController(CaseService caseService) {
        this.caseService = caseService;
    }

    @PostMapping("")
    public ResponseEntity<CaseEntity> createCase(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) UUID facilityId,
            @RequestBody Map<String, Object> body) {
        UUID bodyFacilityId = uuid(body, "facilityId");
        String reporterActorId = str(body, "reporterActorId");
        String reporterActorType = str(body, "reporterActorType");
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = body.get("metadata") instanceof Map
                ? (Map<String, Object>) body.get("metadata") : Map.of();
        CaseService.NewCase newCase = new CaseService.NewCase(
                str(body, "caseType"),
                str(body, "title"),
                str(body, "description"),
                str(body, "severity"),
                str(body, "source"),
                str(body, "category"),
                boolVal(body, "anonymous"),
                bodyFacilityId != null ? bodyFacilityId : facilityId,
                uuid(body, "districtId"),
                uuid(body, "provinceId"),
                uuid(body, "programmeId"),
                reporterActorId != null ? reporterActorId : actorId,
                reporterActorType != null ? reporterActorType : actorType,
                str(body, "subjectHealthId"),
                boolVal(body, "asDraft"),
                metadata);
        return ResponseEntity.status(HttpStatus.CREATED).body(caseService.createCase(tenantId, newCase));
    }

    @GetMapping("")
    public List<CaseEntity> list(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @RequestParam(required = false) String status,
            @RequestParam(value = "case_type", required = false) String caseType,
            @RequestParam(value = "facility_id", required = false) UUID facilityId,
            @RequestParam(value = "assigned_to", required = false) String assignedTo) {
        return caseService.list(tenantId, status, caseType, facilityId, assignedTo, actorId, actorType);
    }

    @GetMapping("/{id}")
    public CaseEntity get(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @PathVariable UUID id) {
        return caseService.get(tenantId, id, actorId, actorType);
    }

    @GetMapping("/{id}/timeline")
    public List<CaseEventEntity> timeline(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @PathVariable UUID id) {
        return caseService.timeline(tenantId, id, actorId, actorType);
    }

    @GetMapping("/{id}/parties")
    public List<CasePartyEntity> parties(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @PathVariable UUID id) {
        return caseService.parties(tenantId, id, actorId, actorType);
    }

    @GetMapping("/{id}/links")
    public List<CaseLinkEntity> links(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @PathVariable UUID id) {
        return caseService.links(tenantId, id, actorId, actorType);
    }

    @GetMapping("/{id}/messages")
    public List<CaseMessageEntity> messages(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @PathVariable UUID id) {
        return caseService.messages(tenantId, id, actorId, actorType);
    }

    @PostMapping("/{id}/acknowledge")
    public CaseEntity acknowledge(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @PathVariable UUID id) {
        return caseService.acknowledge(tenantId, id, actorId, actorType);
    }

    @PostMapping("/{id}/triage")
    public CaseEntity triage(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        return caseService.triage(tenantId, id,
                str(body, "severity"), str(body, "category"),
                str(body, "ai_suggested_severity"), str(body, "ai_suggested_routing"),
                str(body, "ai_rationale"), actorId, actorType);
    }

    @PostMapping("/{id}/assign")
    public CaseEntity assign(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        return caseService.assign(tenantId, id,
                required(body, "assignee_actor_id"), str(body, "assignee_actor_type"),
                str(body, "assignee_role"), actorId, actorType, str(body, "reason"));
    }

    @PostMapping("/{id}/transition")
    public CaseEntity transition(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        return caseService.transition(tenantId, id,
                required(body, "to_status"), str(body, "note"), actorId, actorType);
    }

    @PostMapping("/{id}/escalate")
    public CaseEntity escalate(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        return caseService.escalate(tenantId, id,
                str(body, "level"), str(body, "reason"), actorId, actorType);
    }

    @PostMapping("/{id}/resolve")
    public CaseEntity resolve(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        return caseService.resolve(tenantId, id,
                str(body, "resolution_summary"), str(body, "outcome"), actorId, actorType);
    }

    @PostMapping("/{id}/close")
    public CaseEntity close(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        return caseService.close(tenantId, id,
                intVal(body, "satisfaction_score"), actorId, actorType);
    }

    @PostMapping("/{id}/reopen")
    public CaseEntity reopen(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        return caseService.reopen(tenantId, id, str(body, "reason"), actorId, actorType);
    }

    @PostMapping("/{id}/cancel")
    public CaseEntity cancel(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        return caseService.cancel(tenantId, id, str(body, "reason"), actorId, actorType);
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<CaseMessageEntity> addMessage(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(caseService.addMessage(tenantId, id,
                str(body, "direction"), str(body, "channel"), actorId, actorType,
                str(body, "visibility"), required(body, "text")));
    }

    @PostMapping("/{id}/parties")
    public ResponseEntity<CasePartyEntity> addParty(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(caseService.addParty(tenantId, id,
                required(body, "role"), str(body, "actor_id"), str(body, "actor_type"),
                str(body, "display_name"), boolVal(body, "identity_protected")));
    }

    @PostMapping("/{id}/links")
    public ResponseEntity<CaseLinkEntity> addLink(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(caseService.addLink(tenantId, id,
                required(body, "link_type"), required(body, "target_ref"),
                str(body, "target_system"), str(body, "note")));
    }

    @PostMapping("/{id}/attachments")
    public ResponseEntity<CaseAttachmentEntity> addAttachment(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(caseService.addAttachment(tenantId, id,
                required(body, "document_ref"), str(body, "file_name"), str(body, "content_type"),
                longVal(body, "size_bytes"), actorId));
    }

    // ---- request-map helpers ----

    private static String required(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || v.toString().isBlank()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return v.toString();
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v != null ? v.toString() : null;
    }

    private static UUID uuid(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || v.toString().isBlank()) {
            return null;
        }
        return UUID.fromString(v.toString());
    }

    private static boolean boolVal(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v instanceof Boolean b) {
            return b;
        }
        return v != null && Boolean.parseBoolean(v.toString());
    }

    private static Integer intVal(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        return Integer.parseInt(v.toString());
    }

    private static Long longVal(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(v.toString());
    }
}
