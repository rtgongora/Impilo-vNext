package zw.gov.mohcc.impilo.vito.api;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;
import zw.gov.mohcc.impilo.vito.api.dto.ClientRegistryDtos;
import zw.gov.mohcc.impilo.vito.core.ClientIdentityOperationsService;
import zw.gov.mohcc.impilo.vito.core.ClientVerificationState;
import zw.gov.mohcc.impilo.vito.core.IdentityStatus;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/client-registry")
public class ClientIdentityOperationsController {

    private final ClientIdentityOperationsService service;

    public ClientIdentityOperationsController(ClientIdentityOperationsService service) {
        this.service = service;
    }

    @GetMapping("/clients")
    public ResponseEntity<ApiResponse<PagedResponse<ClientRegistryDtos.ClientRegistrySummaryResponse>>> listClients(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) IdentityStatus status,
            @RequestParam(required = false) ClientVerificationState verificationState,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        TrustContext ctx = TrustContextHolder.require();
        Page<ClientRegistryDtos.ClientRegistrySummaryResponse> results = service.searchClients(query, status, verificationState, page, size);
        return ResponseEntity.ok(ApiResponse.ok(
                PagedResponse.of(results.getContent(), page, size, results.getTotalElements()),
                correlationId(ctx)
        ));
    }

    @GetMapping("/clients/{healthId}")
    public ResponseEntity<ApiResponse<ClientRegistryDtos.ClientProfileResponse>> getClientProfile(@PathVariable UUID healthId) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(service.getClientProfile(healthId), correlationId(ctx)));
    }

    @PostMapping("/registrations")
    public ResponseEntity<ApiResponse<ClientRegistryDtos.ClientProfileResponse>> createRegistration(
            @Valid @RequestBody ClientRegistryDtos.CreateRegistrationRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.status(201).body(ApiResponse.ok(service.createRegistration(request), correlationId(ctx)));
    }

    @PostMapping("/registrations/{registrationId}/submit")
    public ResponseEntity<ApiResponse<ClientRegistryDtos.ClientProfileResponse>> submitRegistration(@PathVariable UUID registrationId) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(service.submitRegistration(registrationId), correlationId(ctx)));
    }

    @PostMapping("/clients/{healthId}/evidence")
    public ResponseEntity<ApiResponse<ClientRegistryDtos.EvidenceView>> addEvidence(
            @PathVariable UUID healthId,
            @Valid @RequestBody ClientRegistryDtos.AddEvidenceRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.status(201).body(ApiResponse.ok(service.addEvidence(healthId, request), correlationId(ctx)));
    }

    @PostMapping("/clients/{healthId}/verification-reviews")
    public ResponseEntity<ApiResponse<ClientRegistryDtos.VerificationReviewView>> recordVerificationReview(
            @PathVariable UUID healthId,
            @Valid @RequestBody ClientRegistryDtos.RecordVerificationReviewRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.status(201).body(ApiResponse.ok(service.recordVerificationReview(healthId, request), correlationId(ctx)));
    }

    @PostMapping("/clients/{healthId}/match")
    public ResponseEntity<ApiResponse<List<ClientRegistryDtos.MatchCandidateView>>> runMatching(@PathVariable UUID healthId) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(service.runMatching(healthId), correlationId(ctx)));
    }

    @PostMapping("/match-candidates/{matchId}/review")
    public ResponseEntity<ApiResponse<ClientRegistryDtos.MatchCandidateView>> reviewMatchCandidate(
            @PathVariable Long matchId,
            @Valid @RequestBody ClientRegistryDtos.ReviewMatchCandidateRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(service.reviewMatchCandidate(matchId, request), correlationId(ctx)));
    }

    @PostMapping("/merge-cases")
    public ResponseEntity<ApiResponse<ClientRegistryDtos.MergeCaseView>> createMergeCase(
            @Valid @RequestBody ClientRegistryDtos.CreateMergeCaseRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.status(201).body(ApiResponse.ok(service.createMergeCase(request), correlationId(ctx)));
    }

    @PostMapping("/merge-cases/{mergeCaseId}/decision")
    public ResponseEntity<ApiResponse<ClientRegistryDtos.MergeCaseView>> decideMergeCase(
            @PathVariable UUID mergeCaseId,
            @Valid @RequestBody ClientRegistryDtos.DecideMergeCaseRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(service.decideMergeCase(mergeCaseId, request), correlationId(ctx)));
    }

    @PostMapping("/clients/{healthId}/relationships")
    public ResponseEntity<ApiResponse<ClientRegistryDtos.RelationshipView>> addRelationship(
            @PathVariable UUID healthId,
            @Valid @RequestBody ClientRegistryDtos.AddRelationshipRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.status(201).body(ApiResponse.ok(service.addRelationship(healthId, request), correlationId(ctx)));
    }

    @PostMapping("/clients/{healthId}/authorization-links")
    public ResponseEntity<ApiResponse<ClientRegistryDtos.AuthorizationLinkView>> addAuthorizationLink(
            @PathVariable UUID healthId,
            @Valid @RequestBody ClientRegistryDtos.AddAuthorizationLinkRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.status(201).body(ApiResponse.ok(service.addAuthorizationLink(healthId, request), correlationId(ctx)));
    }

    @PostMapping("/clients/{healthId}/stewardship-actions")
    public ResponseEntity<ApiResponse<ClientRegistryDtos.StewardshipActionView>> createStewardshipAction(
            @PathVariable UUID healthId,
            @Valid @RequestBody ClientRegistryDtos.CreateStewardshipActionRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.status(201).body(ApiResponse.ok(service.createStewardshipAction(healthId, request), correlationId(ctx)));
    }

    @PostMapping("/stewardship-actions/{actionId}")
    public ResponseEntity<ApiResponse<ClientRegistryDtos.StewardshipActionView>> updateStewardshipAction(
            @PathVariable UUID actionId,
            @Valid @RequestBody ClientRegistryDtos.UpdateStewardshipActionRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(service.updateStewardshipAction(actionId, request), correlationId(ctx)));
    }

    @PostMapping("/clients/{healthId}/corrections")
    public ResponseEntity<ApiResponse<ClientRegistryDtos.ClientProfileResponse>> recordCorrection(
            @PathVariable UUID healthId,
            @Valid @RequestBody ClientRegistryDtos.RecordCorrectionRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(service.recordCorrection(healthId, request), correlationId(ctx)));
    }

    @GetMapping("/dashboard/summary")
    public ResponseEntity<ApiResponse<ClientRegistryDtos.DashboardSummaryResponse>> dashboardSummary() {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(service.getDashboardSummary(), correlationId(ctx)));
    }

    @GetMapping("/stewardship/workspace")
    public ResponseEntity<ApiResponse<ClientRegistryDtos.StewardshipWorkspaceResponse>> stewardshipWorkspace() {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(service.getStewardshipWorkspace(), correlationId(ctx)));
    }

    private String correlationId(TrustContext ctx) {
        return ctx.correlationId() != null ? ctx.correlationId().toString() : null;
    }
}
