package zw.gov.mohcc.impilo.msika.api.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.msika.api.dto.StorefrontDtos;
import zw.gov.mohcc.impilo.msika.core.StorefrontService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;

/**
 * Seller storefronts. Creation is seller-role-gated; the verification decision
 * (recording the Varapi/Tuso/Indawo validation outcome) is operator/governance-gated.
 */
@RestController
@RequestMapping("/v1/storefronts")
public class StorefrontController {

    private final StorefrontService storefrontService;

    public StorefrontController(StorefrontService storefrontService) {
        this.storefrontService = storefrontService;
    }

    private String corr() {
        return TrustContextHolder.require().correlationId().toString();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('MARKETPLACE_SELLER','PROVIDER','FACILITY_ADMIN','MARKETPLACE_OPERATOR','SYSTEM_ADMIN','DEVELOPER')")
    public ResponseEntity<ApiResponse<StorefrontDtos.StorefrontView>> create(@Valid @RequestBody StorefrontDtos.CreateStorefrontRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(storefrontService.create(req), corr()));
    }

    @PostMapping("/{storefrontId}/verify")
    @PreAuthorize("hasAnyRole('MARKETPLACE_OPERATOR','REGULATORY_VIEWER','SYSTEM_ADMIN','DEVELOPER')")
    public ResponseEntity<ApiResponse<StorefrontDtos.StorefrontView>> verify(@PathVariable String storefrontId,
                                                                             @Valid @RequestBody StorefrontDtos.VerifyStorefrontRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(storefrontService.verify(storefrontId, req), corr()));
    }

    @GetMapping("/{storefrontId}")
    public ResponseEntity<ApiResponse<StorefrontDtos.StorefrontView>> get(@PathVariable String storefrontId) {
        return ResponseEntity.ok(ApiResponse.ok(storefrontService.get(storefrontId), corr()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<StorefrontDtos.StorefrontView>>> list(@RequestParam(required = false) String sellerType) {
        List<StorefrontDtos.StorefrontView> result = sellerType != null
                ? storefrontService.listBySellerType(sellerType) : List.of();
        return ResponseEntity.ok(ApiResponse.ok(result, corr()));
    }
}
