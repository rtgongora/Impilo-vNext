package zw.gov.mohcc.impilo.tuso.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityClassificationMappingRuleEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityClassificationMappingRuleRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClassificationMappingSeederTest {

    @Mock
    private FacilityClassificationMappingRuleRepository ruleRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ClassificationMappingSeeder seeder() {
        return new ClassificationMappingSeeder(ruleRepository, objectMapper);
    }

    @Test
    void loadsAndActivatesTheRulePackFromGovernanceManifest() throws Exception {
        when(ruleRepository.findByCodeAndVersion(eq("HPA_FACILITY_CLASSIFICATION_MAP"), eq(1)))
                .thenReturn(Optional.empty());

        seeder().run(null);

        ArgumentCaptor<FacilityClassificationMappingRuleEntity> captor =
                ArgumentCaptor.forClass(FacilityClassificationMappingRuleEntity.class);
        verify(ruleRepository).save(captor.capture());
        FacilityClassificationMappingRuleEntity saved = captor.getValue();
        assertThat(saved.getCode()).isEqualTo("HPA_FACILITY_CLASSIFICATION_MAP");
        assertThat(saved.getVersion()).isEqualTo(1);
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");            // per content-governance.json
        assertThat(saved.getContentChecksum()).isNotBlank();
        assertThat(saved.getApprovedBy()).isNotBlank();               // activation stamps provenance
        assertThat(saved.getRuleJson()).containsKeys("ownershipAuthority", "facilityType", "catalogueLabels");
        // The catalogue gap is encoded, never rounded to a real label.
        @SuppressWarnings("unchecked")
        var labels = (java.util.Map<String, Object>) saved.getRuleJson().get("catalogueLabels");
        @SuppressWarnings("unchecked")
        var publicHospital = (java.util.Map<String, Object>) labels.get("publicHospital");
        assertThat(publicHospital.get("UP_TO_50")).isEqualTo("__CATALOGUE_GAP__");
    }

    @Test
    void isIdempotentWhenTheSameVersionIsAlreadyPersistedWithMatchingChecksum() throws Exception {
        // Compute the pack's real checksum the way the seeder does.
        var resource = new org.springframework.core.io.support.PathMatchingResourcePatternResolver()
                .getResources("classpath:regulatory/classification-mapping/*.json")[0];
        java.util.Map<String, Object> doc =
                objectMapper.readValue(resource.getInputStream(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
        String checksum = ClassificationMappingSeeder.checksum(objectMapper.writeValueAsString(doc));

        FacilityClassificationMappingRuleEntity existing = new FacilityClassificationMappingRuleEntity();
        existing.setContentChecksum(checksum);
        when(ruleRepository.findByCodeAndVersion(eq("HPA_FACILITY_CLASSIFICATION_MAP"), eq(1)))
                .thenReturn(Optional.of(existing));

        seeder().run(null);

        verify(ruleRepository, never()).save(any());
    }

    @Test
    void failsFastWhenAnExistingVersionHasADifferentChecksum() throws Exception {
        FacilityClassificationMappingRuleEntity existing = new FacilityClassificationMappingRuleEntity();
        existing.setContentChecksum("stale-checksum-from-an-older-policy");
        when(ruleRepository.findByCodeAndVersion(eq("HPA_FACILITY_CLASSIFICATION_MAP"), eq(1)))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> seeder().run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Immutable content violation");
        verify(ruleRepository, never()).save(any());
    }
}
