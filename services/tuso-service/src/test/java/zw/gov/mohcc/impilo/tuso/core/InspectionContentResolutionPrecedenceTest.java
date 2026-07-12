package zw.gov.mohcc.impilo.tuso.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.tuso.persistence.entity.ClassificationTemplateApplicabilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityRegulatoryProfileEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.InspectionRequirementModuleEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.InspectionTemplateCompositionEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.ClassificationTemplateApplicabilityRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRegulatoryProfileRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityUnitRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.InspectionRequirementModuleRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.InspectionTemplateCompositionRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InspectionContentResolutionPrecedenceTest {

    @Mock private InspectionRequirementModuleRepository moduleRepository;
    @Mock private InspectionTemplateCompositionRepository compositionRepository;
    @Mock private ClassificationTemplateApplicabilityRepository applicabilityRepository;
    @Mock private FacilityUnitRepository facilityUnitRepository;
    @Mock private FacilityRegulatoryProfileRepository profileRepository;

    private InspectionContentService service;

    @BeforeEach
    void setUp() {
        service = new InspectionContentService(moduleRepository, compositionRepository,
                applicabilityRepository, facilityUnitRepository, profileRepository);
        lenient().when(facilityUnitRepository.findByFacilityIdOrderByCreatedAtAsc(anyLong())).thenReturn(List.of());
    }

    private FacilityEntity facility(long id, String type, Map<String, Object> metadata) {
        FacilityEntity f = new FacilityEntity();
        f.setId(id);
        f.setFacilityType(type);
        f.setMetadata(metadata);
        return f;
    }

    private FacilityRegulatoryProfileEntity profile(String status, String label, String hpaClass) {
        FacilityRegulatoryProfileEntity p = new FacilityRegulatoryProfileEntity();
        p.setFacilityId(1L);
        p.setClassificationStatus(status);
        p.setHpaClassificationLabel(label);
        p.setHpaClass(hpaClass);
        p.setMappingRuleCode("HPA_FACILITY_CLASSIFICATION_MAP");
        p.setMappingRuleVersion(1);
        return p;
    }

    private void compositionResolvesFor(String composition, String label) {
        var mapping = new ClassificationTemplateApplicabilityEntity();
        mapping.setCompositionCode(composition);
        lenient().when(applicabilityRepository.findByClassificationLabelIgnoreCase(label)).thenReturn(List.of(mapping));
        var comp = new InspectionTemplateCompositionEntity();
        comp.setCode(composition);
        comp.setModuleCodes(List.of("MOD-ALL-HEALTH-INSTITUTIONS"));
        lenient().when(compositionRepository.findFirstByCodeAndStatusOrderByVersionDesc(composition, "ACTIVE"))
                .thenReturn(Optional.of(comp));
        var module = new InspectionRequirementModuleEntity();
        module.setCode("MOD-ALL-HEALTH-INSTITUTIONS");
        module.setVersion(1);
        lenient().when(moduleRepository.findFirstByCodeAndStatusOrderByVersionDesc("MOD-ALL-HEALTH-INSTITUTIONS", "ACTIVE"))
                .thenReturn(Optional.of(module));
    }

    @Test
    void confirmedProfileResolvesByItsLabelWithAConfirmedTrail() {
        when(profileRepository.findByFacilityId(1L))
                .thenReturn(Optional.of(profile("HUMAN_CONFIRMED",
                        "Government, Local Authority and Mission: Clinics (out patients only)", "C")));
        compositionResolvesFor("COMP-CLINIC", "Government, Local Authority and Mission: Clinics (out patients only)");

        var resolved = service.resolveForFacility(facility(1, "CLINIC", null), null, null);

        assertThat(resolved.compositionCode()).isEqualTo("COMP-CLINIC");
        assertThat(resolved.resolutionTrail()).anyMatch(t -> t.startsWith("confirmed classification"));
    }

    @Test
    void unconfirmedProfileHardStopsAndNeverFallsThroughToMetadata() {
        FacilityRegulatoryProfileEntity p = profile("MISSING_REQUIRED_FACTS", null, "C");
        p.setRequiredFacts(List.of("care_setting"));
        when(profileRepository.findByFacilityId(1L)).thenReturn(Optional.of(p));
        // A metadata label is present, but the hard stop must NOT use it.
        FacilityEntity f = facility(1, "CLINIC",
                Map.of("hpaClassificationLabel", "Government, Local Authority and Mission: Clinics (out patients only)"));

        assertThatThrownBy(() -> service.resolveForFacility(f, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unconfirmed classification profile")
                .hasMessageContaining("care_setting");
    }

    @Test
    void notApplicableProfileHasNoComposition() {
        FacilityRegulatoryProfileEntity p = profile("HUMAN_CONFIRMED", null, "NOT_APPLICABLE");
        p.setHpaClassJustification("standalone administrative office, not a health institution");
        when(profileRepository.findByFacilityId(1L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.resolveForFacility(facility(1, "CLINIC", null), null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NOT_APPLICABLE");
    }

    @Test
    void facilityWithNoProfileUsesTheLegacyMetadataLabelPath() {
        when(profileRepository.findByFacilityId(1L)).thenReturn(Optional.empty());
        compositionResolvesFor("COMP-LEGACY", "Some legacy label");
        FacilityEntity f = facility(1, "CLINIC", Map.of("hpaClassificationLabel", "Some legacy label"));

        var resolved = service.resolveForFacility(f, null, null);

        assertThat(resolved.compositionCode()).isEqualTo("COMP-LEGACY");
        assertThat(resolved.resolutionTrail()).anyMatch(t -> t.startsWith("classification 'Some legacy label'"));
    }

    @Test
    void explicitCompositionStillWinsOverEverything() {
        var comp = new InspectionTemplateCompositionEntity();
        comp.setCode("COMP-EXPLICIT");
        comp.setModuleCodes(List.of("MOD-ALL-HEALTH-INSTITUTIONS"));
        when(compositionRepository.findFirstByCodeAndStatusOrderByVersionDesc("COMP-EXPLICIT", "ACTIVE"))
                .thenReturn(Optional.of(comp));
        var module = new InspectionRequirementModuleEntity();
        module.setCode("MOD-ALL-HEALTH-INSTITUTIONS");
        module.setVersion(1);
        when(moduleRepository.findFirstByCodeAndStatusOrderByVersionDesc("MOD-ALL-HEALTH-INSTITUTIONS", "ACTIVE"))
                .thenReturn(Optional.of(module));

        var resolved = service.resolveForFacility(facility(1, "CLINIC", null), "COMP-EXPLICIT", null);

        assertThat(resolved.compositionCode()).isEqualTo("COMP-EXPLICIT");
        // The profile repository is never consulted on the explicit path.
        org.mockito.Mockito.verify(profileRepository, org.mockito.Mockito.never()).findByFacilityId(any());
    }
}
