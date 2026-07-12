package zw.gov.mohcc.impilo.msika.api.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.msika.api.dto.OfferingDtos;
import zw.gov.mohcc.impilo.msika.core.OfferingService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/offerings")
public class OfferingController {

    private final OfferingService offeringService;

    public OfferingController(OfferingService offeringService) {
        this.offeringService = offeringService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CATALOG_ADMIN','MARKETPLACE_OPERATOR','SYSTEM_ADMIN','DEVELOPER')")
    public ResponseEntity<ApiResponse<OfferingDtos.OfferingView>> create(@Valid @RequestBody OfferingDtos.CreateOfferingRequest req) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(offeringService.create(req), correlationId));
    }

    @PatchMapping("/{offeringId}")
    @PreAuthorize("hasAnyRole('CATALOG_ADMIN','MARKETPLACE_OPERATOR','SYSTEM_ADMIN','DEVELOPER')")
    public ResponseEntity<ApiResponse<OfferingDtos.OfferingView>> update(@PathVariable String offeringId,
                                                                         @RequestBody OfferingDtos.UpdateOfferingRequest req) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(offeringService.update(offeringId, req), correlationId));
    }

    /**
     * Resolve the fulfilable offering (+ effective fulfillment policy) for a
     * listing or catalog item. Authenticated read — msika-flow calls this at
     * order time. 404 when nothing resolves.
     */
    @GetMapping("/resolve")
    public ResponseEntity<ApiResponse<OfferingDtos.ResolvedOfferingView>> resolve(
            @RequestParam(required = false) String catalogItemId,
            @RequestParam(required = false) String listingId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(offeringService.resolve(catalogItemId, listingId), correlationId));
    }

    @GetMapping("/{offeringId}")
    @PreAuthorize("hasAnyRole('CATALOG_ADMIN','CATALOG_REVIEWER','MARKETPLACE_OPERATOR','REGULATORY_VIEWER','SYSTEM_ADMIN','DEVELOPER')")
    public ResponseEntity<ApiResponse<OfferingDtos.OfferingView>> get(@PathVariable String offeringId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(offeringService.get(offeringId), correlationId));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CATALOG_ADMIN','CATALOG_REVIEWER','MARKETPLACE_OPERATOR','REGULATORY_VIEWER','SYSTEM_ADMIN','DEVELOPER')")
    public ResponseEntity<ApiResponse<List<OfferingDtos.OfferingView>>> list(
            @RequestParam(required = false) String itemId,
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(defaultValue = "true") boolean activeOnly
    ) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        List<OfferingDtos.OfferingView> result;
        if (itemId != null) {
            result = offeringService.listForItem(itemId, activeOnly);
        } else if (tenantId != null) {
            result = offeringService.listForTenant(tenantId, activeOnly);
        } else {
            result = List.of();
        }
        return ResponseEntity.ok(ApiResponse.ok(result, correlationId));
    }
}

