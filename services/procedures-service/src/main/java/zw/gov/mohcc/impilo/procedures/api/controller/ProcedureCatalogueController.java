package zw.gov.mohcc.impilo.procedures.api.controller;

import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.procedures.api.dto.CatalogueDtos.CatalogueDetail;
import zw.gov.mohcc.impilo.procedures.api.dto.CatalogueDtos.CatalogueListResponse;
import zw.gov.mohcc.impilo.procedures.core.ProcedureCatalogueService;

import java.util.UUID;

/**
 * Read API for the canonical procedure catalogue.
 *
 * <p>Read-only by design at P1. Authoring and approval are governance operations with their
 * own authorisation and audit requirements and land with the governance surface, not here —
 * an unauthenticated-by-omission write path into national clinical content is not something
 * to add speculatively.</p>
 */
@RestController
@RequestMapping("/internal/v1/procedures/catalogue")
public class ProcedureCatalogueController {

    private final ProcedureCatalogueService catalogue;

    public ProcedureCatalogueController(ProcedureCatalogueService catalogue) {
        this.catalogue = catalogue;
    }

    @GetMapping
    public CatalogueListResponse search(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestParam(required = false) String specialty,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String q) {
        return catalogue.search(tenantId, specialty, category, q);
    }

    @GetMapping("/{definitionCode}")
    public CatalogueDetail byCode(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @PathVariable String definitionCode) {
        return catalogue.byCode(tenantId, definitionCode);
    }
}
