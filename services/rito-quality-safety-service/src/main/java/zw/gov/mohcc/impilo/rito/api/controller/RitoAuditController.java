package zw.gov.mohcc.impilo.rito.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.rito.core.AuditService;
import zw.gov.mohcc.impilo.rito.persistence.entity.AuditEntity;
import zw.gov.mohcc.impilo.rito.persistence.entity.AuditFindingEntity;
import zw.gov.mohcc.impilo.rito.persistence.entity.AuditSectionEntity;
import zw.gov.mohcc.impilo.rito.persistence.entity.AuditToolEntity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/rito")
public class RitoAuditController {

    private final AuditService auditService;

    public RitoAuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    // ---- audit tools ----

    @PostMapping("/audit-tools")
    public ResponseEntity<AuditToolEntity> createTool(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(auditService.createTool(tenantId,
                required(body, "tool_key"), required(body, "name"), str(body, "description"),
                str(body, "version"), str(body, "audit_type"), str(body, "form_schema_ref"),
                bd(body, "max_score")));
    }

    @GetMapping("/audit-tools")
    public List<AuditToolEntity> listTools(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId) {
        return auditService.listTools(tenantId);
    }

    @GetMapping("/audit-tools/{id}")
    public AuditToolEntity getTool(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID id) {
        return auditService.getTool(tenantId, id);
    }

    @PostMapping("/audit-tools/{id}/sections")
    public ResponseEntity<AuditSectionEntity> addSection(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(auditService.addSection(tenantId, id,
                required(body, "section_key"), required(body, "title"), intVal(body, "ordinal"),
                bd(body, "weight"), bd(body, "max_score")));
    }

    @GetMapping("/audit-tools/{id}/sections")
    public List<AuditSectionEntity> listSections(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID id) {
        return auditService.listSections(id);
    }

    // ---- audits ----

    @PostMapping("/audits")
    public ResponseEntity<AuditEntity> createAudit(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(auditService.createAudit(tenantId,
                uuid(body, "audit_tool_id"), required(body, "audit_type"), uuid(body, "facility_id"),
                uuid(body, "district_id"), uuid(body, "province_id"), str(body, "lead_auditor_actor_id"),
                odt(body, "scheduled_at"), actorId));
    }

    @GetMapping("/audits")
    public List<AuditEntity> listAudits(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestParam(required = false) String status) {
        return auditService.listAudits(tenantId, status);
    }

    @GetMapping("/audits/{id}")
    public AuditEntity getAudit(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID id) {
        return auditService.getAudit(tenantId, id);
    }

    @PostMapping("/audits/{id}/start")
    public AuditEntity startAudit(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID id) {
        return auditService.startAudit(tenantId, id);
    }

    @PostMapping("/audits/{id}/findings")
    public ResponseEntity<AuditFindingEntity> addFinding(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(auditService.addFinding(tenantId, id,
                str(body, "section_key"), str(body, "item_ref"), required(body, "finding_type"),
                str(body, "severity"), str(body, "description"), str(body, "evidence"),
                str(body, "recommended_action"), bd(body, "score")));
    }

    @GetMapping("/audits/{id}/findings")
    public List<AuditFindingEntity> listFindings(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID id) {
        return auditService.listFindings(id);
    }

    @PostMapping("/audits/{id}/submit")
    public AuditEntity submitAudit(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID id) {
        return auditService.submitAudit(tenantId, id);
    }

    @PostMapping("/audits/{id}/close")
    public AuditEntity closeAudit(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID id) {
        return auditService.closeAudit(tenantId, id);
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

    private static BigDecimal bd(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || v.toString().isBlank()) {
            return null;
        }
        if (v instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        return new BigDecimal(v.toString());
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

    private static OffsetDateTime odt(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || v.toString().isBlank()) {
            return null;
        }
        return OffsetDateTime.parse(v.toString());
    }
}
