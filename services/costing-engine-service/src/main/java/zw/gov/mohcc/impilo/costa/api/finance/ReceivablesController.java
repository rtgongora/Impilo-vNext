package zw.gov.mohcc.impilo.costa.api.finance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.costa.domain.entity.ReceivableEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.WriteOffEntity;
import zw.gov.mohcc.impilo.costa.service.ReceivablesService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/finance/receivables")
public class ReceivablesController {

    private final ReceivablesService receivablesService;

    public ReceivablesController(ReceivablesService receivablesService) {
        this.receivablesService = receivablesService;
    }

    @GetMapping
    public ResponseEntity<Page<ReceivableEntity>> list(
            @RequestParam(value = "facility_id", required = false) UUID facilityId,
            @RequestParam(value = "debtor_ref", required = false) String debtorRef,
            @RequestParam(value = "debtor_type", required = false) String debtorType,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "aging_bucket", required = false) String agingBucket,
            @RequestParam(value = "outstanding_only", defaultValue = "true") boolean outstandingOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        var ctx = TrustContextHolder.require();
        Page<ReceivableEntity> result = receivablesService.search(
                ctx.tenantId(), facilityId, debtorRef, debtorType, status, agingBucket, outstandingOnly,
                PageRequest.of(page, size));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/debtor-outstanding")
    public ResponseEntity<List<ReceivableEntity>> debtorOutstanding(
            @RequestParam("debtor_ref") String debtorRef,
            @RequestParam("debtor_type") String debtorType) {
        var ctx = TrustContextHolder.require();
        return ResponseEntity.ok(receivablesService.listOutstandingByDebtor(ctx.tenantId(), debtorRef, debtorType));
    }

    @PostMapping("/write-off")
    public ResponseEntity<WriteOffEntity> requestWriteOff(@RequestBody Map<String, Object> body) {
        var ctx = TrustContextHolder.require();
        UUID facilityId = UUID.fromString(body.get("facility_id").toString());
        UUID receivableId = UUID.fromString(body.get("receivable_id").toString());
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        String reason = body.get("reason").toString();
        WriteOffEntity w = receivablesService.requestWriteOff(
                ctx.tenantId(), facilityId, receivableId, amount, reason);
        return ResponseEntity.status(HttpStatus.CREATED).body(w);
    }

    @PutMapping("/payments")
    public ResponseEntity<ReceivableEntity> recordPayment(@RequestBody Map<String, Object> body) {
        var ctx = TrustContextHolder.require();
        UUID receivableId = UUID.fromString(body.get("receivable_id").toString());
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        return ResponseEntity.ok(receivablesService.recordPaymentOnReceivable(ctx.tenantId(), receivableId, amount));
    }
}
