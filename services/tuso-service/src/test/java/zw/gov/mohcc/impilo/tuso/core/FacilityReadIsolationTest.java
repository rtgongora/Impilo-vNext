package zw.gov.mohcc.impilo.tuso.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * §17 cross-tenant read isolation (G-TI-01): an organisation admin / regulator may never read another
 * tenant's facility data. Proves the single-facility read denies a cross-tenant id and the search read
 * is always tenant-bound at the query.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FacilityReadIsolationTest {

    @Mock private FacilityRepository facilityRepository;
    @Mock private FacilityIdentifierRepository identifierRepository;
    @Mock private FacilityContactRepository contactRepository;
    @Mock private FacilityGeoRepository geoRepository;
    @Mock private FacilityCapabilityRepository capabilityRepository;
    @Mock private FacilityReadinessRepository readinessRepository;
    @Mock private FacilityHistoryRepository historyRepository;
    @Mock private WorkspaceRepository workspaceRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private zw.gov.mohcc.impilo.tuso.persistence.repository.FacilitySourceLegitimacyRepository legitimacyRepository;

    private FacilityService service;
    private final UUID myTenant = UUID.randomUUID();
    private final UUID otherTenant = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new FacilityService(facilityRepository, identifierRepository, contactRepository,
                geoRepository, capabilityRepository, readinessRepository, historyRepository,
                workspaceRepository, outboxRepository,
                new FacilityLegitimacyReverificationService(legitimacyRepository));
        TrustContextHolder.set(new TrustContext(
                myTenant, "org-admin-1", "OPERATOR", "ADMINISTRATION", "device-1",
                UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private FacilityEntity facilityOf(UUID tenant) {
        FacilityEntity f = new FacilityEntity();
        f.setTenantId(tenant);
        return f;
    }

    @Test
    void getFacility_allows_reading_within_own_tenant() {
        when(facilityRepository.findById(1L)).thenReturn(Optional.of(facilityOf(myTenant)));
        when(identifierRepository.findByFacilityId(1L)).thenReturn(List.of());
        when(contactRepository.findByFacilityId(1L)).thenReturn(List.of());
        when(geoRepository.findByFacilityId(1L)).thenReturn(Optional.empty());
        when(capabilityRepository.findByFacilityIdAndActiveTrue(1L)).thenReturn(List.of());
        when(readinessRepository.findByFacilityId(1L)).thenReturn(Optional.empty());

        assertEquals(myTenant, service.getFacility(1L).facility().getTenantId());
    }

    @Test
    void getFacility_denies_reading_another_tenants_facility() {
        // A facility owned by another tenant must not be readable by id (cross-tenant IDOR).
        when(facilityRepository.findById(99L)).thenReturn(Optional.of(facilityOf(otherTenant)));

        SecurityException ex = assertThrows(SecurityException.class, () -> service.getFacility(99L));
        assertEquals(true, ex.getMessage().contains("Tenant isolation"));
        // The cross-tenant facility's child data must never be fetched.
        verify(identifierRepository, org.mockito.Mockito.never()).findByFacilityId(anyLong());
    }

    @Test
    void searchFacilities_is_always_bound_to_the_callers_tenant() {
        when(facilityRepository.findByFilters(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.searchFacilities(myTenant, null, null, PageRequest.of(0, 20));

        // The query is scoped to the caller's tenant; another tenant's partition is never queried.
        verify(facilityRepository).findByFilters(eq(myTenant), any(), any(), any(), any(), any(), any());
    }
}
