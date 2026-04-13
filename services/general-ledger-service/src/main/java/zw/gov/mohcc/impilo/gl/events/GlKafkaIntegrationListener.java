package zw.gov.mohcc.impilo.gl.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.gl.core.JournalService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Consumes cross-plane financial events and posts balanced journal entries to the enterprise GL.
 */
@Component
@ConditionalOnProperty(prefix = "gl.kafka", name = "integration-listeners-enabled", havingValue = "true", matchIfMissing = true)
public class GlKafkaIntegrationListener {

    private static final Logger log = LoggerFactory.getLogger(GlKafkaIntegrationListener.class);

    private final JournalService journalService;
    private final ObjectMapper objectMapper;

    public GlKafkaIntegrationListener(JournalService journalService, ObjectMapper objectMapper) {
        this.journalService = journalService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = {
                    "costa.bill.finalized",
                    "mushex.payment.status.changed",
                    "payroll.payslip.generated",
                    "payroll.run.completed",
                    "procurement.invoice.matched",
                    "procurement.payment.completed",
                    "impilo.hrpayroll.payslip.generated.v1",
                    "impilo.procurement.invoice.matched.v1"
            },
            groupId = "general-ledger-integration")
    public void onFinancialEvent(String message, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            JsonNode root = objectMapper.readTree(message);
            JsonNode data = unwrap(root);
            UUID tenant = uuid(text(data, "tenantId", "tenant_id"));
            if (tenant == null) {
                tenant = uuid(text(root, "tenantId", "tenant_id"));
            }
            if (tenant == null) {
                log.warn("GL integration skip topic={}: missing tenant", topic);
                return;
            }
            LocalDate entryDate = parseDate(text(data, "entryDate", "entry_date", "billDate", "bill_date", "paidAt", "paid_at", "occurredAt", "occurred_at"));
            if (entryDate == null) {
                entryDate = LocalDate.now();
            }
            switch (topic) {
                case "costa.bill.finalized" -> onCostaBill(tenant, entryDate, data);
                case "mushex.payment.status.changed" -> onMushexPayment(tenant, entryDate, data);
                case "payroll.payslip.generated", "impilo.hrpayroll.payslip.generated.v1" -> onPayslip(tenant, entryDate, data);
                case "payroll.run.completed" -> log.debug("payroll.run.completed acknowledged for tenant {}", tenant);
                case "procurement.invoice.matched", "impilo.procurement.invoice.matched.v1" -> onProcurementInvoice(tenant, entryDate, data);
                case "procurement.payment.completed" -> onProcurementPayment(tenant, entryDate, data);
                default -> log.debug("Unhandled topic {}", topic);
            }
        } catch (Exception e) {
            log.error("GL integration failed topic={}: {}", topic, e.getMessage(), e);
        }
    }

    private void onCostaBill(UUID tenant, LocalDate date, JsonNode d) {
        BigDecimal amt = money(d, "totalAmount", "total_amount", "amount", "balance");
        if (amt == null || amt.signum() <= 0) {
            return;
        }
        String billId = text(d, "billId", "bill_id", "id");
        journalService.createPostedAutoEntry(tenant, date, "COSTA", "bill:" + billId,
                "COSTA bill finalized",
                List.of(
                        new JournalService.AutoLine("1200", amt, null, uuid(text(d, "facilityId", "facility_id"))),
                        new JournalService.AutoLine("4010", null, amt, null)
                ));
    }

    private void onMushexPayment(UUID tenant, LocalDate date, JsonNode d) {
        String status = text(d, "status", "paymentStatus", "payment_status");
        if (status != null && !status.equalsIgnoreCase("COMPLETED") && !status.equalsIgnoreCase("SETTLED")
                && !status.equalsIgnoreCase("SUCCESS")) {
            return;
        }
        BigDecimal amt = money(d, "amount", "paidAmount", "paid_amount");
        if (amt == null || amt.signum() <= 0) {
            return;
        }
        String payId = text(d, "paymentId", "payment_id", "id");
        journalService.createPostedAutoEntry(tenant, date, "MUSHEX", "payment:" + payId,
                "MUSHEX payment received",
                List.of(
                        new JournalService.AutoLine("1010", amt, null, null),
                        new JournalService.AutoLine("1200", null, amt, null)
                ));
    }

    private void onPayslip(UUID tenant, LocalDate date, JsonNode d) {
        BigDecimal gross = money(d, "grossPay", "gross_pay", "gross");
        BigDecimal net = money(d, "netPay", "net_pay", "net");
        BigDecimal tax = money(d, "tax", "paye");
        BigDecimal pension = money(d, "pension");
        if (net == null) {
            net = BigDecimal.ZERO;
        }
        if (tax == null) {
            tax = BigDecimal.ZERO;
        }
        if (pension == null) {
            pension = BigDecimal.ZERO;
        }
        BigDecimal deductions = tax.add(pension);
        BigDecimal expense = gross != null && gross.compareTo(BigDecimal.ZERO) > 0
                ? gross
                : net.add(deductions);
        String slipId = text(d, "payslipId", "payslip_id", "id");
        journalService.createPostedAutoEntry(tenant, date, "PAYROLL", "payslip:" + slipId,
                "Payroll payslip posted",
                List.of(
                        new JournalService.AutoLine("5010", expense, null, null),
                        new JournalService.AutoLine("1010", null, net, null),
                        new JournalService.AutoLine("2300", null, deductions, null)
                ));
    }

    private void onProcurementInvoice(UUID tenant, LocalDate date, JsonNode d) {
        BigDecimal amt = money(d, "totalAmount", "total_amount", "amount");
        if (amt == null || amt.signum() <= 0) {
            return;
        }
        String inv = text(d, "invoiceId", "invoice_id", "id");
        journalService.createPostedAutoEntry(tenant, date, "PROCUREMENT", "inv:" + inv,
                "Supplier invoice matched (GRN/PO)",
                List.of(
                        new JournalService.AutoLine("1300", amt, null, null),
                        new JournalService.AutoLine("2000", null, amt, null)
                ));
    }

    private void onProcurementPayment(UUID tenant, LocalDate date, JsonNode d) {
        BigDecimal amt = money(d, "amount", "paidAmount", "paid_amount");
        if (amt == null || amt.signum() <= 0) {
            return;
        }
        String ref = text(d, "paymentRef", "payment_ref", "id");
        journalService.createPostedAutoEntry(tenant, date, "PROCUREMENT", "pay:" + ref,
                "Supplier payment",
                List.of(
                        new JournalService.AutoLine("2000", amt, null, null),
                        new JournalService.AutoLine("1010", null, amt, null)
                ));
    }

    private static JsonNode unwrap(JsonNode root) {
        if (root.hasNonNull("payload") && root.get("payload").isObject()) {
            return root.get("payload");
        }
        if (root.hasNonNull("data") && root.get("data").isObject()) {
            return root.get("data");
        }
        return root;
    }

    private static String text(JsonNode n, String... names) {
        for (String name : names) {
            if (n != null && n.hasNonNull(name)) {
                String s = n.get(name).asText();
                if (!s.isBlank()) {
                    return s;
                }
            }
        }
        return null;
    }

    private static UUID uuid(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            if (s.length() >= 10) {
                return LocalDate.parse(s.substring(0, 10));
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static BigDecimal money(JsonNode n, String... names) {
        for (String name : names) {
            if (n != null && n.has(name) && !n.get(name).isNull()) {
                JsonNode v = n.get(name);
                if (v.isNumber()) {
                    return v.decimalValue();
                }
                try {
                    return new BigDecimal(v.asText().trim());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return null;
    }
}
