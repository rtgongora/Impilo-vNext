package zw.gov.mohcc.impilo.procedures.api.controller;

import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.procedures.api.dto.CompetenceDtos.CompetenceVerdict;
import zw.gov.mohcc.impilo.procedures.core.CompetenceResolver;

import java.util.UUID;

/**
 * Competence and privilege resolution (pipeline §7). Evaluation only — engine-not-store.
 * VARAPI remains the credentialing system of record; nothing is persisted here.
 */
@RestController
@RequestMapping("/internal/v1/procedures/competence")
public class CompetenceController {

    private final CompetenceResolver resolver;

    public CompetenceController(CompetenceResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping
    public CompetenceVerdict resolve(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestParam String providerId,
            @RequestParam String definitionCode) {
        return resolver.resolve(tenantId, providerId, definitionCode);
    }
}
