package zw.gov.mohcc.impilo.zibo.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.zibo.domain.ArtifactStatus;
import zw.gov.mohcc.impilo.zibo.domain.ArtifactType;
import zw.gov.mohcc.impilo.zibo.domain.AuthorityScope;
import zw.gov.mohcc.impilo.zibo.persistence.entity.ArtifactEntity;
import zw.gov.mohcc.impilo.zibo.persistence.entity.ConceptEntity;
import zw.gov.mohcc.impilo.zibo.persistence.repository.ArtifactRepository;
import zw.gov.mohcc.impilo.zibo.persistence.repository.ConceptRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for whole-estate concept reprojection.
 *
 * <p>The defect these cover: {@code ConceptProjectionService.rebuild} had exactly one caller,
 * {@code ArtifactService.publish}. Every vocabulary ZIBO actually holds is inserted directly by a
 * migration or a seed script at {@code PUBLISHED}/{@code ACTIVE} — none of which calls
 * {@code publish}. So the index was empty on every database built the way real ones are built, and
 * {@code $lookup} / {@code $expand} answered nothing for content sitting in plain view in
 * {@code zibo_artifacts}. Deploying the migration that creates the table does not fix that.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConceptReprojectionTest {

    private static final UUID NATIONAL_PLANE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CARE_PLANE = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @Mock
    private ConceptRepository conceptRepository;

    @Mock
    private ArtifactRepository artifactRepository;

    @Mock
    private EntityManager entityManager;

    private ConceptReprojectionService service;
    private List<ConceptEntity> saved;

    @BeforeEach
    void setUp() {
        saved = new ArrayList<>();
        when(entityManager.createNativeQuery(anyString())).thenReturn(mock(Query.class, invocation -> {
            // The search_vector UPDATE is fluent; every call returns the query itself except the
            // terminal executeUpdate. A bare mock would return null on setParameter and NPE, which
            // would route this whole suite through the catch block instead of the happy path.
            if (invocation.getMethod().getName().equals("executeUpdate")) {
                return 1;
            }
            return invocation.getMock();
        }));
        when(conceptRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<ConceptEntity> batch = invocation.getArgument(0);
            for (ConceptEntity c : batch) {
                if (c.getConceptId() == null) {
                    c.setConceptId(UUID.randomUUID());
                }
            }
            saved.addAll(batch);
            return batch;
        });

        ConceptProjectionService projection = new ConceptProjectionService(
                conceptRepository, new ObjectMapper(), entityManager, NATIONAL_PLANE.toString());
        service = new ConceptReprojectionService(projection, artifactRepository);
    }

    @Test
    @DisplayName("reprojectAll: projects seeded CodeSystems that were never published through the API")
    void test_reprojectAll_projectsSeededCodeSystems() {
        ArtifactEntity seeded = codeSystem(NATIONAL_PLANE, ArtifactStatus.PUBLISHED,
                "https://impilo.gov.zw/fhir/CodeSystem/clinical-specialty", "1.1.0",
                concepts("GP", "General Practice", "SURG", "General Surgery"));

        when(artifactRepository.findByFhirTypeAndStatusIn(eq(ArtifactType.CODE_SYSTEM), anyList()))
                .thenReturn(List.of(seeded));

        ConceptReprojectionService.ReprojectionResult result = service.reprojectAll();

        assertThat(result.codeSystemsFound()).isEqualTo(1);
        assertThat(result.codeSystemsProjected()).isEqualTo(1);
        assertThat(result.conceptsWritten()).isEqualTo(2);
        assertThat(result.failed()).isZero();
        assertThat(saved).extracting(ConceptEntity::getCode).containsExactly("GP", "SURG");
    }

    @Test
    @DisplayName("reprojectAll: a care-plane artifact projects as TENANT scope, not NATIONAL")
    void test_reprojectAll_respectsAuthorityPlane() {
        ArtifactEntity national = codeSystem(NATIONAL_PLANE, ArtifactStatus.PUBLISHED,
                "https://impilo.gov.zw/fhir/CodeSystem/a", "1.0.0", concepts("A", "Alpha"));
        ArtifactEntity care = codeSystem(CARE_PLANE, ArtifactStatus.PUBLISHED,
                "https://zibo.mohcc.gov.zw/fhir/CodeSystem/icd10-zw", "10-2025", concepts("B54", "Malaria"));

        when(artifactRepository.findByFhirTypeAndStatusIn(eq(ArtifactType.CODE_SYSTEM), anyList()))
                .thenReturn(List.of(national, care));

        service.reprojectAll();

        ConceptEntity nationalConcept = saved.stream()
                .filter(c -> "A".equals(c.getCode())).findFirst().orElseThrow();
        ConceptEntity careConcept = saved.stream()
                .filter(c -> "B54".equals(c.getCode())).findFirst().orElseThrow();

        assertThat(nationalConcept.getAuthorityScope()).isEqualTo(AuthorityScope.NATIONAL);
        assertThat(nationalConcept.getTenantId()).isNull();
        assertThat(careConcept.getAuthorityScope()).isEqualTo(AuthorityScope.TENANT);
        assertThat(careConcept.getTenantId()).isEqualTo(CARE_PLANE);
    }

    @Test
    @DisplayName("reprojectAll: one unparseable artifact does not abandon the rest")
    void test_reprojectAll_isolatesFailures() {
        ArtifactEntity broken = codeSystem(NATIONAL_PLANE, ArtifactStatus.PUBLISHED,
                "https://impilo.gov.zw/fhir/CodeSystem/broken", "1.0.0", "{ this is not json");
        ArtifactEntity good = codeSystem(NATIONAL_PLANE, ArtifactStatus.PUBLISHED,
                "https://impilo.gov.zw/fhir/CodeSystem/good", "1.0.0", concepts("OK", "Fine"));

        when(artifactRepository.findByFhirTypeAndStatusIn(eq(ArtifactType.CODE_SYSTEM), anyList()))
                .thenReturn(List.of(broken, good));

        ConceptReprojectionService.ReprojectionResult result = service.reprojectAll();

        // Unparseable content is logged and returns 0 rather than throwing, so it counts as
        // projected-with-nothing. Either way the second artifact must still be written.
        assertThat(result.codeSystemsFound()).isEqualTo(2);
        assertThat(result.conceptsWritten()).isEqualTo(1);
        assertThat(saved).extracting(ConceptEntity::getCode).containsExactly("OK");
    }

    @Test
    @DisplayName("reprojectAll: replaces an artifact's rows wholesale, so repeating it is idempotent")
    void test_reprojectAll_isIdempotent() {
        ArtifactEntity seeded = codeSystem(NATIONAL_PLANE, ArtifactStatus.PUBLISHED,
                "https://impilo.gov.zw/fhir/CodeSystem/a", "1.0.0", concepts("A", "Alpha"));

        when(artifactRepository.findByFhirTypeAndStatusIn(eq(ArtifactType.CODE_SYSTEM), anyList()))
                .thenReturn(List.of(seeded));

        service.reprojectAll();
        saved.clear();
        ConceptReprojectionService.ReprojectionResult second = service.reprojectAll();

        assertThat(second.conceptsWritten()).isEqualTo(1);
        // Deletion before insert is what makes a second run a replacement rather than a duplicate.
        verify(conceptRepository, org.mockito.Mockito.times(2))
                .deleteByArtifactId(seeded.getArtifactId());
    }

    @Test
    @DisplayName("reprojectAll: never asks the repository for DRAFT artifacts")
    void test_reprojectAll_excludesDraft() {
        when(artifactRepository.findByFhirTypeAndStatusIn(eq(ArtifactType.CODE_SYSTEM), anyList()))
                .thenReturn(List.of());

        service.reprojectAll();

        // A DRAFT artifact projects when it is published; including it here would make unreviewed
        // content searchable and could collide with the published row on (system, version, code).
        verify(artifactRepository).findByFhirTypeAndStatusIn(eq(ArtifactType.CODE_SYSTEM),
                org.mockito.ArgumentMatchers.argThat(
                        statuses -> !statuses.contains(ArtifactStatus.DRAFT)
                                && statuses.contains(ArtifactStatus.PUBLISHED)
                                && statuses.contains(ArtifactStatus.ACTIVE)));
    }

    @Test
    @DisplayName("projection never clears the whole persistence context")
    void test_persist_detachesOnlyItsOwnBatch() {
        ArtifactEntity seeded = codeSystem(NATIONAL_PLANE, ArtifactStatus.PUBLISHED,
                "https://impilo.gov.zw/fhir/CodeSystem/a", "1.0.0", concepts("A", "Alpha"));

        when(artifactRepository.findByFhirTypeAndStatusIn(eq(ArtifactType.CODE_SYSTEM), anyList()))
                .thenReturn(List.of(seeded));

        service.reprojectAll();

        // clear() also detached the ArtifactEntity that publish() was mid-way through returning.
        verify(entityManager, never()).clear();
        verify(entityManager).detach(any(ConceptEntity.class));
    }

    // --- fixtures -------------------------------------------------------------------------------

    private static String concepts(String... codeDisplayPairs) {
        StringBuilder sb = new StringBuilder(
                "{\"resourceType\":\"CodeSystem\",\"content\":\"complete\",\"concept\":[");
        for (int i = 0; i < codeDisplayPairs.length; i += 2) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"code\":\"").append(codeDisplayPairs[i])
              .append("\",\"display\":\"").append(codeDisplayPairs[i + 1]).append("\"}");
        }
        return sb.append("]}").toString();
    }

    private static ArtifactEntity codeSystem(UUID tenantId, ArtifactStatus status,
                                             String url, String version, String contentJson) {
        ArtifactEntity a = new ArtifactEntity();
        a.setArtifactId(UUID.randomUUID());
        a.setTenantId(tenantId);
        a.setFhirType(ArtifactType.CODE_SYSTEM);
        a.setCanonicalUrl(url);
        a.setVersion(version);
        a.setStatus(status);
        a.setContentJson(contentJson);
        return a;
    }
}
