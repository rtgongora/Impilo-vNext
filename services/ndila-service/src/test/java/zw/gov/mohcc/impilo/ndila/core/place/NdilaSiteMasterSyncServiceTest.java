package zw.gov.mohcc.impilo.ndila.core.place;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.ndila.core.location.NdilaLocationService;
import zw.gov.mohcc.impilo.ndila.domain.NdilaGeocodeReviewEntity;
import zw.gov.mohcc.impilo.ndila.domain.NdilaLocationEntity;
import zw.gov.mohcc.impilo.ndila.repository.NdilaGeocodeReviewRepository;
import zw.gov.mohcc.impilo.ndila.repository.NdilaLocationRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * D-L1: Indawo sites join the owner-keyed location index as INDAWO/SITE rows;
 * missing coordinates land in the geocode review queue under owner_service
 * INDAWO (never silently dropped); resync updates rather than duplicates.
 */
@ExtendWith(MockitoExtension.class)
class NdilaSiteMasterSyncServiceTest {

    @Mock private NdilaLocationRepository locationRepository;
    @Mock private NdilaLocationService locationService;
    @Mock private NdilaGeocodeReviewRepository reviewRepository;

    private NdilaSiteMasterSyncService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID correlationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new NdilaSiteMasterSyncService(locationRepository, locationService, reviewRepository);
        lenient().when(locationService.create(any(), any()))
                .thenAnswer(inv -> {
                    NdilaLocationEntity e = mock(NdilaLocationEntity.class);
                    lenient().when(e.getId()).thenReturn(UUID.randomUUID());
                    return e;
                });
    }

    private Map<String, Object> site(String id, Object lat, Object lng) {
        return Map.of(
                "site_id", id,
                "site_code", "IS-001",
                "site_name", "Mbare Market",
                "site_category", "MARKET",
                "latitude", lat,
                "longitude", lng,
                "district", "Harare",
                "province", "Harare");
    }

    @Test
    void newSiteWithCoordinatesCreatesOwnerKeyedLocation() {
        String siteId = UUID.randomUUID().toString();
        when(locationRepository.findByTenantIdAndOwnerServiceAndOwnerEntityTypeAndOwnerEntityId(
                tenantId, "INDAWO", "SITE", siteId)).thenReturn(Optional.empty());

        var result = service.syncRecords(tenantId, List.of(site(siteId, -17.86, 31.03)), correlationId);

        assertThat(result.locationsCreated()).isEqualTo(1);
        var captor = org.mockito.ArgumentCaptor.forClass(NdilaLocationService.CreateLocation.class);
        verify(locationService).create(captor.capture(), eq(correlationId));
        assertThat(captor.getValue().ownerService()).isEqualTo("INDAWO");
        assertThat(captor.getValue().ownerEntityType()).isEqualTo("SITE");
        assertThat(captor.getValue().ownerEntityId()).isEqualTo(siteId);
        assertThat(captor.getValue().locationType()).isEqualTo("PUBLIC_HEALTH_SITE");
    }

    @Test
    void existingSiteIsUpdatedNotDuplicated() {
        String siteId = UUID.randomUUID().toString();
        NdilaLocationEntity existing = mock(NdilaLocationEntity.class);
        when(existing.getId()).thenReturn(UUID.randomUUID());
        when(locationRepository.findByTenantIdAndOwnerServiceAndOwnerEntityTypeAndOwnerEntityId(
                tenantId, "INDAWO", "SITE", siteId)).thenReturn(Optional.of(existing));

        var result = service.syncRecords(tenantId, List.of(site(siteId, -17.86, 31.03)), correlationId);

        assertThat(result.locationsUpdated()).isEqualTo(1);
        assertThat(result.locationsCreated()).isZero();
        verify(locationService).updateCoordinates(eq(existing.getId()), any(), any(), any(),
                eq("INDAWO_SITE_SYNC"), eq(correlationId));
    }

    @Test
    void missingCoordinatesQueueGeocodeReviewUnderIndawoOwner() {
        String siteId = UUID.randomUUID().toString();
        when(reviewRepository.findByTenantIdAndOwnerServiceAndOwnerEntityId(tenantId, "INDAWO", siteId))
                .thenReturn(Optional.empty());

        var result = service.syncRecords(tenantId, List.of(site(siteId, 0, 0)), correlationId);

        assertThat(result.reviewQueued()).isEqualTo(1);
        var captor = org.mockito.ArgumentCaptor.forClass(NdilaGeocodeReviewEntity.class);
        verify(reviewRepository).save(captor.capture());
        assertThat(captor.getValue().getOwnerService()).isEqualTo("INDAWO");
        assertThat(captor.getValue().getOwnerEntityId()).isEqualTo(siteId);
    }
}
