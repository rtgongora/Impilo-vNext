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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.mushex.api.dto.WalletPlatformRequests;
import zw.gov.mohcc.impilo.mushex.domain.entity.WalletAccountEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.WalletTransactionEntity;
import zw.gov.mohcc.impilo.mushex.service.PlatformResourceNotFoundException;
import zw.gov.mohcc.impilo.mushex.service.WalletPlatformService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;

@RestController
@RequestMapping("/mushex/v1/platform/wallets")
public class WalletPlatformController {

    private final WalletPlatformService walletPlatformService;

    public WalletPlatformController(WalletPlatformService walletPlatformService) {
        this.walletPlatformService = walletPlatformService;
    }

    @ExceptionHandler(PlatformResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(PlatformResourceNotFoundException ex) {
        var ctx = TrustContextHolder.require();
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(PlatformResourceNotFoundException.CODE,
                        ex.getMessage(), 404, ctx.correlationId().toString()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<WalletAccountEntity>>> list(@RequestParam String ownerRef) {
        var ctx = TrustContextHolder.require();
        List<WalletAccountEntity> rows = walletPlatformService.listForOwner(ownerRef);
        return ResponseEntity.ok(ApiResponse.ok(rows, ctx.correlationId().toString()));
    }

    @GetMapping("/{walletId}")
    public ResponseEntity<ApiResponse<WalletAccountEntity>> getById(@PathVariable String walletId) {
        var ctx = TrustContextHolder.require();
        WalletAccountEntity row = walletPlatformService.getWallet(walletId);
        return ResponseEntity.ok(ApiResponse.ok(row, ctx.correlationId().toString()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WalletAccountEntity>> create(
            @Valid @RequestBody WalletPlatformRequests.CreateWalletPlatformRequest request) {
        var ctx = TrustContextHolder.require();
        WalletAccountEntity w = walletPlatformService.createWallet(
                request.ownerType(), request.ownerRef(), request.currency(), request.walletType());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(w, ctx.correlationId().toString()));
    }

    @PostMapping("/{walletId}/credit")
    public ResponseEntity<ApiResponse<WalletTransactionEntity>> credit(
            @PathVariable String walletId,
            @Valid @RequestBody WalletPlatformRequests.WalletMovementRequest request) {
        var ctx = TrustContextHolder.require();
        WalletTransactionEntity txn = walletPlatformService.credit(
                walletId, request.amount(), request.transactionType(), request.referenceCode());
        return ResponseEntity.ok(ApiResponse.ok(txn, ctx.correlationId().toString()));
    }

    @PostMapping("/{walletId}/debit")
    public ResponseEntity<ApiResponse<WalletTransactionEntity>> debit(
            @PathVariable String walletId,
            @Valid @RequestBody WalletPlatformRequests.WalletMovementRequest request) {
        var ctx = TrustContextHolder.require();
        WalletTransactionEntity txn = walletPlatformService.debit(
                walletId, request.amount(), request.transactionType(), request.referenceCode());
        return ResponseEntity.ok(ApiResponse.ok(txn, ctx.correlationId().toString()));
    }

    @GetMapping("/{walletId}/transactions")
    public ResponseEntity<ApiResponse<List<WalletTransactionEntity>>> transactions(@PathVariable String walletId) {
        var ctx = TrustContextHolder.require();
        List<WalletTransactionEntity> rows = walletPlatformService.listTransactions(walletId);
        return ResponseEntity.ok(ApiResponse.ok(rows, ctx.correlationId().toString()));
    }
}
