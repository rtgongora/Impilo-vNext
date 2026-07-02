package zw.gov.mohcc.impilo.datagovernance.api;

import zw.gov.mohcc.impilo.datagovernance.core.TenantKeys;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.companion.error.ErrorEnvelope;
import zw.gov.mohcc.impilo.datagovernance.api.dto.ExportRequest;
import zw.gov.mohcc.impilo.datagovernance.api.dto.ExportResponse;
import zw.gov.mohcc.impilo.datagovernance.core.GovernanceService;

import java.util.UUID;

@RestController
public class ExportsController {

    private static final Logger log = LoggerFactory.getLogger(ExportsController.class);
    private final GovernanceService governanceService;

    public ExportsController(GovernanceService governanceService) {
        this.governanceService = governanceService;
    }

    @PostMapping("/external/v1/exports")
    public ResponseEntity<?> requestExport(@RequestBody ExportRequest request,
                                            HttpServletRequest httpRequest) {
        RequestContext ctx = RequestContextHolder.require();
        String idempotencyKey = httpRequest.getHeader(CompanionHeaders.IDEMPOTENCY_KEY);

        if (request.purposeOfUse() == null || request.purposeOfUse().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorEnvelope.of("MISSING_PURPOSE_OF_USE",
                            "purpose_of_use is required for export requests",
                            ctx.requestId(), ctx.correlationId()));
        }

        if (request.dataset() == null || request.dataset().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorEnvelope.of("MISSING_DATASET",
                            "dataset is required for export requests",
                            ctx.requestId(), ctx.correlationId()));
        }

        log.info("Export request [dataset={}, purpose={}] correlationId={}",
                request.dataset(), request.purposeOfUse(), ctx.correlationId());

        UUID tenantId = TenantKeys.tenantUuid(ctx.tenantId());
        ExportResponse response = governanceService.evaluateExport(
                request, tenantId, ctx.correlationId(), idempotencyKey);

        if ("DENY".equals(response.decision())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        return ResponseEntity.ok(response);
    }
}
