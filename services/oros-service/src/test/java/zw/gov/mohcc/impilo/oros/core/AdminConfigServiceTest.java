package zw.gov.mohcc.impilo.oros.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.oros.persistence.entity.AdminConfigEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.AdminConfigRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminConfigServiceTest {

    @Mock private AdminConfigRepository repository;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID FACILITY_ID = UUID.randomUUID();

    private TrustContext ctx() {
        return new TrustContext(TENANT_ID, "admin-1", "ADMIN", "ADMINISTRATION",
                null, UUID.randomUUID(), FACILITY_ID, UUID.randomUUID(), null, AccessMode.INTERNAL);
    }

    @Test
    @DisplayName("put upserts and round-trips the config JSON")
    void putRoundTrips() {
        try (MockedStatic<TrustContextHolder> h = mockStatic(TrustContextHolder.class)) {
            h.when(TrustContextHolder::require).thenReturn(ctx());
            when(repository.findByTenantIdAndFacilityIdIsNullAndConfigType(TENANT_ID, AdminConfigService.ROUTING_RULES))
                    .thenReturn(Optional.empty());
            when(repository.save(any(AdminConfigEntity.class))).thenAnswer(i -> i.getArgument(0));

            AdminConfigService service = new AdminConfigService(repository);
            String stored = service.put(AdminConfigService.ROUTING_RULES, null, "{\"defaultMode\":\"INTERNAL\"}");

            assertThat(stored).contains("defaultMode");
        }
    }

    @Test
    @DisplayName("get returns facility-scoped config when present")
    void getFacilityScoped() {
        try (MockedStatic<TrustContextHolder> h = mockStatic(TrustContextHolder.class)) {
            h.when(TrustContextHolder::require).thenReturn(ctx());
            AdminConfigEntity scoped = new AdminConfigEntity();
            scoped.setConfigJson("{\"ackTimeoutMinutes\":15}");
            when(repository.findByTenantIdAndFacilityIdAndConfigType(TENANT_ID, FACILITY_ID, AdminConfigService.CRITICAL_ESCALATION_RULES))
                    .thenReturn(Optional.of(scoped));

            String json = new AdminConfigService(repository).get(AdminConfigService.CRITICAL_ESCALATION_RULES, FACILITY_ID);

            assertThat(json).contains("ackTimeoutMinutes");
        }
    }

    @Test
    @DisplayName("get falls back to tenant default and returns {} when nothing configured")
    void getFallsBackToEmpty() {
        try (MockedStatic<TrustContextHolder> h = mockStatic(TrustContextHolder.class)) {
            h.when(TrustContextHolder::require).thenReturn(ctx());
            when(repository.findByTenantIdAndFacilityIdAndConfigType(any(), any(), any())).thenReturn(Optional.empty());
            when(repository.findByTenantIdAndFacilityIdIsNullAndConfigType(any(), any())).thenReturn(Optional.empty());

            String json = new AdminConfigService(repository).get(AdminConfigService.ROUTING_RULES, FACILITY_ID);

            assertThat(json).isEqualTo("{}");
        }
    }
}
