package zw.gov.mohcc.impilo.obs.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.obs.persistence.entity.DashboardEntity;
import zw.gov.mohcc.impilo.obs.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.obs.persistence.repository.DashboardRepository;
import zw.gov.mohcc.impilo.obs.persistence.repository.EventOutboxRepository;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private DashboardRepository dashboardRepository;
    @Mock private EventOutboxRepository outboxRepository;

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(dashboardRepository, outboxRepository);
    }

    @Nested
    @DisplayName("Create Dashboard")
    class CreateDashboard {

        @Test
        void createsAndSavesDashboard() {
            UUID tenantId = UUID.randomUUID();
            DashboardEntity saved = new DashboardEntity();
            saved.setId(1L);
            saved.setTenantId(tenantId);
            saved.setName("Ops Dashboard");
            saved.setDashboardType(DashboardType.GRAFANA);
            saved.setCreatedBy("admin");

            when(dashboardRepository.save(any(DashboardEntity.class))).thenReturn(saved);

            DashboardEntity result = dashboardService.createDashboard(
                    tenantId, "admin", UUID.randomUUID(),
                    "Ops Dashboard", "Main ops dashboard",
                    DashboardType.GRAFANA, "{}");

            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getName()).isEqualTo("Ops Dashboard");
        }

        @Test
        void publishesOutboxEvent() {
            UUID tenantId = UUID.randomUUID();
            DashboardEntity saved = new DashboardEntity();
            saved.setId(2L);
            saved.setTenantId(tenantId);
            saved.setName("Test");
            saved.setDashboardType(DashboardType.CUSTOM);
            saved.setCreatedBy("admin");
            saved.setStatus("ACTIVE");

            when(dashboardRepository.save(any(DashboardEntity.class))).thenReturn(saved);

            dashboardService.createDashboard(tenantId, "admin", UUID.randomUUID(),
                    "Test", null, DashboardType.CUSTOM, null);

            ArgumentCaptor<EventOutboxEntity> captor = ArgumentCaptor.forClass(EventOutboxEntity.class);
            verify(outboxRepository).save(captor.capture());
            assertThat(captor.getValue().getEventType()).isEqualTo("DASHBOARD_CREATED");
        }
    }

    @Nested
    @DisplayName("List Dashboards")
    class ListDashboards {

        @Test
        void returnsAllDashboardsForTenant() {
            UUID tenantId = UUID.randomUUID();
            when(dashboardRepository.findByTenantId(tenantId)).thenReturn(List.of(new DashboardEntity()));

            List<DashboardEntity> result = dashboardService.listDashboards(tenantId);
            assertThat(result).hasSize(1);
        }
    }
}
