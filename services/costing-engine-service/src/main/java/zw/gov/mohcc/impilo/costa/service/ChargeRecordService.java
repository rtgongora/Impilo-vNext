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
import zw.gov.mohcc.impilo.costa.domain.repository.ChargeRecordRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ChargeRecordService {

    private static final Logger log = LoggerFactory.getLogger(ChargeRecordService.class);

    public static final String SOURCE_MSIKA_FLOW_ORDER_PRICED = "MSIKA_FLOW_ORDER_PRICED";
    public static final String SOURCE_TELECONSULT_COMPLETED = "TELECONSULT_COMPLETED";

    private final ChargeRecordRepository chargeRecordRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public ChargeRecordService(ChargeRecordRepository chargeRecordRepository,
                               EventOutboxRepository outboxRepository,
                               ObjectMapper objectMapper) {
        this.chargeRecordRepository = chargeRecordRepository;
        this.outboxRepository = outboxRepository;
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
     * Idempotent teleconsult charge from PCT's {@code telemedicine.session.completed} event
     * (topic {@code clinical.teleconsult.lifecycle}). Telemedicine is governed like in-person
     * care: a completed teleconsult is a billable service event that must map to exactly one
     * value event (journey §9). PCT owns the encounter/referral; COSTA owns the charge — this
     * consumes the service event, it does not re-implement PCT.
     *
     * <p><b>Double-charge guard (C1).</b> PCT emits {@code telemedicine.session.completed} for
     * the SAME teleconsult from two sources: the referral-complete event (payload {@code id} =
     * referralId) and the session-end event (payload {@code id} = sessionId, plus a separate
     * {@code referralId} = referralId). To bill a teleconsult exactly once regardless of which
     * event(s) arrive and in what order, the charge is anchored on a STABLE SHARED key — the
     * referralId — resolved as: explicit {@code referralId} field → {@code id} (which IS the
     * referralId on the referral-complete event) → {@code encounterId} as a last resort. This
     * anchor is used as both the idempotency (dedup) key and the {@code sourceRef}, so both
     * lifecycle events collapse to one {@code TELECONSULT:*} charge + one {@code CHARGE_CREATED}.
     *
     * @param payload the inner referral payload (envelope already unwrapped by the consumer)
     * @param tenantId resolved tenant from the envelope
     */
    @Transactional
    public void ingestTeleconsultCompleted(JsonNode payload, UUID tenantId) {
        // Stable shared anchor across BOTH lifecycle events (C1). The session-end event carries
        // an explicit referralId; the referral-complete event's id IS the referralId. Prefer the
        // explicit referralId field, then id, then encounterId as a last resort — this is the one
        // key both events agree on, so the teleconsult is charged exactly once.
        String referralId = text(payload, "referralId");
        if (referralId == null) {
            referralId = text(payload, "id");
        }
        if (referralId == null) {
            referralId = text(payload, "encounterId");
        }
        if (referralId == null || tenantId == null) {
            log.warn("teleconsult.completed event missing referral anchor (referralId/id/encounterId) or tenantId");
            return;
        }
        String status = text(payload, "status");
        if (status != null && !"COMPLETED".equalsIgnoreCase(status)) {
            // Only the completed lifecycle stage is billable.
            return;
        }
        if (chargeRecordRepository.existsByTenantIdAndSourceTypeAndSourceRef(
                tenantId, SOURCE_TELECONSULT_COMPLETED, referralId)) {
            return;  // idempotent
        }

        String patientCpid = text(payload, "patientCpid");
        String providerId = text(payload, "providerId");
        String encounterId = text(payload, "encounterId");
        String specialty = text(payload, "specialty");
        String modality = text(payload, "modality");
        if (modality == null) {
            modality = text(payload, "virtualMode");
        }
        UUID facilityId = parseUuidOrNull(text(payload, "facilityId"));

        ChargeRecordEntity e = new ChargeRecordEntity();
        e.setChargeId(UlidGenerator.generate());
        e.setTenantId(tenantId);
        e.setChargeCode("TELECONSULT:" + (specialty != null ? specialty : "GENERAL"));
        e.setChargeType("TELECONSULT");
        e.setSourceType(SOURCE_TELECONSULT_COMPLETED);
        e.setSourceRef(referralId);
        e.setClientRef(patientCpid);
        e.setPayerRef(patientCpid);
        e.setProviderRef(providerId);
        e.setFacilityId(facilityId);
        e.setServiceRef(encounterId);
        e.setOfferingRef(referralId);
        // Tariff-driven pricing is resolved downstream by charging rules / bill posting;
        // the charge row is the billable signal. Amount left to ruleset (no fabricated price).
        e.setChargeAmount(BigDecimal.ZERO);
        e.setCurrency("USD");
        e.setChargeStatus("OPEN");
        e.setBillableFlag(true);
        try {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("kafkaEvent", "telemedicine.session.completed");
            meta.put("specialty", specialty);
            meta.put("modality", modality);
            meta.put("encounterId", encounterId);
            e.setMetadataJson(objectMapper.writeValueAsString(meta));
        } catch (Exception ex) {
            e.setMetadataJson("{}");
        }
        chargeRecordRepository.save(e);
        publishChargeCreated(e);
        log.info("Recorded COSTA teleconsult charge {} for completed referral {} (specialty={})",
                e.getChargeId(), referralId, specialty);
    }

    private static UUID parseUuidOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * Enqueue the CHARGE_CREATED value-event in the SAME transaction as the charge (M3).
     * An outbox-save failure must roll back the charge so we never commit a billable charge
     * with no value event (silent value leak). Serialization failures are mapped to a runtime
     * exception so the transaction rolls back rather than being swallowed.
     */
    private void publishChargeCreated(ChargeRecordEntity e) {
        EventOutboxEntity ev = new EventOutboxEntity();
        ev.setAggregateType("CHARGE");
        ev.setAggregateId(e.getChargeId());
        ev.setEventType("CHARGE_CREATED");
        try {
            ev.setPayload(objectMapper.writeValueAsString(new LinkedHashMap<>(Map.of(
                    "chargeId", e.getChargeId(),
                    "sourceType", e.getSourceType(),
                    "sourceRef", e.getSourceRef(),
                    "chargeAmount", e.getChargeAmount(),
                    "currency", e.getCurrency()
            ))));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize CHARGE_CREATED value-event", ex);
        }
        ev.setTenantId(e.getTenantId());
        outboxRepository.save(ev);
    }

    private static String text(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }
}
