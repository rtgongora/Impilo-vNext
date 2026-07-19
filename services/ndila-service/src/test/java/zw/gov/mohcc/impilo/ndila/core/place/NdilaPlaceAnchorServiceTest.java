package zw.gov.mohcc.impilo.ndila.core.place;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.ndila.domain.NdilaLocationEntity;
import zw.gov.mohcc.impilo.ndila.domain.NdilaPlaceAnchorEntity;
import zw.gov.mohcc.impilo.ndila.repository.NdilaLocationRepository;
import zw.gov.mohcc.impilo.ndila.repository.NdilaPlaceAnchorRepository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * D-L1: anchors are geo-only and shareable — locations from DIFFERENT owner
 * registries can be assigned to one anchor (the campus view); cross-tenant
 * assignment is rejected.
 */
@ExtendWith(MockitoExtension.class)
class NdilaPlaceAnchorServiceTest {

    @Mock private NdilaPlaceAnchorRepository anchorRepository;
    @Mock private NdilaLocationRepository locationRepository;

    private NdilaPlaceAnchorService service;

    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new NdilaPlaceAnchorService(anchorRepository, locationRepository);
        lenient().when(anchorRepository.save(any())).thenAnswer(inv -> {
            NdilaPlaceAnchorEntity e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(UUID.randomUUID());
            }
            return e;
        });
        lenient().when(locationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createRequiresCentroid() {
        assertThatThrownBy(() -> service.create(tenantId, "steward-1",
                new NdilaPlaceAnchorService.CreateAnchor(null, null, null, null, null, null, "Campus")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void locationsFromDifferentRegistriesShareOneAnchor() {
        NdilaPlaceAnchorEntity anchor = service.create(tenantId, "steward-1",
                new NdilaPlaceAnchorService.CreateAnchor(
                        new BigDecimal("-17.8300000"), new BigDecimal("31.0500000"),
                        null, null, "Harare", "Harare", "Central Hospital campus"));
        when(anchorRepository.findByIdAndTenantId(anchor.getId(), tenantId)).thenReturn(Optional.of(anchor));

        NdilaLocationEntity tusoFacility = mock(NdilaLocationEntity.class);
        when(tusoFacility.getTenantId()).thenReturn(tenantId);
        NdilaLocationEntity indawoSite = mock(NdilaLocationEntity.class);
        when(indawoSite.getTenantId()).thenReturn(tenantId);
        UUID facilityLocId = UUID.randomUUID();
        UUID siteLocId = UUID.randomUUID();
        when(locationRepository.findById(facilityLocId)).thenReturn(Optional.of(tusoFacility));
        when(locationRepository.findById(siteLocId)).thenReturn(Optional.of(indawoSite));

        service.assign(tenantId, anchor.getId(), facilityLocId);
        service.assign(tenantId, anchor.getId(), siteLocId);

        org.mockito.Mockito.verify(tusoFacility).setAnchorId(anchor.getId());
        org.mockito.Mockito.verify(indawoSite).setAnchorId(anchor.getId());
    }

    @Test
    void crossTenantAssignmentIsRejected() {
        NdilaPlaceAnchorEntity anchor = new NdilaPlaceAnchorEntity();
        anchor.setId(UUID.randomUUID());
        anchor.setTenantId(tenantId);
        when(anchorRepository.findByIdAndTenantId(anchor.getId(), tenantId)).thenReturn(Optional.of(anchor));

        NdilaLocationEntity foreign = mock(NdilaLocationEntity.class);
        when(foreign.getTenantId()).thenReturn(UUID.randomUUID());
        UUID locId = UUID.randomUUID();
        when(locationRepository.findById(locId)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.assign(tenantId, anchor.getId(), locId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown location");
    }

    @Test
    void campusViewRequiresKnownAnchor() {
        UUID anchorId = UUID.randomUUID();
        when(anchorRepository.findByIdAndTenantId(anchorId, tenantId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.locations(tenantId, anchorId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anchorCarriesGeographyOnly() {
        // Structural guard: the anchor entity must never grow name/status/authority
        // fields — geography and a campus label only (D-L1).
        var fields = java.util.Arrays.stream(NdilaPlaceAnchorEntity.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName)
                .toList();
        assertThat(fields).doesNotContain("name", "status", "ownerName", "operatorName", "regulatoryStatus");
    }
}
