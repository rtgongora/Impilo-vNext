package zw.gov.mohcc.impilo.ndr.api;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.companion.error.ErrorEnvelope;
import zw.gov.mohcc.impilo.ndr.api.dto.ReleaseRunResponse;
import zw.gov.mohcc.impilo.ndr.client.DataGovernanceDeidClient.DeidUnavailableException;
import zw.gov.mohcc.impilo.ndr.core.NdrReleaseService;
import zw.gov.mohcc.impilo.ndr.domain.ReleaseRunEntity;

import java.util.UUID;

/**
 * Internal trigger for building the NDR released / analytics zone from gold via the
 * data-governance de-identification engine. Internal plane only (behind Envoy).
 */
@RestController
public class NdrReleaseController {

    private static final Logger log = LoggerFactory.getLogger(NdrReleaseController.class);

    private final NdrReleaseService releaseService;

    public NdrReleaseController(NdrReleaseService releaseService) {
        this.releaseService = releaseService;
    }

    // ── POST /internal/v1/ndr/release/{datasetRef} — Build the released zone for a dataset ──

    @PostMapping("/internal/v1/ndr/release/{datasetRef}")
    public ResponseEntity<?> release(@PathVariable("datasetRef") String datasetRef,
                                     HttpServletRequest httpRequest) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());

        log.info("NDR release build requested [dataset={}] correlationId={}", datasetRef, ctx.correlationId());
        try {
            ReleaseRunEntity run = releaseService.release(
                    tenantId, datasetRef, ctx.podId(), ctx.correlationId());
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(toResponse(run));
        } catch (DeidUnavailableException e) {
            log.error("De-identification engine unavailable for release build: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ErrorEnvelope.of("DEID_UNAVAILABLE",
                            "De-identification engine is unreachable, cannot build release zone",
                            ctx.requestId(), ctx.correlationId()));
        }
    }

    private ReleaseRunResponse toResponse(ReleaseRunEntity run) {
        return new ReleaseRunResponse(
                run.getReleaseId().toString(),
                run.getDatasetRef(),
                run.getStatus(),
                run.getDeidRunId() != null ? run.getDeidRunId().toString() : null,
                run.getManifestId() != null ? run.getManifestId().toString() : null,
                run.getPolicyVersion(),
                run.getRowsIn(),
                run.getRowsOut(),
                run.getRowsSuppressed(),
                run.getRejectionReason(),
                run.getBuiltAt() != null ? run.getBuiltAt().toString() : null);
    }
}
