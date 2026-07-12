package zw.gov.mohcc.impilo.msikaflow.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.SettlementEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.OrderRepository;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.SettlementRepository;

import java.util.Optional;

/**
 * Thin consumer of {@code costa.charge.created}: persists the COSTA charge reference
 * (money-truth linkage) for MARKETPLACE_ORDER charges.
 *
 * <p>Ordering note: COSTA creates the charge when the order is PRICED, but the
 * settlement row only exists once the buyer pays. When the settlement is not there
 * yet the charge ref is stashed on the order's price snapshot
 * ({@code costaChargeRef}); {@code PaymentService.createPaymentIntent} copies it onto
 * {@code mf_settlements.costa_charge_ref} at settlement creation. Late charge events
 * (settlement already present) update the settlement directly.</p>
 */
@Service
public class CostaChargeEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(CostaChargeEventConsumer.class);

    /** Must match ChargeRecordService.SOURCE_MSIKA_FLOW_ORDER_PRICED in costing-engine. */
    static final String SOURCE_MSIKA_FLOW_ORDER_PRICED = "MSIKA_FLOW_ORDER_PRICED";

    private final SettlementRepository settlementRepository;
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;

    public CostaChargeEventConsumer(SettlementRepository settlementRepository,
                                    OrderRepository orderRepository,
                                    ObjectMapper objectMapper) {
        this.settlementRepository = settlementRepository;
        this.orderRepository = orderRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "costa.charge.created", groupId = "msika-flow-service")
    @Transactional
    public void onChargeCreated(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            JsonNode node = unwrapPayload(root);

            String sourceType = text(node, "sourceType");
            if (!SOURCE_MSIKA_FLOW_ORDER_PRICED.equals(sourceType)) {
                return; // not a marketplace charge
            }
            String orderId = text(node, "sourceRef");
            String chargeId = text(node, "chargeId");
            if (orderId == null || chargeId == null) {
                log.warn("costa.charge.created missing sourceRef/chargeId");
                return;
            }

            Optional<SettlementEntity> settlement = settlementRepository.findByOrderId(orderId);
            if (settlement.isPresent()) {
                SettlementEntity s = settlement.get();
                if (chargeId.equals(s.getCostaChargeRef())) {
                    return; // idempotent redelivery
                }
                s.setCostaChargeRef(chargeId);
                settlementRepository.save(s);
                log.info("Linked COSTA charge {} to settlement for order {}", chargeId, orderId);
                return;
            }

            // Settlement not created yet (normal ordering: charge fires at PRICED,
            // settlement at pay time) — stash on the order snapshot for pickup.
            Optional<OrderEntity> order = orderRepository.findById(orderId);
            if (order.isEmpty()) {
                log.debug("costa.charge.created for unknown order {}; skipped", orderId);
                return;
            }
            OrderEntity o = order.get();
            ObjectNode snap;
            if (o.getPriceSnapshot() != null && !o.getPriceSnapshot().isBlank()) {
                JsonNode parsed = objectMapper.readTree(o.getPriceSnapshot());
                snap = parsed.isObject() ? (ObjectNode) parsed : objectMapper.createObjectNode();
            } else {
                snap = objectMapper.createObjectNode();
            }
            snap.put("costaChargeRef", chargeId);
            o.setPriceSnapshot(objectMapper.writeValueAsString(snap));
            orderRepository.save(o);
            log.info("Stashed COSTA charge {} on order {} pending settlement", chargeId, orderId);
        } catch (Exception e) {
            log.error("Failed to process costa.charge.created: {}", e.getMessage(), e);
        }
    }

    private static JsonNode unwrapPayload(JsonNode root) {
        if (root.has("payload") && root.get("payload").isObject()) {
            return root.get("payload");
        }
        if (root.has("data") && root.get("data").isObject()) {
            return root.get("data");
        }
        return root;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        String v = node.get(field).asText();
        return v != null && !v.isBlank() ? v : null;
    }
}
