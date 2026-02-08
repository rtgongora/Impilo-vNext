package zw.gov.mohcc.impilo.costa.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.costa.domain.entity.*;
import zw.gov.mohcc.impilo.costa.domain.repository.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/costa/v1/audit")
public class AuditController {

    private final BillHeaderRepository billHeaderRepository;
    private final BillLineRepository billLineRepository;
    private final BillPartyRepository billPartyRepository;
    private final ApprovalRepository approvalRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final ClaimPackRepository claimPackRepository;
    private final InvoiceRepository invoiceRepository;

    public AuditController(BillHeaderRepository billHeaderRepository,
                           BillLineRepository billLineRepository,
                           BillPartyRepository billPartyRepository,
                           ApprovalRepository approvalRepository,
                           PaymentRepository paymentRepository,
                           RefundRepository refundRepository,
                           ClaimPackRepository claimPackRepository,
                           InvoiceRepository invoiceRepository) {
        this.billHeaderRepository = billHeaderRepository;
        this.billLineRepository = billLineRepository;
        this.billPartyRepository = billPartyRepository;
        this.approvalRepository = approvalRepository;
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.claimPackRepository = claimPackRepository;
        this.invoiceRepository = invoiceRepository;
    }

    @GetMapping("/bill/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> auditBill(@PathVariable String id) {
        var ctx = TrustContextHolder.require();

        BillHeaderEntity bill = billHeaderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Bill not found: " + id));
        List<BillLineEntity> lines = billLineRepository.findByBillId(id);
        List<BillPartyEntity> parties = billPartyRepository.findByBillId(id);
        List<ApprovalEntity> approvals = approvalRepository.findByBillIdOrderByCreatedAtAsc(id);
        List<PaymentEntity> payments = paymentRepository.findByBillId(id);
        List<RefundEntity> refunds = refundRepository.findByBillId(id);
        List<ClaimPackEntity> claims = claimPackRepository.findByBillId(id);
        var invoice = invoiceRepository.findByBillId(id);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("bill", bill);
        audit.put("lines", lines);
        audit.put("lines_count", lines.size());
        audit.put("voided_lines", lines.stream().filter(BillLineEntity::isVoided).count());
        audit.put("parties", parties);
        audit.put("approvals", approvals);
        audit.put("payments", payments);
        audit.put("refunds", refunds);
        audit.put("claims", claims);
        audit.put("invoice", invoice.orElse(null));

        return ResponseEntity.ok(ApiResponse.ok(audit, ctx.correlationId().toString()));
    }
}
