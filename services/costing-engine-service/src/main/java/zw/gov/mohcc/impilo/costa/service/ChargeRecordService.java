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
import zw.gov.mohcc.impilo.costa.domain.enums.CostMethodType;
import zw.gov.mohcc.impilo.costa.domain.repository.ChargeRecordRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.costa.engine.CostEngine;
import zw.gov.mohcc.impilo.costa.engine.CostEngineRegistry;
import zw.gov.mohcc.impilo.costa.engine.CostResult;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
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
    private final ObjectMapper objectMapper;

    public ChargeRecordService(ChargeRecordRepository chargeRecordRepository,
                               EventOutboxRepository outboxRepository,
                               CostEngineRegistry costEngineRegistry,
                               ObjectMapper objectMapper) {
        this.chargeRecordRepository = chargeRecordRepository;
        this.outboxRepository = outboxRepository;
        this.costEngineRegistry = costEngineRegistry;
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
     * price, so COSTA — as the costing authority — resolves the applicable teleconsult tariff
     * at ingest via the TARIFF cost engine (specialty-specific code first, then the generic
     * {@link #TELECONSULT_TARIFF_CODE}), records the resulting amount and full tariff trace,
     * and emits {@code CHARGE_CREATED}. If no teleconsult tariff is configured for the tenant,
     * the charge is recorded {@code OPEN} with a zero amount and {@code pendingPricing=true} so
     * the gap is visible rather than silently dropped.
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
        UUID facilityId = null;
        String fid = text(event, "facilityId");
        if (fid != null && !fid.isBlank()) {
            facilityId = UUID.fromString(fid);
        }

        // Resolve the teleconsult tariff via the cost engine (tenant/facility are parameters,
        // so this works off the request thread, e.g. inside the Kafka consumer).
        CostResult priced = resolveTeleconsultTariff(tenantId, facilityId, specialty);
        BigDecimal amount = priced.totalCost() != null ? priced.totalCost() : BigDecimal.ZERO;
        boolean pendingPricing = amount.signum() <= 0;

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
        e.setChargeAmount(amount);
        e.setCurrency("USD");
        e.setChargeStatus("OPEN");
        e.setBillableFlag(true);
        try {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("kafkaEvent", "clinical.teleconsult.value");
            meta.put("costMethodUsed", priced.methodUsed().name());
            meta.put("tariffSource", priced.unitCostSource());
            meta.put("unitPrice", priced.unitCost());
            meta.put("pendingPricing", pendingPricing);
            if (specialty != null) meta.put("specialty", specialty);
            if (modality != null) meta.put("modality", modality);
            if (encounterId != null) meta.put("encounterId", encounterId);
            meta.put("tariffTrace", priced.trace());
            e.setMetadataJson(objectMapper.writeValueAsString(meta));
        } catch (Exception ex) {
            e.setMetadataJson("{}");
        }
        chargeRecordRepository.save(e);
        publishChargeCreated(e);
        if (pendingPricing) {
            log.warn("Recorded COSTA teleconsult charge {} for referral {} with NO tariff configured "
                    + "(specialty={}); amount=0, pendingPricing=true", e.getChargeId(), referralId, specialty);
        } else {
            log.info("Recorded COSTA teleconsult charge {} for referral {}: {} via {}",
                    e.getChargeId(), referralId, amount, priced.unitCostSource());
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
