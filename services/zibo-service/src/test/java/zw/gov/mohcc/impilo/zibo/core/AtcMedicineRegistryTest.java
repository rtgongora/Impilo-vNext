package zw.gov.mohcc.impilo.zibo.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.zibo.config.ZiboProperties;
import zw.gov.mohcc.impilo.zibo.domain.ArtifactStatus;
import zw.gov.mohcc.impilo.zibo.domain.ArtifactType;
import zw.gov.mohcc.impilo.zibo.domain.PolicyMode;
import zw.gov.mohcc.impilo.zibo.persistence.entity.ArtifactEntity;
import zw.gov.mohcc.impilo.zibo.persistence.repository.ArtifactRepository;
import zw.gov.mohcc.impilo.zibo.persistence.repository.AssignmentRepository;
import zw.gov.mohcc.impilo.zibo.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.zibo.persistence.repository.ValidationJobRepository;
import zw.gov.mohcc.impilo.zibo.persistence.repository.ValidationLogRepository;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;

/**
 * OF-B8/OF-D — the ZIBO national medicine registry lane, proven over the EXACT
 * content V007 seeds (the CodeSystem JSON is extracted from the migration file
 * itself, so this test cannot drift from the seed):
 *
 * <ul>
 *   <li>seed shape — 20 EDLIZ-starter concepts under {@code http://www.whocc.no/atc},
 *       governed properties present, exactly 2 controlled medicines;</li>
 *   <li>the OF-B3 fix — validate-coding for an ATC coding now resolves the
 *       registered CodeSystem (an unknown code is {@code code-invalid}, never
 *       the pre-V007 {@code not-found} system miss);</li>
 *   <li>registry read model — resolve by code and search by name project the
 *       same concepts (no parallel machinery).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AtcMedicineRegistryTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID CORRELATION = UUID.randomUUID();
    private static final String ATC = "http://www.whocc.no/atc";

    private static String seededContent;

    @Mock private ArtifactRepository artifactRepository;
    @Mock private AssignmentRepository assignmentRepository;
    @Mock private ValidationJobRepository validationJobRepository;
    @Mock private ValidationLogRepository validationLogRepository;
    @Mock private EventOutboxRepository outboxRepository;

    private final ObjectMapper mapper = new ObjectMapper();
    private ValidationEngine engine;
    private MedicineRegistryService registry;

    @BeforeAll
    static void extractSeedContentFromMigration() throws Exception {
        String sql = new String(new ClassPathResource("db/migration/V007__atc_medicine_registry.sql")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int start = sql.indexOf("$cs$");
        int end = sql.lastIndexOf("$cs$");
        assertThat(start).as("migration must contain a $cs$-delimited CodeSystem").isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);
        seededContent = sql.substring(start + 4, end).trim();
    }

    @BeforeEach
    void setUp() {
        ZiboProperties props = new ZiboProperties();
        props.getValidation().setDefaultMode("STRICT");
        engine = new ValidationEngine(artifactRepository, assignmentRepository, validationJobRepository,
                validationLogRepository, outboxRepository, props, mapper);
        registry = new MedicineRegistryService(artifactRepository, mapper);
        lenient().when(artifactRepository.findByTenantIdAndCanonicalUrl(TENANT, ATC))
                .thenReturn(List.of(publishedAtcCodeSystem()));
    }

    private TrustContext ctx() {
        return new TrustContext(TENANT, "pharmacist-1", "PROVIDER", "TREATMENT",
                null, CORRELATION, null, null, null, AccessMode.INTERNAL);
    }

    private ArtifactEntity publishedAtcCodeSystem() {
        ArtifactEntity a = new ArtifactEntity();
        a.setArtifactId(UUID.randomUUID());
        a.setTenantId(TENANT);
        a.setFhirType(ArtifactType.CODE_SYSTEM);
        a.setCanonicalUrl(ATC);
        a.setVersion("2026.01");
        a.setName("who-atc-medicines-zw-starter");
        a.setStatus(ArtifactStatus.PUBLISHED);
        a.setContentJson(seededContent);
        a.setContentHash("hash-atc");
        a.setCreatedBy("system-seeder");
        a.setPublishedBy("system-seeder");
        a.setPublishedAt(OffsetDateTime.now());
        a.setCreatedAt(OffsetDateTime.now());
        a.setUpdatedAt(OffsetDateTime.now());
        return a;
    }

    // ── seed shape ───────────────────────────────────────────────────────

    @Test
    void seedIsValidJson_with20Concepts_and2Controlled_andGovernedProperties() throws Exception {
        JsonNode root = mapper.readTree(seededContent);
        assertThat(root.path("resourceType").asText()).isEqualTo("CodeSystem");
        assertThat(root.path("url").asText()).isEqualTo(ATC);

        JsonNode concepts = root.path("concept");
        assertThat(concepts.isArray()).isTrue();
        assertThat(concepts.size()).isEqualTo(20);

        int controlled = 0;
        for (JsonNode concept : concepts) {
            assertThat(concept.path("code").asText()).isNotBlank();
            assertThat(concept.path("display").asText()).isNotBlank();
            boolean hasGeneric = false;
            boolean hasSchedule = false;
            for (JsonNode p : concept.path("property")) {
                switch (p.path("code").asText()) {
                    case "genericName" -> hasGeneric = !p.path("valueString").asText().isBlank();
                    case "scheduleClass" -> hasSchedule = !p.path("valueString").asText().isBlank();
                    case "controlled" -> controlled += p.path("valueBoolean").asBoolean() ? 1 : 0;
                    default -> { }
                }
            }
            assertThat(hasGeneric).as("genericName on %s", concept.path("code").asText()).isTrue();
            assertThat(hasSchedule).as("scheduleClass on %s", concept.path("code").asText()).isTrue();
        }
        assertThat(controlled).as("exactly two controlled medicines in the starter set").isEqualTo(2);
    }

    // ── the OF-B3 fix: ATC codings now validate ──────────────────────────

    @Test
    void validateCoding_seededAtcCode_isValid() {
        try (MockedStatic<TrustContextHolder> h = mockStatic(TrustContextHolder.class)) {
            h.when(TrustContextHolder::require).thenReturn(ctx());
            h.when(TrustContextHolder::get).thenReturn(ctx());

            ValidationEngine.ValidationResult r = engine.validateCoding(
                    ATC, "J01CA04", "Amoxicillin 500 mg capsule", PolicyMode.STRICT, null);

            assertThat(r.valid()).isTrue();
            assertThat(r.issues()).isEmpty();
        }
    }

    @Test
    void validateCoding_unknownAtcCode_isCodeInvalid_neverSystemNotFound() {
        try (MockedStatic<TrustContextHolder> h = mockStatic(TrustContextHolder.class)) {
            h.when(TrustContextHolder::require).thenReturn(ctx());
            h.when(TrustContextHolder::get).thenReturn(ctx());

            ValidationEngine.ValidationResult r = engine.validateCoding(
                    ATC, "Z99ZZ99", null, PolicyMode.STRICT, null);

            assertThat(r.valid()).isFalse();
            assertThat(r.issues()).hasSize(1);
            // The pre-V007 posture was "not-found" (no CodeSystem for the ATC
            // system at all — the OF-B3 finding). Registered now: the system
            // resolves and only the CODE is judged.
            assertThat(r.issues().get(0).code()).isEqualTo("code-invalid");
        }
    }

    // ── registry read model ──────────────────────────────────────────────

    @Test
    void resolveByCode_projectsGovernedProperties() {
        try (MockedStatic<TrustContextHolder> h = mockStatic(TrustContextHolder.class)) {
            h.when(TrustContextHolder::require).thenReturn(ctx());

            Optional<MedicineRegistryService.Medicine> morphine = registry.resolveByCode("N02AA01");

            assertThat(morphine).isPresent();
            assertThat(morphine.get().genericName()).isEqualTo("Morphine");
            assertThat(morphine.get().scheduleClass()).isEqualTo("CONTROLLED_DRUG");
            assertThat(morphine.get().controlled()).isTrue();
            assertThat(morphine.get().system()).isEqualTo(ATC);
        }
    }

    @Test
    void resolveByCode_unknown_isEmpty() {
        try (MockedStatic<TrustContextHolder> h = mockStatic(TrustContextHolder.class)) {
            h.when(TrustContextHolder::require).thenReturn(ctx());

            assertThat(registry.resolveByCode("Z99ZZ99")).isEmpty();
        }
    }

    @Test
    void searchByName_matchesGenericName_caseInsensitively() {
        try (MockedStatic<TrustContextHolder> h = mockStatic(TrustContextHolder.class)) {
            h.when(TrustContextHolder::require).thenReturn(ctx());

            List<MedicineRegistryService.Medicine> hits = registry.searchByName("amoxi", 10);

            assertThat(hits).hasSize(1);
            assertThat(hits.get(0).atcCode()).isEqualTo("J01CA04");
        }
    }

    @Test
    void searchByName_matchesAtcCodePrefix() {
        try (MockedStatic<TrustContextHolder> h = mockStatic(TrustContextHolder.class)) {
            h.when(TrustContextHolder::require).thenReturn(ctx());

            List<MedicineRegistryService.Medicine> hits = registry.searchByName("J01", 10);

            // J01CA04, J01EE01, J01AA02, J01DD04, J01CE08
            assertThat(hits).extracting(MedicineRegistryService.Medicine::atcCode)
                    .containsExactlyInAnyOrder("J01CA04", "J01EE01", "J01AA02", "J01DD04", "J01CE08");
        }
    }

    @Test
    void noRegisteredAtcCodeSystem_registryIsHonestlyEmpty() {
        try (MockedStatic<TrustContextHolder> h = mockStatic(TrustContextHolder.class)) {
            h.when(TrustContextHolder::require).thenReturn(ctx());
            lenient().when(artifactRepository.findByTenantIdAndCanonicalUrl(TENANT, ATC))
                    .thenReturn(List.of());

            assertThat(registry.resolveByCode("J01CA04")).isEmpty();
            assertThat(registry.searchByName("amoxicillin", 10)).isEmpty();
        }
    }
}
