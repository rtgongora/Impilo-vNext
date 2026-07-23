package zw.gov.mohcc.impilo.pharmacy.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.pharmacy.domain.ClarificationStatus;
import zw.gov.mohcc.impilo.pharmacy.domain.DispenseItemStatus;
import zw.gov.mohcc.impilo.pharmacy.domain.DispenseStatus;
import zw.gov.mohcc.impilo.pharmacy.domain.OrderPriority;
import zw.gov.mohcc.impilo.pharmacy.integration.OrosIntegration;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.DispenseItemEntity;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.DispenseOrderEntity;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.ClarificationRequestRepository;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.DispenseItemRepository;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.DispenseOrderRepository;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * OF-B12 — prescription-claim ↔ dispense-episode linkage on the REAL engine
 * (§13.3.1): claim stored at creation, episode-ref bound onto the OROS claim at
 * completion (honest failure = anomaly event), pre-dispense cancellation
 * releases the claim (compensation), post-dispense cancellation never does,
 * and a PENDING clarification holds the episode fail-closed (OF-B15 seam).
 */
@ExtendWith(MockitoExtension.class)
class DispenseClaimLinkageTest {

    @Mock DispenseOrderRepository orderRepository;
    @Mock DispenseItemRepository itemRepository;
    @Mock StockLedgerService stockLedgerService;
    @Mock EventOutboxRepository outboxRepository;
    @Mock OrosIntegration orosIntegration;
    @Mock ClarificationRequestRepository clarificationRepository;

    private DispenseEngineImpl engine;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID FACILITY_ID = UUID.randomUUID();
    private static final UUID CLAIM_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        engine = new DispenseEngineImpl(orderRepository, itemRepository, stockLedgerService,
                outboxRepository, new ObjectMapper(), orosIntegration, clarificationRepository);
        TrustContextHolder.set(new TrustContext(TENANT_ID, "pharmacist-1", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), FACILITY_ID, null, null, AccessMode.INTERNAL));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private DispenseOrderEntity order(DispenseStatus status, UUID claimId) {
        DispenseOrderEntity order = new DispenseOrderEntity();
        order.setOrderId(UUID.randomUUID());
        order.setTenantId(TENANT_ID);
        order.setFacilityId(FACILITY_ID);
        order.setPatientCpid("CPID-1");
        order.setOrosOrderId("01OROSORDERAAAAAAAAAAAAAAA");
        order.setPriority(OrderPriority.ROUTINE);
        order.setStatus(status);
        order.setPrescriptionClaimId(claimId);
        when(orderRepository.findById(order.getOrderId())).thenReturn(Optional.of(order));
        return order;
    }

    private DispenseItemEntity pickedItem(UUID orderId) {
        DispenseItemEntity item = new DispenseItemEntity();
        item.setItemId(UUID.randomUUID());
        item.setDispenseOrderId(orderId);
        item.setDrugCode("PARA-500");
        item.setQtyRequested(10);
        item.setQtyDispensed(10);
        item.setStatus(DispenseItemStatus.PICKED);
        return item;
    }

    @Test
    @DisplayName("createFromOrosOrder stores the prescription claim id on the episode")
    void createStoresClaimId() {
        when(orderRepository.save(any())).thenAnswer(inv -> {
            DispenseOrderEntity o = inv.getArgument(0);
            if (o.getOrderId() == null) {
                o.setOrderId(UUID.randomUUID());
            }
            return o;
        });

        DispenseOrderEntity created = engine.createFromOrosOrder(
                "01OROSORDERAAAAAAAAAAAAAAA", "CPID-1", FACILITY_ID, null,
                List.of(new DispenseEngine.OrderItemData("PARA-500", "Paracetamol", 10, null)),
                CLAIM_ID);

        assertThat(created.getPrescriptionClaimId()).isEqualTo(CLAIM_ID);
    }

    @Test
    @DisplayName("completeDispense binds the episode ref onto the OROS claim")
    void completeBindsClaim() {
        DispenseOrderEntity order = order(DispenseStatus.READY, CLAIM_ID);
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(itemRepository.findByDispenseOrderIdOrderByCreatedAtAsc(order.getOrderId()))
                .thenReturn(List.of(pickedItem(order.getOrderId())));
        when(clarificationRepository.existsByDispenseOrderIdAndStatus(
                order.getOrderId(), ClarificationStatus.PENDING)).thenReturn(false);
        when(orosIntegration.bindClaimEpisode(CLAIM_ID, order.getOrderId().toString())).thenReturn(true);

        DispenseOrderEntity completed = engine.completeDispense(order.getOrderId());

        assertThat(completed.getStatus()).isEqualTo(DispenseStatus.DISPENSED);
        assertThat(completed.getClaimBoundAt()).isNotNull();
        verify(orosIntegration).bindClaimEpisode(CLAIM_ID, order.getOrderId().toString());
    }

    @Test
    @DisplayName("bind failure keeps claim_bound_at null and emits a coded anomaly event")
    void bindFailureEmitsAnomaly() {
        DispenseOrderEntity order = order(DispenseStatus.READY, CLAIM_ID);
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(itemRepository.findByDispenseOrderIdOrderByCreatedAtAsc(order.getOrderId()))
                .thenReturn(List.of());
        when(clarificationRepository.existsByDispenseOrderIdAndStatus(
                order.getOrderId(), ClarificationStatus.PENDING)).thenReturn(false);
        when(orosIntegration.bindClaimEpisode(eq(CLAIM_ID), any())).thenReturn(false);

        DispenseOrderEntity completed = engine.completeDispense(order.getOrderId());

        assertThat(completed.getClaimBoundAt()).isNull();
        verify(outboxRepository).save(argThat(e ->
                "DISPENSE_CLAIM_ANOMALY".equals(e.getEventType())
                        && e.getPayload().contains("CLAIM_BIND_FAILED")));
    }

    @Test
    @DisplayName("no-claim episode never calls the bind seam")
    void noClaimNoBind() {
        DispenseOrderEntity order = order(DispenseStatus.READY, null);
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(itemRepository.findByDispenseOrderIdOrderByCreatedAtAsc(order.getOrderId()))
                .thenReturn(List.of());
        when(clarificationRepository.existsByDispenseOrderIdAndStatus(
                order.getOrderId(), ClarificationStatus.PENDING)).thenReturn(false);

        engine.completeDispense(order.getOrderId());

        verify(orosIntegration, never()).bindClaimEpisode(any(), any());
    }

    @Test
    @DisplayName("pre-dispense cancellation releases the claim (compensation, §9.2)")
    void preDispenseCancelReleasesClaim() {
        DispenseOrderEntity order = order(DispenseStatus.ACCEPTED, CLAIM_ID);
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(itemRepository.findByDispenseOrderIdAndStatus(order.getOrderId(), DispenseItemStatus.PENDING))
                .thenReturn(List.of());
        when(orosIntegration.releaseClaim(eq(CLAIM_ID), any())).thenReturn(true);

        DispenseOrderEntity cancelled = engine.cancelOrder(order.getOrderId());

        assertThat(cancelled.getStatus()).isEqualTo(DispenseStatus.CANCELLED);
        assertThat(cancelled.getClaimReleasedAt()).isNotNull();
        verify(orosIntegration).releaseClaim(eq(CLAIM_ID), any());
    }

    @Test
    @DisplayName("post-dispense cancellation NEVER releases the claim")
    void postDispenseCancelDoesNotRelease() {
        DispenseOrderEntity order = order(DispenseStatus.DISPENSED, CLAIM_ID);
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(itemRepository.findByDispenseOrderIdAndStatus(order.getOrderId(), DispenseItemStatus.PENDING))
                .thenReturn(List.of());

        DispenseOrderEntity cancelled = engine.cancelOrder(order.getOrderId());

        assertThat(cancelled.getStatus()).isEqualTo(DispenseStatus.CANCELLED);
        assertThat(cancelled.getClaimReleasedAt()).isNull();
        verify(orosIntegration, never()).releaseClaim(any(), any());
    }

    @Test
    @DisplayName("failed claim release emits a coded anomaly event (honest, ops-visible)")
    void releaseFailureEmitsAnomaly() {
        DispenseOrderEntity order = order(DispenseStatus.ACCEPTED, CLAIM_ID);
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(itemRepository.findByDispenseOrderIdAndStatus(order.getOrderId(), DispenseItemStatus.PENDING))
                .thenReturn(List.of());
        when(orosIntegration.releaseClaim(eq(CLAIM_ID), any())).thenReturn(false);

        DispenseOrderEntity cancelled = engine.cancelOrder(order.getOrderId());

        assertThat(cancelled.getClaimReleasedAt()).isNull();
        verify(outboxRepository).save(argThat(e ->
                "DISPENSE_CLAIM_ANOMALY".equals(e.getEventType())
                        && e.getPayload().contains("CLAIM_RELEASE_FAILED")));
    }

    @Test
    @DisplayName("OF-B15: a PENDING clarification holds the episode fail-closed")
    void pendingClarificationHoldsEpisode() {
        DispenseOrderEntity order = order(DispenseStatus.READY, null);
        when(clarificationRepository.existsByDispenseOrderIdAndStatus(
                order.getOrderId(), ClarificationStatus.PENDING)).thenReturn(true);

        assertThatThrownBy(() -> engine.completeDispense(order.getOrderId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("clarification is pending");

        assertThat(order.getStatus()).isEqualTo(DispenseStatus.READY);
        assertThatThrownBy(() -> engine.partialDispense(order.getOrderId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("clarification is pending");

        OffsetDateTime completedAt = order.getCompletedAt();
        assertThat(completedAt).isNull();
    }
}
