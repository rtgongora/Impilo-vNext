package zw.gov.mohcc.impilo.costa.api;

import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.costa.api.dto.finance.OutstandingBillDto;
import zw.gov.mohcc.impilo.costa.api.dto.finance.PatientAccountSummaryDto;
import zw.gov.mohcc.impilo.costa.api.dto.finance.PatientTransactionRowDto;
import zw.gov.mohcc.impilo.costa.api.dto.finance.RecordPatientPaymentRequest;
import zw.gov.mohcc.impilo.costa.domain.entity.PatientTransactionEntity;
import zw.gov.mohcc.impilo.costa.service.PatientAccountService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/costa/v1/finance/patient-accounts")
public class PatientAccountController {

    private final PatientAccountService patientAccountService;

    public PatientAccountController(PatientAccountService patientAccountService) {
        this.patientAccountService = patientAccountService;
    }

    @GetMapping("/{cpid}")
    public ResponseEntity<ApiResponse<PatientAccountSummaryDto>> getAccount(@PathVariable String cpid) {
        var ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(patientAccountService.getAccountSummary(cpid), ctx.correlationId().toString()));
    }

    @GetMapping("/{cpid}/transactions")
    public ResponseEntity<ApiResponse<PagedResponse<PatientTransactionRowDto>>> listTransactions(
            @PathVariable String cpid,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dateTo) {
        var ctx = TrustContextHolder.require();
        var slice = patientAccountService.listTransactions(cpid, dateFrom, dateTo,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "txnDate")));
        PagedResponse<PatientTransactionRowDto> paged = PagedResponse.of(
                slice.getContent(), page, size, slice.getTotalElements());
        return ResponseEntity.ok(ApiResponse.ok(paged, ctx.correlationId().toString()));
    }

    @GetMapping("/{cpid}/outstanding")
    public ResponseEntity<ApiResponse<java.util.List<OutstandingBillDto>>> outstanding(
            @PathVariable String cpid) {
        var ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(patientAccountService.listOutstandingBills(cpid),
                ctx.correlationId().toString()));
    }

    @PostMapping("/{cpid}/payments")
    public ResponseEntity<ApiResponse<PatientTransactionEntity>> recordPayment(
            @PathVariable String cpid,
            @Valid @RequestBody RecordPatientPaymentRequest body) {
        var ctx = TrustContextHolder.require();
        PatientTransactionEntity txn = patientAccountService.recordManualPatientPayment(cpid, body);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(txn, ctx.correlationId().toString()));
    }
}
