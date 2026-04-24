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
