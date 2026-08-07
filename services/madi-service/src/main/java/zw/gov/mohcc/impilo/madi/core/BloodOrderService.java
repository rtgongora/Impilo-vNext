package zw.gov.mohcc.impilo.madi.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.madi.domain.BloodOrderStatus;
import zw.gov.mohcc.impilo.madi.domain.BloodUnitStatus;
import zw.gov.mohcc.impilo.madi.domain.CrossmatchResultStatus;
import zw.gov.mohcc.impilo.madi.events.MadiEventEmitter;
import zw.gov.mohcc.impilo.sharedkernel.security.EmergencyAccessGuard;
import zw.gov.mohcc.impilo.madi.integration.OrosIntegration;
import zw.gov.mohcc.impilo.madi.persistence.entity.*;
import zw.gov.mohcc.impilo.madi.persistence.repository.*;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BloodOrderService {

    private final EmergencyAccessGuard emergencyAccessGuard;

    private final BloodOrderRepository orderRepository;
    private final BloodOrderItemRepository itemRepository;
    private final BloodSampleRepository sampleRepository;
    private final CrossmatchRequestRepository crossmatchRequestRepository;
    private final CrossmatchResultRepository crossmatchResultRepository;
    private final BloodReservationRepository reservationRepository;
    private final BloodIssueRepository issueRepository;
    private final BloodUnitService bloodUnitService;
    private final OrosIntegration orosIntegration;
    private final MadiEventEmitter eventEmitter;
    private final BloodOrderSlaService slaService;

    public BloodOrderService(BloodOrderRepository orderRepository,
                             BloodOrderItemRepository itemRepository,
                             BloodSampleRepository sampleRepository,
                             CrossmatchRequestRepository crossmatchRequestRepository,
                             CrossmatchResultRepository crossmatchResultRepository,
                             BloodReservationRepository reservationRepository,
                             BloodIssueRepository issueRepository,
                             BloodUnitService bloodUnitService,
                             OrosIntegration orosIntegration,
                             MadiEventEmitter eventEmitter,
                             BloodOrderSlaService slaService,
                             EmergencyAccessGuard emergencyAccessGuard) {
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.sampleRepository = sampleRepository;
        this.crossmatchRequestRepository = crossmatchRequestRepository;
        this.crossmatchResultRepository = crossmatchResultRepository;
        this.reservationRepository = reservationRepository;
        this.issueRepository = issueRepository;
        this.bloodUnitService = bloodUnitService;
        this.orosIntegration = orosIntegration;
        this.eventEmitter = eventEmitter;
        this.slaService = slaService;
        this.emergencyAccessGuard = emergencyAccessGuard;
    }

    @Transactional
    public BloodOrderEntity createOrder(BloodOrderEntity order, List<BloodOrderItemEntity> items) {
        order.setStatus(BloodOrderStatus.DRAFT.name());
        order.setUpdatedAt(OffsetDateTime.now());
        BloodOrderEntity saved = orderRepository.save(order);
        for (BloodOrderItemEntity item : items) {
            item.setTenantId(saved.getTenantId());
            item.setOrderId(saved.getOrderId());
            item.setStatus("PENDING");
            itemRepository.save(item);
        }
        eventEmitter.emit("BLOOD_ORDER", saved.getOrderId().toString(), "ORDER_CREATED", "BLOOD_ORDER",
                saved.getOrderId().toString(),
                Map.of("patientCpid", saved.getPatientCpid(), "orosOrderRef", saved.getOrosOrderRef() != null ? saved.getOrosOrderRef() : ""),
                saved.getTenantId());
        return saved;
    }

    @Transactional
    public BloodOrderEntity submit(UUID tenantId, UUID orderId) {
        BloodOrderEntity order = requireOrder(tenantId, orderId);
        order.setStatus(BloodOrderStatus.SUBMITTED.name());
        order.setUpdatedAt(OffsetDateTime.now());
        BloodOrderEntity saved = orderRepository.save(order);
        if (saved.getOrosOrderRef() != null) {
            orosIntegration.notifyOrderSubmitted(saved.getOrosOrderRef(), saved.getOrderId().toString());
        }
        // Start the crossmatch SLA timer at submit.
        slaService.start(tenantId, orderId, BloodOrderSlaService.STAGE_CROSSMATCH);
        eventEmitter.emit("BLOOD_ORDER", orderId.toString(), "ORDER_SUBMITTED", "BLOOD_ORDER",
                orderId.toString(), refPayload(saved, Map.of("madiOrderId", orderId.toString())), tenantId);
        return saved;
    }

    @Transactional
    public BloodSampleEntity collectSample(UUID tenantId, UUID orderId, String sampleNumber,
                                           String collectedBy, UUID facilityId) {
        BloodOrderEntity order = requireOrder(tenantId, orderId);
        BloodSampleEntity sample = new BloodSampleEntity();
        sample.setTenantId(tenantId);
        sample.setOrderId(orderId);
        sample.setSampleNumber(sampleNumber);
        sample.setCollectedBy(collectedBy);
        sample.setFacilityId(facilityId);
        sample.setStatus("COLLECTED");
        sample.setCollectedAt(OffsetDateTime.now());
        BloodSampleEntity saved = sampleRepository.save(sample);
        order.setStatus(BloodOrderStatus.SAMPLE_COLLECTED.name());
        order.setUpdatedAt(OffsetDateTime.now());
        orderRepository.save(order);
        return saved;
    }

    @Transactional
    public CrossmatchResultEntity crossmatch(UUID tenantId, UUID orderId, UUID sampleId, UUID unitId,
                                             CrossmatchResultStatus result, String testedBy, UUID facilityId) {
        requireOrder(tenantId, orderId);
        CrossmatchRequestEntity request = new CrossmatchRequestEntity();
        request.setTenantId(tenantId);
        request.setOrderId(orderId);
        request.setSampleId(sampleId);
        request.setUnitId(unitId);
        request.setRequestedBy(testedBy);
        request.setFacilityId(facilityId);
        request.setStatus("COMPLETED");
        request.setRequestedAt(OffsetDateTime.now());
        CrossmatchRequestEntity savedRequest = crossmatchRequestRepository.save(request);
        CrossmatchResultEntity resultEntity = new CrossmatchResultEntity();
        resultEntity.setTenantId(tenantId);
        resultEntity.setRequestId(savedRequest.getRequestId());
        resultEntity.setResultStatus(result.name());
        resultEntity.setTestedBy(testedBy);
        resultEntity.setFacilityId(facilityId);
        resultEntity.setTestedAt(OffsetDateTime.now());
        CrossmatchResultEntity saved = crossmatchResultRepository.save(resultEntity);
        BloodOrderEntity order = requireOrder(tenantId, orderId);
        order.setStatus(BloodOrderStatus.CROSSMATCH_PENDING.name());
        if (result == CrossmatchResultStatus.COMPATIBLE) {
            order.setStatus(BloodOrderStatus.RESERVED.name());
        }
        order.setUpdatedAt(OffsetDateTime.now());
        orderRepository.save(order);
        eventEmitter.emit("BLOOD_ORDER", orderId.toString(), "CROSSMATCH_COMPLETED", "BLOOD_ORDER",
                orderId.toString(), refPayload(order, Map.of("result", result.name())), tenantId);
        // SLA: crossmatch stage done; open the issue-stage timer.
        slaService.complete(orderId, BloodOrderSlaService.STAGE_CROSSMATCH);
        slaService.start(tenantId, orderId, BloodOrderSlaService.STAGE_ISSUE);
        // Return the compatibility result to OROS so it surfaces in the requester's inbox /
        // patient file (incompatible -> critical). Best-effort; OROS unavailability is non-blocking.
        if (order.getOrosOrderRef() != null) {
            orosIntegration.notifyCrossmatchResult(order.getOrosOrderRef(), result.name(), null);
        }
        return saved;
    }

    @Transactional
    public BloodReservationEntity reserve(UUID tenantId, UUID orderId, UUID unitId,
                                          String reservedBy, UUID facilityId) {
        BloodOrderEntity order = requireOrder(tenantId, orderId);
        bloodUnitService.transition(tenantId, unitId, BloodUnitStatus.RESERVED);
        BloodReservationEntity reservation = new BloodReservationEntity();
        reservation.setTenantId(tenantId);
        reservation.setOrderId(orderId);
        reservation.setUnitId(unitId);
        reservation.setReservedBy(reservedBy);
        reservation.setFacilityId(facilityId);
        reservation.setStatus("ACTIVE");
        reservation.setReservedAt(OffsetDateTime.now());
        reservation.setExpiresAt(OffsetDateTime.now().plusHours(24));
        BloodReservationEntity saved = reservationRepository.save(reservation);
        order.setStatus(BloodOrderStatus.RESERVED.name());
        order.setUpdatedAt(OffsetDateTime.now());
        orderRepository.save(order);
        return saved;
    }

    @Transactional
    public BloodIssueEntity issue(UUID tenantId, UUID orderId, UUID unitId, UUID reservationId,
                                  String issuedBy, UUID facilityId) {
        BloodOrderEntity order = requireOrder(tenantId, orderId);
        bloodUnitService.transition(tenantId, unitId, BloodUnitStatus.ISSUED);
        BloodIssueEntity issue = new BloodIssueEntity();
        issue.setTenantId(tenantId);
        issue.setOrderId(orderId);
        issue.setUnitId(unitId);
        issue.setReservationId(reservationId);
        issue.setIssuedBy(issuedBy);
        issue.setFacilityId(facilityId);
        issue.setStatus("ISSUED");
        issue.setIssuedAt(OffsetDateTime.now());
        BloodIssueEntity saved = issueRepository.save(issue);
        order.setStatus(BloodOrderStatus.ISSUED.name());
        order.setUpdatedAt(OffsetDateTime.now());
        orderRepository.save(order);
        if (order.getOrosOrderRef() != null) {
            orosIntegration.notifyBloodIssued(order.getOrosOrderRef(), unitId.toString());
        }
        // SLA: issue stage met.
        slaService.complete(orderId, BloodOrderSlaService.STAGE_ISSUE);
        eventEmitter.emit("BLOOD_ORDER", orderId.toString(), "BLOOD_ISSUED", "BLOOD_ORDER",
                orderId.toString(), refPayload(order, Map.of("unitId", unitId.toString())), tenantId);
        return saved;
    }

    /**
     * Delivery-side completion: issued blood arrived at the requesting site
     * (e.g. Nhume drop-off sign-off). Order must be ISSUED.
     */
    @Transactional
    public BloodOrderEntity complete(UUID tenantId, UUID orderId, String completedBy, String deliveryRef) {
        BloodOrderEntity order = requireOrder(tenantId, orderId);
        if (!BloodOrderStatus.ISSUED.name().equals(order.getStatus())) {
            throw new IllegalStateException(
                    "Blood order must be ISSUED to complete; current status is " + order.getStatus());
        }
        order.setStatus(BloodOrderStatus.COMPLETED.name());
        order.setUpdatedAt(OffsetDateTime.now());
        orderRepository.save(order);
        eventEmitter.emit("BLOOD_ORDER", orderId.toString(), "BLOOD_ORDER_COMPLETED", "BLOOD_ORDER",
                orderId.toString(), refPayload(order, Map.of(
                        "completedBy", completedBy == null ? "" : completedBy,
                        "deliveryRef", deliveryRef == null ? "" : deliveryRef)), tenantId);
        return order;
    }

    /**
     * Emergency (O-negative / uncrossmatched) blood release under break-glass (G1.14). Life-saving
     * release bypasses the RESERVED+COMPATIBLE gate, but ONLY under an EMERGENCY/BREAK_GLASS
     * purpose-of-use (else the shared {@link EmergencyAccessGuard} DENIES it — never a silent bypass),
     * and it writes an ELEVATED break-glass audit event carrying the trauma-episode link. The blood
     * bank still reconciles crossmatch retrospectively; this only authorises the emergency issue.
     */
    @Transactional
    public BloodOrderEntity emergencyRelease(UUID tenantId, UUID orderId, String purposeOfUse,
                                             String actorId, String reason, String bloodUnitId,
                                             String escalationGrantId) {
        BloodOrderEntity order = requireOrder(tenantId, orderId);
        // Throws EmergencyAccessDeniedException (→ 403) without an emergency purpose OR without an
        // active escalation grant. An unreachable trust plane allows and records an override.
        emergencyAccessGuard.requireBreakGlass(new EmergencyAccessGuard.EmergencyAccessRequest(
                tenantId == null ? null : tenantId.toString(), actorId, purposeOfUse,
                "O_NEG_EMERGENCY_RELEASE", order.getPatientCpid(), escalationGrantId));
        order.setStatus(BloodOrderStatus.ISSUED.name());
        order.setUpdatedAt(OffsetDateTime.now());
        orderRepository.save(order);

        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("patientCpid", order.getPatientCpid());
        payload.put("bloodGroup", order.getBloodGroup());
        payload.put("bloodUnitId", bloodUnitId == null ? "" : bloodUnitId);
        payload.put("breakGlass", true);
        payload.put("purposeOfUse", purposeOfUse);
        payload.put("reason", reason == null ? "emergency uncrossmatched release" : reason);
        payload.put("actorId", actorId == null ? "" : actorId);
        payload.put("traumaEpisodeId", order.getTraumaEpisodeId() != null ? order.getTraumaEpisodeId().toString() : null);
        // Elevated-audit outbox event — an emergency override is recorded, never a silent bypass.
        eventEmitter.emit("BLOOD_ORDER", orderId.toString(), "EMERGENCY_RELEASE_BREAK_GLASS", "BLOOD_ORDER",
                orderId.toString(), payload, tenantId);
        return order;
    }

    @Transactional(readOnly = true)
    public List<BloodOrderEntity> listOrders(UUID tenantId, UUID facilityId, String status, String patientCpid) {
        List<BloodOrderEntity> rows = facilityId != null
                ? orderRepository.findByTenantIdAndFacilityIdOrderByCreatedAtDesc(tenantId, facilityId)
                : orderRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        return rows.stream()
                .filter(o -> status == null || status.isBlank() || status.equalsIgnoreCase(o.getStatus()))
                .filter(o -> patientCpid == null || patientCpid.isBlank()
                        || patientCpid.equalsIgnoreCase(o.getPatientCpid()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getOrderDetail(UUID tenantId, UUID orderId) {
        BloodOrderEntity order = requireOrder(tenantId, orderId);
        List<BloodOrderItemEntity> items = itemRepository.findByOrderIdAndTenantId(orderId, tenantId);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("order", order);
        detail.put("items", items);
        if (order.getOrosOrderRef() != null && !order.getOrosOrderRef().isBlank()) {
            detail.put("orosDeepLinkPath", "/lab?orderId=" + order.getOrosOrderRef());
            detail.put("butanoServiceRequestHint",
                    "/internal/v1/fhir/ServiceRequest?identifier=" + order.getOrosOrderRef());
        } else {
            detail.put("orosDeepLinkPath", null);
            detail.put("butanoServiceRequestHint", null);
        }
        return detail;
    }

    private BloodOrderEntity requireOrder(UUID tenantId, UUID orderId) {
        return orderRepository.findByOrderIdAndTenantId(orderId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Blood order not found"));
    }

    /**
     * Event payload carrying {@code orosOrderRef} alongside the supplied fields, so OROS's
     * event-driven consumer can apply the update to the originating order (resilient alternative
     * to the best-effort REST callback).
     */
    private Map<String, Object> refPayload(BloodOrderEntity order, Map<String, Object> extra) {
        Map<String, Object> payload = new LinkedHashMap<>(extra);
        payload.put("orosOrderRef", order.getOrosOrderRef() != null ? order.getOrosOrderRef() : "");
        return payload;
    }
}
