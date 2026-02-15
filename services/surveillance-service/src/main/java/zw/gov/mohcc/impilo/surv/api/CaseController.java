package zw.gov.mohcc.impilo.surv.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;
import zw.gov.mohcc.impilo.surv.core.CaseService;
import zw.gov.mohcc.impilo.surv.core.CaseStatus;
import zw.gov.mohcc.impilo.surv.persistence.entity.CaseEntity;

@RestController
@RequestMapping("/internal/v1/cases")
public class CaseController {

    private final CaseService caseService;

    public CaseController(CaseService caseService) {
        this.caseService = caseService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<CaseEntity>>> listCases(
            @RequestParam(required = false) CaseStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        TrustContext ctx = TrustContextHolder.require();

        Page<CaseEntity> results = caseService.listCases(
                ctx.tenantId(), status, PageRequest.of(page, size));

        return ResponseEntity.ok(ApiResponse.ok(
                PagedResponse.of(results.getContent(), page, size, results.getTotalElements()),
                ctx.correlationId().toString()));
    }
}
