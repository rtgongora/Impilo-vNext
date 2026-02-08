package zw.gov.mohcc.impilo.mushex.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.mushex.domain.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.PaymentIntentEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.ReconQueueEntity;
import zw.gov.mohcc.impilo.mushex.domain.enums.ReconStatus;
import zw.gov.mohcc.impilo.mushex.domain.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.PaymentIntentRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.ReconQueueRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bank reconciliation service.
 *
 * Imports external bank/payment-provider statement lines and matches them
 * against internal MUSHEX payment intents. Uses amount tolerance matching
 * (1%) to account for rounding differences in external systems.
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    /** Tolerance for amount matching: 1% difference allowed. */
    private static final BigDecimal MATCH_TOLERANCE = new BigDecimal("0.01");

    private final ReconQueueRepository reconRepository;
    private final PaymentIntentRepository intentRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public ReconciliationService(ReconQueueRepository reconRepository,
                                 PaymentIntentRepository intentRepository,
                                 EventOutboxRepository outboxRepository,
                                 ObjectMapper objectMapper) {
        this.reconRepository = reconRepository;
        this.intentRepository = intentRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Import bank statement lines for reconciliation.
     * Each line is parsed and stored as an UNMATCHED entry in the reconciliation queue.
     *
     * @param tenantId the tenant importing the statement
     * @param lines    list of statement line maps, each containing: ref, date, amount, currency, counterparty
     * @return list of created reconciliation queue entities
     */
    @Transactional
    public List<ReconQueueEntity> importStatement(UUID tenantId, List<Map<String, String>> lines) {
        TrustContextHolder.require();

        List<ReconQueueEntity> created = new ArrayList<>();

        for (Map<String, String> line : lines) {
            String ref = line.get("ref");
            String dateStr = line.get("date");
            String amountStr = line.get("amount");
            String currency = line.getOrDefault("currency", "USD");
            String counterparty = line.get("counterparty");

            if (ref == null || amountStr == null || dateStr == null) {
                log.warn("Skipping incomplete statement line: ref={}, date={}, amount={}", ref, dateStr, amountStr);
                continue;
            }

            ReconQueueEntity entry = new ReconQueueEntity();
            entry.setId(UlidGenerator.generate());
            entry.setTenantId(tenantId);
            entry.setStatementRef(ref);
            entry.setStatementDate(LocalDate.parse(dateStr));
            entry.setAmount(new BigDecimal(amountStr));
            entry.setCurrency(currency);
            entry.setCounterparty(counterparty);
            entry.setStatus(ReconStatus.UNMATCHED);

            entry = reconRepository.save(entry);
            created.add(entry);
        }

        log.info("Imported {} statement lines for tenant {}", created.size(), tenantId);

        return created;
    }

    /**
     * Get all unmatched reconciliation entries for a tenant.
     *
     * @param tenantId the tenant ID
     * @param pageable pagination parameters
     * @return page of unmatched entries
     */
    public Page<ReconQueueEntity> getUnmatched(UUID tenantId, Pageable pageable) {
        return reconRepository.findByTenantIdAndStatus(tenantId, ReconStatus.UNMATCHED, pageable);
    }

    /**
     * Match a reconciliation entry to a payment intent.
     * Validates that the amounts are within the 1% tolerance, then marks
     * the entry as MATCHED with the linked intent ID.
     *
     * @param reconId  the reconciliation entry ID
     * @param intentId the payment intent to match against
     * @return the matched reconciliation entry
     */
    @Transactional
    public ReconQueueEntity matchEntry(String reconId, String intentId) {
        TrustContext ctx = TrustContextHolder.require();

        ReconQueueEntity entry = reconRepository.findById(reconId)
                .orElseThrow(() -> new IllegalArgumentException("Reconciliation entry not found: " + reconId));

        if (entry.getStatus() != ReconStatus.UNMATCHED) {
            throw new IllegalStateException(
                    "Reconciliation entry is not unmatched, current status: " + entry.getStatus());
        }

        PaymentIntentEntity intent = intentRepository.findById(intentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment intent not found: " + intentId));

        // Validate amount tolerance (within 1%)
        BigDecimal reconAmount = entry.getAmount();
        BigDecimal intentAmount = intent.getAmountPaid();

        if (!isWithinTolerance(reconAmount, intentAmount)) {
            throw new IllegalArgumentException(
                    String.format("Amount mismatch beyond tolerance: statement=%s, intent=%s (tolerance=%s%%)",
                            reconAmount.toPlainString(), intentAmount.toPlainString(),
                            MATCH_TOLERANCE.multiply(new BigDecimal("100")).toPlainString()));
        }

        // Mark as matched
        entry.setStatus(ReconStatus.MATCHED);
        entry.setMatchedIntentId(intentId);
        entry.setMatchedAt(OffsetDateTime.now());
        entry = reconRepository.save(entry);

        log.info("Reconciliation matched: reconId={}, intentId={}, statementAmount={}, intentAmount={}",
                reconId, intentId, reconAmount.toPlainString(), intentAmount.toPlainString());

        publishEvent("RECONCILIATION", reconId, "RECON_MATCHED",
                Map.of(
                        "reconId", reconId,
                        "intentId", intentId,
                        "statementRef", entry.getStatementRef(),
                        "statementAmount", reconAmount.toPlainString(),
                        "intentAmount", intentAmount.toPlainString()
                ),
                ctx.tenantId());

        return entry;
    }

    /**
     * Check if two amounts are within the tolerance threshold (1%).
     * Computes abs(a - b) / max(abs(a), abs(b)) and checks if it's within tolerance.
     */
    private boolean isWithinTolerance(BigDecimal a, BigDecimal b) {
        if (a.compareTo(BigDecimal.ZERO) == 0 && b.compareTo(BigDecimal.ZERO) == 0) {
            return true;
        }

        BigDecimal diff = a.subtract(b).abs();
        BigDecimal maxVal = a.abs().max(b.abs());

        if (maxVal.compareTo(BigDecimal.ZERO) == 0) {
            return true;
        }

        BigDecimal ratio = diff.divide(maxVal, 6, RoundingMode.HALF_UP);
        return ratio.compareTo(MATCH_TOLERANCE) <= 0;
    }

    private void publishEvent(String aggregateType, String aggregateId,
                              String eventType, Map<String, Object> payload, UUID tenantId) {
        try {
            EventOutboxEntity event = new EventOutboxEntity();
            event.setAggregateType(aggregateType);
            event.setAggregateId(aggregateId);
            event.setEventType(eventType);
            event.setPayload(objectMapper.writeValueAsString(payload));
            event.setTenantId(tenantId);
            outboxRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to write outbox event: {}", eventType, e);
        }
    }
}
