package zw.gov.mohcc.impilo.msikaflow.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.msikaflow.domain.*;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.*;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class SubstitutionService {

    private static final Logger log = LoggerFactory.getLogger(SubstitutionService.class);

    private final OrderLineRepository orderLineRepository;
    private final OrderStateMachine stateMachine;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public SubstitutionService(OrderLineRepository orderLineRepository,
                               OrderStateMachine stateMachine,
                               EventOutboxRepository outboxRepository,
                               ObjectMapper objectMapper) {
        this.orderLineRepository = orderLineRepository;
        this.stateMachine = stateMachine;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OrderLineEntity proposeSubstitution(String orderId, String lineId,
                                                String substituteCode, BigDecimal substitutePrice,
                                                String reason, String actorId) {
        OrderEntity order = stateMachine.getOrder(orderId);
        if (order.getOrderType() != OrderType.RX_FULFILLMENT_ORDER) {
            throw new IllegalArgumentException("Substitution only applies to Rx orders");
        }

        OrderLineEntity line = orderLineRepository.findById(lineId)
                .orElseThrow(() -> new IllegalArgumentException("Line not found: " + lineId));

        if (!line.getOrderId().equals(orderId)) {
            throw new IllegalArgumentException("Line does not belong to order");
        }

        // Store substitution proposal in metadata
        try {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("substitutionProposed", true);
            meta.put("originalCode", line.getMsikaCoreCode());
            meta.put("substituteCode", substituteCode);
            meta.put("substitutePrice", substitutePrice.toPlainString());
            meta.put("reason", reason);
            meta.put("proposedBy", actorId);
            meta.put("proposedAt", OffsetDateTime.now().toString());
            meta.put("approved", false);
            line.setMetadata(objectMapper.writeValueAsString(meta));
        } catch (Exception e) {
            log.warn("Failed to serialize substitution metadata: {}", e.getMessage());
        }

        orderLineRepository.save(line);
        log.info("Substitution proposed: orderId={} lineId={} substitute={}", orderId, lineId, substituteCode);
        return line;
    }

    @SuppressWarnings("unchecked")
    @Transactional
    public OrderLineEntity approveSubstitution(String orderId, String lineId, String actorId) {
        OrderLineEntity line = orderLineRepository.findById(lineId)
                .orElseThrow(() -> new IllegalArgumentException("Line not found: " + lineId));

        if (!line.getOrderId().equals(orderId)) {
            throw new IllegalArgumentException("Line does not belong to order");
        }

        try {
            if (line.getMetadata() != null) {
                Map<String, Object> meta = objectMapper.readValue(line.getMetadata(), Map.class);
                if (!Boolean.TRUE.equals(meta.get("substitutionProposed"))) {
                    throw new IllegalStateException("No substitution proposal on this line");
                }

                // Apply substitution
                String substituteCode = (String) meta.get("substituteCode");
                String substitutePrice = (String) meta.get("substitutePrice");

                line.setMsikaCoreCode(substituteCode);
                if (substitutePrice != null) {
                    line.setUnitPrice(new BigDecimal(substitutePrice));
                    line.setLineTotal(line.getUnitPrice().multiply(BigDecimal.valueOf(line.getQty())));
                }

                meta.put("approved", true);
                meta.put("approvedBy", actorId);
                meta.put("approvedAt", OffsetDateTime.now().toString());
                line.setMetadata(objectMapper.writeValueAsString(meta));
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to process substitution approval: " + e.getMessage(), e);
        }

        orderLineRepository.save(line);

        OrderEntity order = stateMachine.getOrder(orderId);
        publishOutbox("Substitution", lineId, "SUBSTITUTION_APPROVED", order.getTenantId(),
                Map.of("orderId", orderId, "lineId", lineId, "approvedBy", actorId));

        log.info("Substitution approved: orderId={} lineId={}", orderId, lineId);
        return line;
    }

    private void publishOutbox(String aggregateType, String aggregateId, String eventType,
                               UUID tenantId, Map<String, Object> data) {
        try {
            EventOutboxEntity outbox = new EventOutboxEntity();
            outbox.setAggregateType(aggregateType);
            outbox.setAggregateId(aggregateId);
            outbox.setEventType(eventType);
            outbox.setPayload(objectMapper.writeValueAsString(data));
            outbox.setTenantId(tenantId);
            outboxRepository.save(outbox);
        } catch (Exception e) {
            log.error("Failed to write outbox: {}", e.getMessage());
        }
    }
}
