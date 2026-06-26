package zw.gov.mohcc.impilo.costa.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.costa.api.dto.CreateChargeRecordRequest;
import zw.gov.mohcc.impilo.costa.domain.entity.ChargeRecordEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.BillLineKind;
import zw.gov.mohcc.impilo.costa.domain.enums.CostMethodType;
import zw.gov.mohcc.impilo.costa.domain.repository.ChargeRecordRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.costa.engine.CostEngine;
import zw.gov.mohcc.impilo.costa.engine.CostEngineRegistry;
import zw.gov.mohcc.impilo.costa.engine.CostResult;
import zw.gov.mohcc.impilo.costa.rules.ChargingRuleEngine;
import zw.gov.mohcc.impilo.costa.rules.RuleContext;
import zw.gov.mohcc.impilo.costa.rules.RuleResult;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class ChargeRecordService {

    private static final Logger log = LoggerFactory.getLogger(ChargeRecordService.class);

    public static final String SOURCE_MSIKA_FLOW_ORDER_PRICED = "MSIKA_FLOW_ORDER_PRICED";
    public static final String SOURCE_TELECONSULT_COMPLETED = "TELECONSULT_COMPLETED";

    /** Tariff code used for teleconsult charges when no specialty-specific tariff is configured. */
    public static final String TELECONSULT_TARIFF_CODE = "TELECONSULT";

    private final ChargeRecordRepository chargeRecordRepository;
    private final EventOutboxRepository outboxRepository;
    private final CostEngineRegistry costEngineRegistry;
    private final ChargingRuleEngine chargingRuleEngine;
    private final ObjectMapper objectMapper;

    public ChargeRecordService(ChargeRecordRepository chargeRecordRepository,
                               EventOutboxRepository outboxRepository,
                               CostEngineRegistry costEngineRegistry,
                               ChargingRuleEngine chargingRuleEngine,
                               ObjectMapper objectMapper) {
        this.chargeRecordRepository = chargeRecordRepository;
        this.outboxRepository = outboxRepository;
        this.costEngineRegistry = costEngineRegistry;
        this.chargingRuleEngine = chargingRuleEngine;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ChargeRecordEntity createFromRequest(CreateChargeRecordRequest req) {
        var ctx = TrustContextHolder.require();
        ChargeRecordEntity e = new ChargeRecordEntity();
        e.setChargeId(UlidGenerator.generate());
        e.setTenantId(ctx.tenantId());
        e.setChargeCode(req.chargeCode());
        e.setChargeType(req.chargeType());
        e.setSourceType(req.sourceType());
        e.setSourceRef(req.sourceRef());
        e.setClientRef(req.clientRef());
        e.setPayerRef(req.payerRef());
        e.setProviderRef(req.providerRef());
        if (req.facilityId() != null && !req.facilityId().isBlank()) {
            try {
                e.setFacilityId(UUID.fromString(req.facilityId()));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Invalid facilityId UUID");
            }
        }
        e.setItemRef(req.itemRef());
        e.setOfferingRef(req.offeringRef());
        e.setServiceRef(req.serviceRef());
        e.setBillId(req.billId());
        e.setLineId(req.lineId());
        e.setChargeAmount(req.chargeAmount());
        e.setCurrency(req.currency() != null ? req.currency() : "USD");
        e.setBillableFlag(req.billableFlag() == null || req.billableFlag());
        e.setMetadataJson(req.metadataJson() != null ? req.metadataJson() : "{}");
        e = chargeRecordRepository.save(e);
        publishChargeCreated(e);
        return e;
    }

    /**
     * Idempotent charge row from Msika Flow {@code ORDER_PRICED} / Kafka {@code msika.flow.order.priced}.
     */
    @Transactional
    public void ingestMsikaFlowOrderPriced(JsonNode event) {
        String orderId = text(event, "orderId");
        String tenantStr = text(event, "tenantId");
        if (orderId == null || tenantStr == null) {
            log.warn("ORDER_PRICED event missing orderId or tenantId");
            return;
        }
        UUID tenantId = UUID.fromString(tenantStr);
        if (chargeRecordRepository.existsByTenantIdAndSourceTypeAndSourceRef(
                tenantId, SOURCE_MSIKA_FLOW_ORDER_PRICED, orderId)) {
            return;
        }
        BigDecimal amount = event.has("amountTotal")
                ? new BigDecimal(event.get("amountTotal").asText()) : null;
        if (amount == null) {
            return;
        }
        String currency = text(event, "currency");
        if (currency == null) {
            currency = "USD";
        }
        String patientCpid = text(event, "patientCpid");
        UUID facilityId = null;
        if (event.has("facilityId") && !event.get("facilityId").isNull()) {
            String fid = text(event, "facilityId");
            if (fid != null && !fid.isBlank()) {
                facilityId = UUID.fromString(fid);
            }
        }

        ChargeRecordEntity e = new ChargeRecordEntity();
        e.setChargeId(UlidGenerator.generate());
        e.setTenantId(tenantId);
        e.setChargeCode("MSIKA_FLOW:" + orderId);
        e.setChargeType("MARKETPLACE_ORDER");
        e.setSourceType(SOURCE_MSIKA_FLOW_ORDER_PRICED);
        e.setSourceRef(orderId);
        e.setClientRef(patientCpid);
        e.setPayerRef(patientCpid);
        e.setFacilityId(facilityId);
        e.setOfferingRef(orderId);
        e.setChargeAmount(amount);
        e.setCurrency(currency);
        e.setChargeStatus("OPEN");
        e.setBillableFlag(true);
        try {
            e.setMetadataJson(objectMapper.writeValueAsString(Map.of("kafkaEvent", "msika.flow.order.priced")));
        } catch (Exception ex) {
            e.setMetadataJson("{}");
        }
        chargeRecordRepository.save(e);
        publishChargeCreated(e);
        log.info("Recorded COSTA charge {} for Msika Flow priced order {}", e.getChargeId(), orderId);
    }

    /**
     * Idempotent teleconsult charge from PCT {@code TELECONSULT_COMPLETED} /
     * Kafka {@code clinical.teleconsult.value}. The L1 (clinical) value-trigger carries no
     * price, so COSTA — as the costing authority — fully prices it at ingest:
     * <ol>
     *   <li>resolve the applicable teleconsult tariff via the TARIFF cost engine
     *       (specialty-specific code first, then the generic {@link #TELECONSULT_TARIFF_CODE});</li>
     *   <li>run the resulting cost through the {@link ChargingRuleEngine} so exemptions, waivers,
     *       discounts and surcharges (e.g. emergency / admitted / patient-category rules) apply,
     *       exactly as the bill-line path does;</li>
     *   <li>record the final billable amount with the full tariff + charge traces and emit
     *       {@code CHARGE_CREATED}.</li>
     * </ol>
     * If a charging rule excludes the item the charge is recorded {@code EXCLUDED} and
     * non-billable (kept for audit, not dropped). If no teleconsult tariff is configured the
     * charge is recorded {@code OPEN} with amount 0 and {@code pendingPricing=true} so the gap
     * is visible.
     *
     * <p>Note: {@code patient_category}/{@code facility_category} are not carried on the value
     * trigger, so rules keyed on those dimensions need that context propagated upstream before
     * they can fire here — matching the bill-line path, which also depends on the caller
     * supplying those keys.</p>
     */
    @Transactional
    public ChargeRecordEntity ingestTeleconsultCompleted(JsonNode event) {
        String referralId = text(event, "referralId");
        String tenantStr = text(event, "tenantId");
        if (referralId == null || tenantStr == null) {
            log.warn("TELECONSULT_COMPLETED event missing referralId or tenantId");
            return null;
        }
        UUID tenantId = UUID.fromString(tenantStr);
        if (chargeRecordRepository.existsByTenantIdAndSourceTypeAndSourceRef(
                tenantId, SOURCE_TELECONSULT_COMPLETED, referralId)) {
            return null;
        }
        String patientCpid = text(event, "patientCpid");
        String encounterId = text(event, "encounterId");
        String specialty = text(event, "specialty");
        String modality = text(event, "modality");
        String facilityCategory = text(event, "facilityCategory");
        String patientCategory = text(event, "patientCategory");
        UUID facilityId = null;
        String fid = text(event, "facilityId");
        if (fid != null && !fid.isBlank()) {
            facilityId = UUID.fromString(fid);
        }

        // 1) Resolve the teleconsult tariff via the cost engine (tenant/facility are parameters,
        //    so this works off the request thread, e.g. inside the Kafka consumer).
        CostResult priced = resolveTeleconsultTariff(tenantId, facilityId, specialty);
        BigDecimal tariffTotal = priced.totalCost() != null ? priced.totalCost() : BigDecimal.ZERO;
        boolean pendingPricing = tariffTotal.signum() <= 0;

        // 2) Apply charging rules (exemptions / waivers / discounts / surcharges) to the tariff
        //    cost, mirroring BillService.postLine.
        RuleContext ruleCtx = new RuleContext(
                tenantId, facilityId, facilityCategory, patientCategory,
                priced.unitCostSource(), BillLineKind.SERVICE, BigDecimal.ONE,
                tariffTotal, "OUTPATIENT", LocalDateTime.now(),
                false, false, Map.of());
        RuleResult ruleResult = chargingRuleEngine.evaluate(ruleCtx);
        BigDecimal finalAmount = ruleResult.chargeAmount() != null ? ruleResult.chargeAmount() : BigDecimal.ZERO;

        ChargeRecordEntity e = new ChargeRecordEntity();
        e.setChargeId(UlidGenerator.generate());
        e.setTenantId(tenantId);
        e.setChargeCode("TELECONSULT:" + (specialty != null && !specialty.isBlank() ? specialty : referralId));
        e.setChargeType("TELECONSULT");
        e.setSourceType(SOURCE_TELECONSULT_COMPLETED);
        e.setSourceRef(referralId);
        e.setClientRef(patientCpid);
        e.setPayerRef(patientCpid);
        e.setFacilityId(facilityId);
        e.setServiceRef(encounterId);
        e.setOfferingRef(referralId);
        e.setChargeAmount(finalAmount);
        e.setCurrency("USD");
        e.setChargeStatus(ruleResult.excluded() ? "EXCLUDED" : "OPEN");
        e.setBillableFlag(!ruleResult.excluded());
        try {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("kafkaEvent", "clinical.teleconsult.value");
            meta.put("costMethodUsed", priced.methodUsed().name());
            meta.put("tariffSource", priced.unitCostSource());
            meta.put("unitPrice", priced.unitCost());
            meta.put("tariffTotal", tariffTotal);
            meta.put("pendingPricing", pendingPricing);
            meta.put("excluded", ruleResult.excluded());
            meta.put("appliedRules", ruleResult.appliedRules());
            if (specialty != null) meta.put("specialty", specialty);
            if (modality != null) meta.put("modality", modality);
            if (encounterId != null) meta.put("encounterId", encounterId);
            meta.put("tariffTrace", priced.trace());
            meta.put("chargeTrace", ruleResult.trace());
            e.setMetadataJson(objectMapper.writeValueAsString(meta));
        } catch (Exception ex) {
            e.setMetadataJson("{}");
        }
        chargeRecordRepository.save(e);
        publishChargeCreated(e);
        if (ruleResult.excluded()) {
            log.info("Recorded COSTA teleconsult charge {} for referral {} EXCLUDED by charging rules {}",
                    e.getChargeId(), referralId, ruleResult.appliedRules());
        } else if (pendingPricing) {
            log.warn("Recorded COSTA teleconsult charge {} for referral {} with NO tariff configured "
                    + "(specialty={}); amount=0, pendingPricing=true", e.getChargeId(), referralId, specialty);
        } else {
            log.info("Recorded COSTA teleconsult charge {} for referral {}: tariff {} -> charge {} (rules {}) via {}",
                    e.getChargeId(), referralId, tariffTotal, finalAmount,
                    ruleResult.appliedRules(), priced.unitCostSource());
        }
        return e;
    }

    /**
     * Resolve the applicable teleconsult tariff: try a specialty-specific code
     * ({@code TELECONSULT_<SPECIALTY>}) first, then the generic {@link #TELECONSULT_TARIFF_CODE}.
     */
    private CostResult resolveTeleconsultTariff(UUID tenantId, UUID facilityId, String specialty) {
        CostEngine engine = costEngineRegistry.resolveOrDefault(CostMethodType.TARIFF, CostMethodType.TARIFF);
        Map<String, Object> context = Map.of();
        if (specialty != null && !specialty.isBlank()) {
            String specialtyCode = TELECONSULT_TARIFF_CODE + "_"
                    + specialty.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
            CostResult specialtyResult = engine.compute(tenantId, facilityId, specialtyCode, BigDecimal.ONE, context);
            if (specialtyResult.totalCost() != null && specialtyResult.totalCost().signum() > 0) {
                return specialtyResult;
            }
        }
        return engine.compute(tenantId, facilityId, TELECONSULT_TARIFF_CODE, BigDecimal.ONE, context);
    }

    private void publishChargeCreated(ChargeRecordEntity e) {
        try {
            EventOutboxEntity ev = new EventOutboxEntity();
            ev.setAggregateType("CHARGE");
            ev.setAggregateId(e.getChargeId());
            ev.setEventType("CHARGE_CREATED");
            ev.setPayload(objectMapper.writeValueAsString(new LinkedHashMap<>(Map.of(
                    "chargeId", e.getChargeId(),
                    "sourceType", e.getSourceType(),
                    "sourceRef", e.getSourceRef(),
                    "chargeAmount", e.getChargeAmount(),
                    "currency", e.getCurrency()
            ))));
            ev.setTenantId(e.getTenantId());
            outboxRepository.save(ev);
        } catch (Exception ex) {
            log.error("Failed to publish CHARGE_CREATED: {}", ex.getMessage());
        }
    }

    private static String text(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }
}
