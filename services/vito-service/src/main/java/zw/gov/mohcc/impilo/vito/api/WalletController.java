package zw.gov.mohcc.impilo.vito.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;
import zw.gov.mohcc.impilo.vito.core.wallet.WalletService;
import zw.gov.mohcc.impilo.vito.persistence.entity.WalletEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.WalletJournalEntity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Health Payments Wallet API.
 * All mutations INTERNAL only. Balance reads available to both modes.
 */
@RestController
@RequestMapping("/v1/wallet")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<WalletEntity>> createWallet(@RequestBody CreateWalletRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        requireInternal(ctx);

        WalletEntity wallet = walletService.createWallet(
                ctx.tenantId(), request.healthId(), request.cardId(), request.currency());

        return ResponseEntity.status(201).body(
                ApiResponse.ok(wallet, ctx.correlationId().toString()));
    }

    @GetMapping("/{healthId}")
    public ResponseEntity<ApiResponse<WalletEntity>> getWallet(@PathVariable UUID healthId) {
        TrustContext ctx = TrustContextHolder.require();

        return walletService.getWallet(ctx.tenantId(), healthId)
                .map(w -> ResponseEntity.ok(ApiResponse.ok(w, ctx.correlationId().toString())))
                .orElse(ResponseEntity.status(404).body(
                        ApiResponse.error("NOT_FOUND", "Wallet not found", 404,
                                ctx.correlationId().toString())));
    }

    @PostMapping("/topup")
    public ResponseEntity<ApiResponse<WalletJournalEntity>> topUp(@RequestBody TopUpRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        requireInternal(ctx);

        WalletJournalEntity entry = walletService.topUp(
                ctx.tenantId(), request.healthId(), request.currency(),
                request.amount(), request.transactionRef(), request.description());

        return ResponseEntity.ok(ApiResponse.ok(entry, ctx.correlationId().toString()));
    }

    @PostMapping("/pay")
    public ResponseEntity<ApiResponse<WalletJournalEntity>> pay(@RequestBody PayRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        requireInternal(ctx);

        WalletJournalEntity entry = walletService.pay(
                ctx.tenantId(), request.healthId(), request.currency(),
                request.amount(), request.transactionRef(),
                request.counterpartyType(), request.counterpartyRef(), request.description());

        return ResponseEntity.ok(ApiResponse.ok(entry, ctx.correlationId().toString()));
    }

    @PostMapping("/offline")
    public ResponseEntity<ApiResponse<WalletJournalEntity>> recordOffline(
            @RequestBody OfflineTransactionRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        requireInternal(ctx);

        WalletJournalEntity entry = walletService.recordOfflineTransaction(
                ctx.tenantId(), request.healthId(), request.currency(),
                request.amount(), request.transactionRef(),
                request.counterpartyType(), request.counterpartyRef(),
                request.jwsSignature(), request.offlineTimestamp());

        return ResponseEntity.ok(ApiResponse.ok(entry, ctx.correlationId().toString()));
    }

    @GetMapping("/{walletId}/journal")
    public ResponseEntity<ApiResponse<PagedResponse<WalletJournalEntity>>> getJournal(
            @PathVariable Long walletId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        TrustContext ctx = TrustContextHolder.require();
        Page<WalletJournalEntity> entries = walletService.getJournal(
                walletId, PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.ok(
                PagedResponse.of(entries.getContent(), page, size, entries.getTotalElements()),
                ctx.correlationId().toString()));
    }

    private void requireInternal(TrustContext ctx) {
        if (ctx.mode() == AccessMode.EXTERNAL) {
            throw new SecurityException("Wallet operations are internal only");
        }
    }

    record CreateWalletRequest(UUID healthId, Long cardId, String currency) {}
    record TopUpRequest(UUID healthId, String currency, BigDecimal amount, String transactionRef, String description) {}
    record PayRequest(UUID healthId, String currency, BigDecimal amount, String transactionRef,
                      String counterpartyType, String counterpartyRef, String description) {}
    record OfflineTransactionRequest(UUID healthId, String currency, BigDecimal amount, String transactionRef,
                                      String counterpartyType, String counterpartyRef,
                                      String jwsSignature, OffsetDateTime offlineTimestamp) {}
}
