package zw.gov.mohcc.impilo.costa.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.costa.service.BudgetActualService;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

/**
 * Ingests MusheX payment events as budget actual references — but ONLY when a
 * payment/disbursement is explicitly tagged to a budget line (carries
 * {@code budgetId} + {@code budgetLineId}). Generic patient payments are NOT
 * treated as budget expenditure — that would fabricate spend. Untagged events
 * are acknowledged and skipped. Idempotency is enforced downstream on
 * (tenant, source, sourceReference).
 *
 * Coverage of auto-ingestion therefore depends on upstream disbursements being
 * budget-tagged; the guaranteed path remains the actuals API/import. See the
 * deferred-seam register.
 */
@Service
public class BudgetActualConsumer {

    private static final Logger log = LoggerFactory.getLogger(BudgetActualConsumer.class);
    private static final Set<String> PAID_STATUSES = Set.of("PAID", "SETTLED", "COMPLETED", "SUCCEEDED");

    private final BudgetActualService actualService;
    private final ObjectMapper objectMapper;

    public BudgetActualConsumer(BudgetActualService actualService, ObjectMapper objectMapper) {
        this.actualService = actualService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "mushex.payment.status.changed", groupId = "costa-budget-actuals")
    @Transactional
    public void onPaymentStatusChanged(String message, Acknowledgment ack) {
        try {
            JsonNode event = objectMapper.readTree(message);

            String budgetId = text(event, "budgetId");
            String budgetLineId = text(event, "budgetLineId");
            if (budgetId == null || budgetLineId == null) {
                // not a budget-tagged disbursement — ignore (no fake expenditure)
                ack.acknowledge();
                return;
            }

            String status = firstNonNull(text(event, "toStatus"), text(event, "status"));
            if (status == null || !PAID_STATUSES.contains(status.toUpperCase())) {
                ack.acknowledge();
                return;
            }

            String tenantId = firstNonNull(text(event, "tenantId"), text(event, "tenant_id"));
            String reference = firstNonNull(text(event, "intentId"), text(event, "paymentIntentId"),
                    text(event, "paymentId"));
            BigDecimal amount = decimal(event, "amountPaid", "paidAmount", "amount");
            if (tenantId == null || reference == null || amount == null || amount.signum() <= 0) {
                log.warn("budget-tagged payment missing tenant/reference/amount: {}", message);
                ack.acknowledge();
                return;
            }

            actualService.post(UUID.fromString(tenantId), UUID.fromString(budgetId),
                    new BudgetActualService.NewActual(UUID.fromString(budgetLineId), "MUSHEX_PAYMENT",
                            reference, null, amount, "EVENT"));
            ack.acknowledge();
            log.info("Posted budget actual from MUSHEX payment {} to line {}", reference, budgetLineId);
        } catch (Exception e) {
            log.error("Failed to process budget-tagged mushex.payment.status.changed", e);
            ack.acknowledge();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static String firstNonNull(String... values) {
        for (String v : values) if (v != null) return v;
        return null;
    }

    private static BigDecimal decimal(JsonNode node, String... fields) {
        for (String f : fields) {
            JsonNode v = node.get(f);
            if (v != null && !v.isNull()) {
                try { return new BigDecimal(v.asText()); } catch (NumberFormatException ignored) { }
            }
        }
        return null;
    }
}
