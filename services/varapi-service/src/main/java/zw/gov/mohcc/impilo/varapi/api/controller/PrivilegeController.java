package zw.gov.mohcc.impilo.varapi.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
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
import zw.gov.mohcc.impilo.varapi.api.dto.GrantPrivilegeRequest;
import zw.gov.mohcc.impilo.varapi.api.dto.PrivilegeDecisionRequest;
import zw.gov.mohcc.impilo.varapi.api.dto.PrivilegeResponse;
import zw.gov.mohcc.impilo.varapi.core.CredentialingService;
import zw.gov.mohcc.impilo.varapi.persistence.entity.PrivilegeEntity;

import java.util.List;

/**
 * Internal REST API for managing provider privileges (credentialing at facilities/workspaces).
 *
 * Handles both provider-scoped privilege operations (grant, revoke, list)
 * and global privilege approval workflows (pending approvals, decisions).
 *
 * All endpoints require a valid trust context provided by TSHEPO ext_authz.
 */
@RestController
@RequestMapping("/v1/internal")
public class PrivilegeController {

    private static final Logger log = LoggerFactory.getLogger(PrivilegeController.class);

    private final CredentialingService credentialingService;

    public PrivilegeController(CredentialingService credentialingService) {
        this.credentialingService = credentialingService;
    }

    // ---- Provider-scoped privilege endpoints ----

    @PostMapping("/providers/{providerPublicId}/privileges/grant")
    public ResponseEntity<ApiResponse<PrivilegeResponse>> grantPrivilege(
            @PathVariable String providerPublicId,
            @Valid @RequestBody GrantPrivilegeRequest request) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Granting privilege [providerPublicId={}, type={}, scope={}] correlationId={}",
                providerPublicId, request.privilegeType(), request.scope(), ctx.correlationId());

        PrivilegeEntity entity = credentialingService.grantPrivilege(providerPublicId, request);
        PrivilegeResponse response = toPrivilegeResponse(entity);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    @PostMapping("/providers/{providerPublicId}/privileges/revoke")
    public ResponseEntity<ApiResponse<PrivilegeResponse>> revokePrivilege(
            @PathVariable String providerPublicId,
            @RequestParam Long privilegeId,
            @RequestParam String reason) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Revoking privilege [providerPublicId={}, privilegeId={}, reason={}] correlationId={}",
                providerPublicId, privilegeId, reason, ctx.correlationId());

        PrivilegeEntity entity = credentialingService.revokePrivilege(providerPublicId, privilegeId, reason);
        PrivilegeResponse response = toPrivilegeResponse(entity);

        return ResponseEntity.ok(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    @GetMapping("/providers/{providerPublicId}/privileges")
    public ResponseEntity<ApiResponse<List<PrivilegeResponse>>> listPrivileges(
            @PathVariable String providerPublicId) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Listing privileges [providerPublicId={}] correlationId={}",
                providerPublicId, ctx.correlationId());

        List<PrivilegeEntity> entities = credentialingService.listPrivileges(providerPublicId);
        List<PrivilegeResponse> responses = entities.stream()
                .map(this::toPrivilegeResponse)
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(responses, ctx.correlationId().toString()));
    }

    // ---- Global privilege approval endpoints ----

    @GetMapping("/privileges/pending")
    public ResponseEntity<ApiResponse<PagedResponse<PrivilegeResponse>>> listPendingApprovals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Listing pending privilege approvals [page={}, size={}] correlationId={}",
                page, size, ctx.correlationId());

        Page<PrivilegeEntity> entityPage = credentialingService.listPendingApprovals(
                PageRequest.of(page, size));
        List<PrivilegeResponse> responses = entityPage.getContent().stream()
                .map(this::toPrivilegeResponse)
                .toList();
        PagedResponse<PrivilegeResponse> paged = PagedResponse.of(
                responses, page, size, entityPage.getTotalElements());

        return ResponseEntity.ok(ApiResponse.ok(paged, ctx.correlationId().toString()));
    }

    @PostMapping("/privileges/{privilegeId}/decide")
    public ResponseEntity<ApiResponse<PrivilegeResponse>> decidePrivilege(
            @PathVariable Long privilegeId,
            @Valid @RequestBody PrivilegeDecisionRequest request) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Deciding privilege [privilegeId={}, decision={}] correlationId={}",
                privilegeId, request.action(), ctx.correlationId());

        PrivilegeEntity entity = credentialingService.approvePrivilege(privilegeId, request);
        PrivilegeResponse response = toPrivilegeResponse(entity);

        return ResponseEntity.ok(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    // ---- Mapper: Entity → Response DTO ----

    private PrivilegeResponse toPrivilegeResponse(PrivilegeEntity e) {
        return new PrivilegeResponse(
                e.getId(),
                e.getProvider().getProviderPublicId(),
                e.getFacilityId(),
                e.getWorkspaceId(),
                e.getScope(),
                e.getPrivilegeType(),
                e.getStatus(),
                e.getGrantedBy(),
                e.getGrantedAt(),
                e.getValidFrom(),
                e.getValidTo(),
                e.getSupervisingProvider() != null
                        ? e.getSupervisingProvider().getProviderPublicId() : null,
                e.getNotes()
        );
    }
}
