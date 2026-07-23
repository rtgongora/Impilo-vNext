package zw.gov.mohcc.impilo.nhume.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.nhume.api.dto.ChainOfCustodyRequest;
import zw.gov.mohcc.impilo.nhume.api.dto.CreateDeliveryRequest;
import zw.gov.mohcc.impilo.nhume.api.dto.ProofRequest;
import zw.gov.mohcc.impilo.nhume.api.dto.StatusChangeRequest;
import zw.gov.mohcc.impilo.nhume.domain.ChainOfCustodyEventEntity;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryAssignmentEntity;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryExceptionEntity;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryProofEntity;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryRequestEntity;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryStatus;
import zw.gov.mohcc.impilo.nhume.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.nhume.integration.commshub.CommsHubClient;
import zw.gov.mohcc.impilo.nhume.integration.ndila.SimulatedNdilaClient;
import zw.gov.mohcc.impilo.nhume.integration.trust.TrustLayerGuard;
import zw.gov.mohcc.impilo.nhume.integration.writeback.NhumeIntegrationWriteBackService;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * OF-B19 §12.6 — cold-chain IoT closed loop:
 * excursion unsuppressibility (every band breach writes custody evidence and
 * flags the delivery — including via the manual custody API), fail-closed
 * handover refusal while flagged, the pharmacist stability-decision matrix,
 * and the QUARANTINE → RETURNED path firing the existing msika-flow
 * selection write-back (§12.7 refund seam).
 */
class ColdChainExcursionTest {

    private final Map<UUID, DeliveryRequestEntity> store = new HashMap<>();
    private final List<DeliveryProofEntity> proofs = new ArrayList<>();
    private final List<ChainOfCustodyEventEntity> custody = new ArrayList<>();
    private final List<DeliveryExceptionEntity> exceptions = new ArrayList<>();
    private final List<OutboxEventEntity> outbox = new ArrayList<>();
    private final NhumeDeliveryServiceTest.StubWriteBackGateway gateway =
            new NhumeDeliveryServiceTest.StubWriteBackGateway();

    private NhumeDeliveryService service;

    @BeforeEach
    void setup() {
        DeliveryRequestRepository deliveryRepo = mock(DeliveryRequestRepository.class);
        DeliveryProofRepository proofRepo = mock(DeliveryProofRepository.class);
        ChainOfCustodyEventRepository custodyRepo = mock(ChainOfCustodyEventRepository.class);
        DeliveryExceptionRepository exceptionRepo = mock(DeliveryExceptionRepository.class);
        DeliveryAssignmentRepository assignmentRepo = mock(DeliveryAssignmentRepository.class);
        OutboxEventRepository outboxRepo = mock(OutboxEventRepository.class);
        DeliveryPolicyRepository policyRepo = mock(DeliveryPolicyRepository.class);
        CommsHubClient commsHub = mock(CommsHubClient.class);
        var biometric = mock(zw.gov.mohcc.impilo.shared.biometric.BiometricVerificationClient.class);

        when(deliveryRepo.save(any(DeliveryRequestEntity.class))).thenAnswer(inv -> {
            DeliveryRequestEntity e = inv.getArgument(0);
            store.put(e.getDeliveryId(), e);
            return e;
        });
        when(deliveryRepo.findById(any(UUID.class)))
                .thenAnswer(inv -> Optional.ofNullable(store.get(inv.getArgument(0, UUID.class))));
        when(proofRepo.save(any(DeliveryProofEntity.class))).thenAnswer(inv -> {
            DeliveryProofEntity p = inv.getArgument(0);
            proofs.add(p);
            return p;
        });
        when(proofRepo.findByDeliveryIdOrderByCapturedAtAsc(any(UUID.class)))
                .thenAnswer(inv -> proofs.stream()
                        .filter(p -> p.getDeliveryId().equals(inv.getArgument(0, UUID.class)))
                        .toList());
        when(custodyRepo.save(any(ChainOfCustodyEventEntity.class))).thenAnswer(inv -> {
            ChainOfCustodyEventEntity c = inv.getArgument(0);
            if (!custody.contains(c)) custody.add(c);
            return c;
        });
        when(custodyRepo.findByDeliveryIdOrderBySequenceNoAsc(any(UUID.class)))
                .thenAnswer(inv -> custody.stream()
                        .filter(c -> c.getDeliveryId().equals(inv.getArgument(0, UUID.class)))
                        .toList());
        when(exceptionRepo.save(any(DeliveryExceptionEntity.class))).thenAnswer(inv -> {
            DeliveryExceptionEntity e = inv.getArgument(0);
            exceptions.add(e);
            return e;
        });
        when(outboxRepo.save(any(OutboxEventEntity.class))).thenAnswer(inv -> {
            OutboxEventEntity e = inv.getArgument(0);
            outbox.add(e);
            return e;
        });
        when(assignmentRepo.findFirstByDeliveryIdAndSupersededAtIsNullOrderByAssignedAtDesc(any(UUID.class)))
                .thenAnswer(inv -> {
                    DeliveryAssignmentEntity a = new DeliveryAssignmentEntity();
                    a.setAssignmentId(UUID.randomUUID());
                    a.setDeliveryId(inv.getArgument(0, UUID.class));
                    return Optional.of(a);
                });
        when(assignmentRepo.save(any(DeliveryAssignmentEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(policyRepo.findByTenantIdAndDeliveryTypeAndActiveTrue(any(UUID.class), anyString()))
                .thenReturn(Optional.empty());
        when(commsHub.dispatch(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(CommsHubClient.DispatchResult.sent("ref"));

        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        NhumeIntegrationWriteBackService writeBack =
                new NhumeIntegrationWriteBackService(gateway, mapper, proofRepo, true);
        service = new NhumeDeliveryService(deliveryRepo,
                mock(DeliveryItemRepository.class), mock(DeliveryPackageRepository.class),
                assignmentRepo, mock(DeliveryStatusEventRepository.class),
                mock(DeliveryTrackingEventRepository.class), proofRepo, custodyRepo, exceptionRepo,
                mock(DeliveryNotificationEventRepository.class), mock(DeliveryAuditEventRepository.class),
                outboxRepo, policyRepo, mock(DriverCourierProfileRepository.class),
                mock(FleetAssetRepository.class),
                commsHub, new SimulatedNdilaClient(), mapper, writeBack, biometric,
                new DeliveryModeService(
                        mock(zw.gov.mohcc.impilo.nhume.repository.ModeCorridorRepository.class),
                        mock(zw.gov.mohcc.impilo.nhume.repository.AutonomousMissionRepository.class),
                        mock(DeliveryPackageRepository.class)));
    }

    private static TrustLayerGuard.ActorContext actor(String id, String type) {
        return new TrustLayerGuard.ActorContext(null, id, type, "TREATMENT", null, null, null, null);
    }

    private static TrustLayerGuard.ActorContext courier() { return actor("courier-1", "COURIER"); }

    private static TrustLayerGuard.ActorContext pharmacist() {
        return actor("pharmacist-1", "PROVIDER");
    }

    /** In-transit cold-chain delivery: sensor-bound, selection-linked, ungraded PoD class. */
    private UUID coldDeliveryInTransit(BigDecimal minC, BigDecimal maxC, boolean coldChain) {
        CreateDeliveryRequest req = new CreateDeliveryRequest(
                "COLD_CHAIN", "STANDARD", "MARKETPLACE_COMMITMENT",
                "msika-flow-service", "SYSTEM", null, null,
                new CreateDeliveryRequest.LocationDto("VENDOR", "vendor-1",
                        "Vendor pharmacy", null, -17.82, 31.05),
                new CreateDeliveryRequest.LocationDto("ADDRESS", "home-1",
                        "Patient home", "5 Samora Machel Ave", -17.83, 31.04),
                new CreateDeliveryRequest.RecipientDto("PATIENT", "sel:SEL-9",
                        "Rudo Ncube", "+263772000000", null, "en"),
                null, null, null, null, null, null,
                "PREPAID", "mpi-9", null, "USD",
                null, "NONE", coldChain, false, false, false, false, false, true, false,
                null, null, null, null, null, null, null, null, null,
                Map.of("links", Map.of("msikaFlowSelectionRef", "SEL-9")), true,
                null, null,
                "IOT-SENS-9", minC, maxC);
        DeliveryRequestEntity d = service.createDelivery(
                UUID.randomUUID(), "national-spine", null, null, req, courier());
        UUID id = d.getDeliveryId();
        service.approve(id, new StatusChangeRequest("ok", null), courier());
        d.setStatus(DeliveryStatus.ASSIGNED.name());
        service.accept(id, courier());
        service.startPickup(id, new StatusChangeRequest("go", null), courier());
        service.confirmPickup(id, new StatusChangeRequest("picked", null), courier());
        service.startTransit(id, new StatusChangeRequest("moving", null), courier());
        gateway.calls.clear();
        outbox.clear();
        return id;
    }

    private ProofRequest signatureHandover(boolean markDelivered) {
        return new ProofRequest("DELIVERY", "SIGNATURE", "courier-1", null, "sig://ref", null, null,
                null, null, null, Map.of(), markDelivered, null, null, null, null, null, null);
    }

    // ─── Excursion unsuppressibility ────────────────────────────────────────

    @Test
    void createBindsSensorAndBand() {
        UUID id = coldDeliveryInTransit(new BigDecimal("2.00"), new BigDecimal("8.00"), true);
        DeliveryRequestEntity d = store.get(id);
        assertThat(d.getSensorDeviceRef()).isEqualTo("IOT-SENS-9");
        assertThat(d.getColdMinC()).isEqualByComparingTo("2.00");
        assertThat(d.getColdMaxC()).isEqualByComparingTo("8.00");
        assertThat(d.isExcursionFlagged()).isFalse();
    }

    @Test
    void inBandReading_appendsCustodyOnly_neverFlags() {
        UUID id = coldDeliveryInTransit(null, null, true); // default 2–8 °C band

        service.recordTemperatureReading(id, "IOT-SENS-9", new BigDecimal("5.4"),
                null, "IOT_BUS", null);

        assertThat(custody).anyMatch(c -> "TEMPERATURE_READING".equals(c.getEventKind())
                && c.getTemperatureC().compareTo(new BigDecimal("5.4")) == 0);
        assertThat(custody).noneMatch(c -> "TEMPERATURE_EXCURSION".equals(c.getEventKind()));
        assertThat(store.get(id).isExcursionFlagged()).isFalse();
        assertThat(exceptions).isEmpty();
    }

    @Test
    void bandBreach_alwaysWritesCustodyAndFlags_everyTime() {
        UUID id = coldDeliveryInTransit(null, null, true);

        service.recordTemperatureReading(id, "IOT-SENS-9", new BigDecimal("12.7"),
                null, "IOT_BUS", null);

        DeliveryRequestEntity d = store.get(id);
        assertThat(d.isExcursionFlagged()).isTrue();
        assertThat(custody).anyMatch(c -> "TEMPERATURE_EXCURSION".equals(c.getEventKind()));
        assertThat(exceptions).anyMatch(e -> "COLD_CHAIN_EXCURSION".equals(e.getExceptionKind())
                && "CRITICAL".equals(e.getSeverity()));
        assertThat(outbox).anyMatch(e ->
                NhumeEvents.DELIVERY_TEMPERATURE_EXCURSION.equals(e.getEventType()));

        // Unsuppressible: a second breach appends fresh custody evidence again —
        // the flag being already set never swallows evidence.
        service.recordTemperatureReading(id, "IOT-SENS-9", new BigDecimal("13.9"),
                null, "IOT_BUS", null);
        long excursionEvents = custody.stream()
                .filter(c -> "TEMPERATURE_EXCURSION".equals(c.getEventKind())).count();
        assertThat(excursionEvents).isEqualTo(2);
    }

    @Test
    void cargoSpecificBand_overridesDefault() {
        // Frozen product: −25…−15 °C. A reading fine for a fridge product breaches it.
        UUID id = coldDeliveryInTransit(new BigDecimal("-25"), new BigDecimal("-15"), true);

        service.recordTemperatureReading(id, "IOT-SENS-9", new BigDecimal("-20"),
                null, "IOT_BUS", null);
        assertThat(store.get(id).isExcursionFlagged()).isFalse();

        service.recordTemperatureReading(id, "IOT-SENS-9", new BigDecimal("5.0"),
                null, "IOT_BUS", null);
        assertThat(store.get(id).isExcursionFlagged()).isTrue();
    }

    @Test
    void manualCustodyTemperature_ridesTheSameUnsuppressiblePath() {
        UUID id = coldDeliveryInTransit(null, null, true);

        service.recordCustody(id, new ChainOfCustodyRequest("CHECKPOINT", null, "Courier",
                "Roadside", "SEAL-9", new BigDecimal("15.0"), "checkpoint", null,
                "device-A", null, null), courier());

        assertThat(store.get(id).isExcursionFlagged()).isTrue();
        assertThat(custody).anyMatch(c -> "TEMPERATURE_EXCURSION".equals(c.getEventKind()));
        assertThat(exceptions).anyMatch(e -> "COLD_CHAIN_EXCURSION".equals(e.getExceptionKind()));
    }

    // ─── Fail-closed handover refusal ───────────────────────────────────────

    @Test
    void flaggedDelivery_refusesHandover_untilDecisionRecorded() {
        UUID id = coldDeliveryInTransit(null, null, true);
        service.recordTemperatureReading(id, "IOT-SENS-9", new BigDecimal("12.0"),
                null, "IOT_BUS", null);

        assertThatThrownBy(() -> service.captureProof(id, signatureHandover(true), courier()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("COLD_CHAIN_STABILITY_DECISION_REQUIRED");
        assertThatThrownBy(() -> service.markDelivered(id,
                new StatusChangeRequest("try direct", null), courier()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("COLD_CHAIN_STABILITY_DECISION_REQUIRED");
        assertThat(store.get(id).getStatus()).isEqualTo(DeliveryStatus.IN_TRANSIT.name());
        assertThat(store.get(id).getDeliveredAt()).isNull();
    }

    @Test
    void useDecision_unblocksHandover() {
        UUID id = coldDeliveryInTransit(null, null, true);
        service.recordTemperatureReading(id, "IOT-SENS-9", new BigDecimal("9.5"),
                null, "IOT_BUS", null);

        service.recordStabilityDecision(id, "USE",
                "Excursion within product stability budget (30 min < 2 h allowance)", pharmacist());

        DeliveryRequestEntity d = store.get(id);
        assertThat(d.getStabilityDecision()).isEqualTo("USE");
        assertThat(d.getStabilityDecisionBy()).isEqualTo("pharmacist-1");
        assertThat(custody).anyMatch(c -> "STABILITY_DECISION".equals(c.getEventKind()));

        service.captureProof(id, signatureHandover(true), courier());
        assertThat(store.get(id).getStatus()).isEqualTo(DeliveryStatus.DELIVERED.name());
    }

    // ─── Quarantine → RETURNED → refund seam ────────────────────────────────

    @Test
    void quarantineDecision_returnsDelivery_andFiresSelectionWriteBack() {
        UUID id = coldDeliveryInTransit(null, null, true);
        service.recordTemperatureReading(id, "IOT-SENS-9", new BigDecimal("14.2"),
                null, "IOT_BUS", null);

        service.recordStabilityDecision(id, "QUARANTINE", "Outside stability budget", pharmacist());

        DeliveryRequestEntity d = store.get(id);
        assertThat(d.getStabilityDecision()).isEqualTo("QUARANTINE");
        assertThat(d.getStatus()).isEqualTo(DeliveryStatus.RETURNED.name());
        assertThat(custody).anyMatch(c -> "RETURN_INITIATED".equals(c.getEventKind()));
        // §12.7 — the RETURNED transition rides the EXISTING OF-B17 selection
        // write-back; msika-flow's refund seam sees the honest status.
        assertThat(gateway.calls).contains("MSIKA_FLOW_SELECTION:SEL-9:RETURNED");
        // Quarantined cargo can never be handed over afterwards.
        assertThatThrownBy(() -> service.captureProof(id, signatureHandover(true), courier()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("COLD_CHAIN_QUARANTINED");
    }

    // ─── Stability-decision matrix ──────────────────────────────────────────

    @Test
    void stabilityDecision_matrix_rejectsInvalidStates() {
        // No excursion → nothing to decide.
        UUID clean = coldDeliveryInTransit(null, null, true);
        assertThatThrownBy(() -> service.recordStabilityDecision(clean, "USE", null, pharmacist()))
                .hasMessageContaining("STABILITY_DECISION_WITHOUT_EXCURSION");

        // Not a cold delivery → not applicable.
        UUID warm = coldDeliveryInTransit(null, null, false);
        assertThatThrownBy(() -> service.recordStabilityDecision(warm, "USE", null, pharmacist()))
                .hasMessageContaining("STABILITY_DECISION_NOT_APPLICABLE");

        // Invalid decision value.
        UUID flagged = coldDeliveryInTransit(null, null, true);
        service.recordTemperatureReading(flagged, "IOT-SENS-9", new BigDecimal("11.0"),
                null, "IOT_BUS", null);
        assertThatThrownBy(() -> service.recordStabilityDecision(flagged, "MAYBE", null, pharmacist()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("STABILITY_DECISION_INVALID");

        // Append-only: a second decision is refused.
        service.recordStabilityDecision(flagged, "USE", null, pharmacist());
        assertThatThrownBy(() -> service.recordStabilityDecision(flagged, "QUARANTINE", null, pharmacist()))
                .hasMessageContaining("STABILITY_DECISION_ALREADY_RECORDED");
    }
}
