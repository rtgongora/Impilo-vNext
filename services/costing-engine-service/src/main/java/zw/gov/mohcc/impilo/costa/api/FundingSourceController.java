package zw.gov.mohcc.impilo.costa.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.costa.api.dto.financial.CreateFundingSourceRequest;
import zw.gov.mohcc.impilo.costa.domain.entity.FundingSourceEntity;
import zw.gov.mohcc.impilo.costa.service.FundingSourceService;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Funding sources / donors / grants that pay for budget lines. */
@RestController
@RequestMapping("/internal/v1/finance/funding-sources")
public class FundingSourceController {

    private final FundingSourceService service;

    public FundingSourceController(FundingSourceService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(@Valid @RequestBody CreateFundingSourceRequest body) {
        TrustContext ctx = TrustContextHolder.require();
        FundingSourceEntity e = service.create(ctx.tenantId(),
                new FundingSourceService.NewFundingSource(
                        body.name(), body.code(), body.sourceType(), body.donorName(),
                        body.grantReference(), body.ceilingAmount(), body.startDate(), body.endDate()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(BudgetJsonMapper.fundingSource(e), cid(ctx)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list() {
        TrustContext ctx = TrustContextHolder.require();
        List<Map<String, Object>> rows = service.list(ctx.tenantId()).stream()
                .map(BudgetJsonMapper::fundingSource).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(rows, cid(ctx)));
    }

    private static String cid(TrustContext ctx) {
        return ctx.correlationId() != null ? ctx.correlationId().toString() : null;
    }
}
