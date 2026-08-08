package zw.gov.mohcc.impilo.zibo.api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.zibo.core.ConceptReprojectionService;

/**
 * Administration of the derived concept index.
 *
 * <p>Deliberately the same shape as {@code POST /v1/map/rebuild}: the index is a projection, and a
 * projection needs a way to be rebuilt from its source without republishing every artifact one at
 * a time.</p>
 *
 * <p>This exists because the projection had exactly one trigger — {@code ArtifactService.publish}
 * — and every vocabulary ZIBO actually holds was inserted by a migration, which does not call it.
 * The index was therefore empty on any database built from migrations, for content that was
 * plainly present in {@code zibo_artifacts}.</p>
 */
@RestController
@RequestMapping("/v1/concepts")
public class ConceptIndexController {

    private static final Logger log = LoggerFactory.getLogger(ConceptIndexController.class);

    private final ConceptReprojectionService conceptReprojectionService;

    public ConceptIndexController(ConceptReprojectionService conceptReprojectionService) {
        this.conceptReprojectionService = conceptReprojectionService;
    }

    /**
     * Rebuild the concept index for every servable CodeSystem across all tenant planes.
     *
     * <p>Idempotent: each artifact's rows are replaced wholesale, never merged.</p>
     */
    @PostMapping("/rebuild")
    public ResponseEntity<ApiResponse<ConceptReprojectionService.ReprojectionResult>> rebuild() {
        TrustContext ctx = TrustContextHolder.require();

        ConceptReprojectionService.ReprojectionResult result = conceptReprojectionService.reprojectAll();

        log.info("Concept index rebuilt by {}: {} of {} CodeSystems, {} concepts, {} failed",
                ctx.actorId(), result.codeSystemsProjected(), result.codeSystemsFound(),
                result.conceptsWritten(), result.failed());

        return ResponseEntity.ok(ApiResponse.ok(result, ctx.correlationId().toString()));
    }
}
