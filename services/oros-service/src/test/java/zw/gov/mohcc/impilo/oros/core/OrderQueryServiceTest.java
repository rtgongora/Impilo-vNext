package zw.gov.mohcc.impilo.oros.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.oros.domain.ImagingWorkflowState;
import zw.gov.mohcc.impilo.oros.domain.OrderStatus;
import zw.gov.mohcc.impilo.oros.domain.OrderType;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.OrderRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderQueryServiceTest {

    @Mock private OrderRepository orderRepository;

    private static final UUID TENANT_ID = UUID.randomUUID();

    private TrustContext ctx() {
        return new TrustContext(TENANT_ID, "actor-1", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, AccessMode.INTERNAL);
    }

    @Test
    @DisplayName("search scopes to tenant and normalizes blank filters to null")
    void searchScopesAndNormalizes() {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(ctx());
            OrderQueryService service = new OrderQueryService(orderRepository);
            when(orderRepository.search(TENANT_ID, "CPID-1", null, OrderStatus.PLACED, OrderType.IMAGING, null))
                    .thenReturn(List.of(new OrderEntity()));

            List<OrderEntity> result = service.search("CPID-1", "  ", OrderStatus.PLACED, OrderType.IMAGING);

            assertThat(result).hasSize(1);
            verify(orderRepository).search(TENANT_ID, "CPID-1", null, OrderStatus.PLACED, OrderType.IMAGING, null);
        }
    }

    @Test
    @DisplayName("imagingWorklist defaults to active pre-report states when none supplied")
    @SuppressWarnings("unchecked")
    void imagingWorklistDefaults() {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(ctx());
            OrderQueryService service = new OrderQueryService(orderRepository);
            when(orderRepository.findByTenantIdAndFacilityIdAndOrderTypeAndImagingStateInOrderByUpdatedAtDesc(
                    eq(TENANT_ID), any(), eq(OrderType.IMAGING), any()))
                    .thenReturn(List.of(new OrderEntity()));

            List<OrderEntity> result = service.imagingWorklist(null);

            assertThat(result).hasSize(1);
            ArgumentCaptor<Collection<ImagingWorkflowState>> captor = ArgumentCaptor.forClass(Collection.class);
            verify(orderRepository).findByTenantIdAndFacilityIdAndOrderTypeAndImagingStateInOrderByUpdatedAtDesc(
                    eq(TENANT_ID), any(), eq(OrderType.IMAGING), captor.capture());
            assertThat(captor.getValue()).contains(
                    ImagingWorkflowState.RECEIVED, ImagingWorkflowState.IN_PROGRESS);
        }
    }
}
