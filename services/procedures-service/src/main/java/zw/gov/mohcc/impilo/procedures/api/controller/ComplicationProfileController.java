package zw.gov.mohcc.impilo.procedures.api.controller;

import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.procedures.api.dto.ComplicationDtos.ClavienDindoGradeView;
import zw.gov.mohcc.impilo.procedures.api.dto.ComplicationDtos.ComplicationProfileView;
import zw.gov.mohcc.impilo.procedures.core.ComplicationProfileService;

import java.util.List;
import java.util.UUID;

/**
 * Read API for Clavien-Dindo severity grades and complication profiles (§18). Read-only —
 * engine-not-store; an actual complication is an execution record for whichever service
 * performs the procedure to keep.
 *
 * <p>Profile codes travel as a QUERY PARAMETER, not a REST path variable — the same route-shape
 * choice P7/P9 made from the start, avoiding the trap P-R.4 found for catalogue-detail.</p>
 */
@RestController
@RequestMapping("/internal/v1/procedures")
public class ComplicationProfileController {

    private final ComplicationProfileService service;

    public ComplicationProfileController(ComplicationProfileService service) {
        this.service = service;
    }

    @GetMapping("/clavien-dindo-grades")
    public List<ClavienDindoGradeView> clavienDindoGrades(@RequestHeader("X-Tenant-ID") UUID tenantId) {
        return service.clavienDindoGrades(tenantId);
    }

    @GetMapping("/complication-profiles")
    public ComplicationProfileView complicationProfile(
            @RequestHeader("X-Tenant-ID") UUID tenantId, @RequestParam String code) {
        return service.complicationProfile(tenantId, code);
    }
}
