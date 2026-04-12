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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.wallet.api.dto.PayMerchantRequest;
import zw.gov.mohcc.impilo.wallet.api.dto.RegisterMerchantRequest;
import zw.gov.mohcc.impilo.wallet.core.MerchantService;
import zw.gov.mohcc.impilo.wallet.persistence.entity.MerchantAccountEntity;
import zw.gov.mohcc.impilo.wallet.persistence.entity.TransactionEntity;

import java.util.UUID;

/**
 * Merchant registration, lookup, and payment endpoints.
 */
@RestController
@RequestMapping("/internal/v1/merchants")
public class MerchantController {

    private final MerchantService merchantService;

    public MerchantController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MerchantAccountEntity>> registerMerchant(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @Valid @RequestBody RegisterMerchantRequest request) {

        UUID tid = UUID.fromString(tenantId);
        MerchantAccountEntity merchant = merchantService.registerMerchant(
                tid,
                request.providerNumber(),
                request.facilityId(),
                request.merchantName(),
                request.merchantType(),
                request.settlementAccount(),
                request.settlementBank());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(merchant, correlationId));
    }

    @GetMapping("/{merchantId}")
    public ResponseEntity<ApiResponse<MerchantAccountEntity>> getMerchant(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @PathVariable UUID merchantId) {

        UUID tid = UUID.fromString(tenantId);
        MerchantAccountEntity merchant = merchantService.getMerchant(tid, merchantId);

        return ResponseEntity.ok(ApiResponse.ok(merchant, correlationId));
    }

    @GetMapping("/by-provider")
    public ResponseEntity<ApiResponse<MerchantAccountEntity>> getMerchantByProviderNumber(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestParam("provider_number") String providerNumber) {

        UUID tid = UUID.fromString(tenantId);
        MerchantAccountEntity merchant = merchantService.getMerchantByProviderNumber(tid, providerNumber);

        return ResponseEntity.ok(ApiResponse.ok(merchant, correlationId));
    }

    @PostMapping("/pay")
    public ResponseEntity<ApiResponse<TransactionEntity>> payMerchant(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody PayMerchantRequest request) {

        UUID tid = UUID.fromString(tenantId);
        UUID corrId = parseUuidOrNull(correlationId);

        TransactionEntity txn = merchantService.payMerchant(
                tid,
                request.fromWalletId(),
                request.merchantProviderNumber(),
                request.amount(),
                request.reference(),
                request.channel(),
                corrId,
                idempotencyKey);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(txn, correlationId));
    }

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
