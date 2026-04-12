package zw.gov.mohcc.impilo.wallet.api.controller;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;
import zw.gov.mohcc.impilo.wallet.api.dto.CreateWalletRequest;
import zw.gov.mohcc.impilo.wallet.api.dto.CreditWalletRequest;
import zw.gov.mohcc.impilo.wallet.api.dto.DebitWalletRequest;
import zw.gov.mohcc.impilo.wallet.api.dto.TransferRequest;
import zw.gov.mohcc.impilo.wallet.core.WalletService;
import zw.gov.mohcc.impilo.wallet.persistence.entity.TransactionEntity;
import zw.gov.mohcc.impilo.wallet.persistence.entity.WalletEntity;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Wallet CRUD and transaction endpoints.
 */
@RestController
@RequestMapping("/internal/v1/wallets")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WalletEntity>> createWallet(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @Valid @RequestBody CreateWalletRequest request) {

        UUID tid = UUID.fromString(tenantId);
        WalletEntity wallet = walletService.createWallet(
                tid,
                request.ownerType(),
                request.ownerRef(),
                request.displayName(),
                request.currency());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(wallet, correlationId));
    }

    @GetMapping("/{walletId}")
    public ResponseEntity<ApiResponse<WalletEntity>> getWallet(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @PathVariable UUID walletId) {

        UUID tid = UUID.fromString(tenantId);
        WalletEntity wallet = walletService.getWallet(tid, walletId);

        return ResponseEntity.ok(ApiResponse.ok(wallet, correlationId));
    }

    @GetMapping("/by-owner")
    public ResponseEntity<ApiResponse<WalletEntity>> getWalletByOwner(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestParam("owner_type") String ownerType,
            @RequestParam("owner_ref") String ownerRef) {

        UUID tid = UUID.fromString(tenantId);
        WalletEntity wallet = walletService.getWalletByOwner(tid, ownerType, ownerRef);

        return ResponseEntity.ok(ApiResponse.ok(wallet, correlationId));
    }

    @GetMapping("/{walletId}/balance")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getBalance(
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @PathVariable UUID walletId) {

        BigDecimal balance = walletService.getBalance(walletId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("walletId", walletId.toString());
        data.put("availableBalance", balance);

        return ResponseEntity.ok(ApiResponse.ok(data, correlationId));
    }

    @GetMapping("/{walletId}/transactions")
    public ResponseEntity<ApiResponse<PagedResponse<TransactionEntity>>> getTransactions(
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @PathVariable UUID walletId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<TransactionEntity> txnPage = walletService.getTransactions(walletId, pageable);

        PagedResponse<TransactionEntity> paged = PagedResponse.of(
                txnPage.getContent(), page, size, txnPage.getTotalElements());

        return ResponseEntity.ok(ApiResponse.ok(paged, correlationId));
    }

    @PostMapping("/{walletId}/credit")
    public ResponseEntity<ApiResponse<TransactionEntity>> credit(
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @PathVariable UUID walletId,
            @Valid @RequestBody CreditWalletRequest request) {

        UUID corrId = parseUuidOrNull(correlationId);

        TransactionEntity txn = walletService.credit(
                walletId,
                request.amount(),
                request.txnType(),
                request.channel(),
                request.reference(),
                request.description(),
                request.counterpartyRef(),
                request.counterpartyName(),
                corrId,
                idempotencyKey);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(txn, correlationId));
    }

    @PostMapping("/{walletId}/debit")
    public ResponseEntity<ApiResponse<TransactionEntity>> debit(
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @PathVariable UUID walletId,
            @Valid @RequestBody DebitWalletRequest request) {

        UUID corrId = parseUuidOrNull(correlationId);

        TransactionEntity txn = walletService.debit(
                walletId,
                request.amount(),
                request.txnType(),
                request.channel(),
                request.reference(),
                request.description(),
                request.counterpartyRef(),
                request.counterpartyName(),
                corrId,
                idempotencyKey);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(txn, correlationId));
    }

    @PostMapping("/transfer")
    public ResponseEntity<ApiResponse<TransactionEntity>> transfer(
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {

        UUID corrId = parseUuidOrNull(correlationId);

        TransactionEntity txn = walletService.transfer(
                request.fromWalletId(),
                request.toWalletId(),
                request.amount(),
                request.reference(),
                request.description(),
                request.channel(),
                corrId,
                idempotencyKey);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(txn, correlationId));
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private UUID parseUuidOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
