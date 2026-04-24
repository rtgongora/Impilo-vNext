package zw.gov.mohcc.impilo.varapi.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.varapi.api.dto.collaboration.CouncilSummaryResponse;
import zw.gov.mohcc.impilo.varapi.core.collaboration.ExternalProviderCollaborationService;

import java.util.List;
import java.util.UUID;

/**
 * Internal Varapi APIs supporting patient-mediated collaboration (council picker).
 */
@RestController
@RequestMapping("/v1/internal/collaboration/councils")
public class CollaborationCouncilsController {

    private final ExternalProviderCollaborationService collaborationService;

    public CollaborationCouncilsController(ExternalProviderCollaborationService collaborationService) {
        this.collaborationService = collaborationService;
    }

    @GetMapping
    public ResponseEntity<List<CouncilSummaryResponse>> list(@RequestParam UUID tenantId) {
        return ResponseEntity.ok(collaborationService.listCouncilsForCollaboration(tenantId));
    }
}
