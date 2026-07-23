package zw.gov.mohcc.impilo.nhume.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.nhume.api.dto.AssignDeliveryRequest;
import zw.gov.mohcc.impilo.nhume.api.dto.ChainOfCustodyRequest;
import zw.gov.mohcc.impilo.nhume.api.dto.CreateDeliveryRequest;
import zw.gov.mohcc.impilo.nhume.api.dto.ProofRequest;
import zw.gov.mohcc.impilo.nhume.api.dto.StatusChangeRequest;
import zw.gov.mohcc.impilo.nhume.api.dto.TrackingUpdateRequest;
import zw.gov.mohcc.impilo.nhume.domain.ChainOfCustodyEventEntity;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryAssignmentEntity;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryAuditEventEntity;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryExceptionEntity;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryItemEntity;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryNotificationEventEntity;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryPackageEntity;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryPolicyEntity;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryPriority;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryProofEntity;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryRequestEntity;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryStatus;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryStatusEventEntity;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryTrackingEventEntity;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryType;
import zw.gov.mohcc.impilo.nhume.domain.DriverCourierProfileEntity;
import zw.gov.mohcc.impilo.nhume.domain.FleetAssetEntity;
import zw.gov.mohcc.impilo.nhume.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.nhume.integration.commshub.CommsHubClient;
import zw.gov.mohcc.impilo.nhume.integration.ndila.NdilaClient;
import zw.gov.mohcc.impilo.nhume.integration.trust.TrustLayerGuard;
import zw.gov.mohcc.impilo.nhume.repository.ChainOfCustodyEventRepository;
import zw.gov.mohcc.impilo.nhume.repository.DeliveryAssignmentRepository;
import zw.gov.mohcc.impilo.nhume.repository.DeliveryAuditEventRepository;
import zw.gov.mohcc.impilo.nhume.repository.DeliveryExceptionRepository;
import zw.gov.mohcc.impilo.nhume.repository.DeliveryItemRepository;
import zw.gov.mohcc.impilo.nhume.repository.DeliveryNotificationEventRepository;
import zw.gov.mohcc.impilo.nhume.repository.DeliveryPackageRepository;
import zw.gov.mohcc.impilo.nhume.repository.DeliveryPolicyRepository;
import zw.gov.mohcc.impilo.nhume.repository.DeliveryProofRepository;
import zw.gov.mohcc.impilo.nhume.repository.DeliveryRequestRepository;
import zw.gov.mohcc.impilo.nhume.repository.DeliveryStatusEventRepository;
import zw.gov.mohcc.impilo.nhume.repository.DeliveryTrackingEventRepository;
import zw.gov.mohcc.impilo.nhume.repository.DriverCourierProfileRepository;
import zw.gov.mohcc.impilo.nhume.repository.FleetAssetRepository;
import zw.gov.mohcc.impilo.nhume.repository.OutboxEventRepository;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Core orchestration service for Nhume deliveries — owns lifecycle, dispatch,
 * tracking, proof and chain-of-custody mutations. Every state-changing
 * operation:
 *
 * <ul>
 *   <li>captures a {@link TrustLayerGuard.ActorContext} for audit</li>
 *   <li>records a {@code nhume_delivery_audit_events} row</li>
 *   <li>appends a delivery status / tracking event</li>
 *   <li>emits a {@code nhume.*} outbox event</li>
 *   <li>fires Comms Hub notifications when configured</li>
 * </ul>
 */
@Service
public class NhumeDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(NhumeDeliveryService.class);
    private static final String AGGREGATE = "NhumeDelivery";
    private static final String PRODUCER = "nhume-service";

    private final DeliveryRequestRepository deliveryRepo;
    private final DeliveryItemRepository itemRepo;
    private final DeliveryPackageRepository packageRepo;
    private final DeliveryAssignmentRepository assignmentRepo;
    private final DeliveryStatusEventRepository statusEventRepo;
    private final DeliveryTrackingEventRepository trackingRepo;
    private final DeliveryProofRepository proofRepo;
    private final ChainOfCustodyEventRepository custodyRepo;
    private final DeliveryExceptionRepository exceptionRepo;
    private final DeliveryNotificationEventRepository notificationRepo;
    private final DeliveryAuditEventRepository auditRepo;
    private final OutboxEventRepository outboxRepo;
    private final DeliveryPolicyRepository policyRepo;
    private final DriverCourierProfileRepository courierRepo;
    private final FleetAssetRepository assetRepo;
    private final CommsHubClient commsHub;
    private final NdilaClient ndila;
    private final ObjectMapper objectMapper;
    private final zw.gov.mohcc.impilo.nhume.integration.writeback.NhumeIntegrationWriteBackService writeBack;
    private final zw.gov.mohcc.impilo.shared.biometric.BiometricVerificationClient biometricVerification;

    public NhumeDeliveryService(DeliveryRequestRepository deliveryRepo,
                                DeliveryItemRepository itemRepo,
                                DeliveryPackageRepository packageRepo,
                                DeliveryAssignmentRepository assignmentRepo,
                                DeliveryStatusEventRepository statusEventRepo,
                                DeliveryTrackingEventRepository trackingRepo,
                                DeliveryProofRepository proofRepo,
                                ChainOfCustodyEventRepository custodyRepo,
                                DeliveryExceptionRepository exceptionRepo,
                                DeliveryNotificationEventRepository notificationRepo,
                                DeliveryAuditEventRepository auditRepo,
                                OutboxEventRepository outboxRepo,
                                DeliveryPolicyRepository policyRepo,
                                DriverCourierProfileRepository courierRepo,
                                FleetAssetRepository assetRepo,
                                CommsHubClient commsHub,
                                NdilaClient ndila,
                                ObjectMapper objectMapper,
                                zw.gov.mohcc.impilo.nhume.integration.writeback.NhumeIntegrationWriteBackService writeBack,
                                zw.gov.mohcc.impilo.shared.biometric.BiometricVerificationClient biometricVerification) {
        this.deliveryRepo = deliveryRepo;
        this.itemRepo = itemRepo;
        this.packageRepo = packageRepo;
        this.assignmentRepo = assignmentRepo;
        this.statusEventRepo = statusEventRepo;
        this.trackingRepo = trackingRepo;
        this.proofRepo = proofRepo;
        this.custodyRepo = custodyRepo;
        this.exceptionRepo = exceptionRepo;
        this.notificationRepo = notificationRepo;
        this.auditRepo = auditRepo;
        this.outboxRepo = outboxRepo;
        this.policyRepo = policyRepo;
        this.courierRepo = courierRepo;
        this.assetRepo = assetRepo;
        this.commsHub = commsHub;
        this.ndila = ndila;
        this.objectMapper = objectMapper;
        this.writeBack = writeBack;
        this.biometricVerification = biometricVerification;
    }

    // ─── Reads ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<DeliveryRequestEntity> listDeliveries(UUID tenantId, String status, String facilityId,
                                                       String deliveryType, String priority,
                                                       int page, int size) {
        return deliveryRepo.findFiltered(tenantId, status, facilityId, deliveryType, priority,
                PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public Optional<DeliveryRequestEntity> getDelivery(UUID deliveryId) {
        return deliveryRepo.findById(deliveryId);
    }

    @Transactional(readOnly = true)
    public List<DeliveryStatusEventEntity> getStatusTimeline(UUID deliveryId) {
        return statusEventRepo.findByDeliveryIdOrderByCreatedAtAsc(deliveryId);
    }

    @Transactional(readOnly = true)
    public List<DeliveryTrackingEventEntity> getTrackingTimeline(UUID deliveryId) {
        return trackingRepo.findByDeliveryIdOrderByCapturedAtDesc(deliveryId);
    }

    @Transactional(readOnly = true)
    public List<DeliveryProofEntity> getProofs(UUID deliveryId) {
        return proofRepo.findByDeliveryIdOrderByCapturedAtAsc(deliveryId);
    }

    @Transactional(readOnly = true)
    public List<ChainOfCustodyEventEntity> getCustody(UUID deliveryId) {
        return custodyRepo.findByDeliveryIdOrderBySequenceNoAsc(deliveryId);
    }

    @Transactional(readOnly = true)
    public List<DeliveryAssignmentEntity> getAssignments(UUID deliveryId) {
        return assignmentRepo.findByDeliveryIdOrderByAssignedAtDesc(deliveryId);
    }

    @Transactional(readOnly = true)
    public List<DeliveryItemEntity> getItems(UUID deliveryId) {
        return itemRepo.findByDeliveryIdOrderBySequenceNoAsc(deliveryId);
    }

    @Transactional(readOnly = true)
    public List<DeliveryPackageEntity> getPackages(UUID deliveryId) {
        return packageRepo.findByDeliveryId(deliveryId);
    }

    @Transactional(readOnly = true)
    public List<DeliveryNotificationEventEntity> getNotifications(UUID deliveryId) {
        return notificationRepo.findByDeliveryIdOrderByDispatchedAtDesc(deliveryId);
    }

    // ─── Create ─────────────────────────────────────────────────────────────

    @Transactional
    public DeliveryRequestEntity createDelivery(UUID tenantId, String podId, String correlationId,
                                                 String idempotencyKey, CreateDeliveryRequest req,
                                                 TrustLayerGuard.ActorContext actor) {
        UUID deliveryId = UUID.randomUUID();
        String reference = generateReference();
        DeliveryRequestEntity delivery = new DeliveryRequestEntity(deliveryId, tenantId,
                reference, req.requestSource());
        delivery.setPodId(podId != null ? podId : "national-spine");
        delivery.setCorrelationId(correlationId != null ? safeUuid(correlationId) : null);
        delivery.setCreatedBy(actor != null ? actor.actorId() : null);

        applyRequest(delivery, req);
        applyPolicyDefaults(delivery, tenantId);
        computeSlaDueAt(delivery);

        // OF-B18 §12.4 — an OTP-graded contract needs a server-held OTP truth at
        // creation (an OTP grade can never be "matched" against nothing). The code
        // rides the recipient notification channel, never the courier surface.
        if (HandoverGradePolicy.requiresGradedHandover(delivery)
                && HandoverGradePolicy.requiredGrade(delivery).rank()
                        >= zw.gov.mohcc.impilo.nhume.domain.HandoverVerificationGrade.OTP_MATCH.rank()
                && delivery.getHandoverOtp() == null) {
            delivery.setHandoverOtp(String.format(Locale.ROOT, "%06d",
                    ThreadLocalRandom.current().nextInt(1_000_000)));
        }

        deliveryRepo.save(delivery);

        if (req.items() != null) {
            int seq = 1;
            for (CreateDeliveryRequest.DeliveryItemDto item : req.items()) {
                DeliveryItemEntity entity = new DeliveryItemEntity();
                entity.setItemId(UUID.randomUUID());
                entity.setDeliveryId(deliveryId);
                entity.setSequenceNo(seq++);
                entity.setItemKind(orDefault(item.itemKind(), "GENERAL"));
                entity.setItemRef(item.itemRef());
                entity.setDescription(orDefault(item.description(), "Delivery item"));
                if (item.quantity() != null) entity.setQuantity(item.quantity());
                entity.setUnitOfMeasure(item.unitOfMeasure());
                entity.setPackageType(item.packageType());
                entity.setColdChain(Boolean.TRUE.equals(item.coldChain()));
                entity.setControlled(Boolean.TRUE.equals(item.controlled()));
                itemRepo.save(entity);
            }
        }

        if (req.packages() != null) {
            for (CreateDeliveryRequest.DeliveryPackageDto pkg : req.packages()) {
                DeliveryPackageEntity entity = new DeliveryPackageEntity();
                entity.setPackageId(UUID.randomUUID());
                entity.setDeliveryId(deliveryId);
                entity.setPackageCode(orDefault(pkg.packageCode(), "PKG-" + UUID.randomUUID()));
                entity.setPackageType(pkg.packageType());
                entity.setSealId(pkg.sealId());
                entity.setColdChain(Boolean.TRUE.equals(pkg.coldChain()));
                entity.setFragile(Boolean.TRUE.equals(pkg.fragile()));
                packageRepo.save(entity);
            }
        }

        appendStatusEvent(delivery, null, delivery.getStatus(), actor, "Delivery requested");
        appendOutboxEvent(NhumeEvents.DELIVERY_REQUESTED, delivery, tenantId, podId, correlationId,
                idempotencyKey, buildDeliveryStatePayload(delivery));
        recordAudit(delivery, "delivery.create", actor);
        dispatchNotification(delivery, "DELIVERY_REQUEST_SUBMITTED",
                "nhume.delivery.request_submitted", channelForRecipient(delivery));

        if (Boolean.TRUE.equals(req.submitImmediately())) {
            return transition(delivery.getDeliveryId(), DeliveryStatus.SUBMITTED,
                    new StatusChangeRequest("auto-submit", null), actor, NhumeEvents.DELIVERY_SUBMITTED);
        }

        log.info("Nhume delivery created reference={} status={} type={}",
                reference, delivery.getStatus(), delivery.getDeliveryType());
        return delivery;
    }

    // ─── Lifecycle transitions ──────────────────────────────────────────────

    @Transactional
    public DeliveryRequestEntity submit(UUID deliveryId, StatusChangeRequest req,
                                         TrustLayerGuard.ActorContext actor) {
        return transition(deliveryId, DeliveryStatus.SUBMITTED, req, actor, NhumeEvents.DELIVERY_SUBMITTED);
    }

    @Transactional
    public DeliveryRequestEntity approve(UUID deliveryId, StatusChangeRequest req,
                                          TrustLayerGuard.ActorContext actor) {
        return transition(deliveryId, DeliveryStatus.APPROVED, req, actor, NhumeEvents.DELIVERY_APPROVED);
    }

    @Transactional
    public DeliveryRequestEntity reject(UUID deliveryId, StatusChangeRequest req,
                                         TrustLayerGuard.ActorContext actor) {
        return transition(deliveryId, DeliveryStatus.REJECTED, req, actor, NhumeEvents.DELIVERY_REJECTED);
    }

    @Transactional
    public DeliveryRequestEntity cancel(UUID deliveryId, StatusChangeRequest req,
                                         TrustLayerGuard.ActorContext actor) {
        return transition(deliveryId, DeliveryStatus.CANCELLED, req, actor, NhumeEvents.DELIVERY_CANCELLED);
    }

    @Transactional
    public DeliveryRequestEntity markFailed(UUID deliveryId, StatusChangeRequest req,
                                             TrustLayerGuard.ActorContext actor) {
        DeliveryRequestEntity d = transition(deliveryId, DeliveryStatus.FAILED, req, actor,
                NhumeEvents.DELIVERY_FAILED);
        raiseException(deliveryId, "DELIVERY_FAILED", "WARNING",
                req.reason() != null ? req.reason() : "Delivery failed", actor);
        return d;
    }

    @Transactional
    public DeliveryRequestEntity returnDelivery(UUID deliveryId, StatusChangeRequest req,
                                                 TrustLayerGuard.ActorContext actor) {
        return transition(deliveryId, DeliveryStatus.RETURNED, req, actor,
                NhumeEvents.DELIVERY_RETURN_COMPLETED);
    }

    @Transactional
    public DeliveryRequestEntity startPickup(UUID deliveryId, StatusChangeRequest req,
                                              TrustLayerGuard.ActorContext actor) {
        return transition(deliveryId, DeliveryStatus.EN_ROUTE_TO_PICKUP, req, actor,
                NhumeEvents.DELIVERY_PICKUP_STARTED);
    }

    @Transactional
    public DeliveryRequestEntity confirmPickup(UUID deliveryId, StatusChangeRequest req,
                                                TrustLayerGuard.ActorContext actor) {
        DeliveryRequestEntity d = transition(deliveryId, DeliveryStatus.PICKED_UP, req, actor,
                NhumeEvents.DELIVERY_PICKED_UP);
        // Marketplace pickup milestone → Msika Flow. Fire-and-forget: a failed
        // write-back never blocks the courier-facing pickup sign-off.
        try {
            writeBack.onPickedUp(d, actor);
        } catch (Exception e) {
            log.warn("Pickup write-back pass failed for delivery {}: {}",
                    d.getDeliveryId(), e.toString());
        }
        return d;
    }

    @Transactional
    public DeliveryRequestEntity startTransit(UUID deliveryId, StatusChangeRequest req,
                                               TrustLayerGuard.ActorContext actor) {
        return transition(deliveryId, DeliveryStatus.IN_TRANSIT, req, actor,
                NhumeEvents.DELIVERY_IN_TRANSIT);
    }

    @Transactional
    public DeliveryRequestEntity markDelivered(UUID deliveryId, StatusChangeRequest req,
                                                TrustLayerGuard.ActorContext actor) {
        // OF-B18 — a graded delivery may only be signed off DELIVERED against a
        // verified DELIVERY-stage proof that meets the required §12.4 grade.
        deliveryRepo.findById(deliveryId).ifPresent(this::assertHandoverProven);
        return transition(deliveryId, DeliveryStatus.DELIVERED, req, actor,
                NhumeEvents.DELIVERY_COMPLETED);
    }

    /** Fail-closed DELIVERED gate for graded order classes (OF-B18 §12.4). */
    private void assertHandoverProven(DeliveryRequestEntity d) {
        if (!HandoverGradePolicy.requiresGradedHandover(d)) {
            return;
        }
        zw.gov.mohcc.impilo.nhume.domain.HandoverVerificationGrade required =
                HandoverGradePolicy.requiredGrade(d);
        boolean proven = proofRepo.findByDeliveryIdOrderByCapturedAtAsc(d.getDeliveryId()).stream()
                .filter(p -> "DELIVERY".equals(p.getProofStage()) && p.isVerified())
                .map(p -> zw.gov.mohcc.impilo.nhume.domain.HandoverVerificationGrade
                        .parse(p.getVerificationGrade()))
                .anyMatch(g -> g != null && g.meets(required));
        if (!proven) {
            throw new IllegalStateException("HANDOVER_PROOF_REQUIRED: delivery "
                    + d.getDeliveryId() + " requires a verified proof at grade "
                    + required + " before DELIVERED sign-off");
        }
    }

    private DeliveryRequestEntity transition(UUID deliveryId, DeliveryStatus target,
                                              StatusChangeRequest req,
                                              TrustLayerGuard.ActorContext actor,
                                              String eventType) {
        DeliveryRequestEntity d = deliveryRepo.findById(deliveryId)
                .orElseThrow(() -> new DeliveryNotFoundException(deliveryId));
        DeliveryStatus current = parseStatus(d.getStatus());
        if (!DeliveryStatus.canTransition(current, target)) {
            throw new InvalidDeliveryTransitionException(d.getStatus(), target.name());
        }
        OffsetDateTime now = OffsetDateTime.now();
        String previous = d.getStatus();
        d.setStatus(target.name());
        d.setUpdatedAt(now);
        switch (target) {
            case SUBMITTED -> d.setSubmittedAt(now);
            case APPROVED -> d.setApprovedAt(now);
            case REJECTED -> d.setRejectedAt(now);
            case CANCELLED -> d.setCancelledAt(now);
            case DELIVERED -> d.setDeliveredAt(now);
            case FAILED -> d.setFailedAt(now);
            case CLOSED -> d.setClosedAt(now);
            default -> { /* no-op */ }
        }
        deliveryRepo.save(d);
        appendStatusEvent(d, previous, target.name(), actor, req != null ? req.reason() : null);
        Map<String, Object> payload = buildDeliveryStatePayload(d);
        payload.put("previous_status", previous);
        if (req != null && req.metadata() != null) payload.put("metadata", req.metadata());
        appendOutboxEvent(eventType, d, d.getTenantId(), d.getPodId(),
                d.getCorrelationId() != null ? d.getCorrelationId().toString() : null,
                "transition-" + UUID.randomUUID(), payload);
        recordAudit(d, "delivery.transition." + target.name().toLowerCase(Locale.ROOT), actor);
        dispatchNotification(d, "DELIVERY_" + target.name(),
                "nhume.delivery." + target.name().toLowerCase(Locale.ROOT),
                channelForRecipient(d));
        if (target == DeliveryStatus.DELIVERED) {
            runIntegrationWriteBacks(d, actor);
        } else if (target == DeliveryStatus.DELIVERY_ATTEMPTED
                || target == DeliveryStatus.RETURNED
                || target == DeliveryStatus.FAILED) {
            // OF-B17 §12.7/§12.8 — a marketplace commitment must see the honest
            // non-happy statuses too (attempted / returned → refund seam).
            runSelectionStatusWriteBack(d, target.name(), actor);
        }
        return d;
    }

    /**
     * Selection-linked status write-back. Failure NEVER blocks the courier flow
     * but is NEVER silent either: a failed callback is recorded as an
     * ops-visible delivery exception (§12.8 — no swallowed-to-warning losses).
     */
    private void runSelectionStatusWriteBack(DeliveryRequestEntity d, String status,
                                             TrustLayerGuard.ActorContext actor) {
        try {
            var outcomes = writeBack.onDeliveryStatus(d, status, actor);
            if (outcomes.isEmpty()) {
                return;
            }
            d.setMetadataJson(writeBack.mergeOutcomes(d.getMetadataJson(), outcomes));
            deliveryRepo.save(d);
            boolean anyFailed = outcomes.values().stream()
                    .anyMatch(o -> o.status() == zw.gov.mohcc.impilo.nhume.integration.writeback.WriteBackOutcome.Status.FAILED);
            if (anyFailed) {
                raiseException(d.getDeliveryId(), "SELECTION_WRITEBACK_FAILED", "WARNING",
                        "Marketplace selection could not be told about " + status
                                + "; see links_writeback — reconcile before closing", actor);
            }
        } catch (Exception e) {
            log.warn("Selection status write-back pass failed for delivery {}: {}",
                    d.getDeliveryId(), e.toString());
        }
    }

    /**
     * Drop-off sign-off write-backs: confirm the movement with the services that
     * own the cargo truth (OROS/MADI/PCT via metadata.links). Outcomes — including
     * failures — are recorded on the delivery and in the outbox; a failed
     * write-back never rolls back the courier-facing sign-off.
     */
    private void runIntegrationWriteBacks(DeliveryRequestEntity d, TrustLayerGuard.ActorContext actor) {
        try {
            var outcomes = writeBack.onDelivered(d, actor);
            if (outcomes.isEmpty()) {
                return;
            }
            d.setMetadataJson(writeBack.mergeOutcomes(d.getMetadataJson(), outcomes));
            deliveryRepo.save(d);
            Map<String, Object> payload = new LinkedHashMap<>();
            outcomes.forEach((system, outcome) -> payload.put(system, Map.of(
                    "status", outcome.status().name(),
                    "detail", outcome.detail() == null ? "" : outcome.detail())));
            appendOutboxEvent(NhumeEvents.DELIVERY_WRITEBACK, d, d.getTenantId(), d.getPodId(),
                    d.getCorrelationId() != null ? d.getCorrelationId().toString() : null,
                    "writeback-" + d.getDeliveryId(), payload);
            recordAudit(d, "delivery.integration.writeback", actor);
            boolean anyFailed = outcomes.values().stream()
                    .anyMatch(o -> o.status() == zw.gov.mohcc.impilo.nhume.integration.writeback.WriteBackOutcome.Status.FAILED);
            if (anyFailed) {
                raiseException(d.getDeliveryId(), "INTEGRATION_WRITEBACK_FAILED", "WARNING",
                        "One or more owning-service confirmations failed on drop-off; see links_writeback",
                        actor);
            }
        } catch (Exception e) {
            log.warn("Integration write-back pass failed for delivery {}: {}", d.getDeliveryId(), e.toString());
        }
    }

    // ─── Assignment ─────────────────────────────────────────────────────────

    @Transactional
    public DeliveryAssignmentEntity assign(UUID deliveryId, AssignDeliveryRequest req,
                                            TrustLayerGuard.ActorContext actor,
                                            String idempotencyKey) {
        DeliveryRequestEntity delivery = deliveryRepo.findById(deliveryId)
                .orElseThrow(() -> new DeliveryNotFoundException(deliveryId));
        DeliveryStatus current = parseStatus(delivery.getStatus());
        if (!DeliveryStatus.canTransition(current, DeliveryStatus.ASSIGNED)) {
            throw new InvalidDeliveryTransitionException(delivery.getStatus(),
                    DeliveryStatus.ASSIGNED.name());
        }
        // Supersede any open assignment.
        assignmentRepo.findFirstByDeliveryIdAndSupersededAtIsNullOrderByAssignedAtDesc(deliveryId)
                .ifPresent(prev -> {
                    prev.setSupersededAt(OffsetDateTime.now());
                    assignmentRepo.save(prev);
                });

        DeliveryAssignmentEntity assignment = new DeliveryAssignmentEntity();
        assignment.setAssignmentId(UUID.randomUUID());
        assignment.setDeliveryId(deliveryId);
        assignment.setTenantId(delivery.getTenantId());
        assignment.setCourierId(req.courierId());
        assignment.setAssetId(req.assetId());
        assignment.setIntegrationProvider(req.integrationProvider());
        assignment.setIntegrationRef(req.integrationRef());
        String mode = req.modeCategory();
        if (mode == null && req.assetId() != null) {
            mode = assetRepo.findById(req.assetId()).map(FleetAssetEntity::getModeCategory).orElse("OTHER");
        }
        assignment.setModeCategory(orDefault(mode, "OTHER"));
        assignment.setAssignmentKind(orDefault(req.assignmentKind(), "MANUAL"));
        assignment.setAssignedBy(actor != null ? actor.actorId() : null);
        assignment.setNotes(req.notes());
        assignmentRepo.save(assignment);

        // Update delivery state to ASSIGNED.
        delivery.setStatus(DeliveryStatus.ASSIGNED.name());
        delivery.setUpdatedAt(OffsetDateTime.now());
        deliveryRepo.save(delivery);

        // Mark courier / asset as busy where applicable.
        if (req.courierId() != null) {
            courierRepo.findById(req.courierId()).ifPresent(c -> {
                c.setCurrentStatus("ON_DUTY");
                courierRepo.save(c);
            });
        }
        if (req.assetId() != null) {
            assetRepo.findById(req.assetId()).ifPresent(a -> {
                a.setAvailabilityStatus("IN_USE");
                assetRepo.save(a);
            });
        }

        appendStatusEvent(delivery, current.name(), DeliveryStatus.ASSIGNED.name(), actor, "Assignment created");
        Map<String, Object> payload = buildDeliveryStatePayload(delivery);
        payload.put("assignment_id", assignment.getAssignmentId().toString());
        payload.put("courier_id", req.courierId() != null ? req.courierId().toString() : null);
        payload.put("asset_id", req.assetId() != null ? req.assetId().toString() : null);
        payload.put("mode_category", assignment.getModeCategory());
        appendOutboxEvent(NhumeEvents.DELIVERY_ASSIGNED, delivery, delivery.getTenantId(),
                delivery.getPodId(),
                delivery.getCorrelationId() != null ? delivery.getCorrelationId().toString() : null,
                idempotencyKey, payload);
        recordAudit(delivery, "delivery.assigned", actor);
        dispatchNotification(delivery, "DELIVERY_ASSIGNED", "nhume.delivery.assigned",
                channelForRecipient(delivery));
        if (req.courierId() != null) {
            notificationRepo.save(notificationOf(delivery, "DELIVERY_ASSIGNED", "PUSH",
                    req.courierId().toString(), "nhume.delivery.assigned"));
        }
        return assignment;
    }

    @Transactional
    public DeliveryAssignmentEntity accept(UUID deliveryId,
                                            TrustLayerGuard.ActorContext actor) {
        DeliveryAssignmentEntity assignment = assignmentRepo
                .findFirstByDeliveryIdAndSupersededAtIsNullOrderByAssignedAtDesc(deliveryId)
                .orElseThrow(() -> new DeliveryNotFoundException(deliveryId));
        assignment.setAcceptedAt(OffsetDateTime.now());
        assignment.setStatus("ACCEPTED");
        assignmentRepo.save(assignment);

        DeliveryRequestEntity d = deliveryRepo.findById(deliveryId)
                .orElseThrow(() -> new DeliveryNotFoundException(deliveryId));
        DeliveryStatus current = parseStatus(d.getStatus());
        if (DeliveryStatus.canTransition(current, DeliveryStatus.ACCEPTED)) {
            d.setStatus(DeliveryStatus.ACCEPTED.name());
            d.setUpdatedAt(OffsetDateTime.now());
            deliveryRepo.save(d);
            appendStatusEvent(d, current.name(), DeliveryStatus.ACCEPTED.name(), actor,
                    "Assignment accepted");
        }
        appendOutboxEvent(NhumeEvents.DELIVERY_ASSIGNMENT_ACCEPTED, d, d.getTenantId(),
                d.getPodId(), null, "accept-" + UUID.randomUUID(), buildDeliveryStatePayload(d));
        recordAudit(d, "delivery.assignment.accepted", actor);
        return assignment;
    }

    @Transactional
    public DeliveryAssignmentEntity decline(UUID deliveryId, String reason,
                                             TrustLayerGuard.ActorContext actor) {
        DeliveryAssignmentEntity assignment = assignmentRepo
                .findFirstByDeliveryIdAndSupersededAtIsNullOrderByAssignedAtDesc(deliveryId)
                .orElseThrow(() -> new DeliveryNotFoundException(deliveryId));
        assignment.setDeclinedAt(OffsetDateTime.now());
        assignment.setDeclineReason(reason);
        assignment.setSupersededAt(OffsetDateTime.now());
        assignment.setStatus("DECLINED");
        assignmentRepo.save(assignment);

        DeliveryRequestEntity d = deliveryRepo.findById(deliveryId)
                .orElseThrow(() -> new DeliveryNotFoundException(deliveryId));
        d.setStatus(DeliveryStatus.DISPATCH_PENDING.name());
        d.setUpdatedAt(OffsetDateTime.now());
        deliveryRepo.save(d);
        appendStatusEvent(d, DeliveryStatus.ASSIGNED.name(),
                DeliveryStatus.DISPATCH_PENDING.name(), actor,
                "Assignment declined: " + (reason == null ? "no reason supplied" : reason));
        recordAudit(d, "delivery.assignment.declined", actor);
        return assignment;
    }

    // ─── Tracking ───────────────────────────────────────────────────────────

    @Transactional
    public DeliveryTrackingEventEntity recordTracking(UUID deliveryId, TrackingUpdateRequest req,
                                                       TrustLayerGuard.ActorContext actor) {
        DeliveryRequestEntity d = deliveryRepo.findById(deliveryId)
                .orElseThrow(() -> new DeliveryNotFoundException(deliveryId));
        DeliveryTrackingEventEntity e = new DeliveryTrackingEventEntity();
        e.setDeliveryId(deliveryId);
        e.setCapturedAt(req.capturedAt() != null ? req.capturedAt() : OffsetDateTime.now());
        e.setEventKind(orDefault(req.eventKind(), "LOCATION"));
        e.setLat(req.lat());
        e.setLng(req.lng());
        e.setAccuracyM(req.accuracyM());
        e.setCourierId(req.courierId());
        e.setAssetId(req.assetId());
        e.setSource(orDefault(req.source(), "NHUME"));
        e.setPayloadJson(toJson(req.payload()));
        trackingRepo.save(e);

        if (req.assetId() != null && req.lat() != null && req.lng() != null) {
            assetRepo.findById(req.assetId()).ifPresent(a -> {
                a.setLastLat(req.lat());
                a.setLastLng(req.lng());
                a.setLastSeenAt(OffsetDateTime.now());
                assetRepo.save(a);
            });
        }

        Map<String, Object> payload = buildDeliveryStatePayload(d);
        payload.put("event_kind", e.getEventKind());
        payload.put("lat", req.lat());
        payload.put("lng", req.lng());
        payload.put("source", e.getSource());
        appendOutboxEvent(NhumeEvents.DELIVERY_LOCATION_UPDATED, d, d.getTenantId(), d.getPodId(),
                d.getCorrelationId() != null ? d.getCorrelationId().toString() : null,
                "track-" + UUID.randomUUID(), payload);
        return e;
    }

    // ─── Proof of delivery ──────────────────────────────────────────────────

    @Transactional
    public DeliveryProofEntity captureProof(UUID deliveryId, ProofRequest req,
                                             TrustLayerGuard.ActorContext actor) {
        DeliveryRequestEntity d = deliveryRepo.findById(deliveryId)
                .orElseThrow(() -> new DeliveryNotFoundException(deliveryId));
        // Optional live biometric recipient-verification at handover. MATCH → mark the proof
        // biometric-verified; NO_MATCH → reject the handover (nothing persisted); UNAVAILABLE /
        // NO_REFERENCE → fall back to the declared proof method so an outage never blocks delivery.
        String proofMethod = req.proofMethod();
        String biometricRef = req.biometricRef();
        boolean biometricVerified = false;
        if (req.biometricProbeBase64() != null && !req.biometricProbeBase64().isBlank()
                && req.biometricSubjectRef() != null && !req.biometricSubjectRef().isBlank()) {
            String modality = req.biometricModality() != null && !req.biometricModality().isBlank()
                    ? req.biometricModality() : "FINGERPRINT";
            var decision = biometricVerification.verify(
                    req.biometricSubjectRef(), modality, req.biometricProbeBase64());
            if (decision.isMatch()) {
                proofMethod = "BIOMETRIC";
                biometricRef = req.biometricSubjectRef();
                biometricVerified = true;
            } else if (!"UNAVAILABLE".equals(decision.result())
                    && !"NO_REFERENCE".equals(decision.result())) {
                throw new IllegalStateException(
                        "Biometric recipient verification failed — handover rejected");
            }
            // UNAVAILABLE / NO_REFERENCE → fall through on the declared proof method (unchanged).
        }

        // OF-B18 §12.4 — grade-ladder verdict for DELIVERY-stage handovers.
        // (PICKUP-stage collection sign-offs ride the pharmacy-side OF-B16 ladder.)
        String stage = orDefault(req.proofStage(), "DELIVERY");
        HandoverGradePolicy.Verdict verdict = "DELIVERY".equals(stage)
                ? HandoverGradePolicy.evaluate(d, req, biometricVerified)
                : new HandoverGradePolicy.Verdict(true, null, null, null);

        DeliveryProofEntity proof = new DeliveryProofEntity();
        proof.setProofId(UUID.randomUUID());
        proof.setDeliveryId(deliveryId);
        proof.setProofStage(stage);
        proof.setProofMethod(proofMethod);
        proof.setCapturedBy(actor != null ? actor.actorId() : req.capturedBy());
        proof.setOtpCode(req.otpCode());
        proof.setSignatureUri(req.signatureUri());
        proof.setPhotoUri(req.photoUri());
        proof.setBiometricRef(biometricRef);
        proof.setGeofenceMatch(req.geofenceMatch());
        proof.setLockerCode(req.lockerCode());
        proof.setWebhookRef(req.webhookRef());
        proof.setMetadataJson(toJson(req.metadata()));
        proof.setVerified(verdict.pass());
        proof.setVerificationGrade(verdict.achievedGradeName());
        proof.setReceiverName(req.receiverName());
        proof.setNamedRecipientMatch(verdict.namedRecipientMatch());
        proof.setIdDocumentType(req.idDocumentType());
        proof.setIdDocumentRef(req.idDocumentRef());
        proof.setFailureReason(verdict.failureReason());
        proofRepo.save(proof);

        Map<String, Object> payload = buildDeliveryStatePayload(d);
        payload.put("proof_id", proof.getProofId().toString());
        payload.put("proof_method", proof.getProofMethod());
        payload.put("proof_stage", proof.getProofStage());
        payload.put("verified", proof.isVerified());
        payload.put("verification_grade", proof.getVerificationGrade());
        if (proof.getFailureReason() != null) {
            payload.put("failure_reason", proof.getFailureReason());
        }
        appendOutboxEvent(NhumeEvents.DELIVERY_PROOF_CAPTURED, d, d.getTenantId(), d.getPodId(),
                d.getCorrelationId() != null ? d.getCorrelationId().toString() : null,
                "proof-" + UUID.randomUUID(), payload);
        recordAudit(d, "delivery.proof.captured", actor);

        if (!verdict.pass()) {
            return handleFailedHandover(d, proof, actor);
        }

        if (Boolean.TRUE.equals(req.markDelivered())
                && DeliveryStatus.canTransition(parseStatus(d.getStatus()), DeliveryStatus.DELIVERED)) {
            // The just-captured verified proof IS the §12.4 evidence — transition
            // directly (the public markDelivered gate re-queries persisted proofs).
            transition(deliveryId, DeliveryStatus.DELIVERED,
                    new StatusChangeRequest("Auto-marked delivered after proof capture", null),
                    actor, NhumeEvents.DELIVERY_COMPLETED);
        }
        return proof;
    }

    /**
     * OF-B18 fail-closed handover refusal (§12.4/§12.7): the failed proof is
     * kept as evidence, a HANDOVER_ATTEMPTED custody event is appended, the
     * delivery transitions to DELIVERY_ATTEMPTED, and once the bounded
     * reattempt budget is exhausted the delivery is RETURNED — never a silent
     * retry loop, never a DELIVERED without proof.
     */
    private DeliveryProofEntity handleFailedHandover(DeliveryRequestEntity d, DeliveryProofEntity proof,
                                                     TrustLayerGuard.ActorContext actor) {
        int attempts = d.getHandoverAttempts() + 1;
        d.setHandoverAttempts(attempts);
        d.setUpdatedAt(OffsetDateTime.now());
        deliveryRepo.save(d);

        // Custody truth: the attempted handover is an append-only chain event.
        ChainOfCustodyEventEntity coc = new ChainOfCustodyEventEntity();
        coc.setCocId(UUID.randomUUID());
        coc.setDeliveryId(d.getDeliveryId());
        coc.setSequenceNo(custodyRepo.findByDeliveryIdOrderBySequenceNoAsc(d.getDeliveryId()).size() + 1);
        coc.setEventKind("HANDOVER_ATTEMPTED");
        coc.setCustodyHolder("COURIER");
        coc.setHandoverNotes("Handover verification failed: " + proof.getFailureReason()
                + " (attempt " + attempts + "/" + d.getMaxHandoverAttempts() + ")");
        coc.setVerification(proof.getVerificationGrade());
        coc.setActorId(actor != null ? actor.actorId() : null);
        coc.setActorType(actor != null ? actor.actorType() : null);
        custodyRepo.save(coc);

        raiseException(d.getDeliveryId(), "HANDOVER_VERIFICATION_FAILED", "WARNING",
                "Handover refused (" + proof.getFailureReason() + "), attempt "
                        + attempts + " of " + d.getMaxHandoverAttempts(), actor);

        DeliveryStatus current = parseStatus(d.getStatus());
        if (current != DeliveryStatus.DELIVERY_ATTEMPTED
                && DeliveryStatus.canTransition(current, DeliveryStatus.DELIVERY_ATTEMPTED)) {
            transition(d.getDeliveryId(), DeliveryStatus.DELIVERY_ATTEMPTED,
                    new StatusChangeRequest(proof.getFailureReason(), null), actor,
                    NhumeEvents.DELIVERY_ATTEMPTED);
        }
        if (attempts >= d.getMaxHandoverAttempts()) {
            // §12.7 — reattempt budget exhausted: return chain begins.
            transition(d.getDeliveryId(), DeliveryStatus.RETURNED,
                    new StatusChangeRequest("Handover reattempt budget exhausted after "
                            + attempts + " attempts — returning to sender", null),
                    actor, NhumeEvents.DELIVERY_RETURN_COMPLETED);
        }
        return proof;
    }

    // ─── Chain of custody ───────────────────────────────────────────────────

    @Transactional
    public ChainOfCustodyEventEntity recordCustody(UUID deliveryId, ChainOfCustodyRequest req,
                                                    TrustLayerGuard.ActorContext actor) {
        DeliveryRequestEntity d = deliveryRepo.findById(deliveryId)
                .orElseThrow(() -> new DeliveryNotFoundException(deliveryId));
        List<ChainOfCustodyEventEntity> existing =
                custodyRepo.findByDeliveryIdOrderBySequenceNoAsc(deliveryId);
        int nextSeq = existing.size() + 1;
        ChainOfCustodyEventEntity coc = new ChainOfCustodyEventEntity();
        coc.setCocId(UUID.randomUUID());
        coc.setDeliveryId(deliveryId);
        coc.setPackageId(req.packageId());
        coc.setSequenceNo(nextSeq);
        coc.setEventKind(req.eventKind());
        coc.setCustodyHolder(req.custodyHolder());
        coc.setCustodyLocation(req.custodyLocation());
        coc.setSealId(req.sealId());
        coc.setTemperatureC(req.temperatureC());
        coc.setHandoverNotes(req.handoverNotes());
        coc.setVerification(req.verification());
        coc.setActorId(actor != null ? actor.actorId() : null);
        coc.setActorType(actor != null ? actor.actorType() : null);
        coc.setDeviceUsed(req.deviceUsed());
        coc.setEvidenceUri(req.evidenceUri());
        coc.setExceptionFlags(toJson(req.exceptionFlags()));
        custodyRepo.save(coc);

        Map<String, Object> payload = buildDeliveryStatePayload(d);
        payload.put("coc_id", coc.getCocId().toString());
        payload.put("event_kind", coc.getEventKind());
        payload.put("sequence_no", nextSeq);
        appendOutboxEvent(NhumeEvents.DELIVERY_COC_UPDATED, d, d.getTenantId(), d.getPodId(),
                d.getCorrelationId() != null ? d.getCorrelationId().toString() : null,
                "coc-" + UUID.randomUUID(), payload);
        recordAudit(d, "delivery.custody.recorded", actor);

        if (req.temperatureC() != null && d.isColdChainRequired()
                && (req.temperatureC().doubleValue() > 8.0 || req.temperatureC().doubleValue() < 2.0)) {
            DeliveryExceptionEntity ex = raiseException(deliveryId, "COLD_CHAIN_BREACH", "CRITICAL",
                    "Cold-chain breach detected (" + req.temperatureC() + "°C)", actor);
            appendOutboxEvent(NhumeEvents.DELIVERY_COLD_CHAIN_BREACH, d, d.getTenantId(),
                    d.getPodId(), null, "coc-breach-" + UUID.randomUUID(),
                    Map.of("exception_id", ex.getExceptionId().toString(),
                            "temperature_c", req.temperatureC()));
        }
        return coc;
    }

    // ─── Exceptions ─────────────────────────────────────────────────────────

    @Transactional
    public DeliveryExceptionEntity raiseException(UUID deliveryId, String kind, String severity,
                                                    String summary,
                                                    TrustLayerGuard.ActorContext actor) {
        DeliveryExceptionEntity ex = new DeliveryExceptionEntity();
        ex.setExceptionId(UUID.randomUUID());
        ex.setDeliveryId(deliveryId);
        ex.setExceptionKind(kind);
        ex.setSeverity(orDefault(severity, "WARNING"));
        ex.setSummary(orDefault(summary, "Delivery exception"));
        ex.setRaisedBy(actor != null ? actor.actorId() : null);
        exceptionRepo.save(ex);

        DeliveryRequestEntity d = deliveryRepo.findById(deliveryId).orElse(null);
        if (d != null) {
            appendOutboxEvent(NhumeEvents.DELIVERY_EXCEPTION, d, d.getTenantId(), d.getPodId(),
                    d.getCorrelationId() != null ? d.getCorrelationId().toString() : null,
                    "exception-" + UUID.randomUUID(),
                    Map.of("exception_id", ex.getExceptionId().toString(),
                            "kind", kind, "severity", ex.getSeverity(), "summary", ex.getSummary()));
            recordAudit(d, "delivery.exception.raised:" + kind, actor);
            dispatchNotification(d, "DELIVERY_EXCEPTION_" + kind,
                    "nhume.delivery.exception", "DISPATCHER");
        }
        return ex;
    }

    private static final java.util.Set<String> LINK_KEYS = java.util.Set.of(
            "daidzaiIncidentRef", "orosOrderRef", "madiOrderRef", "pctReferralRef", "duraRequisitionRef",
            // OF-B17 — marketplace-commitment linkage (selection + request + dispense episode)
            "msikaFlowSelectionRef", "msikaFlowRequestRef", "dispenseEpisodeRef");

    /** Attach/update a whitelisted integration link in metadata.links (audited). */
    @Transactional
    public DeliveryRequestEntity addIntegrationLink(UUID deliveryId, String key, String ref,
                                                    TrustLayerGuard.ActorContext actor) {
        if (key == null || !LINK_KEYS.contains(key)) {
            throw new IllegalArgumentException("Unsupported link key: " + key);
        }
        if (ref == null || ref.isBlank()) {
            throw new IllegalArgumentException("Link ref is required");
        }
        DeliveryRequestEntity d = deliveryRepo.findById(deliveryId)
                .orElseThrow(() -> new DeliveryNotFoundException(deliveryId));
        try {
            Map<String, Object> metadata = d.getMetadataJson() == null || d.getMetadataJson().isBlank()
                    ? new LinkedHashMap<>()
                    : objectMapper.readValue(d.getMetadataJson(),
                            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            Object linksRaw = metadata.get("links");
            Map<String, Object> links = linksRaw instanceof Map<?, ?> m
                    ? new LinkedHashMap<>(m.entrySet().stream().collect(
                            java.util.stream.Collectors.toMap(e -> e.getKey().toString(), Map.Entry::getValue)))
                    : new LinkedHashMap<>();
            links.put(key, ref);
            metadata.put("links", links);
            d.setMetadataJson(objectMapper.writeValueAsString(metadata));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not update delivery links", e);
        }
        d.setUpdatedAt(OffsetDateTime.now());
        deliveryRepo.save(d);
        recordAudit(d, "delivery.link.attached:" + key, actor);
        return d;
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private void applyRequest(DeliveryRequestEntity d, CreateDeliveryRequest req) {
        d.setDeliveryType(orDefault(req.deliveryType(), DeliveryType.GENERAL.name()));
        d.setPriority(orDefault(req.priority(), DeliveryPriority.STANDARD.name()));
        d.setRequestingActorId(req.requestingActorId());
        d.setRequestingActorType(req.requestingActorType());
        d.setRequestingOrgId(req.requestingOrgId());
        d.setRequestingFacilityId(req.requestingFacilityId());
        if (req.origin() != null) {
            d.setOriginKind(orDefault(req.origin().kind(), "ADDRESS"));
            d.setOriginRef(orDefault(req.origin().ref(), "ANON"));
            d.setOriginLabel(req.origin().label());
            d.setOriginAddressLine(req.origin().address());
            d.setOriginLat(req.origin().lat());
            d.setOriginLng(req.origin().lng());
        } else {
            d.setOriginKind("ADDRESS");
            d.setOriginRef("ANON");
        }
        if (req.destination() != null) {
            d.setDestinationKind(orDefault(req.destination().kind(), "ADDRESS"));
            d.setDestinationRef(orDefault(req.destination().ref(), "ANON"));
            d.setDestinationLabel(req.destination().label());
            d.setDestinationAddressLine(req.destination().address());
            d.setDestinationLat(req.destination().lat());
            d.setDestinationLng(req.destination().lng());
        } else {
            d.setDestinationKind("ADDRESS");
            d.setDestinationRef("ANON");
        }
        if (req.recipient() != null) {
            d.setRecipientKind(req.recipient().kind());
            d.setRecipientRef(req.recipient().ref());
            d.setRecipientName(req.recipient().name());
            d.setRecipientPhone(req.recipient().phone());
            d.setRecipientEmail(req.recipient().email());
            d.setRecipientLanguage(req.recipient().language());
        }
        d.setClinicalContextRef(req.clinicalContextRef());
        d.setProgrammeContextRef(req.programmeContextRef());
        d.setTelehealthSessionRef(req.telehealthSessionRef());
        d.setMarketplaceOrderRef(req.marketplaceOrderRef());
        d.setPaymentPath(orDefault(req.paymentPath(), "NONE"));
        d.setPaymentReference(req.paymentReference());
        d.setDeclaredValueCents(req.declaredValueCents());
        d.setCurrency(req.currency());
        if (req.consentRequired() != null) d.setConsentRequired(req.consentRequired());
        d.setIdentityVerification(orDefault(req.identityVerification(), "NONE"));
        if (req.coldChainRequired() != null) d.setColdChainRequired(req.coldChainRequired());
        if (req.controlledItem() != null) d.setControlledItem(req.controlledItem());
        if (req.fragile() != null) d.setFragile(req.fragile());
        if (req.hazardous() != null) d.setHazardous(req.hazardous());
        if (req.biohazard() != null) d.setBiohazard(req.biohazard());
        if (req.specimen() != null) d.setSpecimen(req.specimen());
        if (req.chainOfCustodyRequired() != null) d.setChainOfCustodyRequired(req.chainOfCustodyRequired());
        if (req.returnRequired() != null) d.setReturnRequired(req.returnRequired());
        if (req.requiredPodGrade() != null && !req.requiredPodGrade().isBlank()) {
            d.setRequiredPodGrade(req.requiredPodGrade());
        }
        if (req.namedRecipientRequired() != null) d.setNamedRecipientRequired(req.namedRecipientRequired());
        d.setRequiredBy(req.requiredBy());
        d.setPickupWindowStart(req.pickupWindowStart());
        d.setPickupWindowEnd(req.pickupWindowEnd());
        d.setDeliveryWindowStart(req.deliveryWindowStart());
        d.setDeliveryWindowEnd(req.deliveryWindowEnd());
        if (req.allowedModes() != null) d.setAllowedModesJson(toJson(req.allowedModes()));
        d.setPolicyId(req.policyId());
        d.setSlaId(req.slaId());
        d.setNotes(req.notes());
        if (req.metadata() != null) d.setMetadataJson(toJson(req.metadata()));
    }

    private void applyPolicyDefaults(DeliveryRequestEntity d, UUID tenantId) {
        if (d.getPolicyId() == null) {
            policyRepo.findByTenantIdAndDeliveryTypeAndActiveTrue(tenantId, d.getDeliveryType())
                    .ifPresent(p -> {
                        d.setPolicyId(p.getPolicyId());
                        if (!d.isConsentRequired() && p.isRequiresConsent()) d.setConsentRequired(true);
                        if (!d.isColdChainRequired() && p.isColdChainRequired()) d.setColdChainRequired(true);
                        if (!d.isChainOfCustodyRequired() && p.isRequiresChainOfCustody())
                            d.setChainOfCustodyRequired(true);
                        if ("NONE".equals(d.getIdentityVerification())
                                && !"NONE".equals(p.getRequiresIdentityVerify())) {
                            d.setIdentityVerification(p.getRequiresIdentityVerify());
                        }
                    });
        }
    }

    private void computeSlaDueAt(DeliveryRequestEntity d) {
        if (d.getSlaDueAt() != null) return;
        Integer minutes = null;
        if (d.getPolicyId() != null) {
            DeliveryPolicyEntity p = policyRepo.findById(d.getPolicyId()).orElse(null);
            if (p != null) minutes = p.getSlaTargetMinutes();
        }
        if (minutes == null) {
            DeliveryPriority pr = DeliveryPriority.valueOf(d.getPriority());
            minutes = switch (pr) {
                case EMERGENCY -> 60;
                case URGENT -> 120;
                case STANDARD -> 240;
                case LOW -> 720;
            };
        }
        d.setSlaDueAt(OffsetDateTime.now().plus(minutes, ChronoUnit.MINUTES));
    }

    private void appendStatusEvent(DeliveryRequestEntity d, String previous, String next,
                                    TrustLayerGuard.ActorContext actor, String reason) {
        DeliveryStatusEventEntity e = new DeliveryStatusEventEntity();
        e.setDeliveryId(d.getDeliveryId());
        e.setPreviousStatus(previous);
        e.setNewStatus(next);
        e.setActorId(actor != null ? actor.actorId() : null);
        e.setActorType(actor != null ? actor.actorType() : null);
        e.setReason(reason);
        statusEventRepo.save(e);
    }

    private void appendOutboxEvent(String eventType, DeliveryRequestEntity d, UUID tenantId,
                                    String podId, String correlationId, String idempotencyKey,
                                    Map<String, Object> payload) {
        OutboxEventEntity outbox = new OutboxEventEntity();
        outbox.setAggregateType(AGGREGATE);
        outbox.setAggregateId(d.getDeliveryId().toString());
        outbox.setEventType(eventType);
        outbox.setSchemaVersion("1");
        outbox.setCorrelationId(correlationId != null ? safeUuid(correlationId) : null);
        outbox.setCausationId(correlationId != null ? safeUuid(correlationId) : null);
        outbox.setIdempotencyKey(idempotencyKey != null ? idempotencyKey
                : ("auto-" + UUID.randomUUID()));
        outbox.setProducer(PRODUCER);
        outbox.setTenantId(tenantId);
        outbox.setPodId(podId != null ? podId : "national-spine");
        outbox.setSubjectId(d.getDeliveryId().toString());
        outbox.setSubjectType(AGGREGATE);
        outbox.setOccurredAt(OffsetDateTime.now());
        outbox.setPayloadJson(toJson(payload));
        outbox.setPartitionKey(d.getDeliveryId().toString());
        outboxRepo.save(outbox);
    }

    private void recordAudit(DeliveryRequestEntity d, String action, TrustLayerGuard.ActorContext actor) {
        DeliveryAuditEventEntity a = new DeliveryAuditEventEntity();
        a.setDeliveryId(d.getDeliveryId());
        a.setAggregateType(AGGREGATE);
        a.setAggregateId(d.getDeliveryId().toString());
        a.setAction(action);
        if (actor != null) {
            a.setActorId(actor.actorId());
            a.setActorType(actor.actorType());
            a.setPurposeOfUse(actor.purposeOfUse());
            a.setFacilityId(actor.facilityId());
            a.setWorkspaceId(actor.workspaceId());
            a.setShiftId(actor.shiftId());
            a.setDeviceFingerprint(actor.deviceFingerprint());
            if (actor.base() != null && actor.base().correlationId() != null) {
                a.setCorrelationId(safeUuid(actor.base().correlationId()));
            }
        }
        auditRepo.save(a);
    }

    private void dispatchNotification(DeliveryRequestEntity d, String eventCode,
                                       String templateCode, String channel) {
        String recipient = recipientFor(d, channel);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reference_code", d.getReferenceCode());
        data.put("delivery_id", d.getDeliveryId().toString());
        data.put("status", d.getStatus());
        data.put("recipient_name", d.getRecipientName());
        data.put("destination_label", d.getDestinationLabel());
        // OF-B18 — the handover OTP rides ONLY the recipient's own channel
        // (SMS/EMAIL/IN_APP); dispatcher/courier surfaces never receive it.
        if (d.getHandoverOtp() != null
                && ("SMS".equalsIgnoreCase(channel) || "EMAIL".equalsIgnoreCase(channel)
                        || "IN_APP".equalsIgnoreCase(channel) || "WHATSAPP".equalsIgnoreCase(channel))) {
            data.put("handover_otp", d.getHandoverOtp());
        }
        CommsHubClient.DispatchResult result = commsHub.dispatch(
                eventCode, channel, recipient, templateCode, data);
        notificationRepo.save(notificationOf(d, eventCode, channel, recipient, templateCode,
                result.providerRef(), result.state()));
    }

    private DeliveryNotificationEventEntity notificationOf(DeliveryRequestEntity d, String eventCode,
                                                            String channel, String recipient,
                                                            String templateCode) {
        return notificationOf(d, eventCode, channel, recipient, templateCode, null, "QUEUED");
    }

    private DeliveryNotificationEventEntity notificationOf(DeliveryRequestEntity d, String eventCode,
                                                            String channel, String recipient,
                                                            String templateCode, String providerRef,
                                                            String state) {
        DeliveryNotificationEventEntity n = new DeliveryNotificationEventEntity();
        n.setDeliveryId(d.getDeliveryId());
        n.setEventCode(eventCode);
        n.setChannel(channel);
        n.setRecipientRef(recipient);
        n.setTemplateCode(templateCode);
        n.setDeliveryState(state != null ? state : "QUEUED");
        n.setProviderRef(providerRef);
        return n;
    }

    private String recipientFor(DeliveryRequestEntity d, String channel) {
        if ("SMS".equalsIgnoreCase(channel) || "WHATSAPP".equalsIgnoreCase(channel)
                || "VOICE".equalsIgnoreCase(channel)) {
            return d.getRecipientPhone();
        }
        if ("EMAIL".equalsIgnoreCase(channel)) return d.getRecipientEmail();
        if ("PUSH".equalsIgnoreCase(channel) || "IN_APP".equalsIgnoreCase(channel)) {
            return d.getRecipientRef();
        }
        if ("DISPATCHER".equalsIgnoreCase(channel)) return "dispatcher-broadcast";
        return d.getRecipientRef() != null ? d.getRecipientRef() : d.getRecipientPhone();
    }

    private String channelForRecipient(DeliveryRequestEntity d) {
        if (d.getRecipientPhone() != null && !d.getRecipientPhone().isBlank()) return "SMS";
        if (d.getRecipientEmail() != null && !d.getRecipientEmail().isBlank()) return "EMAIL";
        return "IN_APP";
    }

    public Map<String, Object> buildDeliveryStatePayload(DeliveryRequestEntity d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("delivery_id", d.getDeliveryId().toString());
        m.put("tenant_id", d.getTenantId().toString());
        m.put("reference_code", d.getReferenceCode());
        m.put("status", d.getStatus());
        m.put("priority", d.getPriority());
        m.put("delivery_type", d.getDeliveryType());
        m.put("request_source", d.getRequestSource());
        m.put("origin_label", d.getOriginLabel());
        m.put("destination_label", d.getDestinationLabel());
        m.put("recipient_name", d.getRecipientName());
        m.put("sla_due_at", d.getSlaDueAt() != null ? d.getSlaDueAt().toString() : null);
        m.put("cold_chain_required", d.isColdChainRequired());
        m.put("chain_of_custody_required", d.isChainOfCustodyRequired());
        m.put("is_demo", d.isDemo());
        return m;
    }

    private static DeliveryStatus parseStatus(String s) {
        try { return DeliveryStatus.valueOf(s); } catch (Exception e) { return DeliveryStatus.DRAFT; }
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static UUID safeUuid(String s) {
        try { return UUID.fromString(s); } catch (Exception e) { return null; }
    }

    private String generateReference() {
        String suffix = String.format(Locale.ROOT, "%04d", ThreadLocalRandom.current().nextInt(10_000));
        return "NHM-" + System.currentTimeMillis() % 1_000_000L + "-" + suffix;
    }

    private String toJson(Object o) {
        if (o == null) return "{}";
        try { return objectMapper.writeValueAsString(o); }
        catch (JsonProcessingException e) { throw new RuntimeException("Failed to serialize JSON", e); }
    }

    /** Read access to Ndila for controllers that surface map metadata. */
    public NdilaClient ndila() { return ndila; }
}
