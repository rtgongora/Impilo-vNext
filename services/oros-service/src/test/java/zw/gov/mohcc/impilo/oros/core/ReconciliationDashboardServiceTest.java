package zw.gov.mohcc.impilo.oros.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.oros.domain.DiagnosticReconcileBucket;
import zw.gov.mohcc.impilo.oros.domain.ImagingWorkflowState;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.ResultEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.OrderRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.ResultRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReconciliationDashboardServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private ResultRepository resultRepository;

    private static final UUID TENANT_ID = UUID.randomUUID();

    private TrustContext ctx() {
        return new TrustContext(TENANT_ID, "ops-1", "PROVIDER", "OPERATIONS",
                null, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, AccessMode.INTERNAL);
    }

    @Test
    @DisplayName("summary returns a count for every bucket plus the unacknowledged-critical count")
    void summaryCoversAllBuckets() {
        try (MockedStatic<TrustContextHolder> h = mockStatic(TrustContextHolder.class)) {
            h.when(TrustContextHolder::require).thenReturn(ctx());
            ReconciliationDashboardService service = new ReconciliationDashboardService(orderRepository, resultRepository);
            when(orderRepository.stuckImaging(eq(TENANT_ID), any(), anyCollection(), any()))
                    .thenReturn(List.of(new OrderEntity()));
            when(resultRepository.criticalUnacknowledged(TENANT_ID))
                    .thenReturn(List.of(new ResultEntity(), new ResultEntity()));

            Map<String, Long> summary = service.summary(null, null);

            for (DiagnosticReconcileBucket b : DiagnosticReconcileBucket.values()) {
                assertThat(summary).containsEntry(b.name(), 1L);
            }
            assertThat(summary).containsEntry("CRITICAL_UNACKNOWLEDGED", 2L);
        }
    }

    @Test
    @DisplayName("stuck with olderThanMinutes passes a non-null age threshold to the query")
    void stuckAppliesAgeThreshold() {
        try (MockedStatic<TrustContextHolder> h = mockStatic(TrustContextHolder.class)) {
            h.when(TrustContextHolder::require).thenReturn(ctx());
            ReconciliationDashboardService service = new ReconciliationDashboardService(orderRepository, resultRepository);
            when(orderRepository.stuckImaging(eq(TENANT_ID), isNull(), anyCollection(), any(OffsetDateTime.class)))
                    .thenReturn(List.of());

            service.stuck(DiagnosticReconcileBucket.RECEIVED_NOT_ACCEPTED, null, 60);

            verify(orderRepository).stuckImaging(eq(TENANT_ID), isNull(),
                    eq(DiagnosticReconcileBucket.RECEIVED_NOT_ACCEPTED.states()), any(OffsetDateTime.class));
        }
    }

    @Test
    @DisplayName("workloadByState maps grouped count rows into an enum-keyed map")
    void workloadByStateMapsRows() {
        try (MockedStatic<TrustContextHolder> h = mockStatic(TrustContextHolder.class)) {
            h.when(TrustContextHolder::require).thenReturn(ctx());
            ReconciliationDashboardService service = new ReconciliationDashboardService(orderRepository, resultRepository);
            when(orderRepository.imagingStateCounts(TENANT_ID, null)).thenReturn(List.of(
                    new Object[]{ImagingWorkflowState.RECEIVED, 3L},
                    new Object[]{ImagingWorkflowState.FINAL_REPORT, 1L}));

            Map<ImagingWorkflowState, Long> byState = service.workloadByState(null);

            assertThat(byState).containsEntry(ImagingWorkflowState.RECEIVED, 3L)
                    .containsEntry(ImagingWorkflowState.FINAL_REPORT, 1L);
        }
    }
}
