package zw.gov.mohcc.impilo.tuso.api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.visibility.AggregateVisibilityGuard;
import zw.gov.mohcc.impilo.shared.visibility.VisibilityContextHolder;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.VisibilityProfile;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityModeContextResponse;
import zw.gov.mohcc.impilo.tuso.core.FacilityModeService;

/**
 * Produces the C4 {@code FacilityModeContext} read-model — the single boundary the
 * shell consumes when a provider ENTERS facility mode. TUSO is the facility SoR and
 * the sole producer of this read-model.
 *
 * <p>Authz: TSHEPO ext_authz (existing path). Policy {@code FACILITY-MODE-ENTER}
 * (spec-only, track P) gates entry. Cross-tenant reads obey {@link AggregateVisibilityGuard}
 * — under aggregate-only visibility, only non-PII structural + aggregate fields are returned.</p>
 */
@RestController
@RequestMapping("/v1/internal/facility-mode")
public class FacilityModeController {

    private static final Logger log = LoggerFactory.getLogger(FacilityModeController.class);

    private final FacilityModeService facilityModeService;

    public FacilityModeController(FacilityModeService facilityModeService) {
        this.facilityModeService = facilityModeService;
    }

    @GetMapping("/{facilityId}/context")
    public ResponseEntity<ApiResponse<FacilityModeContextResponse>> getContext(
            @PathVariable Long facilityId) {

        TrustContext ctx = TrustContextHolder.require();
        VisibilityProfile vis = VisibilityContextHolder.current().orElse(null);
        boolean aggregateOnly = AggregateVisibilityGuard.blocksRowLevelDetail(vis);

        log.info("Building FacilityModeContext [facilityId={}, aggregateOnly={}] correlationId={}",
                facilityId, aggregateOnly, ctx.correlationId());

        FacilityModeContextResponse response = facilityModeService.buildContext(
                ctx, facilityId, aggregateOnly ? "AGGREGATE_ONLY" : "ROW_DETAIL");

        return ResponseEntity.ok(ApiResponse.ok(response, ctx.correlationId().toString()));
    }
}
