package zw.gov.mohcc.impilo.procurement.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.procurement.persistence.entity.*;
import zw.gov.mohcc.impilo.procurement.persistence.repository.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SupplierInvoiceService {
    private static final BigDecimal TOLERANCE = new BigDecimal("0.05");

    private final SupplierInvoiceRepository invoiceRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PoLineRepository poLineRepository;
    private final GoodsReceivedRepository goodsReceivedRepository;
    private final GrnLineRepository grnLineRepository;
    private final ProcOutboxWriter outboxWriter;

    public SupplierInvoiceService(SupplierInvoiceRepository invoiceRepository,
                                  PurchaseOrderRepository purchaseOrderRepository,
                                  PoLineRepository poLineRepository,
                                  GoodsReceivedRepository goodsReceivedRepository,
                                  GrnLineRepository grnLineRepository,
                                  ProcOutboxWriter outboxWriter) {
        this.invoiceRepository = invoiceRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.poLineRepository = poLineRepository;
        this.goodsReceivedRepository = goodsReceivedRepository;
        this.grnLineRepository = grnLineRepository;
        this.outboxWriter = outboxWriter;
    }

    /**
     * Three-way match: PO line totals vs GRN received quantities valued at PO unit prices vs invoice total.
     */
    @Transactional
    public SupplierInvoiceEntity attemptMatch(UUID tenantId, UUID invoiceId) throws Exception {
        SupplierInvoiceEntity inv = invoiceRepository.findById(invoiceId)
                .filter(i -> i.getTenantId().equals(tenantId))
                .orElseThrow();
        if (inv.getPoId() == null || inv.getGrnId() == null) {
            throw new IllegalStateException("Invoice must reference PO and GRN for matching");
        }
        PurchaseOrderEntity po = purchaseOrderRepository.findById(inv.getPoId()).orElseThrow();
        List<PoLineEntity> poLines = poLineRepository.findByPoId(po.getPoId());
        BigDecimal poTotal = poLines.stream()
                .map(l -> l.getTotalPrice() != null ? l.getTotalPrice() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        GoodsReceivedEntity grn = goodsReceivedRepository.findById(inv.getGrnId()).orElseThrow();
        List<GrnLineEntity> grnLines = grnLineRepository.findByGrnId(grn.getGrnId());
        BigDecimal grnValued = BigDecimal.ZERO;
        for (GrnLineEntity gl : grnLines) {
            PoLineEntity pl = poLineRepository.findById(gl.getPoLineId()).orElseThrow();
            BigDecimal qty = gl.getQuantityReceived() != null ? gl.getQuantityReceived() : BigDecimal.ZERO;
            BigDecimal unit = pl.getUnitPrice() != null ? pl.getUnitPrice() : BigDecimal.ZERO;
            grnValued = grnValued.add(qty.multiply(unit)).setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal invTotal = inv.getTotalAmount() != null ? inv.getTotalAmount() : BigDecimal.ZERO;
        boolean amountOk = poTotal.subtract(invTotal).abs().compareTo(TOLERANCE) <= 0
                || grnValued.subtract(invTotal).abs().compareTo(TOLERANCE) <= 0;
        boolean qtyOk = true;
        for (GrnLineEntity gl : grnLines) {
            PoLineEntity pl = poLineRepository.findById(gl.getPoLineId()).orElseThrow();
            BigDecimal ordered = pl.getQuantity() != null ? pl.getQuantity() : BigDecimal.ZERO;
            BigDecimal received = gl.getQuantityReceived() != null ? gl.getQuantityReceived() : BigDecimal.ZERO;
            if (received.compareTo(ordered) > 0) {
                qtyOk = false;
                break;
            }
        }
        if (amountOk && qtyOk) {
            inv.setThreeWayMatch(true);
            inv.setStatus("MATCHED");
            inv.setMatchedAt(OffsetDateTime.now());
            invoiceRepository.save(inv);
            outboxWriter.publish(tenantId, "SUPPLIER_INVOICE", inv.getInvoiceId().toString(), "supplier_invoice", "matched",
                    "proc:inv:matched:" + inv.getInvoiceId(),
                    Map.of("tenantId", tenantId.toString(), "invoiceId", inv.getInvoiceId().toString(),
                            "totalAmount", invTotal, "poId", po.getPoId().toString(), "grnId", grn.getGrnId().toString()));
        }
        return inv;
    }

    /**
     * Marks an invoice paid and emits {@code procurement.payment.completed} for GL integration.
     */
    @Transactional
    public SupplierInvoiceEntity markPaid(UUID tenantId, UUID invoiceId) throws Exception {
        SupplierInvoiceEntity inv = invoiceRepository.findById(invoiceId)
                .filter(i -> i.getTenantId().equals(tenantId))
                .orElseThrow();
        inv.setStatus("PAID");
        inv.setPaidAt(OffsetDateTime.now());
        invoiceRepository.save(inv);
        BigDecimal amt = inv.getTotalAmount() != null ? inv.getTotalAmount() : BigDecimal.ZERO;
        outboxWriter.publish(tenantId, "SUPPLIER_PAYMENT", inv.getInvoiceId().toString(), "supplier_payment", "completed",
                "proc:pay:" + inv.getInvoiceId(),
                Map.of(
                        "tenantId", tenantId.toString(),
                        "invoiceId", inv.getInvoiceId().toString(),
                        "amount", amt,
                        "paidAt", inv.getPaidAt().toString(),
                        "id", inv.getInvoiceId().toString()));
        return inv;
    }
}
