package zw.gov.mohcc.impilo.varapi.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
import zw.gov.mohcc.impilo.varapi.api.dto.ReconciliationCaseResponse;
import zw.gov.mohcc.impilo.varapi.api.dto.ReconciliationDecisionRequest;
import zw.gov.mohcc.impilo.varapi.core.ReconciliationService;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ReconciliationCaseEntity;

import java.util.List;

/**
 * Internal REST API for managing reconciliation cases within the VARAPI Provider Registry.
 *
 * Reconciliation handles conflicts that arise during council data imports,
 * such as duplicate providers, mismatched identifiers, or conflicting
 * demographic data. Cases are reviewed and resolved by authorized users.
 *
 * All endpoints require a valid trust context provided by TSHEPO ext_authz.
 */
@RestController
@RequestMapping("/v1/internal/reconciliation")
public class ReconciliationController {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationController.class);

    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @GetMapping("/queue")
    public ResponseEntity<ApiResponse<PagedResponse<ReconciliationCaseResponse>>> getQueue(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Fetching reconciliation queue [status={}, page={}, size={}] correlationId={}",
                status, page, size, ctx.correlationId());

        Page<ReconciliationCaseEntity> entityPage = reconciliationService.getQueue(
                status, PageRequest.of(page, size));
        List<ReconciliationCaseResponse> responses = entityPage.getContent().stream()
                .map(this::toReconciliationCaseResponse)
                .toList();
        PagedResponse<ReconciliationCaseResponse> paged = PagedResponse.of(
                responses, page, size, entityPage.getTotalElements());

        return ResponseEntity.ok(ApiResponse.ok(paged, ctx.correlationId().toString()));
    }

    @PostMapping("/{caseId}/decision")
    public ResponseEntity<ApiResponse<ReconciliationCaseResponse>> decideCase(
            @PathVariable Long caseId,
            @Valid @RequestBody ReconciliationDecisionRequest request) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Deciding reconciliation case [caseId={}, action={}] correlationId={}",
                caseId, request.action(), ctx.correlationId());

        ReconciliationCaseEntity entity = reconciliationService.decideCase(caseId, request);
        ReconciliationCaseResponse response = toReconciliationCaseResponse(entity);

        return ResponseEntity.ok(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    // ---- Mapper: Entity → Response DTO ----

    private ReconciliationCaseResponse toReconciliationCaseResponse(ReconciliationCaseEntity entity) {
        return new ReconciliationCaseResponse(
                entity.getId(),
                entity.getCouncil() != null ? entity.getCouncil().getId() : null,
                entity.getCouncil() != null ? entity.getCouncil().getName() : null,
                entity.getSourceSystem(),
                entity.getSourceRef(),
                entity.getProvider() != null ? entity.getProvider().getProviderPublicId() : null,
                entity.getCaseType(),
                entity.getStatus(),
                entity.getPayload(),
                entity.getNotes(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
