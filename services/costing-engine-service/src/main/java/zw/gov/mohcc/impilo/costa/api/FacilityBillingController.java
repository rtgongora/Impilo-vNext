package zw.gov.mohcc.impilo.costa.api;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.costa.domain.entity.BillHeaderEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.InvoiceEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.PaymentEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.BillStatus;
import zw.gov.mohcc.impilo.costa.domain.repository.BillHeaderRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.InvoiceRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.PaymentRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Facility-scoped billing read views for the ops console. COSTA owns the billing
 * truth; these are facility-scoped queries over the existing bill/invoice/payment
 * tables (invoices/payments carry no facilityId, so they join through the bill).
 */
@RestController
@RequestMapping("/internal/v1/finance/facility")
public class FacilityBillingController {

    /** Bills not yet finalised/voided count as "unbilled charges". */
    private static final List<BillStatus> UNBILLED = List.of(
            BillStatus.DRAFT, BillStatus.ACCUMULATING, BillStatus.APPROVAL_PENDING, BillStatus.APPROVED);

    private final BillHeaderRepository bills;
    private final InvoiceRepository invoices;
    private final PaymentRepository payments;

    public FacilityBillingController(BillHeaderRepository bills, InvoiceRepository invoices,
                                     PaymentRepository payments) {
        this.bills = bills;
        this.invoices = invoices;
        this.payments = payments;
    }

    private static int clampLimit(int limit) {
        return Math.min(Math.max(limit, 1), 100);
    }

    @GetMapping("/unbilled-charges")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> unbilledCharges(
            @RequestParam(name = "facility_id") UUID facilityId,
            @RequestParam(defaultValue = "50") int limit) {
        var ctx = TrustContextHolder.require();
        List<Map<String, Object>> rows = bills
                .findByTenantIdAndFacilityIdAndStatusIn(ctx.tenantId(), facilityId, UNBILLED,
                        PageRequest.of(0, clampLimit(limit)))
                .map(FacilityBillingController::toChargeRow)
                .getContent();
        return ResponseEntity.ok(ApiResponse.ok(rows, ctx.correlationId().toString()));
    }

    @GetMapping("/invoices")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> facilityInvoices(
            @RequestParam(name = "facility_id") UUID facilityId,
            @RequestParam(defaultValue = "50") int limit) {
        var ctx = TrustContextHolder.require();
        List<Map<String, Object>> rows = invoices
                .findByFacility(ctx.tenantId(), facilityId, PageRequest.of(0, clampLimit(limit)))
                .stream().map(FacilityBillingController::toInvoiceRow).toList();
        return ResponseEntity.ok(ApiResponse.ok(rows, ctx.correlationId().toString()));
    }

    @GetMapping("/payments")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> facilityPayments(
            @RequestParam(name = "facility_id") UUID facilityId,
            @RequestParam(defaultValue = "50") int limit) {
        var ctx = TrustContextHolder.require();
        List<Map<String, Object>> rows = payments
                .findRecentByFacility(ctx.tenantId(), facilityId, PageRequest.of(0, clampLimit(limit)))
                .stream().map(FacilityBillingController::toPaymentRow).toList();
        return ResponseEntity.ok(ApiResponse.ok(rows, ctx.correlationId().toString()));
    }

    private static Map<String, Object> toChargeRow(BillHeaderEntity b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("billId", b.getBillId());
        m.put("encounterId", b.getEncounterId());
        m.put("status", b.getStatus() != null ? b.getStatus().name() : null);
        m.put("totalCharge", b.getTotalCharge());
        m.put("patientPayable", b.getPatientPayable());
        m.put("currency", b.getCurrency());
        m.put("createdAt", b.getCreatedAt() != null ? b.getCreatedAt().toString() : null);
        return m;
    }

    private static Map<String, Object> toInvoiceRow(InvoiceEntity i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("invoiceId", i.getInvoiceId());
        m.put("invoiceNumber", i.getInvoiceNumber());
        m.put("status", i.getInvoiceStatus());
        m.put("total", i.getTotal());
        m.put("outstanding", i.getOutstandingAmount());
        m.put("currency", i.getCurrency());
        m.put("payerRef", i.getPayerRef());
        m.put("issuedAt", i.getIssuedAt() != null ? i.getIssuedAt().toString() : null);
        return m;
    }

    private static Map<String, Object> toPaymentRow(PaymentEntity p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("paymentId", p.getId() != null ? p.getId().toString() : null);
        m.put("billId", p.getBillId());
        m.put("status", p.getStatus() != null ? p.getStatus().name() : null);
        m.put("paymentType", p.getPaymentType() != null ? p.getPaymentType().name() : null);
        m.put("amount", p.getAmount());
        m.put("paidAmount", p.getPaidAmount());
        m.put("currency", p.getCurrency());
        m.put("paidAt", p.getPaidAt() != null ? p.getPaidAt().toString() : null);
        return m;
    }
}
