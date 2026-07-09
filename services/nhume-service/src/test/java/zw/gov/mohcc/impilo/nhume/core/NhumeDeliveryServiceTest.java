package zw.gov.mohcc.impilo.nhume.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import zw.gov.mohcc.impilo.nhume.domain.DeliveryNotificationEventEntity;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryProofEntity;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryRequestEntity;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryStatus;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryStatusEventEntity;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryTrackingEventEntity;
import zw.gov.mohcc.impilo.nhume.domain.DriverCourierProfileEntity;
import zw.gov.mohcc.impilo.nhume.domain.FleetAssetEntity;
import zw.gov.mohcc.impilo.nhume.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.nhume.integration.commshub.CommsHubClient;
import zw.gov.mohcc.impilo.nhume.integration.ndila.NdilaClient;
import zw.gov.mohcc.impilo.nhume.integration.ndila.SimulatedNdilaClient;
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

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the Nhume delivery service end-to-end:
 * lifecycle, assignments, tracking, proof, chain-of-custody, exception handling.
 */
class NhumeDeliveryServiceTest {

    private DeliveryRequestRepository deliveryRepo;
    private DeliveryItemRepository itemRepo;
    private DeliveryPackageRepository packageRepo;
    private DeliveryAssignmentRepository assignmentRepo;
    private DeliveryStatusEventRepository statusEventRepo;
    private DeliveryTrackingEventRepository trackingRepo;
    private DeliveryProofRepository proofRepo;
    private ChainOfCustodyEventRepository custodyRepo;
    private DeliveryExceptionRepository exceptionRepo;
    private DeliveryNotificationEventRepository notificationRepo;
    private DeliveryAuditEventRepository auditRepo;
    private OutboxEventRepository outboxRepo;
    private DeliveryPolicyRepository policyRepo;
    private DriverCourierProfileRepository courierRepo;
    private FleetAssetRepository assetRepo;
    private CommsHubClient commsHub;
    private NdilaClient ndila;
    private NhumeDeliveryService service;

    private final UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final Map<UUID, DeliveryRequestEntity> store = new HashMap<>();
    private final Map<UUID, DeliveryAssignmentEntity> assignmentStore = new HashMap<>();
    private final Map<UUID, FleetAssetEntity> assetStore = new HashMap<>();
    private final Map<UUID, DriverCourierProfileEntity> courierStore = new HashMap<>();

    @BeforeEach
    void setup() {
        deliveryRepo = mock(DeliveryRequestRepository.class);
        itemRepo = mock(DeliveryItemRepository.class);
        packageRepo = mock(DeliveryPackageRepository.class);
        assignmentRepo = mock(DeliveryAssignmentRepository.class);
        statusEventRepo = mock(DeliveryStatusEventRepository.class);
        trackingRepo = mock(DeliveryTrackingEventRepository.class);
        proofRepo = mock(DeliveryProofRepository.class);
        custodyRepo = mock(ChainOfCustodyEventRepository.class);
        exceptionRepo = mock(DeliveryExceptionRepository.class);
        notificationRepo = mock(DeliveryNotificationEventRepository.class);
        auditRepo = mock(DeliveryAuditEventRepository.class);
        outboxRepo = mock(OutboxEventRepository.class);
        policyRepo = mock(DeliveryPolicyRepository.class);
        courierRepo = mock(DriverCourierProfileRepository.class);
        assetRepo = mock(FleetAssetRepository.class);
        commsHub = mock(CommsHubClient.class);
        ndila = new SimulatedNdilaClient();

        when(deliveryRepo.save(any(DeliveryRequestEntity.class)))
                .thenAnswer(inv -> {
                    DeliveryRequestEntity e = inv.getArgument(0);
                    store.put(e.getDeliveryId(), e);
                    return e;
                });
        when(deliveryRepo.findById(any(UUID.class)))
                .thenAnswer(inv -> Optional.ofNullable(store.get(inv.getArgument(0, UUID.class))));
        when(assignmentRepo.save(any(DeliveryAssignmentEntity.class)))
                .thenAnswer(inv -> {
                    DeliveryAssignmentEntity a = inv.getArgument(0);
                    assignmentStore.put(a.getAssignmentId(), a);
                    return a;
                });
        when(assignmentRepo
                .findFirstByDeliveryIdAndSupersededAtIsNullOrderByAssignedAtDesc(any(UUID.class)))
                .thenAnswer(inv -> assignmentStore.values().stream()
                        .filter(a -> a.getDeliveryId().equals(inv.getArgument(0))
                                && a.getSupersededAt() == null)
                        .findFirst());
        when(assetRepo.save(any(FleetAssetEntity.class)))
                .thenAnswer(inv -> { FleetAssetEntity a = inv.getArgument(0);
                    assetStore.put(a.getAssetId(), a); return a; });
        when(assetRepo.findById(any(UUID.class)))
                .thenAnswer(inv -> Optional.ofNullable(assetStore.get(inv.getArgument(0, UUID.class))));
        when(courierRepo.save(any(DriverCourierProfileEntity.class)))
                .thenAnswer(inv -> { DriverCourierProfileEntity c = inv.getArgument(0);
                    courierStore.put(c.getCourierId(), c); return c; });
        when(courierRepo.findById(any(UUID.class)))
                .thenAnswer(inv -> Optional.ofNullable(courierStore.get(inv.getArgument(0, UUID.class))));
        when(custodyRepo.findByDeliveryIdOrderBySequenceNoAsc(any(UUID.class))).thenReturn(List.of());
        when(policyRepo.findByTenantIdAndDeliveryTypeAndActiveTrue(any(UUID.class), anyString()))
                .thenReturn(Optional.empty());
        when(commsHub.dispatch(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(CommsHubClient.DispatchResult.sent("test-provider-ref"));

        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        service = new NhumeDeliveryService(deliveryRepo, itemRepo, packageRepo, assignmentRepo,
                statusEventRepo, trackingRepo, proofRepo, custodyRepo, exceptionRepo,
                notificationRepo, auditRepo, outboxRepo, policyRepo, courierRepo, assetRepo,
                commsHub, ndila, mapper);
    }

    @Test
    void createDelivery_persistsAndEmitsRequestedEvent() {
        CreateDeliveryRequest req = baseRequest();
        DeliveryRequestEntity d = service.createDelivery(tenantId, "national-spine",
                UUID.randomUUID().toString(), "test-key", req, actorCtx());

        assertThat(d.getDeliveryId()).isNotNull();
        assertThat(d.getStatus()).isEqualTo(DeliveryStatus.DRAFT.name());
        assertThat(d.getReferenceCode()).startsWith("NHM-");
        assertThat(d.getRecipientName()).isEqualTo("Test Patient");
        // outbox + status + audit + notification
        verify(outboxRepo, atLeastOnce()).save(any(OutboxEventEntity.class));
        verify(statusEventRepo, atLeastOnce()).save(any(DeliveryStatusEventEntity.class));
        verify(auditRepo, atLeastOnce()).save(any(DeliveryAuditEventEntity.class));
        verify(notificationRepo, atLeastOnce()).save(any(DeliveryNotificationEventEntity.class));
        verify(commsHub, atLeastOnce()).dispatch(anyString(), anyString(),
                anyString(), anyString(), any());
    }

    @Test
    void submitImmediately_transitionsToSubmitted() {
        CreateDeliveryRequest req = baseRequestBuilder(true);
        DeliveryRequestEntity d = service.createDelivery(tenantId, "national-spine", null, null,
                req, actorCtx());
        assertThat(d.getStatus()).isEqualTo(DeliveryStatus.SUBMITTED.name());
        assertThat(d.getSubmittedAt()).isNotNull();
    }

    @Test
    void lifecycle_full_happy_path() {
        DeliveryRequestEntity d = service.createDelivery(tenantId, "national-spine", null, null,
                baseRequestBuilder(true), actorCtx());

        UUID id = d.getDeliveryId();
        FleetAssetEntity asset = newAsset();
        DriverCourierProfileEntity courier = newCourier();

        service.approve(id, new StatusChangeRequest("ok", null), actorCtx());
        DeliveryAssignmentEntity assignment = service.assign(id,
                new AssignDeliveryRequest(courier.getCourierId(), asset.getAssetId(),
                        null, null, "MOTORCYCLE", "MANUAL", "go"),
                actorCtx(), "k1");
        assertThat(assignment.getAssignmentId()).isNotNull();
        assertThat(store.get(id).getStatus()).isEqualTo(DeliveryStatus.ASSIGNED.name());

        service.accept(id, actorCtx());
        assertThat(store.get(id).getStatus()).isEqualTo(DeliveryStatus.ACCEPTED.name());

        service.startPickup(id, new StatusChangeRequest("rolling", null), actorCtx());
        assertThat(store.get(id).getStatus()).isEqualTo(DeliveryStatus.EN_ROUTE_TO_PICKUP.name());

        service.confirmPickup(id, new StatusChangeRequest("picked", null), actorCtx());
        assertThat(store.get(id).getStatus()).isEqualTo(DeliveryStatus.PICKED_UP.name());

        service.startTransit(id, new StatusChangeRequest("en route", null), actorCtx());
        assertThat(store.get(id).getStatus()).isEqualTo(DeliveryStatus.IN_TRANSIT.name());

        DeliveryTrackingEventEntity t = service.recordTracking(id,
                new TrackingUpdateRequest(null, "LOCATION", -17.8, 31.0,
                        BigDecimal.valueOf(5), courier.getCourierId(), asset.getAssetId(),
                        "NHUME", Map.of("speed", 35)), actorCtx());
        assertThat(t).isNotNull();

        DeliveryProofEntity proof = service.captureProof(id,
                new ProofRequest("DELIVERY", "OTP", "tester", "123456", null, null, null,
                        true, null, null, Map.of(), true), actorCtx());
        assertThat(proof.getProofId()).isNotNull();
        assertThat(proof.isVerified()).isTrue();
        assertThat(store.get(id).getStatus()).isEqualTo(DeliveryStatus.DELIVERED.name());
        assertThat(store.get(id).getDeliveredAt()).isNotNull();
    }

    @Test
    void collectionSignOff_recordsPickupProof_withoutMarkingDelivered() {
        DeliveryRequestEntity d = service.createDelivery(tenantId, "national-spine", null, null,
                baseRequestBuilder(true), actorCtx());
        UUID id = d.getDeliveryId();
        FleetAssetEntity asset = newAsset();
        DriverCourierProfileEntity courier = newCourier();

        service.approve(id, new StatusChangeRequest("ok", null), actorCtx());
        service.assign(id, new AssignDeliveryRequest(courier.getCourierId(), asset.getAssetId(),
                null, null, "BICYCLE", "MANUAL", "go"), actorCtx(), "k1");
        service.accept(id, actorCtx());
        service.startPickup(id, new StatusChangeRequest("rolling", null), actorCtx());
        service.confirmPickup(id, new StatusChangeRequest("picked", null), actorCtx());
        assertThat(store.get(id).getStatus()).isEqualTo(DeliveryStatus.PICKED_UP.name());

        // Collection sign-off: PICKUP stage, mark_delivered=false.
        DeliveryProofEntity proof = service.captureProof(id,
                new ProofRequest("PICKUP", "RECIPIENT_SIGNATURE", "origin-clerk", null,
                        "sig:collection", null, null, null, null, null, Map.of(), false),
                actorCtx());

        assertThat(proof.getProofStage()).isEqualTo("PICKUP");
        // The collection sign-off must NOT close the mission.
        assertThat(store.get(id).getStatus()).isEqualTo(DeliveryStatus.PICKED_UP.name());
        assertThat(store.get(id).getDeliveredAt()).isNull();
    }

    @Test
    void invalidTransition_throws() {
        DeliveryRequestEntity d = service.createDelivery(tenantId, "national-spine", null, null,
                baseRequest(), actorCtx());
        assertThatThrownBy(() -> service.markDelivered(d.getDeliveryId(),
                new StatusChangeRequest("from draft", null), actorCtx()))
                .isInstanceOf(InvalidDeliveryTransitionException.class);
    }

    @Test
    void chainOfCustody_coldChainBreach_raisesException() {
        CreateDeliveryRequest req = coldChainRequest();
        DeliveryRequestEntity d = service.createDelivery(tenantId, "national-spine", null, null,
                req, actorCtx());
        when(custodyRepo.findByDeliveryIdOrderBySequenceNoAsc(d.getDeliveryId())).thenReturn(List.of());

        ChainOfCustodyEventEntity coc = service.recordCustody(d.getDeliveryId(),
                new ChainOfCustodyRequest("PICKED_UP", null, "Driver",
                        "Lytton Rd", "SEAL-1", BigDecimal.valueOf(12.5), "ok",
                        "OTP", "device-A", null, null), actorCtx());

        assertThat(coc.getCocId()).isNotNull();
        assertThat(coc.getTemperatureC()).isEqualTo(BigDecimal.valueOf(12.5));
        verify(exceptionRepo, atLeastOnce()).save(any(DeliveryExceptionEntity.class));
    }

    @Test
    void assignment_supersedesPrevious_onReassign() {
        DeliveryRequestEntity d = service.createDelivery(tenantId, "national-spine", null, null,
                baseRequestBuilder(true), actorCtx());
        service.approve(d.getDeliveryId(), new StatusChangeRequest("ok", null), actorCtx());
        FleetAssetEntity asset1 = newAsset();
        FleetAssetEntity asset2 = newAsset();
        DriverCourierProfileEntity c1 = newCourier();
        DriverCourierProfileEntity c2 = newCourier();

        service.assign(d.getDeliveryId(),
                new AssignDeliveryRequest(c1.getCourierId(), asset1.getAssetId(), null, null,
                        "MOTORCYCLE", "MANUAL", null), actorCtx(), "k1");
        service.assign(d.getDeliveryId(),
                new AssignDeliveryRequest(c2.getCourierId(), asset2.getAssetId(), null, null,
                        "CAR", "MANUAL", null), actorCtx(), "k2");

        ArgumentCaptor<DeliveryAssignmentEntity> captor = ArgumentCaptor.forClass(DeliveryAssignmentEntity.class);
        verify(assignmentRepo, times(3)).save(captor.capture()); // assignment-1, supersede-prev, assignment-2
        List<DeliveryAssignmentEntity> all = captor.getAllValues();
        assertThat(all).hasSize(3);
        assertThat(all.get(1).getSupersededAt()).isNotNull();
    }

    @Test
    void outboxEvents_emitNhumeTopics() {
        DeliveryRequestEntity d = service.createDelivery(tenantId, "national-spine", null, null,
                baseRequest(), actorCtx());
        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxRepo, atLeastOnce()).save(captor.capture());
        OutboxEventEntity emitted = captor.getValue();
        assertThat(emitted.getEventType()).isEqualTo(NhumeEvents.DELIVERY_REQUESTED);
        assertThat(emitted.getAggregateType()).isEqualTo("NhumeDelivery");
        assertThat(emitted.getAggregateId()).isEqualTo(d.getDeliveryId().toString());
        assertThat(emitted.getProducer()).isEqualTo("nhume-service");
        assertThat(emitted.getPayloadJson()).contains("reference_code");
    }

    // ─── fixtures ───────────────────────────────────────────────────────────

    private CreateDeliveryRequest baseRequest() { return baseRequestBuilder(false); }

    private CreateDeliveryRequest baseRequestBuilder(boolean submit) {
        return new CreateDeliveryRequest(
                "MEDICINE", "STANDARD", "CITIZEN_APP",
                "actor-1", "CITIZEN", null, "TUSO:FAC-HARARE-CENTRAL",
                new CreateDeliveryRequest.LocationDto("FACILITY", "TUSO:FAC-PHARMACY-CBD",
                        "Avondale Pharmacy", "12 King George Rd", -17.79, 31.04),
                new CreateDeliveryRequest.LocationDto("ADDRESS", "home:test",
                        "Patient home", "Mt Pleasant", -17.76, 31.05),
                new CreateDeliveryRequest.RecipientDto("CITIZEN", "VITO:demo-001",
                        "Test Patient", "+263770000000", "test@example.com", "en"),
                List.of(new CreateDeliveryRequest.DeliveryItemDto("MEDICINE", "MED:001",
                        "Paracetamol 500mg", BigDecimal.ONE, "TABLET", null, false, false)),
                null, null, null, null, null,
                "NONE", null, null, "USD",
                true, "OTP", false, false, false, false, false, false, false, false,
                null, null, null, null, null,
                List.of("MOTORCYCLE", "BICYCLE"), null, null,
                "demo", Map.of("test", true), submit);
    }

    private CreateDeliveryRequest coldChainRequest() {
        CreateDeliveryRequest base = baseRequest();
        return new CreateDeliveryRequest(
                "COLD_CHAIN", base.priority(), base.requestSource(),
                base.requestingActorId(), base.requestingActorType(), base.requestingOrgId(),
                base.requestingFacilityId(), base.origin(), base.destination(), base.recipient(),
                base.items(), base.packages(), base.clinicalContextRef(), base.programmeContextRef(),
                base.telehealthSessionRef(), base.marketplaceOrderRef(),
                base.paymentPath(), base.paymentReference(), base.declaredValueCents(),
                base.currency(), base.consentRequired(), base.identityVerification(),
                /* coldChainRequired */ true, base.controlledItem(), base.fragile(),
                base.hazardous(), base.biohazard(), base.specimen(), true,
                base.returnRequired(), base.requiredBy(), base.pickupWindowStart(),
                base.pickupWindowEnd(), base.deliveryWindowStart(), base.deliveryWindowEnd(),
                base.allowedModes(), base.policyId(), base.slaId(), base.notes(),
                base.metadata(), false);
    }

    private TrustLayerGuard.ActorContext actorCtx() {
        return new TrustLayerGuard.ActorContext(null, "actor-1", "CITIZEN",
                "ROUTINE_CARE", null, null, null, null);
    }

    private FleetAssetEntity newAsset() {
        FleetAssetEntity a = new FleetAssetEntity();
        a.setAssetId(UUID.randomUUID());
        a.setTenantId(tenantId);
        a.setAssetCode("ASSET-" + UUID.randomUUID());
        a.setDisplayName("Test asset");
        a.setVehicleType("MOTORCYCLE");
        a.setModeCategory("MOTORCYCLE");
        a.setAvailabilityStatus("AVAILABLE");
        assetStore.put(a.getAssetId(), a);
        return a;
    }

    private DriverCourierProfileEntity newCourier() {
        DriverCourierProfileEntity c = new DriverCourierProfileEntity();
        c.setCourierId(UUID.randomUUID());
        c.setTenantId(tenantId);
        c.setCourierCode("CRR-" + UUID.randomUUID());
        c.setDisplayName("Test courier");
        c.setCurrentStatus("ON_DUTY");
        courierStore.put(c.getCourierId(), c);
        return c;
    }
}
