package zw.gov.mohcc.impilo.tuso.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Automated activation proof for the manual-derived inspection content
 * catalogue (HPA Manual Vol 1, 2017 — current operative manual):
 * provenance, uniqueness, coverage, applicability and no-invented-policy
 * invariants. A failure here means the catalogue must not activate.
 */
class InspectionContentCoverageTest {

    /** Every minimum-requirement schedule in the manual (pp16-73). */
    private static final Set<String> EXPECTED_MODULES = Set.of(
            "MOD-ALL-HEALTH-INSTITUTIONS", "MOD-MEDICAL-CONSULTING", "MOD-DENTAL-SURGERY",
            "MOD-PHARMACY", "MOD-OPTICAL-ROOMS", "MOD-MATERNITY-HOME",
            "MOD-OUTPATIENT-DIAGNOSTIC-IMAGING", "MOD-CONVENTIONAL-RADIOGRAPHY",
            "MOD-RF-TOMOGRAPHY", "MOD-ULTRASOUND", "MOD-CT", "MOD-MAMMOGRAPHY", "MOD-MRI",
            "MOD-INTERVENTIONAL-IMAGING", "MOD-NUCLEAR-MEDICINE", "MOD-RADIOTHERAPY", "MOD-DXA",
            "MOD-PHYSIOTHERAPY", "MOD-OCCUPATIONAL-THERAPY", "MOD-LABORATORY",
            "MOD-ACCIDENT-EMERGENCY", "MOD-AMBULANCE", "MOD-CLINIC", "MOD-VCT", "MOD-ICU",
            "MOD-THEATRE", "MOD-PHARMACEUTICAL-WHOLESALER", "MOD-NATURAL-THERAPIST",
            "MOD-NATURAL-HERBAL-MANUFACTURING", "MOD-ORTHOPAEDIC-TECHNOLOGIST",
            "MOD-CHIROPODIST-PODIATRIST", "MOD-HOSPITAL-FOOD-SERVICES", "MOD-BIOKINETICS",
            "MOD-AUDIOLOGY-SPEECH", "MOD-DIETICIAN", "MOD-PSYCHOLOGIST",
            "MOD-CLINICAL-SOCIAL-WORKER", "MOD-AIR-AMBULANCE");

    private static final Set<String> RETIRED_SAMPLE_TEMPLATES = Set.of(
            "GENERAL-MIN-INITIAL-V1", "CLINIC-INITIAL-V1", "LAB-INITIAL-V1", "MATERNITY-ROUTINE-V1");

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static List<Map<String, Object>> modules;
    private static Map<String, Object> compositionsDoc;
    private static Map<String, Object> applicabilityDoc;
    private static Map<String, Object> governanceDoc;

    @BeforeAll
    static void load() throws Exception {
        var resolver = new PathMatchingResourcePatternResolver();
        modules = new ArrayList<>();
        for (Resource file : resolver.getResources("classpath:regulatory/inspection-modules/*.json")) {
            modules.add(MAPPER.readValue(file.getInputStream(), new TypeReference<>() {}));
        }
        compositionsDoc = MAPPER.readValue(
                resolver.getResource("classpath:regulatory/inspection-compositions.json").getInputStream(),
                new TypeReference<>() {});
        applicabilityDoc = MAPPER.readValue(
                resolver.getResource("classpath:regulatory/classification-applicability.json").getInputStream(),
                new TypeReference<>() {});
        governanceDoc = MAPPER.readValue(
                resolver.getResource("classpath:regulatory/content-governance.json").getInputStream(),
                new TypeReference<>() {});
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(Map<String, Object> module) {
        return (List<Map<String, Object>>) module.get("items");
    }

    @Test
    void everySourceSectionIsRepresented() {
        Set<String> codes = new HashSet<>();
        modules.forEach(m -> codes.add((String) m.get("moduleCode")));
        assertThat(codes).containsExactlyInAnyOrderElementsOf(EXPECTED_MODULES);
    }

    @Test
    void everyActiveItemHasSourceProvenanceAndNoInventedPolicy() {
        for (Map<String, Object> module : modules) {
            String moduleCode = (String) module.get("moduleCode");
            assertThat(module.get("sourceHeading")).as(moduleCode + " sourceHeading").isNotNull();
            assertThat(module.get("sourcePages")).as(moduleCode + " sourcePages").isNotNull();
            for (Map<String, Object> item : items(module)) {
                String code = (String) item.get("code");
                assertThat(item.get("sourcePage")).as(code + " sourcePage").isNotNull();
                assertThat(item.get("sourceHeading")).as(code + " sourceHeading").isNotNull();
                assertThat((String) item.get("requirement")).as(code + " requirement").isNotBlank();
                assertThat(InspectionContentSeeder.VALID_OBLIGATIONS)
                        .as(code + " obligation").contains((String) item.get("obligation"));
                // No invented severity/scoring/pass-thresholds/enforcement consequences.
                assertThat(item).as(code + " invented policy fields")
                        .doesNotContainKeys("severity", "critical", "score", "passThreshold");
                if (Boolean.TRUE.equals(item.get("reviewRequired"))) {
                    assertThat((String) item.get("reviewReason")).as(code + " reviewReason").isNotBlank();
                }
            }
        }
    }

    @Test
    void noDuplicateStableRequirementCodes() {
        Set<String> seen = new HashSet<>();
        for (Map<String, Object> module : modules) {
            for (Map<String, Object> item : items(module)) {
                assertThat(seen.add((String) item.get("code")))
                        .as("duplicate stable requirement code: " + item.get("code")).isTrue();
            }
        }
        assertThat(seen.size()).isGreaterThan(1000);
    }

    @Test
    void groupedListsKeepExactlyOneParentPerGroup() {
        for (Map<String, Object> module : modules) {
            Map<String, Integer> parents = new HashMap<>();
            Map<String, Integer> members = new HashMap<>();
            for (Map<String, Object> item : items(module)) {
                String group = (String) item.get("group");
                if (group == null) continue;
                members.merge(group, 1, Integer::sum);
                if ("PARENT".equals(item.get("groupRole"))) {
                    parents.merge(group, 1, Integer::sum);
                }
            }
            for (Map.Entry<String, Integer> group : members.entrySet()) {
                assertThat(parents.getOrDefault(group.getKey(), 0))
                        .as(module.get("moduleCode") + " group " + group.getKey()).isEqualTo(1);
            }
        }
    }

    @Test
    void airAmbulancePreservesTheManualsOwnChecklistNumbering() {
        Map<String, Object> airAmbulance = modules.stream()
                .filter(m -> "MOD-AIR-AMBULANCE".equals(m.get("moduleCode")))
                .findFirst().orElseThrow();
        for (Map<String, Object> item : items(airAmbulance)) {
            assertThat((String) item.get("sourceItemNumber"))
                    .as(item.get("code") + " sourceItemNumber").isNotBlank();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void compositionsReferenceOnlyKnownModulesAndStartWithTheCommonModule() {
        Set<String> moduleCodes = new HashSet<>();
        modules.forEach(m -> moduleCodes.add((String) m.get("moduleCode")));
        List<Map<String, Object>> compositions = (List<Map<String, Object>>) compositionsDoc.get("compositions");
        assertThat(compositions).isNotEmpty();
        for (Map<String, Object> composition : compositions) {
            List<String> codes = (List<String>) composition.get("moduleCodes");
            assertThat(moduleCodes).as(composition.get("code") + " modules").containsAll(codes);
            if (codes.size() > 1 && codes.contains("MOD-ALL-HEALTH-INSTITUTIONS")) {
                assertThat(codes.get(0))
                        .as(composition.get("code") + " common module ordering")
                        .isEqualTo("MOD-ALL-HEALTH-INSTITUTIONS");
            }
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyV018ClassificationResolvesToAComposition() throws Exception {
        String migration = new String(new PathMatchingResourcePatternResolver()
                .getResource("classpath:db/migration/V018__hpa_regulatory_platform.sql")
                .getInputStream().readAllBytes());
        Matcher block = Pattern.compile(
                "INSERT INTO tuso\\.facility_classification.*?ON CONFLICT \\(class_code", Pattern.DOTALL)
                .matcher(migration);
        assertThat(block.find()).isTrue();
        Matcher labels = Pattern.compile("'[ABC]', '([^']+)'").matcher(block.group());
        Set<String> classificationLabels = new HashSet<>();
        while (labels.find()) {
            classificationLabels.add(labels.group(1));
        }
        assertThat(classificationLabels).hasSize(39);

        List<Map<String, Object>> mappings = (List<Map<String, Object>>) applicabilityDoc.get("mappings");
        Set<String> mappedLabels = new HashSet<>();
        Set<String> compositionCodes = new HashSet<>();
        ((List<Map<String, Object>>) compositionsDoc.get("compositions"))
                .forEach(c -> compositionCodes.add((String) c.get("code")));
        for (Map<String, Object> mapping : mappings) {
            assertThat(compositionCodes).as("mapping composition").contains((String) mapping.get("compositionCode"));
            if (mapping.get("classificationLabel") != null) {
                mappedLabels.add((String) mapping.get("classificationLabel"));
            }
        }
        assertThat(mappedLabels).as("all 39 V018 classifications covered").containsAll(classificationLabels);
    }

    @Test
    void governanceManifestCoversEveryModuleAndDeclaresApproval() {
        @SuppressWarnings("unchecked")
        Map<String, String> statuses = (Map<String, String>) governanceDoc.get("moduleStatuses");
        assertThat(statuses.keySet()).containsExactlyInAnyOrderElementsOf(EXPECTED_MODULES);
        statuses.values().forEach(status ->
                assertThat(InspectionContentSeeder.VALID_STATUSES).contains(status));
        assertThat((String) governanceDoc.get("approvedBy")).isNotBlank();
        assertThat((String) governanceDoc.get("effectiveFrom")).isNotBlank();
    }

    @Test
    void retiredSampleTemplatesAreRetiredInTheMigrationAndNeverReferencedByCompositions() throws Exception {
        String migration = new String(new PathMatchingResourcePatternResolver()
                .getResource("classpath:db/migration/V019__hpa_inspection_content_catalogue.sql")
                .getInputStream().readAllBytes());
        for (String sample : RETIRED_SAMPLE_TEMPLATES) {
            assertThat(migration).contains(sample);
        }
        assertThat(migration).contains("SET status = 'RETIRED'");
        String compositionsRaw = MAPPER.writeValueAsString(compositionsDoc);
        for (String sample : RETIRED_SAMPLE_TEMPLATES) {
            assertThat(compositionsRaw).doesNotContain(sample);
        }
    }
}
