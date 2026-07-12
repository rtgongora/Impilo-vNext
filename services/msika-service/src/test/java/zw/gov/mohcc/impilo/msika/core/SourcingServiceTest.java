package zw.gov.mohcc.impilo.msika.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.msika.persistence.entity.RequirementSourcingMapEntity;
import zw.gov.mohcc.impilo.msika.persistence.entity.SourcingCategoryEntity;
import zw.gov.mohcc.impilo.msika.persistence.repository.ListingRepository;
import zw.gov.mohcc.impilo.msika.persistence.repository.RequirementSourcingMapRepository;
import zw.gov.mohcc.impilo.msika.persistence.repository.SourcingCategoryRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Resolution is regulator-neutral, explicitly partial, and version-stable:
 * unmapped codes are absent, RETIRED content never resolves, and the highest
 * APPROVED version wins so re-seeds cannot double results.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SourcingServiceTest {

    @Mock private SourcingCategoryRepository categoryRepository;
    @Mock private RequirementSourcingMapRepository mapRepository;
    @Mock private ListingRepository listingRepository;

    private SourcingService service() {
        return new SourcingService(categoryRepository, mapRepository, listingRepository);
    }

    private static RequirementSourcingMapEntity map(String code, String category, int version) {
        RequirementSourcingMapEntity row = new RequirementSourcingMapEntity();
        row.setMapId("SRCMAP" + code + version);
        row.setRequirementCode(code);
        row.setSourcingCategoryCode(category);
        row.setStatus("APPROVED");
        row.setVersion(version);
        return row;
    }

    private static SourcingCategoryEntity category(String code, boolean restricted) {
        SourcingCategoryEntity entity = new SourcingCategoryEntity();
        entity.setCode(code);
        entity.setLabel(code);
        entity.setRestricted(restricted);
        entity.setStatus("ACTIVE");
        if (restricted) {
            entity.setRequiredCredential("PHARMACEUTICAL_WHOLESALER_CERTIFICATE");
        }
        return entity;
    }

    @Test
    void resolveIsPartialAndCarriesRestrictionAndCounts() {
        when(mapRepository.findByRequirementCodeInAndStatus(anyCollection(), eq("APPROVED")))
                .thenReturn(List.of(map("PHM-014", "MEDICINES_GENERAL", 1)));
        when(categoryRepository.findAllById(any()))
                .thenReturn(List.of(category("MEDICINES_GENERAL", true)));
        when(listingRepository.countPublishedByCategory())
                .thenReturn(List.<Object[]>of(new Object[]{"MEDICINES_GENERAL", 4L}));

        Map<String, SourcingService.ResolutionView> result =
                service().resolve(List.of("PHM-014", "THR-099"));

        assertThat(result).containsOnlyKeys("PHM-014"); // THR-099 unmapped → absent
        SourcingService.ResolutionView view = result.get("PHM-014");
        assertThat(view.categoryCode()).isEqualTo("MEDICINES_GENERAL");
        assertThat(view.restricted()).isTrue();
        assertThat(view.requiredCredential()).isEqualTo("PHARMACEUTICAL_WHOLESALER_CERTIFICATE");
        assertThat(view.publishedListingCount()).isEqualTo(4);
    }

    @Test
    void highestApprovedVersionWins() {
        when(mapRepository.findByRequirementCodeInAndStatus(anyCollection(), eq("APPROVED")))
                .thenReturn(List.of(
                        map("AEU-010", "EMERGENCY_RESUSCITATION", 1),
                        map("AEU-010", "EMERGENCY_RESUSCITATION", 2)));
        when(categoryRepository.findAllById(any()))
                .thenReturn(List.of(category("EMERGENCY_RESUSCITATION", false)));
        when(listingRepository.countPublishedByCategory()).thenReturn(List.of());

        Map<String, SourcingService.ResolutionView> result = service().resolve(List.of("AEU-010"));

        assertThat(result).hasSize(1); // never doubled by re-seeds
        assertThat(result.get("AEU-010").categoryCode()).isEqualTo("EMERGENCY_RESUSCITATION");
    }

    @Test
    void retiredCategoryNeverResolves() {
        SourcingCategoryEntity retired = category("OBSOLETE", false);
        retired.setStatus("RETIRED");
        when(mapRepository.findByRequirementCodeInAndStatus(anyCollection(), eq("APPROVED")))
                .thenReturn(List.of(map("CLN-001", "OBSOLETE", 1)));
        when(categoryRepository.findAllById(any())).thenReturn(List.of(retired));
        when(listingRepository.countPublishedByCategory()).thenReturn(List.of());

        assertThat(service().resolve(List.of("CLN-001"))).isEmpty();
    }

    @Test
    void restrictedCategoryLookupIsNullSafe() {
        when(categoryRepository.findById("MEDICINES_GENERAL"))
                .thenReturn(Optional.of(category("MEDICINES_GENERAL", true)));
        assertThat(service().isRestrictedCategory("MEDICINES_GENERAL")).isTrue();
        assertThat(service().isRestrictedCategory(null)).isFalse();
        assertThat(service().isRestrictedCategory("UNKNOWN")).isFalse();
    }
}
