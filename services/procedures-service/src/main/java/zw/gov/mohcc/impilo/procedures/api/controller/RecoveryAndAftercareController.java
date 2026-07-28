package zw.gov.mohcc.impilo.procedures.api.controller;

import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.procedures.api.dto.AftercareDtos.AftercareTemplateView;
import zw.gov.mohcc.impilo.procedures.api.dto.AftercareDtos.RecoverySettingView;
import zw.gov.mohcc.impilo.procedures.core.RecoveryAndAftercareService;

import java.util.List;
import java.util.UUID;

/**
 * Read API for recovery settings (§15) and aftercare templates (§17). Read-only — engine-not-
 * store; delivering an aftercare instruction or completing a recovery period is an execution/
 * delivery concern for whichever service performs or discharges the procedure.
 *
 * <p>Template/setting codes travel as QUERY PARAMETERS, not REST path variables — the same
 * route-shape choice P-R.4 proved necessary and P7 applied pre-emptively: a free-text code as the
 * final path segment defeats {@code AuthzInternalRequest.deriveResourceType}.</p>
 */
@RestController
@RequestMapping("/internal/v1/procedures")
public class RecoveryAndAftercareController {

    private final RecoveryAndAftercareService service;

    public RecoveryAndAftercareController(RecoveryAndAftercareService service) {
        this.service = service;
    }

    @GetMapping("/recovery-settings")
    public List<RecoverySettingView> recoverySettings(@RequestHeader("X-Tenant-ID") UUID tenantId) {
        return service.recoverySettings(tenantId);
    }

    @GetMapping("/recovery-setting-detail")
    public RecoverySettingView recoverySetting(
            @RequestHeader("X-Tenant-ID") UUID tenantId, @RequestParam String code) {
        return service.recoverySetting(tenantId, code);
    }

    @GetMapping("/aftercare-templates")
    public AftercareTemplateView aftercareTemplate(
            @RequestHeader("X-Tenant-ID") UUID tenantId, @RequestParam String code) {
        return service.aftercareTemplate(tenantId, code);
    }
}
