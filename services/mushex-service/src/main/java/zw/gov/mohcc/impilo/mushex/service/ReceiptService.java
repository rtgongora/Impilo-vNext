package zw.gov.mohcc.impilo.mushex.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.mushex.domain.entity.PaymentIntentEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.ReceiptEntity;
import zw.gov.mohcc.impilo.mushex.domain.repository.PaymentIntentRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.ReceiptRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Receipt generation service.
 *
 * Creates receipt records when a payment intent reaches PAID status.
 * The receipt captures a summary snapshot of the payment and can optionally
 * be linked to a Landela document for PDF generation.
 */
@Service
public class ReceiptService {

    private static final Logger log = LoggerFactory.getLogger(ReceiptService.class);

    private final ReceiptRepository receiptRepository;
    private final PaymentIntentRepository intentRepository;
    private final ObjectMapper objectMapper;

    public ReceiptService(ReceiptRepository receiptRepository,
                          PaymentIntentRepository intentRepository,
                          ObjectMapper objectMapper) {
        this.receiptRepository = receiptRepository;
        this.intentRepository = intentRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Generate a receipt for a paid payment intent.
     * Creates a receipt with a JSON summary capturing the payment details.
     * The landelaDocId is left null and would be populated by an async
     * integration with the Landela document service.
     *
     * @param intentId the payment intent to generate a receipt for
     * @return the generated receipt entity
     */
    @Transactional
    public ReceiptEntity generateReceipt(String intentId) {
        PaymentIntentEntity intent = intentRepository.findById(intentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment intent not found: " + intentId));

        // Check if receipt already exists (idempotent)
        Optional<ReceiptEntity> existing = receiptRepository.findByIntentId(intentId);
        if (existing.isPresent()) {
            log.info("Receipt already exists for intent {}: receiptId={}",
                    intentId, existing.get().getReceiptId());
            return existing.get();
        }

        // Build summary JSON
        Map<String, Object> summaryMap = new LinkedHashMap<>();
        summaryMap.put("intentId", intent.getIntentId());
        summaryMap.put("sourceType", intent.getSourceType().name());
        summaryMap.put("sourceId", intent.getSourceId());
        summaryMap.put("amountTotal", intent.getAmountTotal().toPlainString());
        summaryMap.put("amountPaid", intent.getAmountPaid().toPlainString());
        summaryMap.put("currency", intent.getCurrency());
        summaryMap.put("status", intent.getStatus().name());
        summaryMap.put("tenantId", intent.getTenantId().toString());
        // Citizen marketplace/fee intents legitimately carry no facility; an NPE
        // here killed the settlement AFTER the wallet was already debited.
        summaryMap.put("facilityId", intent.getFacilityId() != null ? intent.getFacilityId().toString() : null);
        summaryMap.put("issuedAt", OffsetDateTime.now().toString());

        String summaryJson;
        try {
            summaryJson = objectMapper.writeValueAsString(summaryMap);
        } catch (Exception e) {
            log.error("Failed to serialize receipt summary for intent {}", intentId, e);
            summaryJson = "{}";
        }

        ReceiptEntity receipt = new ReceiptEntity();
        receipt.setReceiptId(UlidGenerator.generate());
        receipt.setIntentId(intentId);
        receipt.setLandelaDocId(null); // Will be filled by doc-service integration
        receipt.setIssuedAt(OffsetDateTime.now());
        receipt.setSummary(summaryJson);

        receipt = receiptRepository.save(receipt);

        log.info("Generated receipt: receiptId={}, intentId={}", receipt.getReceiptId(), intentId);

        return receipt;
    }

    /**
     * Get the receipt for a payment intent.
     *
     * @param intentId the payment intent ID
     * @return the receipt, or empty if none exists
     */
    public Optional<ReceiptEntity> getReceipt(String intentId) {
        return receiptRepository.findByIntentId(intentId);
    }

    public List<ReceiptEntity> getReceiptsByIntentId(String intentId) {
        return receiptRepository.findByIntentId(intentId)
                .map(List::of)
                .orElseGet(List::of);
    }
}
