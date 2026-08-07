package zw.gov.mohcc.impilo.madi.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.madi.domain.BloodOrderStatus;
import zw.gov.mohcc.impilo.madi.events.MadiEventEmitter;
import zw.gov.mohcc.impilo.madi.integration.OrosIntegration;
import zw.gov.mohcc.impilo.madi.persistence.entity.BloodOrderEntity;
import zw.gov.mohcc.impilo.madi.persistence.repository.*;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BloodOrderServiceTest {

    @Mock private BloodOrderRepository orderRepository;
    @Mock private BloodOrderItemRepository itemRepository;
    @Mock private BloodSampleRepository sampleRepository;
    @Mock private CrossmatchRequestRepository crossmatchRequestRepository;
    @Mock private CrossmatchResultRepository crossmatchResultRepository;
    @Mock private BloodReservationRepository reservationRepository;
    @Mock private BloodIssueRepository issueRepository;
    @Mock private BloodUnitService bloodUnitService;
    @Mock private OrosIntegration orosIntegration;
    @Mock private MadiEventEmitter eventEmitter;
    @Mock private BloodOrderSlaService slaService;
    @Mock private zw.gov.mohcc.impilo.sharedkernel.security.BreakGlassGrantClient grantClient;
    @Mock private zw.gov.mohcc.impilo.sharedkernel.security.UngovernedOverrideRecorder overrideRecorder;

    private BloodOrderService bloodOrderService;
    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        bloodOrderService = new BloodOrderService(orderRepository, itemRepository, sampleRepository,
                crossmatchRequestRepository, crossmatchResultRepository, reservationRepository,
                issueRepository, bloodUnitService, orosIntegration, eventEmitter, slaService,
                grantClient, overrideRecorder);
        // Default: the actor holds a real grant. The purpose-of-use alone no longer suffices.
        lenient().when(grantClient.check(any())).thenReturn(
                zw.gov.mohcc.impilo.sharedkernel.security.BreakGlassGrantCheck.valid(
                        "BREAK_GLASS_REQUEST", "grant-test"));
    }

    @Test
    void complete_transitionsIssuedOrderAndEmitsEvent() {
        UUID orderId = UUID.randomUUID();
        BloodOrderEntity order = new BloodOrderEntity();
        order.setOrderId(orderId);
        order.setTenantId(TENANT_ID);
        order.setStatus(BloodOrderStatus.ISSUED.name());
        when(orderRepository.findByOrderIdAndTenantId(orderId, TENANT_ID))
                .thenReturn(java.util.Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BloodOrderEntity completed = bloodOrderService.complete(
                TENANT_ID, orderId, "courier.moyo", "NHM-123");

        assertThat(completed.getStatus()).isEqualTo(BloodOrderStatus.COMPLETED.name());
        verify(eventEmitter).emit(eq("BLOOD_ORDER"), eq(orderId.toString()),
                eq("BLOOD_ORDER_COMPLETED"), anyString(), anyString(), anyMap(), eq(TENANT_ID));
    }

    @Test
    void complete_rejectsOrdersThatAreNotIssued() {
        UUID orderId = UUID.randomUUID();
        BloodOrderEntity order = new BloodOrderEntity();
        order.setOrderId(orderId);
        order.setTenantId(TENANT_ID);
        order.setStatus(BloodOrderStatus.RESERVED.name());
        when(orderRepository.findByOrderIdAndTenantId(orderId, TENANT_ID))
                .thenReturn(java.util.Optional.of(order));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        bloodOrderService.complete(TENANT_ID, orderId, "courier.moyo", "NHM-123"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ISSUED");
    }

    @Test
    void createOrder_setsDraftStatus() {
        BloodOrderEntity order = new BloodOrderEntity();
        order.setTenantId(TENANT_ID);
        order.setPatientCpid("CPID-99");
        order.setBloodGroup("A+");
        order.setComponentType("RED_CELLS");
        when(orderRepository.save(any())).thenAnswer(inv -> {
            BloodOrderEntity o = inv.getArgument(0);
            o.setOrderId(UUID.randomUUID());
            return o;
        });

        BloodOrderEntity saved = bloodOrderService.createOrder(order, Collections.emptyList());

        assertThat(saved.getStatus()).isEqualTo(BloodOrderStatus.DRAFT.name());
        verify(eventEmitter).emit(eq("BLOOD_ORDER"), anyString(), eq("ORDER_CREATED"), anyString(), anyString(), anyMap(), eq(TENANT_ID));
    }

    @Test
    void submit_movesToSubmitted() {
        UUID orderId = UUID.randomUUID();
        BloodOrderEntity order = new BloodOrderEntity();
        order.setOrderId(orderId);
        order.setStatus(BloodOrderStatus.DRAFT.name());
        order.setOrosOrderRef("OROS-1");
        when(orderRepository.findByOrderIdAndTenantId(orderId, TENANT_ID)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BloodOrderEntity result = bloodOrderService.submit(TENANT_ID, orderId);

        assertThat(result.getStatus()).isEqualTo(BloodOrderStatus.SUBMITTED.name());
        verify(orosIntegration).notifyOrderSubmitted("OROS-1", orderId.toString());
        // Submit starts the crossmatch SLA timer.
        verify(slaService).start(TENANT_ID, orderId, BloodOrderSlaService.STAGE_CROSSMATCH);
    }

    @Test
    void crossmatch_returnsResultToOros() {
        UUID orderId = UUID.randomUUID();
        BloodOrderEntity order = new BloodOrderEntity();
        order.setOrderId(orderId);
        order.setTenantId(TENANT_ID);
        order.setStatus(BloodOrderStatus.SAMPLE_COLLECTED.name());
        order.setOrosOrderRef("OROS-7");
        when(orderRepository.findByOrderIdAndTenantId(orderId, TENANT_ID)).thenReturn(Optional.of(order));
        when(crossmatchRequestRepository.save(any())).thenAnswer(inv -> {
            var r = (zw.gov.mohcc.impilo.madi.persistence.entity.CrossmatchRequestEntity) inv.getArgument(0);
            r.setRequestId(UUID.randomUUID());
            return r;
        });
        when(crossmatchResultRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        bloodOrderService.crossmatch(TENANT_ID, orderId, UUID.randomUUID(), UUID.randomUUID(),
                zw.gov.mohcc.impilo.madi.domain.CrossmatchResultStatus.INCOMPATIBLE, "tech-1", UUID.randomUUID());

        // The incompatible result is returned to OROS (which flags it critical for the requester).
        verify(orosIntegration).notifyCrossmatchResult("OROS-7", "INCOMPATIBLE", null);
    }

    // ════════════════════════════════════════════════════════════════════
    // Emergency (O-neg / uncrossmatched) release — the three grant branches
    //
    // This is the path the Phase 0 exposure was actually about. Before the grant check, setting
    // X-Purpose-Of-Use: BREAK_GLASS was sufficient to release uncrossmatched blood.
    // ════════════════════════════════════════════════════════════════════

    private UUID stubReleasableOrder() {
        UUID orderId = UUID.randomUUID();
        BloodOrderEntity order = new BloodOrderEntity();
        order.setOrderId(orderId);
        order.setTenantId(TENANT_ID);
        order.setPatientCpid("HID-42");
        order.setStatus(BloodOrderStatus.SAMPLE_COLLECTED.name());
        when(orderRepository.findByOrderIdAndTenantId(orderId, TENANT_ID)).thenReturn(Optional.of(order));
        lenient().when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        return orderId;
    }

    @Test
    void emergencyRelease_withValidGrant_issuesTheUnit() {
        UUID orderId = stubReleasableOrder();

        BloodOrderEntity released = bloodOrderService.emergencyRelease(
                TENANT_ID, orderId, "EMERGENCY", "clinician-7", "trauma", "UNIT-1");

        assertThat(released.getStatus()).isEqualTo(BloodOrderStatus.ISSUED.name());
        verifyNoInteractions(overrideRecorder);
    }

    @Test
    void emergencyRelease_withNoGrant_isRefusedDespiteEmergencyPurpose() {
        UUID orderId = stubReleasableOrder();
        when(grantClient.check(any())).thenReturn(
                zw.gov.mohcc.impilo.sharedkernel.security.BreakGlassGrantCheck.noGrant());

        // The forged-purpose hole closing on the exact path it mattered on.
        assertThatThrownBy(() -> bloodOrderService.emergencyRelease(
                TENANT_ID, orderId, "BREAK_GLASS", "clinician-7", "trauma", "UNIT-1"))
                .isInstanceOf(zw.gov.mohcc.impilo.sharedkernel.security
                        .EmergencyAccessGuard.EmergencyAccessDeniedException.class);

        verify(orderRepository, never()).save(any());
        verifyNoInteractions(overrideRecorder);
    }

    @Test
    void emergencyRelease_whenTrustPlaneUnreachable_releasesAndRecordsAnUngovernedOverride() {
        UUID orderId = stubReleasableOrder();
        when(grantClient.check(any())).thenReturn(
                zw.gov.mohcc.impilo.sharedkernel.security.BreakGlassGrantCheck
                        .unreachable("connect timed out after 2000ms"));

        BloodOrderEntity released = bloodOrderService.emergencyRelease(
                TENANT_ID, orderId, "EMERGENCY", "clinician-7", "trauma", "UNIT-1");

        // Emergency care is not blocked by a policy-service outage — the PO ruling.
        assertThat(released.getStatus()).isEqualTo(BloodOrderStatus.ISSUED.name());

        // And the price of that allowance is paid: a durable record naming the actor, the action
        // and why nothing could be validated. Asserting the release alone would pass even if the
        // override happened with no trace, which is the defect this exists to prevent.
        var captor = org.mockito.ArgumentCaptor.forClass(
                zw.gov.mohcc.impilo.sharedkernel.security.UngovernedOverride.class);
        verify(overrideRecorder).record(captor.capture());
        assertThat(captor.getValue().actorId()).isEqualTo("clinician-7");
        assertThat(captor.getValue().action()).isEqualTo("O_NEG_EMERGENCY_RELEASE");
        assertThat(captor.getValue().subjectRef()).isEqualTo("HID-42");
        assertThat(captor.getValue().reason()).contains("timed out");
        assertThat(captor.getValue().reviewObligation()).isNotBlank();
    }

    @Test
    void emergencyRelease_underNonEmergencyPurpose_isStillRefusedWithoutConsultingTheTrustPlane() {
        UUID orderId = stubReleasableOrder();

        assertThatThrownBy(() -> bloodOrderService.emergencyRelease(
                TENANT_ID, orderId, "TREATMENT", "clinician-7", "routine", "UNIT-1"))
                .isInstanceOf(zw.gov.mohcc.impilo.sharedkernel.security
                        .EmergencyAccessGuard.EmergencyAccessDeniedException.class);

        verifyNoInteractions(grantClient, overrideRecorder);
    }
}
