package zw.gov.mohcc.impilo.tuso.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityConfigVersionEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.TenantConfigDefaultEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.WorkspaceConfigOverrideEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityConfigVersionRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.TenantConfigDefaultRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.WorkspaceConfigOverrideRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ConfigResolutionEngine}.
 *
 * <p>Validates the three-tier config merge precedence:
 * tenant (lowest) -> facility -> workspace (highest).
 * Nested maps are deep-merged, not replaced.</p>
 */
@ExtendWith(MockitoExtension.class)
class ConfigResolutionEngineTest {

    @Mock
    private TenantConfigDefaultRepository tenantConfigRepo;

    @Mock
    private FacilityConfigVersionRepository facilityConfigRepo;

    @Mock
    private WorkspaceConfigOverrideRepository workspaceConfigRepo;

    @InjectMocks
    private ConfigResolutionEngine engine;

    private static final UUID TENANT_ID = UUID.fromString("dddd0000-0000-0000-0000-000000000001");
    private static final Long FACILITY_ID = 1L;
    private static final UUID WORKSPACE_ID = UUID.fromString("eeee0000-0000-0000-0000-000000000001");

    @Test
    void resolve_tenantOnly_returnsTenantConfig() {
        TenantConfigDefaultEntity tenant = new TenantConfigDefaultEntity();
        tenant.setConfigData(Map.of("theme", "light", "language", "en"));
        when(tenantConfigRepo.findByTenantId(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(facilityConfigRepo.findLatestActive(FACILITY_ID)).thenReturn(Optional.empty());

        Map<String, Object> result = engine.resolve(TENANT_ID, FACILITY_ID, null);

        assertThat(result).containsEntry("theme", "light");
        assertThat(result).containsEntry("language", "en");
        assertThat(result).hasSize(2);
    }

    @Test
    void resolve_facilityOverridesTenant() {
        TenantConfigDefaultEntity tenant = new TenantConfigDefaultEntity();
        tenant.setConfigData(Map.of("theme", "light", "language", "en"));
        when(tenantConfigRepo.findByTenantId(TENANT_ID)).thenReturn(Optional.of(tenant));

        FacilityConfigVersionEntity facility = new FacilityConfigVersionEntity();
        facility.setConfigData(Map.of("theme", "dark", "timezone", "CAT"));
        when(facilityConfigRepo.findLatestActive(FACILITY_ID)).thenReturn(Optional.of(facility));

        Map<String, Object> result = engine.resolve(TENANT_ID, FACILITY_ID, null);

        assertThat(result)
                .containsEntry("theme", "dark")
                .containsEntry("language", "en")
                .containsEntry("timezone", "CAT");
    }

    @Test
    void resolve_workspaceOverridesFacility() {
        TenantConfigDefaultEntity tenant = new TenantConfigDefaultEntity();
        tenant.setConfigData(Map.of("theme", "light"));
        when(tenantConfigRepo.findByTenantId(TENANT_ID)).thenReturn(Optional.of(tenant));

        FacilityConfigVersionEntity facility = new FacilityConfigVersionEntity();
        facility.setConfigData(Map.of("theme", "dark", "maxQueue", 50));
        when(facilityConfigRepo.findLatestActive(FACILITY_ID)).thenReturn(Optional.of(facility));

        WorkspaceConfigOverrideEntity workspace = new WorkspaceConfigOverrideEntity();
        workspace.setConfigData(Map.of("maxQueue", 30, "autoClose", true));
        when(workspaceConfigRepo.findByWorkspaceId(WORKSPACE_ID)).thenReturn(Optional.of(workspace));

        Map<String, Object> result = engine.resolve(TENANT_ID, FACILITY_ID, WORKSPACE_ID);

        assertThat(result)
                .containsEntry("theme", "dark")
                .containsEntry("maxQueue", 30)
                .containsEntry("autoClose", true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void resolve_deepMerge_preservesNestedKeys() {
        TenantConfigDefaultEntity tenant = new TenantConfigDefaultEntity();
        Map<String, Object> tenantData = new HashMap<>();
        tenantData.put("notifications", new HashMap<>(Map.of("email", true, "sms", false)));
        tenantData.put("topLevel", "value");
        tenant.setConfigData(tenantData);
        when(tenantConfigRepo.findByTenantId(TENANT_ID)).thenReturn(Optional.of(tenant));

        FacilityConfigVersionEntity facility = new FacilityConfigVersionEntity();
        Map<String, Object> facilityData = new HashMap<>();
        facilityData.put("notifications", new HashMap<>(Map.of("sms", true, "push", true)));
        facility.setConfigData(facilityData);
        when(facilityConfigRepo.findLatestActive(FACILITY_ID)).thenReturn(Optional.of(facility));

        Map<String, Object> result = engine.resolve(TENANT_ID, FACILITY_ID, null);

        assertThat(result).containsEntry("topLevel", "value");
        Map<String, Object> notifications = (Map<String, Object>) result.get("notifications");
        assertThat(notifications)
                .containsEntry("email", true)
                .containsEntry("sms", true)
                .containsEntry("push", true);
    }

    @Test
    void resolve_nullTenant_returnsFacilityConfig() {
        when(tenantConfigRepo.findByTenantId(TENANT_ID)).thenReturn(Optional.empty());

        FacilityConfigVersionEntity facility = new FacilityConfigVersionEntity();
        facility.setConfigData(Map.of("timezone", "CAT", "maxQueue", 25));
        when(facilityConfigRepo.findLatestActive(FACILITY_ID)).thenReturn(Optional.of(facility));

        Map<String, Object> result = engine.resolve(TENANT_ID, FACILITY_ID, null);

        assertThat(result).containsEntry("timezone", "CAT");
        assertThat(result).containsEntry("maxQueue", 25);
        assertThat(result).hasSize(2);
    }

    @Test
    void resolve_allNull_returnsEmptyMap() {
        when(tenantConfigRepo.findByTenantId(TENANT_ID)).thenReturn(Optional.empty());
        when(facilityConfigRepo.findLatestActive(FACILITY_ID)).thenReturn(Optional.empty());

        Map<String, Object> result = engine.resolve(TENANT_ID, FACILITY_ID, null);

        assertThat(result).isEmpty();
        verify(workspaceConfigRepo, never()).findByWorkspaceId(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void resolve_workspaceOverridesNestedKey() {
        TenantConfigDefaultEntity tenant = new TenantConfigDefaultEntity();
        Map<String, Object> tenantData = new HashMap<>();
        tenantData.put("printing", new HashMap<>(Map.of("copies", 1, "color", false, "duplex", true)));
        tenant.setConfigData(tenantData);
        when(tenantConfigRepo.findByTenantId(TENANT_ID)).thenReturn(Optional.of(tenant));

        FacilityConfigVersionEntity facility = new FacilityConfigVersionEntity();
        Map<String, Object> facilityData = new HashMap<>();
        facilityData.put("printing", new HashMap<>(Map.of("copies", 2)));
        facility.setConfigData(facilityData);
        when(facilityConfigRepo.findLatestActive(FACILITY_ID)).thenReturn(Optional.of(facility));

        WorkspaceConfigOverrideEntity workspace = new WorkspaceConfigOverrideEntity();
        Map<String, Object> workspaceData = new HashMap<>();
        workspaceData.put("printing", new HashMap<>(Map.of("color", true)));
        workspace.setConfigData(workspaceData);
        when(workspaceConfigRepo.findByWorkspaceId(WORKSPACE_ID)).thenReturn(Optional.of(workspace));

        Map<String, Object> result = engine.resolve(TENANT_ID, FACILITY_ID, WORKSPACE_ID);

        Map<String, Object> printing = (Map<String, Object>) result.get("printing");
        assertThat(printing).containsEntry("copies", 2);
        assertThat(printing).containsEntry("color", true);
        assertThat(printing).containsEntry("duplex", true);
    }
}
