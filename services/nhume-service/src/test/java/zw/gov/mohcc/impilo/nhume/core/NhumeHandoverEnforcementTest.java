package zw.gov.mohcc.impilo.nhume.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.nhume.api.dto.AssignDeliveryRequest;
import zw.gov.mohcc.impilo.nhume.api.dto.CreateDeliveryRequest;
import zw.gov.mohcc.impilo.nhume.api.dto.ProofRequest;
import zw.gov.mohcc.impilo.nhume.api.dto.StatusChangeRequest;
import zw.gov.mohcc.impilo.nhume.domain.ChainOfCustodyEventEntity;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryAssignmentEntity;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryExceptionEntity;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryProofEntity;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryRequestEntity;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryStatus;
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
 * OF-B18 — end-to-end handover enforcement through the delivery service:
 * fail-closed DELIVERY_ATTEMPTED on verification failure, bounded reattempts
 * then RETURNED (§12.7), the DELIVERED proof gate, OTP truth generation at
 * creation, and the selection write-back legs (OF-B17 §12.8).
 */
class NhumeHandoverEnforcementTest {

    private final Map<UUID, DeliveryRequestEntity> store = new HashMap<>();
    private final List<DeliveryProofEntity> proofs = new ArrayList<>();
    private final List<ChainOfCustodyEventEntity> custody = new ArrayList<>();
    private final List<DeliveryExceptionEntity> exceptions = new ArrayList<>();
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
        FleetAssetRepository assetRepo = mock(FleetAssetRepository.class);
        DriverCourierProfileRepository courierRepo = mock(DriverCourierProfileRepository.class);
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
            custody.add(c);
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
        when(assignmentRepo.findFirstByDeliveryIdAndSupersededAtIsNullOrderByAssignedAtDesc(any(UUID.class)))
                .thenAnswer(inv -> Optional.of(assignmentFor(inv.getArgument(0, UUID.class))));
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
                mock(OutboxEventRepository.class), policyRepo, courierRepo, assetRepo,
                commsHub, new SimulatedNdilaClient(), mapper, writeBack, biometric,
                new DeliveryModeService(
                        mock(zw.gov.mohcc.impilo.nhume.repository.ModeCorridorRepository.class),
                        mock(zw.gov.mohcc.impilo.nhume.repository.AutonomousMissionRepository.class),
                        mock(DeliveryPackageRepository.class)));
    }

    private static DeliveryAssignmentEntity assignmentFor(UUID deliveryId) {
        DeliveryAssignmentEntity a = new DeliveryAssignmentEntity();
        a.setAssignmentId(UUID.randomUUID());
        a.setDeliveryId(deliveryId);
        return a;
    }

    private static TrustLayerGuard.ActorContext actor() {
        return new TrustLayerGuard.ActorContext(null, "courier-1", "COURIER",
                "LOGISTICS", null, null, null, null);
    }

    /** A marketplace-commitment delivery with the OF-B18 grade contract. */
    private UUID commitmentDelivery(boolean controlled, Integer maxAttempts) {
        CreateDeliveryRequest req = new CreateDeliveryRequest(
                "MARKETPLACE", "STANDARD", "MARKETPLACE_COMMITMENT",
                "msika-flow-service", "SYSTEM", null, null,
                new CreateDeliveryRequest.LocationDto("VENDOR", "01VENAAAAAAAAAAAAAAAAAAAAA",
                        "Vendor pharmacy", null, null, null),
                new CreateDeliveryRequest.LocationDto("ADDRESS", "selection:SEL-1",
                        "Patient home", "12 Herbert Chitepo Ave", -17.82, 31.05),
                new CreateDeliveryRequest.RecipientDto("PATIENT", "selection:SEL-1",
                        "Tariro Moyo", "+263771234567", null, "en"),
                null, null, null, null, null, null,
                "PREPAID", "mpi-1", null, "USD",
                null, "NONE", false, controlled, false, false, false, false, true, false,
                null, null, null, null, null, null, null, null, null,
                Map.of("links", Map.of("msikaFlowSelectionRef", "SEL-1")), true,
                "OTP_MATCH", true, null, null, null);
        DeliveryRequestEntity d = service.createDelivery(
                UUID.randomUUID(), "national-spine", null, null, req, actor());
        if (maxAttempts != null) {
            d.setMaxHandoverAttempts(maxAttempts);
        }
        UUID id = d.getDeliveryId();
        service.approve(id, new StatusChangeRequest("ok", null), actor());
        d.setStatus(DeliveryStatus.ASSIGNED.name());
        service.accept(id, actor());
        service.startPickup(id, new StatusChangeRequest("go", null), actor());
        service.confirmPickup(id, new StatusChangeRequest("picked", null), actor());
        service.startTransit(id, new StatusChangeRequest("moving", null), actor());
        gateway.calls.clear(); // pickup milestones are not under test here
        return id;
    }

    private ProofRequest handover(String otp, String receiverName, String idType, String idRef,
                                  boolean markDelivered) {
        return new ProofRequest("DELIVERY", "OTP", "courier-1", otp, null, null, null,
                null, null, null, Map.of(), markDelivered, null, null, null,
                receiverName, idType, idRef);
    }

    @Test
    void otpGradedContract_generatesServerHeldOtpAtCreation() {
        UUID id = commitmentDelivery(false, null);
        DeliveryRequestEntity d = store.get(id);
        assertThat(d.getRequiredPodGrade()).isEqualTo("OTP_MATCH");
        assertThat(d.isNamedRecipientRequired()).isTrue();
        assertThat(d.getHandoverOtp()).isNotNull().hasSize(6);
    }

    @Test
    void failedVerification_failsClosedToDeliveryAttempted_neverDelivered() {
        UUID id = commitmentDelivery(false, null);
        String otp = store.get(id).getHandoverOtp();

        DeliveryProofEntity proof = service.captureProof(id,
                handover(otp, "Someone Else", null, null, true), actor());

        assertThat(proof.isVerified()).isFalse();
        assertThat(proof.getFailureReason()).isEqualTo(HandoverGradePolicy.FAIL_NAMED_RECIPIENT_MISMATCH);
        assertThat(store.get(id).getStatus()).isEqualTo(DeliveryStatus.DELIVERY_ATTEMPTED.name());
        assertThat(store.get(id).getDeliveredAt()).isNull();
        assertThat(store.get(id).getHandoverAttempts()).isEqualTo(1);
        // Custody truth appended; ops-visible exception raised; selection told.
        assertThat(custody).anyMatch(c -> "HANDOVER_ATTEMPTED".equals(c.getEventKind()));
        assertThat(exceptions).anyMatch(e -> "HANDOVER_VERIFICATION_FAILED".equals(e.getExceptionKind()));
        assertThat(gateway.calls).contains("MSIKA_FLOW_SELECTION:SEL-1:DELIVERY_ATTEMPTED");
    }

    @Test
    void reattemptBudgetExhausted_returnsDelivery() {
        UUID id = commitmentDelivery(false, 2);
        String otp = store.get(id).getHandoverOtp();

        service.captureProof(id, handover(otp, "Wrong One", null, null, true), actor());
        assertThat(store.get(id).getStatus()).isEqualTo(DeliveryStatus.DELIVERY_ATTEMPTED.name());

        service.captureProof(id, handover(otp, "Wrong Again", null, null, true), actor());

        assertThat(store.get(id).getStatus()).isEqualTo(DeliveryStatus.RETURNED.name());
        assertThat(store.get(id).getHandoverAttempts()).isEqualTo(2);
        assertThat(gateway.calls).contains("MSIKA_FLOW_SELECTION:SEL-1:RETURNED");
    }

    @Test
    void verifiedHandover_deliversAndReportsGradeToSelection() {
        UUID id = commitmentDelivery(false, null);
        String otp = store.get(id).getHandoverOtp();

        DeliveryProofEntity proof = service.captureProof(id,
                handover(otp, "Tariro Moyo", null, null, true), actor());

        assertThat(proof.isVerified()).isTrue();
        assertThat(proof.getVerificationGrade()).isEqualTo("OTP_MATCH");
        assertThat(store.get(id).getStatus()).isEqualTo(DeliveryStatus.DELIVERED.name());
        assertThat(gateway.calls).contains("MSIKA_FLOW_SELECTION:SEL-1:DELIVERED:OTP_MATCH");
    }

    @Test
    void controlledCargo_refusesHandoverWithoutSecondFactor_acceptsOtpPlusId() {
        UUID id = commitmentDelivery(true, null);
        String otp = store.get(id).getHandoverOtp();

        DeliveryProofEntity otpOnly = service.captureProof(id,
                handover(otp, "Tariro Moyo", null, null, true), actor());
        assertThat(otpOnly.isVerified()).isFalse();
        assertThat(otpOnly.getFailureReason())
                .isEqualTo(HandoverGradePolicy.FAIL_CONTROLLED_SECOND_FACTOR_MISSING);
        assertThat(store.get(id).getStatus()).isEqualTo(DeliveryStatus.DELIVERY_ATTEMPTED.name());

        DeliveryProofEntity both = service.captureProof(id,
                handover(otp, "Tariro Moyo", "NATIONAL_ID", "63-123456A00", true), actor());
        assertThat(both.isVerified()).isTrue();
        assertThat(both.getVerificationGrade()).isEqualTo("OTP_PLUS_ID");
        assertThat(store.get(id).getStatus()).isEqualTo(DeliveryStatus.DELIVERED.name());
    }

    @Test
    void directMarkDelivered_isGatedOnAProvenProof_forGradedClassesOnly() {
        UUID id = commitmentDelivery(false, null);
        assertThatThrownBy(() -> service.markDelivered(id,
                new StatusChangeRequest("no proof", null), actor()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HANDOVER_PROOF_REQUIRED");

        // After a verified proof at grade, the direct sign-off is allowed.
        String otp = store.get(id).getHandoverOtp();
        service.captureProof(id, handover(otp, "Tariro Moyo", null, null, false), actor());
        service.markDelivered(id, new StatusChangeRequest("proven", null), actor());
        assertThat(store.get(id).getStatus()).isEqualTo(DeliveryStatus.DELIVERED.name());
    }
}
