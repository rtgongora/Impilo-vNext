package zw.gov.mohcc.impilo.costa.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.costa.api.dto.finance.GenerateFinancialDocumentRequest;
import zw.gov.mohcc.impilo.costa.domain.entity.*;
import zw.gov.mohcc.impilo.costa.domain.enums.PaymentStatus;
import zw.gov.mohcc.impilo.costa.domain.repository.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FinancialDocumentService {

    public static final String DOC_PATIENT_INVOICE = "PATIENT_INVOICE";
    public static final String DOC_EOB = "EOB";
    public static final String DOC_PAYMENT_RECEIPT = "PAYMENT_RECEIPT";
    public static final String DOC_PROVIDER_STATEMENT = "PROVIDER_STATEMENT";
    public static final String DOC_TAX_CERTIFICATE = "TAX_CERTIFICATE";
    public static final String DOC_REMITTANCE_ADVICE = "REMITTANCE_ADVICE";

    private final FinancialDocumentRepository financialDocumentRepository;
    private final BillHeaderRepository billHeaderRepository;
    private final BillLineRepository billLineRepository;
    private final ClaimPackRepository claimPackRepository;
    private final PaymentRepository paymentRepository;
    private final PatientTransactionRepository patientTransactionRepository;
    private final ObjectMapper objectMapper;

    public FinancialDocumentService(FinancialDocumentRepository financialDocumentRepository,
                                    BillHeaderRepository billHeaderRepository,
                                    BillLineRepository billLineRepository,
                                    ClaimPackRepository claimPackRepository,
                                    PaymentRepository paymentRepository,
                                    PatientTransactionRepository patientTransactionRepository,
                                    ObjectMapper objectMapper) {
        this.financialDocumentRepository = financialDocumentRepository;
        this.billHeaderRepository = billHeaderRepository;
        this.billLineRepository = billLineRepository;
        this.claimPackRepository = claimPackRepository;
        this.paymentRepository = paymentRepository;
        this.patientTransactionRepository = patientTransactionRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public FinancialDocumentEntity generate(GenerateFinancialDocumentRequest req) throws Exception {
        var ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();
        String type = req.docType().toUpperCase();

        ObjectNode root = objectMapper.createObjectNode();
        String title;

        switch (type) {
            case DOC_PATIENT_INVOICE -> {
                BillHeaderEntity bill = loadBill(tenantId, req.subjectRef());
                List<BillLineEntity> lines = billLineRepository.findByBillIdAndVoidedFalse(bill.getBillId());
                title = "Patient invoice — " + bill.getBillId();
                root.put("docType", DOC_PATIENT_INVOICE);
                root.put("billId", bill.getBillId());
                root.put("facilityId", bill.getFacilityId().toString());
                root.put("currency", bill.getCurrency());
                root.put("totalPayable", bill.getTotalPayable().doubleValue());
                root.put("patientPayable", bill.getPatientPayable().doubleValue());
                root.put("insurerPayable", bill.getInsurerPayable().doubleValue());
                ArrayNode lineArr = root.putArray("lines");
                for (BillLineEntity line : lines) {
                    ObjectNode ln = lineArr.addObject();
                    ln.put("description", line.getDescription());
                    ln.put("msikaCode", line.getMsikaCode() != null ? line.getMsikaCode() : "");
                    ln.put("qty", line.getQty().doubleValue());
                    ln.put("unitPrice", line.getUnitPrice().doubleValue());
                    ln.put("amount", line.getAmount().doubleValue());
                }
            }
            case DOC_EOB -> {
                long claimId = Long.parseLong(req.subjectRef());
                ClaimPackEntity claim = claimPackRepository.findById(claimId)
                        .orElseThrow(() -> new IllegalArgumentException("Claim not found"));
                BillHeaderEntity bill = billHeaderRepository.findById(claim.getBillId())
                        .orElseThrow(() -> new IllegalArgumentException("Bill not found"));
                if (!bill.getTenantId().equals(tenantId)) {
                    throw new IllegalArgumentException("Claim tenant mismatch");
                }
                title = "Explanation of Benefits — claim " + claimId;
                root.put("docType", DOC_EOB);
                root.put("claimId", claimId);
                root.put("billId", claim.getBillId());
                root.put("insurerRef", claim.getInsurerRef() != null ? claim.getInsurerRef() : "");
                root.put("status", claim.getStatus().name());
                root.put("payload", claim.getPayload());
                if (claim.getResponse() != null) {
                    root.put("adjudicationResponse", claim.getResponse());
                }
                root.put("patientResponsibility", bill.getPatientPayable().doubleValue());
                root.put("insurerResponsibility", bill.getInsurerPayable().doubleValue());
                ArrayNode reasons = root.putArray("reasonCodes");
                billLineRepository.findByBillIdAndVoidedFalse(bill.getBillId()).forEach(line -> {
                    ObjectNode r = reasons.addObject();
                    r.put("lineId", line.getLineId());
                    r.put("description", line.getDescription());
                });
            }
            case DOC_PAYMENT_RECEIPT -> {
                long paymentId = Long.parseLong(req.subjectRef());
                PaymentEntity pay = paymentRepository.findById(paymentId)
                        .orElseThrow(() -> new IllegalArgumentException("Payment not found"));
                BillHeaderEntity bill = billHeaderRepository.findById(pay.getBillId())
                        .orElseThrow(() -> new IllegalArgumentException("Bill not found"));
                if (!bill.getTenantId().equals(tenantId)) {
                    throw new IllegalArgumentException("Payment tenant mismatch");
                }
                title = "Payment receipt — " + paymentId;
                root.put("docType", DOC_PAYMENT_RECEIPT);
                root.put("paymentId", paymentId);
                root.put("billId", pay.getBillId());
                root.put("amount", pay.getAmount().doubleValue());
                root.put("paidAmount", pay.getPaidAmount().doubleValue());
                root.put("currency", pay.getCurrency());
                root.put("status", pay.getStatus().name());
                root.put("paymentType", pay.getPaymentType().name());
                if (pay.getPaidAt() != null) {
                    root.put("paidAt", pay.getPaidAt().toString());
                }
                BigDecimal remaining = bill.getPatientPayable().subtract(sumPaid(pay.getBillId()))
                        .max(BigDecimal.ZERO);
                root.put("remainingPatientBalance", remaining.doubleValue());
            }
            case DOC_PROVIDER_STATEMENT -> {
                title = "Provider statement — " + req.subjectRef();
                root.put("docType", DOC_PROVIDER_STATEMENT);
                root.put("providerRef", req.subjectRef());
                root.put("period", req.period() != null ? req.period() : "");
                ArrayNode agg = buildProviderAggregate(tenantId, req.subjectRef());
                root.set("serviceAggregates", agg);
            }
            case DOC_TAX_CERTIFICATE -> {
                String cpid = req.subjectRef();
                int year = parseTaxYear(req.period());
                title = "Tax certificate — medical expenses " + year;
                root.put("docType", DOC_TAX_CERTIFICATE);
                root.put("patientCpid", cpid);
                root.put("taxYear", year);
                OffsetDateTime start = LocalDate.of(year, 1, 1).atStartOfDay().atOffset(ZoneOffset.UTC);
                OffsetDateTime end = LocalDate.of(year, 12, 31).atTime(23, 59, 59).atOffset(ZoneOffset.UTC);
                List<PatientTransactionEntity> txns = patientTransactionRepository.findForPatient(
                        tenantId, cpid, start, end, Pageable.unpaged()).getContent();
                BigDecimal totalCharges = BigDecimal.ZERO;
                BigDecimal totalPaid = BigDecimal.ZERO;
                for (PatientTransactionEntity t : txns) {
                    if (PatientAccountService.TXN_CHARGE.equals(t.getTxnType()) && t.getGrossAmount() != null) {
                        totalCharges = totalCharges.add(t.getGrossAmount());
                    }
                    if (PatientAccountService.TXN_PAYMENT.equals(t.getTxnType()) && t.getPatientAmount() != null) {
                        totalPaid = totalPaid.add(t.getPatientAmount());
                    }
                }
                root.put("totalEligibleCharges", totalCharges.setScale(2, RoundingMode.HALF_UP).doubleValue());
                root.put("totalPatientPayments", totalPaid.setScale(2, RoundingMode.HALF_UP).doubleValue());
                root.put("lineCount", txns.size());
            }
            case DOC_REMITTANCE_ADVICE -> {
                title = "Remittance advice — " + req.subjectRef();
                root.put("docType", DOC_REMITTANCE_ADVICE);
                root.put("payerRef", req.subjectRef());
                root.put("period", req.period() != null ? req.period() : "");
                ArrayNode lines = root.putArray("remittanceLines");
                List<PatientTransactionEntity> txns = patientTransactionRepository.findByTenantIdAndTxnTypeOrderByTxnDateDesc(
                        tenantId, PatientAccountService.TXN_INSURER_PAYMENT);
                for (PatientTransactionEntity t : txns) {
                    ObjectNode row = lines.addObject();
                    row.put("billId", t.getBillId() != null ? t.getBillId() : "");
                    row.put("amount", t.getInsurerAmount() != null ? t.getInsurerAmount().doubleValue() : 0);
                }
            }
            default -> throw new IllegalArgumentException("Unsupported doc type: " + req.docType());
        }

        FinancialDocumentEntity doc = new FinancialDocumentEntity();
        doc.setTenantId(tenantId);
        doc.setDocType(type);
        doc.setSubjectType(req.subjectType());
        doc.setSubjectRef(req.subjectRef());
        doc.setRecipientType(req.recipientType());
        doc.setRecipientRef(req.recipientRef());
        doc.setTitle(title);
        doc.setContentJson(objectMapper.writeValueAsString(Map.of("document", root)));
        doc.setStatus("GENERATED");
        return financialDocumentRepository.save(doc);
    }

    private ArrayNode buildProviderAggregate(UUID tenantId, String providerRef) {
        ArrayNode arr = objectMapper.createArrayNode();
        List<PatientTransactionEntity> txns;
        try {
            UUID facilityId = UUID.fromString(providerRef);
            txns = patientTransactionRepository.findByTenantIdAndFacilityIdOrderByTxnDateDesc(tenantId, facilityId);
        } catch (IllegalArgumentException ex) {
            txns = List.of();
        }
        for (PatientTransactionEntity t : txns) {
            ObjectNode row = arr.addObject();
            row.put("facilityId", t.getFacilityId() != null ? t.getFacilityId().toString() : "");
            row.put("billId", t.getBillId() != null ? t.getBillId() : "");
            row.put("txnType", t.getTxnType());
            row.put("gross", t.getGrossAmount() != null ? t.getGrossAmount().doubleValue() : 0);
            row.put("patient", t.getPatientAmount() != null ? t.getPatientAmount().doubleValue() : 0);
            row.put("insurer", t.getInsurerAmount() != null ? t.getInsurerAmount().doubleValue() : 0);
            row.put("txnDate", t.getTxnDate().toString());
        }
        return arr;
    }

    private BillHeaderEntity loadBill(UUID tenantId, String billId) {
        BillHeaderEntity bill = billHeaderRepository.findById(billId)
                .orElseThrow(() -> new IllegalArgumentException("Bill not found"));
        if (!bill.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Bill tenant mismatch");
        }
        return bill;
    }

    private BigDecimal sumPaid(String billId) {
        return paymentRepository.findByBillId(billId).stream()
                .filter(p -> p.getStatus() == PaymentStatus.PAID)
                .map(p -> p.getPaidAmount() != null && p.getPaidAmount().compareTo(BigDecimal.ZERO) > 0
                        ? p.getPaidAmount() : p.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static int parseTaxYear(String period) {
        if (period == null || period.isBlank()) {
            return LocalDate.now().getYear() - 1;
        }
        try {
            if (period.length() >= 4) {
                return Integer.parseInt(period.substring(0, 4));
            }
        } catch (NumberFormatException ignored) {
        }
        return LocalDate.now().getYear() - 1;
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<FinancialDocumentEntity> listDocuments(
            String subjectRef, String subjectType, String docType, org.springframework.data.domain.Pageable pageable) {
        var ctx = TrustContextHolder.require();
        return financialDocumentRepository.search(
                ctx.tenantId(), subjectRef,
                blankToNull(subjectType),
                blankToNull(docType),
                pageable);
    }

    @Transactional(readOnly = true)
    public FinancialDocumentEntity getDocument(UUID docId) {
        var ctx = TrustContextHolder.require();
        return financialDocumentRepository.findByTenantIdAndDocId(ctx.tenantId(), docId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
