package zw.gov.mohcc.impilo.tuso.api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.tuso.core.FacilityOperationalizationService;

/**
 * Governed bulk default provisioning for registry-absorbed facilities.
 * Batch-scoped (paced by the operator script) so a national run neither
 * monopolizes the service nor produces an unbounded transaction.
 */
@RestController
@RequestMapping("/v1/internal/facilities")
public class FacilityOperationalizationController {

    private static final Logger log = LoggerFactory.getLogger(FacilityOperationalizationController.class);

    private final FacilityOperationalizationService operationalizationService;

    public FacilityOperationalizationController(FacilityOperationalizationService operationalizationService) {
        this.operationalizationService = operationalizationService;
    }

    public record OperationalizeRequest(String province, Integer batchSize, Boolean dryRun) {}

    @PostMapping("/operationalize-defaults")
    public ResponseEntity<ApiResponse<FacilityOperationalizationService.OperationalizeResult>> operationalizeDefaults(
            @RequestBody(required = false) OperationalizeRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        String province = request != null ? request.province() : null;
        int batchSize = request != null && request.batchSize() != null ? request.batchSize() : 100;
        boolean dryRun = request != null && Boolean.TRUE.equals(request.dryRun());
        log.info("Operationalize-defaults requested [province={}, batchSize={}, dryRun={}] correlationId={}",
                province, batchSize, dryRun, ctx.correlationId());
        FacilityOperationalizationService.OperationalizeResult result =
                operationalizationService.operationalizeDefaults(province, batchSize, dryRun);
        return ResponseEntity.ok(ApiResponse.ok(result, ctx.correlationId().toString()));
    }
}
