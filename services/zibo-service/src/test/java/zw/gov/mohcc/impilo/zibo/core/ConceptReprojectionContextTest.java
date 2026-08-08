package zw.gov.mohcc.impilo.zibo.core;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.zibo.domain.ArtifactStatus;
import zw.gov.mohcc.impilo.zibo.domain.ArtifactType;
import zw.gov.mohcc.impilo.zibo.persistence.entity.ArtifactEntity;
import zw.gov.mohcc.impilo.zibo.persistence.repository.ArtifactRepository;
import zw.gov.mohcc.impilo.zibo.persistence.repository.ConceptRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reprojection through a real Spring context, with real proxies and a real transaction manager.
 *
 * <h2>Why this exists, specifically</h2>
 * <p>The unit tests for reprojection passed while the feature was completely broken in production.
 * They construct the services with {@code new}, so there is no Spring proxy — and the defect was
 * entirely a proxy defect. {@code reprojectAll} originally looped calling {@code this.rebuild(...)}
 * inside the same class, which bypasses the proxy, so
 * {@code @Transactional(REQUIRES_NEW)} never took effect and every {@code @Modifying} delete failed
 * with "Executing an update/delete query".</p>
 *
 * <p>Measured live in preview: {@code codeSystemsFound: 20, codeSystemsProjected: 0,
 * conceptsWritten: 0, failed: 20}. A mock-only suite cannot see that, so this one boots the
 * container. Red-prove it by moving {@code reprojectAll} back into
 * {@link ConceptProjectionService} — it fails, where the unit tests do not.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class ConceptReprojectionContextTest {

    @Autowired
    private ConceptReprojectionService reprojectionService;

    @Autowired
    private ArtifactRepository artifactRepository;

    @Autowired
    private ConceptRepository conceptRepository;

    private ArtifactEntity seedDirectlyLikeAMigrationWould(String url, String version, String json) {
        // Deliberately bypasses ArtifactService.publish — that is exactly how every real vocabulary
        // in this service got here, and exactly why the index was empty.
        ArtifactEntity a = new ArtifactEntity();
        a.setArtifactId(UUID.randomUUID());
        a.setTenantId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        a.setFhirType(ArtifactType.CODE_SYSTEM);
        a.setCanonicalUrl(url);
        a.setVersion(version);
        a.setName("SeededCodeSystem");
        a.setStatus(ArtifactStatus.PUBLISHED);
        a.setContentJson(json);
        a.setContentHash("seed-hash-" + UUID.randomUUID());
        a.setCreatedBy("migration");
        a.setCreatedAt(OffsetDateTime.now());
        a.setUpdatedAt(OffsetDateTime.now());
        return artifactRepository.save(a);
    }

    @Test
    @DisplayName("a migration-seeded CodeSystem becomes searchable after reprojection")
    void seededCodeSystemIsProjectedThroughTheRealContainer() {
        String url = "https://impilo.gov.zw/fhir/CodeSystem/context-test-" + UUID.randomUUID();
        ArtifactEntity seeded = seedDirectlyLikeAMigrationWould(url, "1.0.0",
                "{\"resourceType\":\"CodeSystem\",\"url\":\"" + url + "\",\"version\":\"1.0.0\","
                        + "\"content\":\"complete\",\"concept\":["
                        + "{\"code\":\"GP\",\"display\":\"General Practice\"},"
                        + "{\"code\":\"SURG\",\"display\":\"General Surgery\"}]}");

        assertThat(conceptRepository.countByArtifactId(seeded.getArtifactId())).isZero();

        var result = reprojectionService.reprojectAll();

        // The whole point: this must be zero. A non-zero failed count with a 200 response is what
        // the live estate returned before the proxy boundary was fixed.
        assertThat(result.failed())
                .as("reprojection must not fail — a failure here is the transaction proxy, not the data")
                .isZero();
        assertThat(conceptRepository.countByArtifactId(seeded.getArtifactId())).isEqualTo(2);
    }

    @Test
    @DisplayName("reprojection is idempotent — running it twice does not duplicate concepts")
    void reprojectionIsIdempotentAgainstARealDatabase() {
        String url = "https://impilo.gov.zw/fhir/CodeSystem/context-idem-" + UUID.randomUUID();
        ArtifactEntity seeded = seedDirectlyLikeAMigrationWould(url, "1.0.0",
                "{\"resourceType\":\"CodeSystem\",\"url\":\"" + url + "\",\"version\":\"1.0.0\","
                        + "\"content\":\"complete\",\"concept\":[{\"code\":\"A\",\"display\":\"Alpha\"}]}");

        reprojectionService.reprojectAll();
        long afterFirst = conceptRepository.countByArtifactId(seeded.getArtifactId());
        reprojectionService.reprojectAll();
        long afterSecond = conceptRepository.countByArtifactId(seeded.getArtifactId());

        assertThat(afterFirst).isEqualTo(1);
        assertThat(afterSecond)
                .as("rows are replaced wholesale, never merged")
                .isEqualTo(afterFirst);
    }
}
