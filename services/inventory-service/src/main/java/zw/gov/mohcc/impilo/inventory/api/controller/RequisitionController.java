package zw.gov.mohcc.impilo.inventory.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.inventory.api.dto.ApproveRequisitionRequest;
import zw.gov.mohcc.impilo.inventory.api.dto.CreateRequisitionRequest;
import zw.gov.mohcc.impilo.inventory.api.dto.FulfillRequisitionRequest;
import zw.gov.mohcc.impilo.inventory.core.RequisitionService;
import zw.gov.mohcc.impilo.inventory.persistence.entity.RequisitionEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for inter-store requisition management.
 *
 * <p>Manages requisitions through the full lifecycle: create, submit
 * for approval, approve/reject, and fulfill with batch-level details.</p>
 */
@RestController
@RequestMapping("/v1/requisitions")
public class RequisitionController {

    private static final Logger log = LoggerFactory.getLogger(RequisitionController.class);

    private final RequisitionService requisitionService;

    public RequisitionController(RequisitionService requisitionService) {
        this.requisitionService = requisitionService;
    }

    /**
     * Create a new requisition with line items.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<RequisitionSummary>> createRequisition(
            @Valid @RequestBody CreateRequisitionRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        List<RequisitionService.RequisitionLineData> lines = request.lines().stream()
                .map(line -> new RequisitionService.RequisitionLineData(
                        line.itemCode(), line.qtyRequested(), line.notes()))
                .collect(Collectors.toList());

        RequisitionEntity entity = requisitionService.createRequisition(
                request.fromStoreId(), request.toStoreId(), lines);

        log.info("Requisition created: reqId={}, fromStore={}, toStore={}",
                entity.getReqId(), request.fromStoreId(), request.toStoreId());

        return ResponseEntity.ok(ApiResponse.ok(RequisitionSummary.from(entity), correlationId));
    }

    /**
     * Submit a draft requisition for approval.
     */
    @PostMapping("/{id}/submit")
    public ResponseEntity<ApiResponse<RequisitionSummary>> submitRequisition(@PathVariable UUID id) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        RequisitionEntity entity = requisitionService.submitRequisition(id);

        log.info("Requisition submitted: reqId={}", id);

        return ResponseEntity.ok(ApiResponse.ok(RequisitionSummary.from(entity), correlationId));
    }

    /**
     * Approve a submitted requisition with per-line approved quantities.
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<RequisitionSummary>> approveRequisition(
            @PathVariable UUID id,
            @Valid @RequestBody ApproveRequisitionRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        List<RequisitionService.ApprovalLineData> approvals = request.lines().stream()
                .map(line -> new RequisitionService.ApprovalLineData(
                        line.lineId(), line.qtyApproved()))
                .collect(Collectors.toList());

        RequisitionEntity entity = requisitionService.approveRequisition(id, approvals);

        log.info("Requisition approved: reqId={}", id);

        return ResponseEntity.ok(ApiResponse.ok(RequisitionSummary.from(entity), correlationId));
    }

    /**
     * Reject a submitted requisition.
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<RequisitionSummary>> rejectRequisition(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        String reason = body.getOrDefault("reason", "");

        RequisitionEntity entity = requisitionService.rejectRequisition(id, reason);

        log.info("Requisition rejected: reqId={}, reason={}", id, reason);

        return ResponseEntity.ok(ApiResponse.ok(RequisitionSummary.from(entity), correlationId));
    }

    /**
     * Fulfill an approved requisition with batch-level details.
     */
    @PostMapping("/{id}/fulfill")
    public ResponseEntity<ApiResponse<RequisitionSummary>> fulfillRequisition(
            @PathVariable UUID id,
            @Valid @RequestBody FulfillRequisitionRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        List<RequisitionService.FulfillLineData> fulfillments = request.lines().stream()
                .map(line -> new RequisitionService.FulfillLineData(
                        line.lineId(), line.qtyFulfilled(), line.batch(), line.expiry()))
                .collect(Collectors.toList());

        RequisitionEntity entity = requisitionService.fulfillRequisition(id, fulfillments);

        log.info("Requisition fulfilled: reqId={}", id);

        return ResponseEntity.ok(ApiResponse.ok(RequisitionSummary.from(entity), correlationId));
    }

    /**
     * Inline summary record for requisition responses.
     */
    private record RequisitionSummary(
            UUID reqId,
            UUID fromStoreId,
            UUID toStoreId,
            String status,
            String priority,
            String requestedBy,
            String approvedBy,
            String notes
    ) {
        static RequisitionSummary from(RequisitionEntity entity) {
            return new RequisitionSummary(
                    entity.getReqId(),
                    entity.getFromStoreId(),
                    entity.getToStoreId(),
                    entity.getStatus().name(),
                    entity.getPriority(),
                    entity.getRequestedBy(),
                    entity.getApprovedBy(),
                    entity.getNotes());
        }
    }
}
