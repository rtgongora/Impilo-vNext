package zw.gov.mohcc.impilo.procedures.api.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.procedures.api.dto.AppropriatenessDtos.AppropriatenessRequest;
import zw.gov.mohcc.impilo.procedures.api.dto.AppropriatenessDtos.AppropriatenessVerdict;
import zw.gov.mohcc.impilo.procedures.core.AppropriatenessEngine;

import java.util.UUID;

/**
 * Appropriateness and duplication detection (pipeline §5).
 *
 * <p>Evaluation only — engine-not-store. This returns a judgement and persists nothing; the
 * requesting service records what it decided to do about it. A detections table here would be
 * a second record of a fact the order already owns.</p>
 */
@RestController
@RequestMapping("/internal/v1/procedures/appropriateness")
public class AppropriatenessController {

    private final AppropriatenessEngine engine;

    public AppropriatenessController(AppropriatenessEngine engine) {
        this.engine = engine;
    }

    @PostMapping("/evaluate")
    public AppropriatenessVerdict evaluate(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @Valid @RequestBody AppropriatenessRequest request) {
        return engine.evaluate(tenantId, request);
    }
}
