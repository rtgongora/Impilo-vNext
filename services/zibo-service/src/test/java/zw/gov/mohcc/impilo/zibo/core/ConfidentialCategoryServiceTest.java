package zw.gov.mohcc.impilo.zibo.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.zibo.persistence.entity.MappingIndexEntity;
import zw.gov.mohcc.impilo.zibo.persistence.repository.MappingIndexRepository;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * The governed confidential-category classifier: content classification driven by the reviewable
 * value set in zibo, rather than by a hardcoded list in each clinical service.
 */
@ExtendWith(MockitoExtension.class)
class ConfidentialCategoryServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String ICD10 = "http://hl7.org/fhir/sid/icd-10";

    @Mock private MappingIndexRepository mappingIndexRepository;

    private ConfidentialCategoryService service;

    @BeforeEach
    void setUp() {
        service = new ConfidentialCategoryService(mappingIndexRepository);
    }

    private static MappingIndexEntity row(String sourceCode, String category) {
        MappingIndexEntity e = new MappingIndexEntity();
        e.setTenantId(TENANT);
        e.setSourceSystem(ICD10);
        e.setSourceCode(sourceCode);
        e.setTargetSystem(ConfidentialCategoryService.CATEGORY_SYSTEM);
        e.setTargetCode(category);
        e.setEquivalence("wider");
        return e;
    }

    private void stubGovernedMap(MappingIndexEntity... rows) {
        when(mappingIndexRepository.findByTenantIdAndTargetSystem(
                eq(TENANT), eq(ConfidentialCategoryService.CATEGORY_SYSTEM)))
                .thenReturn(List.of(rows));
    }

    @Test
    @DisplayName("a governed code prefix classifies the content as SPECIALLY_PROTECTED")
    void governedPrefix_classifiesAsProtected() {
        stubGovernedMap(row("B20", "HIV"), row("F3", "MENTAL_HEALTH"));

        var result = service.classifyCode(TENANT, ICD10, "B20.7");

        assertEquals(List.of("HIV"), result.categories(),
                "the governed map must report what it matched, so the seed can be reviewed");
        assertEquals(ConfidentialCategoryService.CATEGORY_MAP_RATIFIED, result.confidential());
        if (ConfidentialCategoryService.CATEGORY_MAP_RATIFIED) {
            assertEquals("SPECIALLY_PROTECTED", result.sensitivityClass());
        } else {
            assertNull(result.sensitivityClass(),
                    "while the map is unratified no class is handed back to stamp — a record "
                            + "carrying a protection label that does not protect it is the failure "
                            + "this seam exists to prevent");
        }
    }

    @Test
    @DisplayName("longest prefix wins: F10-F19 is substance use, not the broader mental-health block")
    void longestPrefixWins() {
        stubGovernedMap(row("F1", "MENTAL_HEALTH"), row("F10", "SUBSTANCE_USE"));

        assertEquals(List.of("SUBSTANCE_USE"),
                service.classifyCode(TENANT, ICD10, "F10.2").categories(),
                "a narrower governed mapping must beat the block it sits inside");
        assertEquals(List.of("MENTAL_HEALTH"),
                service.classifyCode(TENANT, ICD10, "F19.1").categories());
    }

    @Test
    @DisplayName("ordinary clinical codes are not confidential")
    void ordinaryCode_isNotConfidential() {
        stubGovernedMap(row("B20", "HIV"));

        var result = service.classifyCode(TENANT, ICD10, "J02.9");

        assertFalse(result.confidential());
        assertNull(result.sensitivityClass());
        assertTrue(result.categories().isEmpty());
    }

    @Test
    @DisplayName("one confidential code among many makes the whole record confidential")
    void anyConfidentialCode_protectsTheRecord() {
        stubGovernedMap(row("B20", "HIV"));

        var result = service.classify(TENANT, List.of(
                new ConfidentialCategoryService.CodedEntry(ICD10, "J02.9"),
                new ConfidentialCategoryService.CodedEntry(ICD10, "B20.1")));

        assertEquals(List.of("HIV"), result.categories(),
                "a consultation that touched HIV care does not become ordinary because it also "
                        + "recorded a sore throat");
    }

    @Test
    @DisplayName("multiple categories are all reported")
    void multipleCategories_areReported() {
        stubGovernedMap(row("B20", "HIV"), row("T74", "SAFEGUARDING"));

        var result = service.classify(TENANT, List.of(
                new ConfidentialCategoryService.CodedEntry(ICD10, "B20.0"),
                new ConfidentialCategoryService.CodedEntry(ICD10, "T74.1")));

        assertEquals(List.of("HIV", "SAFEGUARDING"), result.categories());
    }

    @Test
    @DisplayName("an uncovered code system is reported as a governance gap, not silently ordinary")
    void uncoveredCodeSystem_isReported() {
        stubGovernedMap(row("B20", "HIV"));

        var result = service.classify(TENANT, List.of(
                new ConfidentialCategoryService.CodedEntry("http://snomed.info/sct", "165816005")));

        assertFalse(result.confidential());
        assertEquals(List.of("http://snomed.info/sct"), result.unmatchedCodeSystems(),
                "an uncovered code system is a gap in the governed list, not evidence the content "
                        + "is ordinary — the caller must be able to tell the difference");
    }

    @Test
    @DisplayName("an empty governed map reports every code system as unmatched")
    void emptyGovernedMap_reportsUnavailable() {
        when(mappingIndexRepository.findByTenantIdAndTargetSystem(
                eq(TENANT), eq(ConfidentialCategoryService.CATEGORY_SYSTEM)))
                .thenReturn(List.of());

        var result = service.classifyCode(TENANT, ICD10, "B20.1");

        assertFalse(result.confidential());
        assertEquals(List.of(ICD10), result.unmatchedCodeSystems(),
                "'no list loaded' and 'nothing is confidential' must not look the same");
    }

    @Test
    @DisplayName("code and system matching is case- and whitespace-insensitive")
    void matchingIsNormalised() {
        stubGovernedMap(row("b20", "HIV"));

        assertEquals(List.of("HIV"),
                service.classifyCode(TENANT, " " + ICD10.toUpperCase() + " ", "B20.9").categories());
    }

    @Test
    @DisplayName("no codes, blank codes and nulls are handled without classifying anything")
    void degenerateInputs() {
        assertFalse(service.classify(TENANT, List.of()).confidential());
        assertFalse(service.classify(TENANT, null).confidential());

        stubGovernedMap(row("B20", "HIV"));
        assertFalse(service.classify(TENANT, List.of(
                new ConfidentialCategoryService.CodedEntry(ICD10, "  "))).confidential());
    }

    @Test
    @DisplayName("the governed map is ratified after W14-D preview flip")
    void shippedMapIsRatified() {
        assertTrue(ConfidentialCategoryService.CATEGORY_MAP_RATIFIED,
                "flip only together with pack ratification and V048/V307 activation");
        assertTrue(service.isRatified());
    }

    @Test
    @DisplayName("the active category vocabulary is readable for governance review")
    void activeCategories_areReadable() {
        stubGovernedMap(row("B20", "HIV"), row("B21", "HIV"), row("T74", "SAFEGUARDING"));

        assertEquals(List.of("HIV", "SAFEGUARDING"), service.activeCategories(TENANT));
    }
}
