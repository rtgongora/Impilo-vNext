package zw.gov.mohcc.impilo.oros.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.oros.domain.ResultKind;
import zw.gov.mohcc.impilo.oros.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.ResultEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.OrderRepository;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Receives blood-bank fulfilment callbacks from the MADI service and closes the loop on the OROS
 * order spine (previously MADI's notifications had no sink). MADI is the sovereign blood-bank
 * fulfiller; OROS records the returned crossmatch/issue/transfusion outcomes as results and events
 * so they surface in the requester's inbox, the patient file, and reconciliation.
 *
 * <p>{@code orosOrderRef} is the OROS order id of the originating BLOOD_BANK order.</p>
 */
@Service
public class BloodOrderCallbackService {

    private static final Logger log = LoggerFactory.getLogger(BloodOrderCallbackService.class);

    private final OrderRepository orderRepository;
    private final ResultService resultService;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public BloodOrderCallbackService(OrderRepository orderRepository,
                                     ResultService resultService,
                                     EventOutboxRepository outboxRepository,
                                     ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.resultService = resultService;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /** MADI accepted the order for fulfilment; record the MADI order reference. */
    @Transactional
    public void submitted(String orosOrderRef, String madiOrderId) {
        OrderEntity order = load(orosOrderRef);
        order.setExternalRefs(mergeRef(order.getExternalRefs(), "madiOrderId", madiOrderId));
        orderRepository.save(order);
        emit(order, "BLOOD_ORDER_SUBMITTED", Map.of("madiOrderId", madiOrderId));
        log.info("Blood order acknowledged by MADI: orderId={}, madiOrderId={}", orosOrderRef, madiOrderId);
    }

    /**
     * Crossmatch compatibility result returned from MADI. Appended as a result; an
     * INCOMPATIBLE/INCONCLUSIVE outcome is flagged critical so the requester is alerted.
     */
    @Transactional
    public ResultEntity crossmatchResult(String orosOrderRef, String resultStatus, String notes) {
        load(orosOrderRef); // validate existence
        boolean critical = resultStatus != null && !"COMPATIBLE".equalsIgnoreCase(resultStatus);
        String summary = json(Map.of(
                "crossmatch", resultStatus != null ? resultStatus : "UNKNOWN",
                "notes", notes != null ? notes : ""));
        ResultEntity r = resultService.postResult(orosOrderRef, ResultKind.LAB, summary, null, null, critical);
        log.info("Blood crossmatch result recorded: orderId={}, status={}, critical={}",
                orosOrderRef, resultStatus, critical);
        return r;
    }

    /** A blood unit was issued for the order. */
    @Transactional
    public void issued(String orosOrderRef, String unitId) {
        OrderEntity order = load(orosOrderRef);
        emit(order, "BLOOD_ISSUED", Map.of("unitId", unitId != null ? unitId : ""));
        log.info("Blood issued for order: orderId={}, unitId={}", orosOrderRef, unitId);
    }

    /**
     * Transfusion outcome returned from MADI. Appended as a result; an adverse/stopped outcome is
     * flagged critical.
     */
    @Transactional
    public ResultEntity transfusionOutcome(String orosOrderRef, String outcome, String notes) {
        load(orosOrderRef);
        boolean adverse = outcome != null
                && (outcome.toUpperCase().contains("ADVERSE") || outcome.toUpperCase().contains("STOPPED"));
        String summary = json(Map.of(
                "transfusionOutcome", outcome != null ? outcome : "UNKNOWN",
                "notes", notes != null ? notes : ""));
        ResultEntity r = resultService.postResult(orosOrderRef, ResultKind.DOCUMENT, summary, null, null, adverse);
        log.info("Transfusion outcome recorded: orderId={}, outcome={}, adverse={}",
                orosOrderRef, outcome, adverse);
        return r;
    }

    private OrderEntity load(String orderId) {
        return orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
    }

    private String mergeRef(String existingJson, String key, String value) {
        try {
            Map<String, Object> refs = (existingJson != null && !existingJson.isBlank())
                    ? objectMapper.readValue(existingJson, Map.class) : new LinkedHashMap<>();
            refs.put(key, value);
            return objectMapper.writeValueAsString(refs);
        } catch (Exception e) {
            log.warn("Failed to merge external ref {}: {}", key, e.getMessage());
            return existingJson;
        }
    }

    private String json(Map<String, Object> m) {
        try {
            return objectMapper.writeValueAsString(m);
        } catch (Exception e) {
            return "{}";
        }
    }

    private void emit(OrderEntity order, String eventType, Map<String, Object> extra) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>(extra);
            payload.put("orderId", order.getOrderId());
            payload.put("patientCpid", order.getPatientCpid());
            EventOutboxEntity event = new EventOutboxEntity();
            event.setAggregateType("ORDER");
            event.setAggregateId(order.getOrderId());
            event.setEventType(eventType);
            event.setPayload(objectMapper.writeValueAsString(payload));
            event.setTenantId(order.getTenantId());
            outboxRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to write blood callback event {}: {}", eventType, e.getMessage(), e);
        }
    }
}
