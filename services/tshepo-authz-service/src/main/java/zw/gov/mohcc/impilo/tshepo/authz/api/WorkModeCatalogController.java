package zw.gov.mohcc.impilo.tshepo.authz.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.tshepo.authz.service.WorkModeCatalog;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only WorkMode catalog lookup, consumed by experience-bff's
 * {@code TshepoAuthzServiceClient} when resolving a work context's available
 * modes (Phase C). No mutation endpoint — the catalog is seeded via Flyway
 * (V054), the same pattern as {@code role_template_catalog}.
 */
@RestController
@RequestMapping("/v1/authz/work-modes")
public class WorkModeCatalogController {

    private final WorkModeCatalog workModeCatalog;

    public WorkModeCatalogController(WorkModeCatalog workModeCatalog) {
        this.workModeCatalog = workModeCatalog;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> catalog(@RequestHeader("X-Tenant-ID") UUID tenantId) {
        List<WorkModeCatalog.WorkModeDefinition> defs = workModeCatalog.allDefinitions(tenantId);
        return ResponseEntity.ok(Map.of("modes", defs));
    }

    @GetMapping("/for-role")
    public ResponseEntity<Map<String, Object>> forRole(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestParam String roleTemplateId) {
        List<WorkModeCatalog.RoleTemplateModeRow> rows = workModeCatalog.modesFor(tenantId, roleTemplateId);
        List<Map<String, Object>> modes = rows.stream()
                .map(r -> {
                    WorkModeCatalog.WorkModeDefinition def = workModeCatalog.definition(tenantId, r.modeCode()).orElse(null);
                    return Map.<String, Object>of(
                            "modeCode", r.modeCode(),
                            "default", r.defaultMode(),
                            "rank", r.rank(),
                            "label", def != null ? def.displayLabel() : r.modeCode(),
                            "anchorKind", def != null ? def.anchorKind() : "",
                            "clinicalDataAccess", def != null ? def.clinicalDataAccess() : "",
                            "defaultPurposeOfUse", def != null ? def.defaultPurposeOfUse() : "");
                })
                .toList();
        return ResponseEntity.ok(Map.of("roleTemplateId", roleTemplateId, "modes", modes));
    }
}
