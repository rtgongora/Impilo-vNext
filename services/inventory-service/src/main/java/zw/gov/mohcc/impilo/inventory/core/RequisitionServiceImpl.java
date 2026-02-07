package zw.gov.mohcc.impilo.inventory.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.inventory.domain.LedgerEventType;
import zw.gov.mohcc.impilo.inventory.domain.RequisitionStatus;
import zw.gov.mohcc.impilo.inventory.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.RequisitionEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.RequisitionLineEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.StoreEntity;
import zw.gov.mohcc.impilo.inventory.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.inventory.persistence.repository.RequisitionLineRepository;
import zw.gov.mohcc.impilo.inventory.persistence.repository.RequisitionRepository;
import zw.gov.mohcc.impilo.inventory.persistence.repository.StoreRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Inter-store requisition workflow implementation.
 *
 * <p>Manages the full requisition lifecycle from draft creation through
 * approval and fulfillment. Fulfillment triggers ledger events for stock
 * movement between stores: TRANSFER_OUT/TRANSFER_IN for intra-facility
 * moves, ISSUE/RECEIPT for cross-facility moves.</p>
 *
 * <p>State machine:
 * <pre>
 *   DRAFT -> SUBMITTED -> APPROVED -> PARTIALLY_FULFILLED -> FULFILLED
 *                      -> REJECTED
 * </pre>
 */
@Service
public class RequisitionServiceImpl implements RequisitionService {

    private static final Logger log = LoggerFactory.getLogger(RequisitionServiceImpl.class);

    private final RequisitionRepository requisitionRepository;
    private final RequisitionLineRepository lineRepository;
    private final StoreRepository storeRepository;
    private final LedgerService ledgerService;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public RequisitionServiceImpl(RequisitionRepository requisitionRepository,
                                   RequisitionLineRepository lineRepository,
                                   StoreRepository storeRepository,
                                   LedgerService ledgerService,
                                   EventOutboxRepository outboxRepository,
                                   ObjectMapper objectMapper) {
        this.requisitionRepository = requisitionRepository;
        this.lineRepository = lineRepository;
        this.storeRepository = storeRepository;
        this.ledgerService = ledgerService;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public RequisitionEntity createRequisition(UUID fromStoreId, UUID toStoreId,
                                                 List<RequisitionLineData> lines) {
        TrustContext ctx = TrustContextHolder.require();

        if (fromStoreId == null || toStoreId == null) {
            throw new IllegalArgumentException("Both fromStoreId and toStoreId are required");
        }
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("At least one line item is required");
        }

        RequisitionEntity requisition = new RequisitionEntity();
        requisition.setTenantId(ctx.tenantId());
        requisition.setFacilityId(ctx.facilityId());
        requisition.setFromStoreId(fromStoreId);
        requisition.setToStoreId(toStoreId);
        requisition.setStatus(RequisitionStatus.DRAFT);
        requisition.setRequestedBy(ctx.actorId());

        requisition = requisitionRepository.save(requisition);
        UUID reqId = requisition.getReqId();

        // Create line items
        for (RequisitionLineData lineData : lines) {
            RequisitionLineEntity line = new RequisitionLineEntity();
            line.setReqId(reqId);
            line.setItemCode(lineData.itemCode());
            line.setQtyRequested(lineData.qtyRequested());
            line.setQtyApproved(0);
            line.setQtyFulfilled(0);
            line.setNotes(lineData.notes());
            lineRepository.save(line);
        }

        publishOutboxEvent("REQUISITION", reqId.toString(),
                "REQUISITION_CREATED", requisition, ctx.tenantId());

        log.info("Requisition created: reqId={}, from={}, to={}, lines={}",
                reqId, fromStoreId, toStoreId, lines.size());
        return requisition;
    }

    @Override
    @Transactional
    public RequisitionEntity submitRequisition(UUID reqId) {
        TrustContext ctx = TrustContextHolder.require();
        RequisitionEntity requisition = loadRequisition(reqId);

        if (requisition.getStatus() != RequisitionStatus.DRAFT) {
            throw new IllegalStateException(
                    "Cannot submit requisition " + reqId + " in status " + requisition.getStatus()
                            + " (expected DRAFT)");
        }

        requisition.setStatus(RequisitionStatus.SUBMITTED);
        requisition = requisitionRepository.save(requisition);

        publishOutboxEvent("REQUISITION", reqId.toString(),
                "REQUISITION_SUBMITTED", requisition, ctx.tenantId());

        log.info("Requisition submitted: reqId={}", reqId);
        return requisition;
    }

    @Override
    @Transactional
    public RequisitionEntity approveRequisition(UUID reqId, List<ApprovalLineData> approvals) {
        TrustContext ctx = TrustContextHolder.require();
        RequisitionEntity requisition = loadRequisition(reqId);

        if (requisition.getStatus() != RequisitionStatus.SUBMITTED) {
            throw new IllegalStateException(
                    "Cannot approve requisition " + reqId + " in status " + requisition.getStatus()
                            + " (expected SUBMITTED)");
        }

        // Apply approved quantities to each line
        for (ApprovalLineData approval : approvals) {
            RequisitionLineEntity line = lineRepository.findById(approval.lineId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Requisition line not found: " + approval.lineId()));

            if (!line.getReqId().equals(reqId)) {
                throw new IllegalArgumentException(
                        "Line " + approval.lineId() + " does not belong to requisition " + reqId);
            }

            if (approval.qtyApproved() < 0) {
                throw new IllegalArgumentException(
                        "Approved quantity cannot be negative for line " + approval.lineId());
            }
            if (approval.qtyApproved() > line.getQtyRequested()) {
                throw new IllegalArgumentException(
                        "Approved quantity " + approval.qtyApproved()
                                + " exceeds requested quantity " + line.getQtyRequested()
                                + " for line " + approval.lineId());
            }

            line.setQtyApproved(approval.qtyApproved());
            lineRepository.save(line);
        }

        requisition.setStatus(RequisitionStatus.APPROVED);
        requisition.setApprovedBy(ctx.actorId());
        requisition.setApprovedAt(OffsetDateTime.now());
        requisition = requisitionRepository.save(requisition);

        publishOutboxEvent("REQUISITION", reqId.toString(),
                "REQUISITION_APPROVED", requisition, ctx.tenantId());

        log.info("Requisition approved: reqId={}, approvedBy={}", reqId, ctx.actorId());
        return requisition;
    }

    @Override
    @Transactional
    public RequisitionEntity rejectRequisition(UUID reqId, String reason) {
        TrustContext ctx = TrustContextHolder.require();
        RequisitionEntity requisition = loadRequisition(reqId);

        if (requisition.getStatus() != RequisitionStatus.SUBMITTED) {
            throw new IllegalStateException(
                    "Cannot reject requisition " + reqId + " in status " + requisition.getStatus()
                            + " (expected SUBMITTED)");
        }

        requisition.setStatus(RequisitionStatus.REJECTED);
        requisition.setNotes(reason);
        requisition = requisitionRepository.save(requisition);

        publishOutboxEvent("REQUISITION", reqId.toString(),
                "REQUISITION_REJECTED", requisition, ctx.tenantId());

        log.info("Requisition rejected: reqId={}, reason={}", reqId, reason);
        return requisition;
    }

    /**
     * {@inheritDoc}
     *
     * <p>For each fulfillment line:
     * <ol>
     *   <li>Updates the fulfilled quantity on the requisition line</li>
     *   <li>Determines if same-facility (TRANSFER) or cross-facility (ISSUE/RECEIPT)</li>
     *   <li>Posts ledger events for stock outflow from the source store and inflow to the destination</li>
     * </ol>
     *
     * <p>After processing all lines, checks whether the requisition is fully or partially fulfilled.</p>
     */
    @Override
    @Transactional
    public RequisitionEntity fulfillRequisition(UUID reqId, List<FulfillLineData> fulfillments) {
        TrustContext ctx = TrustContextHolder.require();
        RequisitionEntity requisition = loadRequisition(reqId);

        if (requisition.getStatus() != RequisitionStatus.APPROVED
                && requisition.getStatus() != RequisitionStatus.PARTIALLY_FULFILLED) {
            throw new IllegalStateException(
                    "Cannot fulfill requisition " + reqId + " in status " + requisition.getStatus()
                            + " (expected APPROVED or PARTIALLY_FULFILLED)");
        }

        // Determine movement type based on whether stores are in the same facility
        boolean sameFacility = areSameFacility(requisition.getFromStoreId(), requisition.getToStoreId());
        LedgerEventType outflowType = sameFacility ? LedgerEventType.TRANSFER_OUT : LedgerEventType.ISSUE;
        LedgerEventType inflowType = sameFacility ? LedgerEventType.TRANSFER_IN : LedgerEventType.RECEIPT;

        // Resolve source/dest facility IDs for cross-facility moves
        UUID sourceFacilityId = resolveStoreFacility(requisition.getToStoreId());
        UUID destFacilityId = resolveStoreFacility(requisition.getFromStoreId());

        for (FulfillLineData fulfillment : fulfillments) {
            RequisitionLineEntity line = lineRepository.findById(fulfillment.lineId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Requisition line not found: " + fulfillment.lineId()));

            if (!line.getReqId().equals(reqId)) {
                throw new IllegalArgumentException(
                        "Line " + fulfillment.lineId() + " does not belong to requisition " + reqId);
            }

            if (fulfillment.qtyFulfilled() <= 0) {
                throw new IllegalArgumentException(
                        "Fulfilled quantity must be positive for line " + fulfillment.lineId());
            }

            int newFulfilled = line.getQtyFulfilled() + fulfillment.qtyFulfilled();
            if (newFulfilled > line.getQtyApproved()) {
                throw new IllegalArgumentException(
                        "Total fulfilled (" + newFulfilled + ") exceeds approved ("
                                + line.getQtyApproved() + ") for line " + fulfillment.lineId());
            }

            line.setQtyFulfilled(newFulfilled);
            lineRepository.save(line);

            String idempotencyPrefix = "REQ:" + reqId + ":LINE:" + fulfillment.lineId()
                    + ":BATCH:" + fulfillment.batch() + ":";

            // Post outflow (stock leaving the source/supplying store)
            LedgerService.LedgerEventData outflowEvent = new LedgerService.LedgerEventData(
                    sourceFacilityId,
                    requisition.getToStoreId(), // supplying store
                    null,
                    line.getItemCode(),
                    fulfillment.batch(),
                    fulfillment.expiry(),
                    -fulfillment.qtyFulfilled(), // negative delta: stock leaving
                    null,
                    outflowType,
                    "Requisition fulfillment " + reqId,
                    "REQUISITION",
                    reqId.toString(),
                    idempotencyPrefix + "OUT"
            );
            ledgerService.postEvent(outflowEvent);

            // Post inflow (stock arriving at the requesting store)
            LedgerService.LedgerEventData inflowEvent = new LedgerService.LedgerEventData(
                    destFacilityId,
                    requisition.getFromStoreId(), // requesting store
                    null,
                    line.getItemCode(),
                    fulfillment.batch(),
                    fulfillment.expiry(),
                    fulfillment.qtyFulfilled(), // positive delta: stock arriving
                    null,
                    inflowType,
                    "Requisition fulfillment " + reqId,
                    "REQUISITION",
                    reqId.toString(),
                    idempotencyPrefix + "IN"
            );
            ledgerService.postEvent(inflowEvent);

            log.debug("Fulfillment posted: reqId={}, line={}, item={}, batch={}, qty={}, type={}",
                    reqId, fulfillment.lineId(), line.getItemCode(),
                    fulfillment.batch(), fulfillment.qtyFulfilled(),
                    sameFacility ? "TRANSFER" : "ISSUE/RECEIPT");
        }

        // Determine new requisition status
        List<RequisitionLineEntity> allLines = lineRepository.findByReqId(reqId);
        boolean fullyFulfilled = allLines.stream()
                .allMatch(l -> l.getQtyFulfilled() >= l.getQtyApproved());

        RequisitionStatus newStatus = fullyFulfilled
                ? RequisitionStatus.FULFILLED
                : RequisitionStatus.PARTIALLY_FULFILLED;

        requisition.setStatus(newStatus);
        requisition = requisitionRepository.save(requisition);

        String eventType = fullyFulfilled ? "REQUISITION_FULFILLED" : "REQUISITION_PARTIALLY_FULFILLED";
        publishOutboxEvent("REQUISITION", reqId.toString(), eventType, requisition, ctx.tenantId());

        log.info("Requisition {}: reqId={}, status={}", eventType, reqId, newStatus);
        return requisition;
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private RequisitionEntity loadRequisition(UUID reqId) {
        return requisitionRepository.findById(reqId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Requisition not found: " + reqId));
    }

    /**
     * Determine if two stores belong to the same facility.
     */
    private boolean areSameFacility(UUID storeId1, UUID storeId2) {
        UUID facility1 = resolveStoreFacility(storeId1);
        UUID facility2 = resolveStoreFacility(storeId2);
        return facility1 != null && facility1.equals(facility2);
    }

    /**
     * Resolve the facility ID for a given store.
     */
    private UUID resolveStoreFacility(UUID storeId) {
        return storeRepository.findById(storeId)
                .map(StoreEntity::getFacilityId)
                .orElse(null);
    }

    private void publishOutboxEvent(String aggregateType, String aggregateId,
                                     String eventType, Object payload, UUID tenantId) {
        try {
            EventOutboxEntity outbox = new EventOutboxEntity();
            outbox.setAggregateType(aggregateType);
            outbox.setAggregateId(aggregateId);
            outbox.setEventType(eventType);
            outbox.setPayload(objectMapper.writeValueAsString(payload));
            outbox.setTenantId(tenantId);
            outboxRepository.save(outbox);
        } catch (Exception e) {
            log.error("Failed to write outbox event: type={}, aggregateId={}", eventType, aggregateId, e);
        }
    }
}
