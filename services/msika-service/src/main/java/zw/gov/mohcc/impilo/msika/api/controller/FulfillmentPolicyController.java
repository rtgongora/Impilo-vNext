package zw.gov.mohcc.impilo.msika.api.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.msika.api.dto.FulfillmentPolicyDtos;
import zw.gov.mohcc.impilo.msika.core.FulfillmentPolicyService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;

@RestController
@RequestMapping("/v1/fulfillment-policies")
public class FulfillmentPolicyController {

    private final FulfillmentPolicyService policyService;

    public FulfillmentPolicyController(FulfillmentPolicyService policyService) {
        this.policyService = policyService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CATALOG_ADMIN','MARKETPLACE_OPERATOR','SYSTEM_ADMIN','DEVELOPER')")
    public ResponseEntity<ApiResponse<FulfillmentPolicyDtos.FulfillmentPolicyView>> create(
            @Valid @RequestBody FulfillmentPolicyDtos.CreateFulfillmentPolicyRequest req) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(policyService.create(req), correlationId));
    }

    @PatchMapping("/{policyId}")
    @PreAuthorize("hasAnyRole('CATALOG_ADMIN','MARKETPLACE_OPERATOR','SYSTEM_ADMIN','DEVELOPER')")
    public ResponseEntity<ApiResponse<FulfillmentPolicyDtos.FulfillmentPolicyView>> update(
            @PathVariable String policyId,
            @RequestBody FulfillmentPolicyDtos.UpdateFulfillmentPolicyRequest req) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(policyService.update(policyId, req), correlationId));
    }

    @GetMapping("/{policyId}")
    @PreAuthorize("hasAnyRole('CATALOG_ADMIN','CATALOG_REVIEWER','MARKETPLACE_OPERATOR','REGULATORY_VIEWER','SYSTEM_ADMIN','DEVELOPER')")
    public ResponseEntity<ApiResponse<FulfillmentPolicyDtos.FulfillmentPolicyView>> get(@PathVariable String policyId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(policyService.get(policyId), correlationId));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CATALOG_ADMIN','CATALOG_REVIEWER','MARKETPLACE_OPERATOR','REGULATORY_VIEWER','SYSTEM_ADMIN','DEVELOPER')")
    public ResponseEntity<ApiResponse<List<FulfillmentPolicyDtos.FulfillmentPolicyView>>> list(
            @RequestParam(required = false) String itemId,
            @RequestParam(required = false) String offeringId,
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        List<FulfillmentPolicyDtos.FulfillmentPolicyView> result;
        if (itemId != null) {
            result = policyService.listForItem(itemId, activeOnly);
        } else if (offeringId != null) {
            result = policyService.listForOffering(offeringId, activeOnly);
        } else {
            result = List.of();
        }
        return ResponseEntity.ok(ApiResponse.ok(result, correlationId));
    }
}

