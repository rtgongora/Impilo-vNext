package zw.gov.mohcc.impilo.wallet.api.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.wallet.api.dto.AddFundingSourceRequest;
import zw.gov.mohcc.impilo.wallet.api.dto.CashDepositRequest;
import zw.gov.mohcc.impilo.wallet.api.dto.DepositRequest;
import zw.gov.mohcc.impilo.wallet.core.FundingService;
import zw.gov.mohcc.impilo.wallet.persistence.entity.FundingSourceEntity;
import zw.gov.mohcc.impilo.wallet.persistence.entity.TransactionEntity;

import java.util.List;
import java.util.UUID;

/**
 * Funding source management and deposit endpoints.
 */
@RestController
@RequestMapping("/internal/v1/funding")
public class FundingController {

    private final FundingService fundingService;

    public FundingController(FundingService fundingService) {
        this.fundingService = fundingService;
    }

    @PostMapping("/{walletId}/sources")
    public ResponseEntity<ApiResponse<FundingSourceEntity>> addFundingSource(
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @PathVariable UUID walletId,
            @Valid @RequestBody AddFundingSourceRequest request) {

        FundingSourceEntity source = fundingService.addFundingSource(
                walletId,
                request.sourceType(),
                request.provider(),
                request.accountRef(),
                request.accountName());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(source, correlationId));
    }

    @GetMapping("/{walletId}/sources")
    public ResponseEntity<ApiResponse<List<FundingSourceEntity>>> listFundingSources(
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @PathVariable UUID walletId) {

        List<FundingSourceEntity> sources = fundingService.listFundingSources(walletId);

        return ResponseEntity.ok(ApiResponse.ok(sources, correlationId));
    }

    @PostMapping("/{walletId}/deposit")
    public ResponseEntity<ApiResponse<TransactionEntity>> depositFromSource(
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @PathVariable UUID walletId,
            @Valid @RequestBody DepositRequest request) {

        TransactionEntity txn = fundingService.depositFromSource(
                walletId,
                request.sourceId(),
                request.amount(),
                request.reference());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(txn, correlationId));
    }

    @PostMapping("/{walletId}/cash-deposit")
    public ResponseEntity<ApiResponse<TransactionEntity>> depositCash(
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @PathVariable UUID walletId,
            @Valid @RequestBody CashDepositRequest request) {

        TransactionEntity txn = fundingService.depositCash(
                walletId,
                request.cashierId(),
                request.facilityId(),
                request.amount(),
                request.reference());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(txn, correlationId));
    }
}
