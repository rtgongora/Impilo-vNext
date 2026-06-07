package zw.gov.mohcc.impilo.madi.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.madi.domain.BloodOrderStatus;
import zw.gov.mohcc.impilo.madi.domain.BloodUnitStatus;
import zw.gov.mohcc.impilo.madi.domain.CrossmatchResultStatus;
import zw.gov.mohcc.impilo.madi.events.MadiEventEmitter;
import zw.gov.mohcc.impilo.madi.integration.OrosIntegration;
import zw.gov.mohcc.impilo.madi.persistence.entity.*;
import zw.gov.mohcc.impilo.madi.persistence.repository.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BloodOrderService {

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

    public BloodOrderService(BloodOrderRepository orderRepository,
                             BloodOrderItemRepository itemRepository,
                             BloodSampleRepository sampleRepository,
                             CrossmatchRequestRepository crossmatchRequestRepository,
                             CrossmatchResultRepository crossmatchResultRepository,
                             BloodReservationRepository reservationRepository,
                             BloodIssueRepository issueRepository,
                             BloodUnitService bloodUnitService,
                             OrosIntegration orosIntegration,
                             MadiEventEmitter eventEmitter) {
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
        eventEmitter.emit("BLOOD_ORDER", orderId.toString(), "ORDER_SUBMITTED", "BLOOD_ORDER",
                orderId.toString(), Map.of(), tenantId);
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
                orderId.toString(), Map.of("result", result.name()), tenantId);
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
        eventEmitter.emit("BLOOD_ORDER", orderId.toString(), "BLOOD_ISSUED", "BLOOD_ORDER",
                orderId.toString(), Map.of("unitId", unitId.toString()), tenantId);
        return saved;
    }

    private BloodOrderEntity requireOrder(UUID tenantId, UUID orderId) {
        return orderRepository.findByOrderIdAndTenantId(orderId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Blood order not found"));
    }
}
