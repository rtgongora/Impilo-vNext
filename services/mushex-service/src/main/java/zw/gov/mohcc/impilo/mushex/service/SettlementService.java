package zw.gov.mohcc.impilo.mushex.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.mushex.domain.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.PaymentIntentEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.PayoutBatchEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.PayoutItemEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.SettlementEntity;
import zw.gov.mohcc.impilo.mushex.domain.enums.AdapterType;
import zw.gov.mohcc.impilo.mushex.domain.enums.IntentStatus;
import zw.gov.mohcc.impilo.mushex.domain.enums.PayeeType;
import zw.gov.mohcc.impilo.mushex.domain.enums.PayoutStatus;
import zw.gov.mohcc.impilo.mushex.domain.enums.SettlementStatus;
import zw.gov.mohcc.impilo.mushex.domain.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.PaymentIntentRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.PayoutBatchRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.PayoutItemRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.SettlementRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Settlement and payout service.
 *
 * Computes settlement totals for a given period by aggregating paid payment intents,
 * creates payout batches and items, and manages the release of funds.
 * Settlement release requires step-up authorization.
 */
@Service
public class SettlementService {

    private static final Logger log = LoggerFactory.getLogger(SettlementService.class);

    private final SettlementRepository settlementRepository;
    private final PayoutBatchRepository batchRepository;
    private final PayoutItemRepository itemRepository;
    private final PaymentIntentRepository intentRepository;
    private final LedgerService ledgerService;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public SettlementService(SettlementRepository settlementRepository,
                             PayoutBatchRepository batchRepository,
                             PayoutItemRepository itemRepository,
                             PaymentIntentRepository intentRepository,
                             LedgerService ledgerService,
                             EventOutboxRepository outboxRepository,
                             ObjectMapper objectMapper) {
        this.settlementRepository = settlementRepository;
        this.batchRepository = batchRepository;
        this.itemRepository = itemRepository;
        this.intentRepository = intentRepository;
        this.ledgerService = ledgerService;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Run a settlement computation for the given period.
     * Aggregates all PAID intents in the date range, computes totals,
     * and creates the settlement record with payout items.
     *
     * @param tenantId    the tenant to settle
     * @param periodStart start of the settlement period (inclusive)
     * @param periodEnd   end of the settlement period (inclusive)
     * @return the created settlement entity
     */
    @Transactional
    public SettlementEntity runSettlement(UUID tenantId, LocalDate periodStart, LocalDate periodEnd) {
        TrustContextHolder.require();

        // Create settlement in COMPUTING status
        SettlementEntity settlement = new SettlementEntity();
        settlement.setSettlementId(UlidGenerator.generate());
        settlement.setTenantId(tenantId);
        settlement.setPeriodStart(periodStart);
        settlement.setPeriodEnd(periodEnd);
        settlement.setStatus(SettlementStatus.COMPUTING);
        settlement = settlementRepository.save(settlement);

        log.info("Starting settlement computation: id={}, period={} to {}",
                settlement.getSettlementId(), periodStart, periodEnd);

        // Aggregate paid intents in the period
        OffsetDateTime startDateTime = periodStart.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime endDateTime = periodEnd.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        List<PaymentIntentEntity> paidIntents = intentRepository
                .findByTenantIdAndStatusAndCreatedAtBetween(tenantId, IntentStatus.PAID, startDateTime, endDateTime);

        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        int intentCount = 0;

        for (PaymentIntentEntity intent : paidIntents) {
            totalAmount = totalAmount.add(intent.getAmountTotal());
            totalPaid = totalPaid.add(intent.getAmountPaid());
            intentCount++;
        }

        // Build totals JSON
        Map<String, Object> totalsMap = new LinkedHashMap<>();
        totalsMap.put("intentCount", intentCount);
        totalsMap.put("totalAmount", totalAmount.toPlainString());
        totalsMap.put("totalPaid", totalPaid.toPlainString());
        totalsMap.put("periodStart", periodStart.toString());
        totalsMap.put("periodEnd", periodEnd.toString());

        String totalsJson;
        try {
            totalsJson = objectMapper.writeValueAsString(totalsMap);
        } catch (Exception e) {
            log.error("Failed to serialize settlement totals", e);
            totalsJson = "{}";
        }

        settlement.setTotals(totalsJson);
        settlement.setStatus(SettlementStatus.COMPUTED);
        settlement = settlementRepository.save(settlement);

        // Create a default payout batch
        PayoutBatchEntity batch = new PayoutBatchEntity();
        batch.setBatchId(UlidGenerator.generate());
        batch.setSettlementId(settlement.getSettlementId());
        batch.setAdapterType(AdapterType.BANK_TRANSFER.name());
        batch.setStatus(PayoutStatus.PENDING);
        batch = batchRepository.save(batch);

        // Create payout items - one per facility with paid intents
        Map<UUID, BigDecimal> facilityTotals = new LinkedHashMap<>();
        for (PaymentIntentEntity intent : paidIntents) {
            facilityTotals.merge(intent.getFacilityId(), intent.getAmountPaid(), BigDecimal::add);
        }

        for (Map.Entry<UUID, BigDecimal> entry : facilityTotals.entrySet()) {
            PayoutItemEntity item = new PayoutItemEntity();
            item.setId(UlidGenerator.generate());
            item.setBatchId(batch.getBatchId());
            item.setPayeeType(PayeeType.FACILITY);
            item.setPayeeRef(entry.getKey().toString());
            item.setAmount(entry.getValue());
            item.setCurrency("USD");
            item.setStatus(PayoutStatus.PENDING);
            itemRepository.save(item);
        }

        log.info("Settlement computed: id={}, intents={}, totalPaid={}",
                settlement.getSettlementId(), intentCount, totalPaid.toPlainString());

        publishEvent("SETTLEMENT", settlement.getSettlementId(), "SETTLEMENT_COMPUTED",
                Map.of(
                        "settlementId", settlement.getSettlementId(),
                        "intentCount", intentCount,
                        "totalPaid", totalPaid.toPlainString(),
                        "periodStart", periodStart.toString(),
                        "periodEnd", periodEnd.toString()
                ),
                tenantId);

        return settlement;
    }

    /**
     * Fetch a settlement with its batches and items.
     *
     * @param settlementId the settlement ID
     * @return the settlement entity
     */
    public SettlementEntity getSettlement(String settlementId) {
        return settlementRepository.findById(settlementId)
                .orElseThrow(() -> new IllegalArgumentException("Settlement not found: " + settlementId));
    }

    /**
     * Release payouts for a computed settlement.
     * Transitions COMPUTED -> RELEASED, creates payout batch records,
     * and posts ledger entries. Requires step-up authorization.
     *
     * @param settlementId the settlement to release
     * @return the released settlement entity
     */
    @Transactional
    public SettlementEntity releasePayouts(String settlementId) {
        TrustContextHolder.require();
        SettlementEntity settlement = getSettlement(settlementId);

        if (settlement.getStatus() != SettlementStatus.COMPUTED) {
            throw new IllegalStateException(
                    "Cannot release settlement in status: " + settlement.getStatus());
        }

        // Step-up authorization would be enforced at the controller/filter level
        log.info("Releasing settlement payouts: id={} (step-up required)", settlementId);

        settlement.setStatus(SettlementStatus.RELEASED);
        settlement = settlementRepository.save(settlement);

        // Mark all batches as processing
        List<PayoutBatchEntity> batches = batchRepository.findBySettlementId(settlementId);
        for (PayoutBatchEntity batch : batches) {
            batch.setStatus(PayoutStatus.PROCESSING);
            batch.setReleasedAt(OffsetDateTime.now());
            batchRepository.save(batch);

            // Post ledger entry for each batch's items
            List<PayoutItemEntity> items = itemRepository.findByBatchId(batch.getBatchId());
            BigDecimal batchTotal = items.stream()
                    .map(PayoutItemEntity::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            ledgerService.postSettlement(settlement.getTenantId(), settlementId,
                    batchTotal, "USD");
        }

        log.info("Settlement released: id={}, batches={}", settlementId, batches.size());

        publishEvent("SETTLEMENT", settlementId, "SETTLEMENT_BATCH_RELEASED",
                Map.of(
                        "settlementId", settlementId,
                        "batchCount", batches.size()
                ),
                settlement.getTenantId());

        return settlement;
    }

    /**
     * Update the status of a payout batch (e.g. when adapter confirms completion).
     *
     * @param batchId the payout batch ID
     * @param status  the new status
     * @return the updated batch entity
     */
    @Transactional
    public PayoutBatchEntity updatePayoutStatus(String batchId, PayoutStatus status) {
        PayoutBatchEntity batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Payout batch not found: " + batchId));

        batch.setStatus(status);
        batch = batchRepository.save(batch);

        log.info("Payout batch status updated: batchId={}, status={}", batchId, status);

        return batch;
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
