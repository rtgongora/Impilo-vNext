package zw.gov.mohcc.impilo.msikaflow.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.msikaflow.domain.OrderStatus;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.OrderLineEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.OrderLineRepository;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.OrderRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class PricingService {

    private static final Logger log = LoggerFactory.getLogger(PricingService.class);

    private final OrderRepository orderRepository;
    private final OrderLineRepository orderLineRepository;
    private final OrderStateMachine stateMachine;
    private final ObjectMapper objectMapper;

    public PricingService(OrderRepository orderRepository,
                          OrderLineRepository orderLineRepository,
                          OrderStateMachine stateMachine,
                          ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.orderLineRepository = orderLineRepository;
        this.stateMachine = stateMachine;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PriceSnapshot priceOrder(String orderId, String actorId, String actorType) {
        OrderEntity order = stateMachine.getOrder(orderId);

        if (order.getStatus() != OrderStatus.VALIDATED) {
            throw new IllegalStateException("Order must be VALIDATED before pricing. Current: " + order.getStatus());
        }

        List<OrderLineEntity> lines = orderLineRepository.findByOrderId(orderId);
        if (lines.isEmpty()) {
            throw new IllegalStateException("Order has no lines to price");
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        List<Map<String, Object>> lineBreakdowns = new ArrayList<>();

        for (OrderLineEntity line : lines) {
            BigDecimal lineTotal = line.getUnitPrice().multiply(BigDecimal.valueOf(line.getQty()));
            line.setLineTotal(lineTotal);
            orderLineRepository.save(line);
            subtotal = subtotal.add(lineTotal);

            lineBreakdowns.add(Map.of(
                    "lineId", line.getLineId(),
                    "msikaCoreCode", line.getMsikaCoreCode(),
                    "qty", line.getQty(),
                    "unitPrice", line.getUnitPrice().toPlainString(),
                    "lineTotal", lineTotal.toPlainString()
            ));
        }

        // Platform fee: 1.5% capped at 50 ZWG
        BigDecimal platformFeeRate = new BigDecimal("0.015");
        BigDecimal platformFeeCap = new BigDecimal("50.00");
        BigDecimal platformFee = subtotal.multiply(platformFeeRate).min(platformFeeCap);

        BigDecimal deliveryFee = BigDecimal.ZERO; // can be set by routing
        BigDecimal total = subtotal.add(platformFee).add(deliveryFee);

        try {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("subtotal", subtotal.toPlainString());
            snapshot.put("platformFee", platformFee.toPlainString());
            snapshot.put("deliveryFee", deliveryFee.toPlainString());
            snapshot.put("total", total.toPlainString());
            snapshot.put("currency", order.getCurrency());
            snapshot.put("lines", lineBreakdowns);
            snapshot.put("pricedAt", OffsetDateTime.now().toString());

            String snapshotJson = objectMapper.writeValueAsString(snapshot);
            order.setPriceSnapshot(snapshotJson);
            order.setAmountTotal(total);
            orderRepository.save(order);

            stateMachine.transition(orderId, OrderStatus.PRICED, actorId, actorType, "ORDER_PRICED", null);

            return new PriceSnapshot(subtotal, platformFee, deliveryFee, total, order.getCurrency(), snapshotJson);

        } catch (Exception e) {
            throw new RuntimeException("Failed to create price snapshot: " + e.getMessage(), e);
        }
    }

    public record PriceSnapshot(BigDecimal subtotal, BigDecimal platformFee, BigDecimal deliveryFee,
                                BigDecimal total, String currency, String snapshotJson) {}
}
