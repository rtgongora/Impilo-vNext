package zw.gov.mohcc.impilo.procedures.api.controller;

import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.procedures.api.dto.SafetyPauseDtos.SafetyPauseTemplateView;
import zw.gov.mohcc.impilo.procedures.api.dto.SafetyPauseDtos.SedationLevelView;
import zw.gov.mohcc.impilo.procedures.core.SafetyPauseAndSedationService;

import java.util.List;
import java.util.UUID;

/**
 * Read API for safety-pause templates (§9) and the sedation continuum (§10). Read-only —
 * engine-not-store; a completion or an encounter is recorded by the executing service.
 *
 * <p>Template and level codes travel as QUERY PARAMETERS on this service's own routes, not as
 * REST path variables — same route-shape choice made for the catalogue in P-R.4, applied here
 * before it was ever wrong rather than after. {@code AuthzInternalRequest.deriveResourceType}
 * (tshepo-authz-service) only skips UUID-shaped and {@code v1}/{@code api} path segments; a
 * free-text code such as {@code SAFETY-PAUSE-SURGERY} or {@code MODERATE_SEDATION} as the final
 * segment would derive THAT code as the resource type instead of a stable resource name, and
 * no ext_authz policy row would ever be found for it. Whichever wave next proxies these routes
 * through the BFF and writes their tshepo-authz rows inherits routes that are already safe to
 * pin, rather than a repeat of the defect V300 was written around.</p>
 */
@RestController
@RequestMapping("/internal/v1/procedures")
public class SafetyPauseController {

    private final SafetyPauseAndSedationService service;

    public SafetyPauseController(SafetyPauseAndSedationService service) {
        this.service = service;
    }

    @GetMapping("/safety-pause-templates")
    public SafetyPauseTemplateView safetyPauseTemplate(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestParam String code) {
        return service.safetyPauseTemplate(tenantId, code);
    }

    @GetMapping("/sedation-levels")
    public List<SedationLevelView> sedationLevels(@RequestHeader("X-Tenant-ID") UUID tenantId) {
        return service.sedationLevels(tenantId);
    }

    @GetMapping("/sedation-level-detail")
    public SedationLevelView sedationLevel(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestParam String code) {
        return service.sedationLevel(tenantId, code);
    }
}
