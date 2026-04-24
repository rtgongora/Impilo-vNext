package zw.gov.mohcc.impilo.varapi.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.varapi.api.dto.collaboration.CreateExternalProviderProfileRequest;
import zw.gov.mohcc.impilo.varapi.api.dto.collaboration.ExternalProviderProfileResponse;
import zw.gov.mohcc.impilo.varapi.core.collaboration.ExternalProviderCollaborationService;

import java.util.Map;

/**
 * Internal Varapi APIs for provisional external provider profiles (patient-mediated share flow).
 */
@RestController
@RequestMapping("/v1/internal/collaboration/external-providers")
public class ExternalProviderCollaborationController {

    private final ExternalProviderCollaborationService collaborationService;

    public ExternalProviderCollaborationController(ExternalProviderCollaborationService collaborationService) {
        this.collaborationService = collaborationService;
    }

    @PostMapping
    public ResponseEntity<ExternalProviderProfileResponse> create(@RequestBody CreateExternalProviderProfileRequest body) {
        return ResponseEntity.ok(collaborationService.createProfile(body));
    }

    @GetMapping("/by-temp/{temporaryProviderPublicId}")
    public ResponseEntity<ExternalProviderProfileResponse> byTemp(@PathVariable String temporaryProviderPublicId) {
        return ResponseEntity.ok(collaborationService.getByTemporaryPublicId(temporaryProviderPublicId));
    }

    @PostMapping("/{profileId}/link-provider")
    public ResponseEntity<ExternalProviderProfileResponse> link(
            @PathVariable long profileId,
            @RequestBody Map<String, String> body) {
        String pub = body.get("providerPublicId");
        if (pub == null || pub.isBlank()) {
            throw new IllegalArgumentException("providerPublicId required");
        }
        return ResponseEntity.ok(collaborationService.linkToOnboardedProvider(profileId, pub));
    }
}
