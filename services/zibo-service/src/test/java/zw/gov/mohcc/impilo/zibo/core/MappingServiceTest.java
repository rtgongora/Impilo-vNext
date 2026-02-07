package zw.gov.mohcc.impilo.zibo.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.zibo.domain.ArtifactStatus;
import zw.gov.mohcc.impilo.zibo.domain.ArtifactType;
import zw.gov.mohcc.impilo.zibo.persistence.entity.ArtifactEntity;
import zw.gov.mohcc.impilo.zibo.persistence.entity.MappingIndexEntity;
import zw.gov.mohcc.impilo.zibo.persistence.repository.ArtifactRepository;
import zw.gov.mohcc.impilo.zibo.persistence.repository.MappingIndexRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MappingService}.
 *
 * <p>Validates concept mapping lookup, ConceptMap parsing, and index
 * rebuild operations.</p>
 */
@ExtendWith(MockitoExtension.class)
class MappingServiceTest {

    @Mock
    private MappingIndexRepository mappingIndexRepository;

    @Mock
    private ArtifactRepository artifactRepository;

    @Mock
    private ArtifactService artifactService;

    private MappingService mappingService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID FACILITY_ID = UUID.randomUUID();
    private static final String ACTOR_ID = "admin-001";
    private static final UUID CORRELATION_ID = UUID.randomUUID();
    private static final String SOURCE_SYSTEM = "http://snomed.info/sct";
    private static final String TARGET_SYSTEM = "http://hl7.org/fhir/sid/icd-10";

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        mappingService = new MappingService(
                mappingIndexRepository, artifactRepository, objectMapper, artifactService);
    }

    private TrustContext createTrustContext() {
        return new TrustContext(TENANT_ID, ACTOR_ID, "ADMIN", "GOVERNANCE",
                null, CORRELATION_ID, FACILITY_ID, null, null, AccessMode.INTERNAL);
    }

    private MappingIndexEntity createMappingEntry(String sourceCode, String targetCode,
                                                    String targetDisplay, String equivalence) {
        MappingIndexEntity entry = new MappingIndexEntity();
        entry.setMappingId(UUID.randomUUID());
        entry.setTenantId(TENANT_ID);
        entry.setSourceSystem(SOURCE_SYSTEM);
        entry.setSourceCode(sourceCode);
        entry.setTargetSystem(TARGET_SYSTEM);
        entry.setTargetCode(targetCode);
        entry.setTargetDisplay(targetDisplay);
        entry.setEquivalence(equivalence);
        entry.setConfidence(BigDecimal.ONE);
        entry.setConceptmapRef(UUID.randomUUID());
        entry.setCreatedAt(OffsetDateTime.now());
        return entry;
    }

    @Test
    @DisplayName("mapCode: found mapping returns result with target code")
    void test_mapCode_foundMapping_returnsResult() {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(createTrustContext());

            MappingIndexEntity entry = createMappingEntry("73211009", "I10", "Essential hypertension", "equivalent");

            when(mappingIndexRepository.findByTenantIdAndSourceSystemAndSourceCode(
                    TENANT_ID, SOURCE_SYSTEM, "73211009"))
                    .thenReturn(List.of(entry));

            MappingService.MappingResult result = mappingService.mapCode(
                    SOURCE_SYSTEM, "73211009", TARGET_SYSTEM);

            assertThat(result.targetCode()).isEqualTo("I10");
            assertThat(result.targetDisplay()).isEqualTo("Essential hypertension");
            assertThat(result.targetSystem()).isEqualTo(TARGET_SYSTEM);
            assertThat(result.equivalence()).isEqualTo("equivalent");
            assertThat(result.confidence()).isEqualByComparingTo(BigDecimal.ONE);
        }
    }

    @Test
    @DisplayName("mapCode: no mapping returns empty result")
    void test_mapCode_noMapping_returnsEmpty() {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(createTrustContext());

            when(mappingIndexRepository.findByTenantIdAndSourceSystemAndSourceCode(
                    TENANT_ID, SOURCE_SYSTEM, "UNKNOWN"))
                    .thenReturn(Collections.emptyList());

            MappingService.MappingResult result = mappingService.mapCode(
                    SOURCE_SYSTEM, "UNKNOWN", TARGET_SYSTEM);

            assertThat(result.targetCode()).isNull();
            assertThat(result.targetSystem()).isEqualTo(TARGET_SYSTEM);
        }
    }

    @Test
    @DisplayName("rebuildIndex: parses ConceptMap and creates mapping entries")
    void test_rebuildIndex_parsesConceptMap() {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(createTrustContext());

            UUID artifactId = UUID.randomUUID();

            String conceptMapJson = """
                    {
                      "resourceType": "ConceptMap",
                      "group": [
                        {
                          "source": "http://snomed.info/sct",
                          "target": "http://hl7.org/fhir/sid/icd-10",
                          "element": [
                            {
                              "code": "73211009",
                              "display": "Diabetes mellitus",
                              "target": [
                                {
                                  "code": "E14",
                                  "display": "Unspecified diabetes mellitus",
                                  "equivalence": "equivalent"
                                }
                              ]
                            },
                            {
                              "code": "38341003",
                              "display": "Hypertension",
                              "target": [
                                {
                                  "code": "I10",
                                  "display": "Essential hypertension",
                                  "equivalence": "equivalent"
                                },
                                {
                                  "code": "I15",
                                  "display": "Secondary hypertension",
                                  "equivalence": "wider"
                                }
                              ]
                            }
                          ]
                        }
                      ]
                    }
                    """;

            ArtifactEntity conceptMap = new ArtifactEntity();
            conceptMap.setArtifactId(artifactId);
            conceptMap.setTenantId(TENANT_ID);
            conceptMap.setFhirType(ArtifactType.CONCEPT_MAP);
            conceptMap.setCanonicalUrl("http://example.org/ConceptMap/snomed-to-icd10");
            conceptMap.setVersion("1.0.0");
            conceptMap.setName("SNOMED-to-ICD10");
            conceptMap.setStatus(ArtifactStatus.PUBLISHED);
            conceptMap.setContentJson(conceptMapJson);
            conceptMap.setContentHash("hash");
            conceptMap.setCreatedBy(ACTOR_ID);
            conceptMap.setCreatedAt(OffsetDateTime.now());
            conceptMap.setUpdatedAt(OffsetDateTime.now());

            when(artifactRepository.findByTenantIdAndArtifactId(TENANT_ID, artifactId))
                    .thenReturn(Optional.of(conceptMap));

            mappingService.rebuildIndex(artifactId);

            verify(mappingIndexRepository).deleteByConceptmapRef(artifactId);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<MappingIndexEntity>> captor = ArgumentCaptor.forClass(List.class);
            verify(mappingIndexRepository).saveAll(captor.capture());

            List<MappingIndexEntity> saved = captor.getValue();
            assertThat(saved).hasSize(3);

            // Verify first entry: 73211009 -> E14
            MappingIndexEntity first = saved.stream()
                    .filter(e -> "73211009".equals(e.getSourceCode()) && "E14".equals(e.getTargetCode()))
                    .findFirst()
                    .orElseThrow();
            assertThat(first.getSourceSystem()).isEqualTo("http://snomed.info/sct");
            assertThat(first.getTargetSystem()).isEqualTo("http://hl7.org/fhir/sid/icd-10");
            assertThat(first.getEquivalence()).isEqualTo("equivalent");

            // Verify second entry: 38341003 -> I10
            MappingIndexEntity second = saved.stream()
                    .filter(e -> "38341003".equals(e.getSourceCode()) && "I10".equals(e.getTargetCode()))
                    .findFirst()
                    .orElseThrow();
            assertThat(second.getEquivalence()).isEqualTo("equivalent");

            // Verify third entry: 38341003 -> I15
            MappingIndexEntity third = saved.stream()
                    .filter(e -> "38341003".equals(e.getSourceCode()) && "I15".equals(e.getTargetCode()))
                    .findFirst()
                    .orElseThrow();
            assertThat(third.getEquivalence()).isEqualTo("wider");
        }
    }
}
