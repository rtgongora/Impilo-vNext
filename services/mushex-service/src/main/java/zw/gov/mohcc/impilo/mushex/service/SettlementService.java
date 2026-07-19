package zw.gov.mohcc.impilo.mushex.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import zw.gov.mohcc.impilo.mushex.domain.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.LedgerEntryEntity;
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
import zw.gov.mohcc.impilo.mushex.domain.repository.LedgerEntryRepository;
import zw.gov.mohcc.impilo.mushex.service.disbursement.DisbursementRail;
import zw.gov.mohcc.impilo.mushex.service.disbursement.DisbursementRailRegistry;
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
    private final LedgerEntryRepository ledgerEntryRepository;
    private final LedgerService ledgerService;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final DisbursementRailRegistry disbursementRails;
    private final zw.gov.mohcc.impilo.mushex.config.MushexProperties properties;
    private final zw.gov.mohcc.impilo.shared.biometric.BiometricVerificationClient biometricVerification;

    public SettlementService(SettlementRepository settlementRepository,
                             PayoutBatchRepository batchRepository,
                             PayoutItemRepository itemRepository,
                             PaymentIntentRepository intentRepository,
                             LedgerEntryRepository ledgerEntryRepository,
                             LedgerService ledgerService,
                             EventOutboxRepository outboxRepository,
                             ObjectMapper objectMapper,
                             DisbursementRailRegistry disbursementRails,
                             zw.gov.mohcc.impilo.mushex.config.MushexProperties properties,
                             zw.gov.mohcc.impilo.shared.biometric.BiometricVerificationClient biometricVerification) {
        this.settlementRepository = settlementRepository;
        this.batchRepository = batchRepository;
        this.itemRepository = itemRepository;
        this.intentRepository = intentRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.ledgerService = ledgerService;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.disbursementRails = disbursementRails;
        this.properties = properties;
        this.biometricVerification = biometricVerification;
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

        // Aggregate payouts by PAYEE, not by facility: marketplace intents carry
        // their payee in metadata (provider_id = "vendor:<sellerId>") and the
        // payee's SHARE in metadata payee_amount (vendor goods + delivery,
        // excluding the platform fee) — paying a vendor the gross amountPaid
        // would hand them the platform fee. Facility intents fall back to the
        // intent's facilityId and gross paid amount. Intents with no resolvable
        // payee are skipped LOUDLY, never attributed to a "null" payee.
        Map<PayeeType, Map<String, BigDecimal>> payeeTotals = new LinkedHashMap<>();
        int skippedNoPayee = 0;
        for (PaymentIntentEntity intent : paidIntents) {
            String providerId = readMetadataField(intent.getMetadata(), "provider_id");
            PayeeType payeeType;
            String payeeRef;
            if (providerId != null && providerId.startsWith("vendor:")) {
                payeeType = PayeeType.VENDOR;
                payeeRef = providerId.substring("vendor:".length());
            } else if (providerId != null && providerId.startsWith("facility:")) {
                payeeType = PayeeType.FACILITY;
                payeeRef = providerId.substring("facility:".length());
            } else if (intent.getFacilityId() != null) {
                payeeType = PayeeType.FACILITY;
                payeeRef = intent.getFacilityId().toString();
            } else {
                skippedNoPayee++;
                log.warn("Settlement {}: intent {} has no resolvable payee — excluded from payouts",
                        settlement.getSettlementId(), intent.getIntentId());
                continue;
            }
            BigDecimal payeeAmount = intent.getAmountPaid();
            String share = readMetadataField(intent.getMetadata(), "payee_amount");
            if (share != null) {
                try {
                    BigDecimal parsed = new BigDecimal(share);
                    // A payee share can never exceed what was actually paid.
                    if (parsed.signum() > 0 && parsed.compareTo(intent.getAmountPaid()) <= 0) {
                        payeeAmount = parsed;
                    }
                } catch (NumberFormatException ignored) {
                    log.warn("Unparseable payee_amount '{}' on intent {} — using gross amountPaid",
                            share, intent.getIntentId());
                }
            }
            payeeTotals.computeIfAbsent(payeeType, k -> new LinkedHashMap<>())
                    .merge(payeeRef, payeeAmount, BigDecimal::add);
        }

        // One batch per rail: VENDOR payees ride the internal WALLET rail (live);
        // FACILITY/other payees ride BANK_TRANSFER (external rail, pending until
        // a disbursement agreement exists — fail-closed, never fake-disbursed).
        for (Map.Entry<PayeeType, Map<String, BigDecimal>> group : payeeTotals.entrySet()) {
            PayoutBatchEntity batch = new PayoutBatchEntity();
            batch.setBatchId(UlidGenerator.generate());
            batch.setSettlementId(settlement.getSettlementId());
            batch.setAdapterType(group.getKey() == PayeeType.VENDOR
                    ? AdapterType.WALLET : AdapterType.BANK_TRANSFER);
            batch.setStatus(PayoutStatus.PENDING);
            batch = batchRepository.save(batch);
            for (Map.Entry<String, BigDecimal> entry : group.getValue().entrySet()) {
                PayoutItemEntity item = new PayoutItemEntity();
                item.setId(UlidGenerator.generate());
                item.setBatchId(batch.getBatchId());
                item.setPayeeType(group.getKey());
                item.setPayeeRef(entry.getKey());
                item.setAmount(entry.getValue());
                item.setCurrency("USD");
                item.setStatus(PayoutStatus.PENDING);
                itemRepository.save(item);
            }
        }
        if (skippedNoPayee > 0) {
            log.warn("Settlement {}: {} intent(s) had no resolvable payee", 
                    settlement.getSettlementId(), skippedNoPayee);
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
     * List settlements for the current tenant, optionally narrowed by a specific intent.
     * Intent filter semantics are additive and read-only: rows are included when the intent's
     * creation date falls inside the settlement period, and any direct settlement ledger links
     * found for the intent are appended if not already present.
     */
    public List<SettlementEntity> listSettlements(String intentId) {
        TrustContext ctx = TrustContextHolder.require();
        if (intentId == null || intentId.isBlank()) {
            Page<SettlementEntity> page = settlementRepository.findByTenantId(
                    ctx.tenantId(), PageRequest.of(0, 100));
            return page.getContent();
        }

        PaymentIntentEntity intent = intentRepository.findById(intentId)
                .orElseThrow(() -> new IntentNotFoundException(intentId));
        if (!ctx.tenantId().equals(intent.getTenantId())) {
            throw new IntentNotFoundException(intentId);
        }

        Page<SettlementEntity> inPeriod = settlementRepository
                .findByTenantIdAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(
                        ctx.tenantId(),
                        intent.getCreatedAt().toLocalDate(),
                        intent.getCreatedAt().toLocalDate(),
                        PageRequest.of(0, 100));
        List<SettlementEntity> rows = new java.util.ArrayList<>(inPeriod.getContent());

        List<LedgerEntryEntity> linkedEntries = ledgerEntryRepository.findByTenantIdAndIntentId(ctx.tenantId(), intentId);
        if (!linkedEntries.isEmpty()) {
            var byId = new java.util.LinkedHashMap<String, SettlementEntity>();
            for (SettlementEntity row : rows) {
                byId.put(row.getSettlementId(), row);
            }
            for (LedgerEntryEntity entry : linkedEntries) {
                if (!"SETTLEMENT".equalsIgnoreCase(entry.getReferenceType())) {
                    continue;
                }
                String settlementId = entry.getReferenceId();
                if (settlementId == null || settlementId.isBlank() || byId.containsKey(settlementId)) {
                    continue;
                }
                settlementRepository.findById(settlementId).ifPresent(settlement -> {
                    if (ctx.tenantId().equals(settlement.getTenantId())) {
                        byId.put(settlement.getSettlementId(), settlement);
                    }
                });
            }
            rows = new java.util.ArrayList<>(byId.values());
        }

        rows.sort(java.util.Comparator.comparing(
                SettlementEntity::getCreatedAt,
                java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));
        return rows;
    }

    public List<SettlementEntity> listSettlements(List<String> intentIds) {
        TrustContextHolder.require();
        if (intentIds == null || intentIds.isEmpty()) {
            return listSettlements((String) null);
        }
        var byId = new java.util.LinkedHashMap<String, SettlementEntity>();
        for (String id : intentIds) {
            if (id == null || id.isBlank()) {
                continue;
            }
            for (SettlementEntity row : listSettlements(id)) {
                byId.putIfAbsent(row.getSettlementId(), row);
            }
        }
        List<SettlementEntity> rows = new java.util.ArrayList<>(byId.values());
        rows.sort(java.util.Comparator.comparing(
                SettlementEntity::getCreatedAt,
                java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));
        return rows;
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
        return releasePayouts(settlementId, null, null, null);
    }

    /**
     * Release payouts with an OPTIONAL biometric step-up for high-value releases.
     *
     * <p>When the release total meets/exceeds {@code mushex.step-up.settlement-release-threshold}
     * AND a biometric probe is supplied, the release is verified through the shared seam:
     * MATCH → the release proceeds, marked {@code releaseStepUpMode="biometric"}; NO_MATCH →
     * the release is blocked (nothing disbursed); engine UNAVAILABLE / NO_REFERENCE → fall back
     * to the existing non-biometric step-up factor (the release is NOT auto-approved by the
     * biometric layer — it proceeds only on whatever approval the caller already carries, exactly
     * as today). Below threshold or absent probe ⇒ unchanged behaviour.
     *
     * @param biometricSubjectRef   subject reference for the approver's biometric (optional)
     * @param biometricModality     "FINGERPRINT"|"FACE"|"IRIS" (defaults to FINGERPRINT)
     * @param biometricProbeBase64  the live probe template (optional)
     */
    @Transactional
    public SettlementEntity releasePayouts(String settlementId, String biometricSubjectRef,
                                           String biometricModality, String biometricProbeBase64) {
        TrustContextHolder.require();
        SettlementEntity settlement = getSettlement(settlementId);

        if (settlement.getStatus() != SettlementStatus.COMPUTED) {
            throw new IllegalStateException(
                    "Cannot release settlement in status: " + settlement.getStatus());
        }

        // High-value biometric step-up: verified BEFORE any state mutation so a NO_MATCH
        // blocks the release with nothing disbursed. Below-threshold or no-probe releases
        // are untouched; an engine outage falls back to the existing step-up factor.
        String releaseStepUpMode = resolveReleaseStepUp(
                settlement, biometricSubjectRef, biometricModality, biometricProbeBase64);
        settlement.setReleaseStepUpMode(releaseStepUpMode);

        // Step-up authorization would be enforced at the controller/filter level
        log.info("Releasing settlement payouts: id={} (step-up required, mode={})",
                settlementId, releaseStepUpMode);

        settlement.setStatus(SettlementStatus.RELEASED);
        settlement = settlementRepository.save(settlement);

        // Release = authorization to disburse. Each batch is handed to its rail;
        // a batch whose rail is not registered stays PENDING (fail-closed) — the
        // record must never claim money moved when no rail could move it.
        List<PayoutBatchEntity> batches = batchRepository.findBySettlementId(settlementId);
        for (PayoutBatchEntity batch : batches) {
            List<PayoutItemEntity> items = itemRepository.findByBatchId(batch.getBatchId());
            DisbursementRail rail = disbursementRails.railFor(batch.getAdapterType()).orElse(null);
            if (rail == null) {
                log.warn("Payout batch {} rail {} has no registered disbursement rail — "
                        + "batch stays PENDING awaiting the external rail", 
                        batch.getBatchId(), batch.getAdapterType());
                publishEvent("SETTLEMENT", settlementId, "SETTLEMENT_PAYOUT_AWAITING_RAIL",
                        Map.of("settlementId", settlementId, "batchId", batch.getBatchId(),
                                "rail", batch.getAdapterType().name(), "items", items.size()),
                        settlement.getTenantId());
                continue;
            }

            batch.setStatus(PayoutStatus.PROCESSING);
            batch.setReleasedAt(OffsetDateTime.now());
            batchRepository.save(batch);

            DisbursementRail.RailOutcome outcome =
                    rail.disburse(settlement.getTenantId(), batch.getBatchId(), items);

            BigDecimal disbursedTotal = BigDecimal.ZERO;
            Map<String, DisbursementRail.ItemOutcome> byItem = new LinkedHashMap<>();
            for (DisbursementRail.ItemOutcome io : outcome.items()) {
                byItem.put(io.itemId(), io);
            }
            for (PayoutItemEntity item : items) {
                DisbursementRail.ItemOutcome io = byItem.get(item.getId());
                boolean credited = io != null && io.credited();
                item.setStatus(credited ? PayoutStatus.COMPLETED : PayoutStatus.FAILED);
                itemRepository.save(item);
                if (credited) {
                    disbursedTotal = disbursedTotal.add(item.getAmount());
                }
            }
            batch.setStatus(outcome.complete() ? PayoutStatus.COMPLETED : PayoutStatus.PARTIAL);
            batchRepository.save(batch);

            // Ledger reflects what was actually disbursed, never the intended total.
            if (disbursedTotal.signum() > 0) {
                ledgerService.postSettlement(settlement.getTenantId(), settlementId,
                        disbursedTotal, "USD");
            }

            publishEvent("SETTLEMENT", settlementId,
                    outcome.complete() ? "SETTLEMENT_PAYOUT_COMPLETED" : "SETTLEMENT_PAYOUT_PARTIAL",
                    Map.of("settlementId", settlementId, "batchId", batch.getBatchId(),
                            "rail", batch.getAdapterType().name(),
                            "credited", outcome.creditedCount(), "items", items.size(),
                            "disbursedTotal", disbursedTotal.toPlainString()),
                    settlement.getTenantId());
            if (!outcome.complete()) {
                log.error("PAYOUT PARTIAL: batch {} credited {}/{} — failed items need ops attention",
                        batch.getBatchId(), outcome.creditedCount(), items.size());
            }
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

    /**
     * Resolve the release step-up mode for a high-value settlement release.
     *
     * @return {@code "biometric"} when a MATCH stepped the release up; otherwise {@code null}
     *         (below threshold, no probe, or engine outage → fall back to existing step-up).
     * @throws IllegalStateException on NO_MATCH — the high-value release is blocked.
     */
    private String resolveReleaseStepUp(SettlementEntity settlement, String subjectRef,
                                        String modality, String probeBase64) {
        boolean probeSupplied = probeBase64 != null && !probeBase64.isBlank()
                && subjectRef != null && !subjectRef.isBlank();
        if (!probeSupplied) {
            return null; // no biometric supplied — unchanged behaviour
        }
        BigDecimal releaseAmount = readReleaseAmount(settlement.getTotals());
        BigDecimal threshold = properties.getStepUp().getSettlementReleaseThreshold();
        if (threshold == null || releaseAmount.compareTo(threshold) < 0) {
            return null; // below the high-value threshold — biometric step-up not required
        }
        String mod = (modality != null && !modality.isBlank()) ? modality : "FINGERPRINT";
        var decision = biometricVerification.verify(subjectRef, mod, probeBase64);
        if (decision.isMatch()) {
            return "biometric";
        }
        if ("UNAVAILABLE".equals(decision.result()) || "NO_REFERENCE".equals(decision.result())) {
            // Fail-closed against fabrication, care-first against outage: fall back to the
            // existing non-biometric step-up factor. Never auto-approve on the biometric layer.
            log.warn("Settlement {} biometric step-up unavailable ({}) — falling back to non-biometric step-up",
                    settlement.getSettlementId(), decision.result());
            return null;
        }
        throw new IllegalStateException(
                "Biometric step-up failed for high-value settlement release " + settlement.getSettlementId());
    }

    /** Read the release amount (totalPaid) from the settlement totals JSON; ZERO when absent. */
    private BigDecimal readReleaseAmount(String totalsJson) {
        String paid = readMetadataField(totalsJson, "totalPaid");
        if (paid == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(paid);
        } catch (NumberFormatException e) {
            log.warn("Unparseable settlement totalPaid '{}' — treating as ZERO for step-up threshold", paid);
            return BigDecimal.ZERO;
        }
    }

    /** Read a single string field from intent metadata JSON; null when absent/unparseable. */
    private String readMetadataField(String metadataJson, String field) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(metadataJson);
            com.fasterxml.jackson.databind.JsonNode v = node.get(field);
            return v != null && !v.isNull() && !v.asText().isBlank() ? v.asText() : null;
        } catch (Exception e) {
            return null;
        }
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
