package zw.gov.mohcc.impilo.oros.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import zw.gov.mohcc.impilo.oros.domain.OrderPriority;
import zw.gov.mohcc.impilo.oros.domain.OrderStatus;
import zw.gov.mohcc.impilo.oros.domain.OrderType;
import zw.gov.mohcc.impilo.oros.domain.RequestSource;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderVersionEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.OrderItemRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.OrderRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.OrderVersionRepository;
import zw.gov.mohcc.impilo.oros.testsupport.OrosTestObjectMapper;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link OrderLifecycleService} (OF-B1): amendment-as-new-version,
 * supersedes chain, hold/resume, revoke, replace, error-mark, and the
 * concurrent-amend race surfacing as {@code AMEND_CONFLICT}.
 */
@ExtendWith(MockitoExtension.class)
class OrderLifecycleServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private OrderVersionRepository versionRepository;

    private OrderLifecycleService service;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID FACILITY_ID = UUID.randomUUID();
    private static final String ORDER_ID = "01ARZ3NDEKTSV4RRFFQ69G5FAV";

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = OrosTestObjectMapper.create();
        OrderVersionWriter versionWriter =
                new OrderVersionWriter(versionRepository, orderItemRepository, objectMapper);
        OrderStateMachine stateMachine = new OrderStateMachine(
                orderRepository, orderItemRepository, outboxRepository, objectMapper, versionWriter);
        service = new OrderLifecycleService(stateMachine, versionWriter, orderRepository,
                orderItemRepository, versionRepository, objectMapper);
    }

    private TrustContext ctx() {
        return new TrustContext(TENANT_ID, "clinician-001", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), FACILITY_ID, null, null, AccessMode.INTERNAL);
    }

    private OrderEntity order(OrderStatus status) {
        OrderEntity order = new OrderEntity();
        order.setOrderId(ORDER_ID);
        order.setTenantId(TENANT_ID);
        order.setFacilityId(FACILITY_ID);
        order.setPatientCpid("CPID-1");
        order.setOrderType(OrderType.PHARMACY);
        order.setPriority(OrderPriority.ROUTINE);
        order.setStatus(status);
        order.setPlacedBy("clinician-001");
        order.setRequestSource(RequestSource.INTERNAL);
        return order;
    }

    private void stubOrder(OrderEntity order) {
        when(orderRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(order));
        lenient().when(orderRepository.save(any(OrderEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(versionRepository.save(any(OrderVersionEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Nested
    @DisplayName("Amendment = new immutable version")
    class Amendment {

        @Test
        void amendPlacedOrderCreatesVersionWithSupersedesLink() {
            OrderEntity order = order(OrderStatus.PLACED);
            stubOrder(order);
            OrderVersionEntity head = new OrderVersionEntity();
            head.setOrderVersionId("01HEADVERSIONXXXXXXXXXXXXX");
            head.setVersion(1);
            when(versionRepository.findFirstByOrderIdOrderByVersionDesc(ORDER_ID))
                    .thenReturn(Optional.of(head));

            try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
                holder.when(TrustContextHolder::require).thenReturn(ctx());

                OrderVersionEntity v2 = service.amend(ORDER_ID, new OrderLifecycleService.AmendRequest(
                        "Updated dosage guidance", null, null, null, null, "Clarification from pharmacy"));

                assertThat(v2.getVersion()).isEqualTo(2);
                assertThat(v2.getSupersedesVersionId()).isEqualTo("01HEADVERSIONXXXXXXXXXXXXX");
                assertThat(v2.getContentHash()).hasSize(64);
                assertThat(v2.getReason()).isEqualTo("Clarification from pharmacy");
                assertThat(order.getClinicalNotes()).isEqualTo("Updated dosage guidance");
                // ORDER_AMENDED event written to the outbox
                verify(outboxRepository).save(argThat(e -> "ORDER_AMENDED".equals(e.getEventType())));
            }
        }

        @Test
        void amendRejectedInTerminalStatus() {
            stubOrderReadOnly(order(OrderStatus.CANCELLED));
            try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
                holder.when(TrustContextHolder::require).thenReturn(ctx());
                assertThatThrownBy(() -> service.amend(ORDER_ID,
                        new OrderLifecycleService.AmendRequest(null, null, null, null, null, "reason")))
                        .isInstanceOf(OrosDomainException.class)
                        .hasMessageContaining("cannot be amended");
            }
        }

        @Test
        void amendWithoutReasonRejected() {
            stubOrderReadOnly(order(OrderStatus.PLACED));
            try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
                holder.when(TrustContextHolder::require).thenReturn(ctx());
                assertThatThrownBy(() -> service.amend(ORDER_ID,
                        new OrderLifecycleService.AmendRequest(null, null, null, null, null, " ")))
                        .isInstanceOf(OrosDomainException.class)
                        .hasMessageContaining("reason");
            }
        }

        @Test
        void concurrentAmendRaceSurfacesAsAmendConflict() {
            OrderEntity order = order(OrderStatus.PLACED);
            stubOrder(order);
            when(versionRepository.findFirstByOrderIdOrderByVersionDesc(ORDER_ID))
                    .thenReturn(Optional.empty());
            // The UNIQUE(order_id, version) loser sees a constraint violation
            when(versionRepository.save(any(OrderVersionEntity.class)))
                    .thenThrow(new DataIntegrityViolationException("uq_oros_order_versions"));

            try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
                holder.when(TrustContextHolder::require).thenReturn(ctx());
                assertThatThrownBy(() -> service.amend(ORDER_ID,
                        new OrderLifecycleService.AmendRequest("x", null, null, null, null, "r")))
                        .isInstanceOf(OrosDomainException.class)
                        .hasMessageContaining("amended concurrently");
            }
        }

        private void stubOrderReadOnly(OrderEntity order) {
            when(orderRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(order));
        }
    }

    @Nested
    @DisplayName("Hold / resume (T4/T5)")
    class HoldResume {

        @Test
        void holdRemembersPriorStateAndResumeReturnsToIt() {
            OrderEntity order = order(OrderStatus.ACCEPTED);
            stubOrder(order);
            try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
                holder.when(TrustContextHolder::require).thenReturn(ctx());

                service.hold(ORDER_ID, "Prior authorisation pending");
                assertThat(order.getStatus()).isEqualTo(OrderStatus.ON_HOLD);
                assertThat(order.getHoldPriorStatus()).isEqualTo("ACCEPTED");
                assertThat(order.getLifecycleReason()).isEqualTo("Prior authorisation pending");

                service.resume(ORDER_ID);
                assertThat(order.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
                assertThat(order.getHoldPriorStatus()).isNull();
            }
        }

        @Test
        void holdRejectedFromDraft() {
            when(orderRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(order(OrderStatus.DRAFT)));
            try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
                holder.when(TrustContextHolder::require).thenReturn(ctx());
                assertThatThrownBy(() -> service.hold(ORDER_ID, "r"))
                        .isInstanceOf(OrosDomainException.class)
                        .hasMessageContaining("cannot be held");
            }
        }

        @Test
        void resumeRejectedWhenNotOnHold() {
            when(orderRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(order(OrderStatus.PLACED)));
            try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
                holder.when(TrustContextHolder::require).thenReturn(ctx());
                assertThatThrownBy(() -> service.resume(ORDER_ID))
                        .isInstanceOf(OrosDomainException.class)
                        .hasMessageContaining("not on hold");
            }
        }
    }

    @Nested
    @DisplayName("Revoke / replace / error-mark (T6/T7/T9)")
    class TerminalLifecycle {

        @Test
        void revokeIsTerminalWithReason() {
            OrderEntity order = order(OrderStatus.PLACED);
            stubOrder(order);
            try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
                holder.when(TrustContextHolder::require).thenReturn(ctx());
                service.revoke(ORDER_ID, "Clinically contraindicated");
                assertThat(order.getStatus()).isEqualTo(OrderStatus.REVOKED);
                assertThat(order.getLifecycleReason()).isEqualTo("Clinically contraindicated");
            }
        }

        @Test
        void replaceCreatesSuccessorDraftAndTerminatesPredecessor() {
            OrderEntity predecessor = order(OrderStatus.PLACED);
            stubOrder(predecessor);
            when(orderItemRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());

            try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
                holder.when(TrustContextHolder::require).thenReturn(ctx());

                OrderEntity successor = service.replace(ORDER_ID, "Dosage change after review");

                assertThat(successor.getOrderId()).isNotEqualTo(ORDER_ID);
                assertThat(successor.getStatus()).isEqualTo(OrderStatus.DRAFT);
                assertThat(successor.getExternalRefs()).contains("\"replaces\":\"" + ORDER_ID + "\"");
                assertThat(predecessor.getStatus()).isEqualTo(OrderStatus.REPLACED);
                assertThat(predecessor.getExternalRefs()).contains("replacedBy");
                verify(outboxRepository).save(argThat(e -> "ORDER_REPLACED".equals(e.getEventType())));
            }
        }

        @Test
        void errorMarkRejectedOnceFulfilmentProgressed() {
            when(orderRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(order(OrderStatus.IN_PROGRESS)));
            try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
                holder.when(TrustContextHolder::require).thenReturn(ctx());
                assertThatThrownBy(() -> service.markEnteredInError(ORDER_ID, "wrong patient"))
                        .isInstanceOf(OrosDomainException.class)
                        .hasMessageContaining("cannot be error-marked");
            }
        }

        @Test
        void errorMarkAllowedPreFulfilment() {
            OrderEntity order = order(OrderStatus.PLACED);
            stubOrder(order);
            try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
                holder.when(TrustContextHolder::require).thenReturn(ctx());
                service.markEnteredInError(ORDER_ID, "wrong patient selected");
                assertThat(order.getStatus()).isEqualTo(OrderStatus.ENTERED_IN_ERROR);
            }
        }
    }
}
