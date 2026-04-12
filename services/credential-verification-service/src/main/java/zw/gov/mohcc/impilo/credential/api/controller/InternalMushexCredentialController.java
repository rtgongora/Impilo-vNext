package zw.gov.mohcc.impilo.credential.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.credential.core.CredentialService;

import java.util.Map;
import java.util.UUID;

/**
 * Internal mesh endpoint for MUSHEX to assert facility credential posture before payouts.
 * Exposed without JWT to simplify service-to-service calls; restrict at the network layer in production.
 */
@RestController
@RequestMapping("/v1/internal/mushex/facilities")
public class InternalMushexCredentialController {

    private final CredentialService credentialService;

    public InternalMushexCredentialController(CredentialService credentialService) {
        this.credentialService = credentialService;
    }

    @GetMapping("/{facilityId}/credential-active")
    public ResponseEntity<Map<String, Boolean>> credentialActive(
            @PathVariable UUID facilityId,
            @RequestParam("tenantId") UUID tenantId) {
        boolean active = credentialService.facilityHasActiveCredential(tenantId, facilityId);
        return ResponseEntity.ok(Map.of("active", active));
    }
}
