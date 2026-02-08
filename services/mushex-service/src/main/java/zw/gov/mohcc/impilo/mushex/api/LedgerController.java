package zw.gov.mohcc.impilo.mushex.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.mushex.domain.entity.LedgerEntryEntity;
import zw.gov.mohcc.impilo.mushex.service.LedgerService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;

@RestController
@RequestMapping("/mushex/v1/ledger")
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @GetMapping("/entries")
    public ResponseEntity<ApiResponse<PagedResponse<LedgerEntryEntity>>> getEntries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();

        Pageable pageable = PageRequest.of(page, size);
        Page<LedgerEntryEntity> result = ledgerService.getEntries(ctx.tenantId(), pageable);

        PagedResponse<LedgerEntryEntity> paged = PagedResponse.of(
                result.getContent(), page, size, result.getTotalElements());

        return ResponseEntity.ok(ApiResponse.ok(paged, correlationId));
    }
}
