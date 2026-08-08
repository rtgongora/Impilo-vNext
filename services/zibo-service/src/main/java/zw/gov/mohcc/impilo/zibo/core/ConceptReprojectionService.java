package zw.gov.mohcc.impilo.zibo.core;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.zibo.domain.ArtifactStatus;
import zw.gov.mohcc.impilo.zibo.domain.ArtifactType;
import zw.gov.mohcc.impilo.zibo.persistence.entity.ArtifactEntity;
import zw.gov.mohcc.impilo.zibo.persistence.repository.ArtifactRepository;

/**
 * Rebuilds the concept index for the whole estate, one artifact at a time.
 *
 * <p>Without this the index is empty on any database whose content arrived through migrations.
 * Every seeded artifact is {@code INSERT}ed directly at {@code PUBLISHED} and never passes through
 * {@code ArtifactService.publish}, which was the only caller of
 * {@link ConceptProjectionService#rebuild}. So a freshly migrated ZIBO answered {@code $lookup} and
 * {@code $expand} with nothing at all for every vocabulary it demonstrably held — measured live in
 * preview at Flyway v402: {@code zibo_concept} existed and contained zero rows, and
 * {@code $expand} on the 31-concept clinical-specialty ValueSet returned
 * {@code "total": 0, "contains": []} with {@code "success": true}.</p>
 *
 * <h2>Why this is its own bean</h2>
 * <p>It has to be. {@link ConceptProjectionService#rebuild} is
 * {@code @Transactional(REQUIRES_NEW)} so that one bad artifact cannot roll the others back — but a
 * loop calling {@code this.rebuild(...)} from inside the same class bypasses the Spring proxy
 * entirely, so no transaction is started and the {@code @Modifying} delete fails with "Executing an
 * update/delete query". That is not theory: the first live run of this reprojection returned
 * {@code codeSystemsFound: 20, projected: 0, failed: 20} for exactly that reason. Calling across a
 * bean boundary is what makes the annotation apply.</p>
 *
 * <p>Unit tests could not catch it — they construct the service directly, so there is no proxy and
 * no transaction manager. {@code ConceptReprojectionContextTest} boots a real context for that
 * reason.</p>
 */
@Service
public class ConceptReprojectionService {

    private static final Logger log = LoggerFactory.getLogger(ConceptReprojectionService.class);

    /**
     * Statuses whose content is servable. {@code DRAFT} is excluded: a draft projects when it is
     * published, and indexing one could collide with the published row on (system, version, code).
     */
    private static final List<ArtifactStatus> SERVABLE = List.of(
            ArtifactStatus.PUBLISHED, ArtifactStatus.ACTIVE,
            ArtifactStatus.DEPRECATED, ArtifactStatus.RETIRED);

    private final ConceptProjectionService conceptProjectionService;
    private final ArtifactRepository artifactRepository;

    public ConceptReprojectionService(ConceptProjectionService conceptProjectionService,
                                      ArtifactRepository artifactRepository) {
        this.conceptProjectionService = conceptProjectionService;
        this.artifactRepository = artifactRepository;
    }

    /**
     * Reprojects every servable CodeSystem across all tenant planes.
     *
     * <p>Idempotent: {@code rebuild} replaces an artifact's rows wholesale rather than merging.</p>
     */
    public ReprojectionResult reprojectAll() {
        List<ArtifactEntity> targets =
                artifactRepository.findByFhirTypeAndStatusIn(ArtifactType.CODE_SYSTEM, SERVABLE);

        int artifacts = 0;
        int concepts = 0;
        int failed = 0;
        for (ArtifactEntity artifact : targets) {
            try {
                concepts += conceptProjectionService.rebuild(artifact);
                artifacts++;
            } catch (Exception e) {
                // One unparseable or unprojectable artifact must not abandon the other forty.
                failed++;
                log.error("ZIBO: reprojection failed for {} v{} ({}): {}",
                        artifact.getCanonicalUrl(), artifact.getVersion(),
                        artifact.getArtifactId(), e.getMessage(), e);
            }
        }
        log.info("ZIBO: reprojected {} of {} CodeSystems, {} concepts, {} failed",
                artifacts, targets.size(), concepts, failed);
        return new ReprojectionResult(targets.size(), artifacts, concepts, failed);
    }

    /**
     * Outcome of a full reprojection.
     *
     * <p>{@code failed} is returned to the caller rather than logged and dropped. A reprojection
     * that quietly reported success while writing nothing is precisely how the empty index went
     * unnoticed in the first place.</p>
     */
    public record ReprojectionResult(int codeSystemsFound, int codeSystemsProjected,
                                     int conceptsWritten, int failed) {
    }
}
