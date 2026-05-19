package zw.gov.mohcc.impilo.mushex.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.mushex.api.dto.FinancialPlatformCardRequests;
import zw.gov.mohcc.impilo.mushex.domain.entity.ReversalRecordEntity;
import zw.gov.mohcc.impilo.mushex.service.FinancialCardReversalPlatformService;
import zw.gov.mohcc.impilo.mushex.service.PlatformResourceNotFoundException;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;

@RestController
@RequestMapping("/mushex/v1/platform/reversals")
public class ReversalRecordPlatformController {

    private final FinancialCardReversalPlatformService service;

    public ReversalRecordPlatformController(FinancialCardReversalPlatformService service) {
        this.service = service;
    }

    @ExceptionHandler(PlatformResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(PlatformResourceNotFoundException ex) {
        var ctx = TrustContextHolder.require();
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(PlatformResourceNotFoundException.CODE,
                        ex.getMessage(), 404, ctx.correlationId().toString()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ReversalRecordEntity>>> list() {
        var ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(service.listReversals(), ctx.correlationId().toString()));
    }

    @GetMapping("/{reversalId}")
    public ResponseEntity<ApiResponse<ReversalRecordEntity>> getById(@PathVariable String reversalId) {
        var ctx = TrustContextHolder.require();
        ReversalRecordEntity row = service.getReversal(reversalId);
        return ResponseEntity.ok(ApiResponse.ok(row, ctx.correlationId().toString()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ReversalRecordEntity>> create(
            @Valid @RequestBody FinancialPlatformCardRequests.CreateReversalRecordRequest request) {
        var ctx = TrustContextHolder.require();
        ReversalRecordEntity saved = service.createReversal(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(saved, ctx.correlationId().toString()));
    }
}
