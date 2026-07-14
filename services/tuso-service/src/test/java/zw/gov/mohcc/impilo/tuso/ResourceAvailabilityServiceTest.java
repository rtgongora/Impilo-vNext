package zw.gov.mohcc.impilo.tuso;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.tuso.core.ClinicalSpaceService;
import zw.gov.mohcc.impilo.tuso.core.ResourceAvailabilityService;
import zw.gov.mohcc.impilo.tuso.persistence.entity.ClinicalSpaceEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.InstrumentSetEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.BedRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.ClinicalSpaceRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.EquipmentAssetRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.InstrumentSetRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResourceAvailabilityServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @Test
    void instrumentSetsFilterByState() {
        BedRepository beds = mock(BedRepository.class);
        EquipmentAssetRepository equipment = mock(EquipmentAssetRepository.class);
        InstrumentSetRepository sets = mock(InstrumentSetRepository.class);
        ResourceAvailabilityService service = new ResourceAvailabilityService(beds, equipment, sets);

        InstrumentSetEntity sterile = new InstrumentSetEntity();
        sterile.setName("General Surgery Set 1");
        sterile.setSterilisationState("STERILE");
        when(sets.findByTenantIdAndSterilisationStateOrderByNameAsc(eq(TENANT), eq("STERILE")))
                .thenReturn(List.of(sterile));

        List<Map<String, Object>> result = service.instrumentSets(TENANT, "sterile");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("sterilisationState")).isEqualTo("STERILE");
    }

    @Test
    void clinicalSpaceAvailabilityReflectsStatus() {
        ClinicalSpaceRepository repo = mock(ClinicalSpaceRepository.class);
        ClinicalSpaceService service = new ClinicalSpaceService(repo);

        ClinicalSpaceEntity space = new ClinicalSpaceEntity();
        space.setName("Theatre 1");
        space.setSpaceType("OPERATING_THEATRE");
        space.setStatus("ACTIVE");
        UUID id = UUID.randomUUID();
        when(repo.findByTenantIdAndId(TENANT, id)).thenReturn(java.util.Optional.of(space));

        Map<String, Object> availability = service.availability(TENANT, id, "2026-04-01");
        assertThat(availability.get("available")).isEqualTo(true);

        space.setStatus("MAINTENANCE");
        Map<String, Object> closed = service.availability(TENANT, id, "2026-04-01");
        assertThat(closed.get("available")).isEqualTo(false);
    }
}
