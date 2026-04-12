package zw.gov.mohcc.impilo.costa.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.costa.api.dto.finance.*;
import zw.gov.mohcc.impilo.costa.domain.entity.*;
import zw.gov.mohcc.impilo.costa.domain.enums.BillStatus;
import zw.gov.mohcc.impilo.costa.domain.enums.PaymentStatus;
import zw.gov.mohcc.impilo.costa.domain.repository.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PatientAccountService {

    public static final String TXN_CHARGE = "CHARGE";
    public static final String TXN_PAYMENT = "PAYMENT";
    public static final String TXN_INSURER_PAYMENT = "INSURER_PAYMENT";
    public static final String TXN_WRITE_OFF = "WRITE_OFF";
    public static final String TXN_REFUND = "REFUND";

    private final PatientAccountRepository patientAccountRepository;
    private final PatientTransactionRepository patientTransactionRepository;
    private final BillHeaderRepository billHeaderRepository;
    private final EncounterRepository encounterRepository;
    private final PaymentRepository paymentRepository;

    public PatientAccountService(PatientAccountRepository patientAccountRepository,
                                 PatientTransactionRepository patientTransactionRepository,
                                 BillHeaderRepository billHeaderRepository,
                                 EncounterRepository encounterRepository,
                                 PaymentRepository paymentRepository) {
        this.patientAccountRepository = patientAccountRepository;
        this.patientTransactionRepository = patientTransactionRepository;
        this.billHeaderRepository = billHeaderRepository;
        this.encounterRepository = encounterRepository;
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public PatientAccountEntity getOrCreateAccount(UUID tenantId, String patientCpid, String currency) {
        return patientAccountRepository.findByTenantIdAndPatientCpid(tenantId, patientCpid)
                .orElseGet(() -> {
                    PatientAccountEntity a = new PatientAccountEntity();
                    a.setTenantId(tenantId);
                    a.setPatientCpid(patientCpid);
                    if (currency != null && !currency.isBlank()) {
                        a.setCurrency(currency);
                    }
                    return patientAccountRepository.save(a);
                });
    }

    @Transactional(readOnly = true)
    public PatientAccountSummaryDto getAccountSummary(String patientCpid) {
        TrustContext ctx = TrustContextHolder.require();
        return patientAccountRepository
                .findByTenantIdAndPatientCpid(ctx.tenantId(), patientCpid)
                .map(this::toSummary)
                .orElseGet(() -> new PatientAccountSummaryDto(null, patientCpid,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        "ZWL", null));
    }

    @Transactional
    public void recordBillFinalized(BillHeaderEntity bill) {
        if (bill.getEncounterId() == null) {
            return;
        }
        EncounterEntity enc = encounterRepository.findById(bill.getEncounterId()).orElse(null);
        if (enc == null || !enc.getTenantId().equals(bill.getTenantId())) {
            return;
        }
        String cpid = enc.getPatientCpid();
        PatientAccountEntity acc = getOrCreateAccount(bill.getTenantId(), cpid, bill.getCurrency());
        if (acc.getCurrency() == null || acc.getCurrency().isBlank()) {
            acc.setCurrency(bill.getCurrency());
        }

        BigDecimal gross = nz(bill.getTotalPayable());
        BigDecimal ins = nz(bill.getInsurerPayable());
        BigDecimal pat = nz(bill.getPatientPayable());

        PatientTransactionEntity txn = new PatientTransactionEntity();
        txn.setTenantId(bill.getTenantId());
        txn.setPatientCpid(cpid);
        txn.setAccountId(acc.getAccountId());
        txn.setTxnType(TXN_CHARGE);
        txn.setFacilityId(bill.getFacilityId());
        txn.setEncounterId(bill.getEncounterId());
        txn.setBillId(bill.getBillId());
        txn.setDescription("Bill finalized — encounter charges");
        txn.setGrossAmount(gross);
        txn.setInsurerAmount(ins);
        txn.setPatientAmount(pat);
        txn.setTxnDate(nzTime(bill.getFinalizedAt()));
        patientTransactionRepository.save(txn);

        acc.setTotalBilled(nz(acc.getTotalBilled()).add(gross));
        acc.setTotalInsurer(nz(acc.getTotalInsurer()).add(ins));
        acc.setTotalOutstanding(nz(acc.getTotalOutstanding()).add(pat));
        acc.setLastActivity(txn.getTxnDate());
        patientAccountRepository.save(acc);
    }

    @Transactional
    public void recordPatientPaymentCaptured(PaymentEntity payment, BillHeaderEntity bill) {
        if (bill.getEncounterId() == null) {
            return;
        }
        EncounterEntity enc = encounterRepository.findById(bill.getEncounterId()).orElse(null);
        if (enc == null) {
            return;
        }
        String cpid = enc.getPatientCpid();
        PatientAccountEntity acc = getOrCreateAccount(bill.getTenantId(), cpid, bill.getCurrency());
        BigDecimal paid = nz(payment.getPaidAmount());
        if (paid.compareTo(BigDecimal.ZERO) <= 0) {
            paid = nz(payment.getAmount());
        }

        PatientTransactionEntity txn = new PatientTransactionEntity();
        txn.setTenantId(bill.getTenantId());
        txn.setPatientCpid(cpid);
        txn.setAccountId(acc.getAccountId());
        txn.setTxnType(TXN_PAYMENT);
        txn.setFacilityId(bill.getFacilityId());
        txn.setEncounterId(bill.getEncounterId());
        txn.setBillId(bill.getBillId());
        txn.setDescription("Patient payment received");
        txn.setPatientAmount(paid);
        txn.setPaymentMethod(payment.getPaymentType() != null ? payment.getPaymentType().name() : null);
        txn.setPaymentRef(payment.getMushexPaymentIntentId());
        txn.setTxnDate(nzTime(payment.getPaidAt()));
        patientTransactionRepository.save(txn);

        acc.setTotalPaid(nz(acc.getTotalPaid()).add(paid));
        acc.setTotalOutstanding(nz(acc.getTotalOutstanding()).subtract(paid).max(BigDecimal.ZERO));
        acc.setLastActivity(txn.getTxnDate());
        patientAccountRepository.save(acc);
    }

    @Transactional
    public PatientTransactionEntity recordManualPatientPayment(String patientCpid, RecordPatientPaymentRequest req) {
        TrustContext ctx = TrustContextHolder.require();
        PatientAccountEntity probe = patientAccountRepository
                .findByTenantIdAndPatientCpid(ctx.tenantId(), patientCpid).orElse(null);
        String cur = probe != null && probe.getCurrency() != null ? probe.getCurrency() : "USD";
        PatientAccountEntity acc = getOrCreateAccount(ctx.tenantId(), patientCpid, cur);
        BigDecimal amt = req.amount();
        if (amt.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }

        if (req.billId() != null && !req.billId().isBlank()) {
            BillHeaderEntity bill = billHeaderRepository.findById(req.billId())
                    .orElseThrow(() -> new IllegalArgumentException("Bill not found"));
            if (!bill.getTenantId().equals(ctx.tenantId())) {
                throw new IllegalArgumentException("Bill tenant mismatch");
            }
            if (bill.getEncounterId() != null) {
                EncounterEntity e = encounterRepository.findById(bill.getEncounterId()).orElse(null);
                if (e != null && !patientCpid.equals(e.getPatientCpid())) {
                    throw new IllegalArgumentException("Bill does not belong to patient");
                }
            }
        }

        PatientTransactionEntity txn = new PatientTransactionEntity();
        txn.setTenantId(ctx.tenantId());
        txn.setPatientCpid(patientCpid);
        txn.setAccountId(acc.getAccountId());
        txn.setTxnType(TXN_PAYMENT);
        txn.setBillId(req.billId());
        txn.setDescription("Patient payment (recorded)");
        txn.setPatientAmount(amt);
        txn.setPaymentMethod(req.paymentMethod());
        txn.setPaymentRef(req.paymentRef());
        txn.setTxnDate(OffsetDateTime.now());
        patientTransactionRepository.save(txn);

        acc.setTotalPaid(nz(acc.getTotalPaid()).add(amt));
        acc.setTotalOutstanding(nz(acc.getTotalOutstanding()).subtract(amt).max(BigDecimal.ZERO));
        acc.setLastActivity(txn.getTxnDate());
        patientAccountRepository.save(acc);
        return txn;
    }

    @Transactional
    public PatientTransactionEntity recordInsurerPayment(String patientCpid, String billId,
                                                          BigDecimal amount, String paymentRef) {
        TrustContext ctx = TrustContextHolder.require();
        BillHeaderEntity bill = billHeaderRepository.findById(billId)
                .orElseThrow(() -> new IllegalArgumentException("Bill not found"));
        if (!bill.getTenantId().equals(ctx.tenantId())) {
            throw new IllegalArgumentException("Bill tenant mismatch");
        }
        validatePatientOnBill(bill, patientCpid);
        PatientAccountEntity acc = getOrCreateAccount(ctx.tenantId(), patientCpid, bill.getCurrency());
        BigDecimal amt = nz(amount);
        if (amt.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        PatientTransactionEntity txn = baseTxn(acc, bill, patientCpid, TXN_INSURER_PAYMENT,
                "Insurer payment", bill.getFacilityId(), bill.getEncounterId());
        txn.setInsurerAmount(amt);
        txn.setPatientAmount(BigDecimal.ZERO);
        txn.setPaymentRef(paymentRef);
        patientTransactionRepository.save(txn);
        acc.setTotalOutstanding(nz(acc.getTotalOutstanding()).subtract(amt).max(BigDecimal.ZERO));
        acc.setLastActivity(txn.getTxnDate());
        patientAccountRepository.save(acc);
        return txn;
    }

    @Transactional
    public PatientTransactionEntity recordWriteOff(String patientCpid, String billId, BigDecimal amount) {
        TrustContext ctx = TrustContextHolder.require();
        BillHeaderEntity bill = billHeaderRepository.findById(billId)
                .orElseThrow(() -> new IllegalArgumentException("Bill not found"));
        if (!bill.getTenantId().equals(ctx.tenantId())) {
            throw new IllegalArgumentException("Bill tenant mismatch");
        }
        validatePatientOnBill(bill, patientCpid);
        PatientAccountEntity acc = getOrCreateAccount(ctx.tenantId(), patientCpid, bill.getCurrency());
        BigDecimal amt = nz(amount);
        if (amt.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        PatientTransactionEntity txn = baseTxn(acc, bill, patientCpid, TXN_WRITE_OFF,
                "Write-off", bill.getFacilityId(), bill.getEncounterId());
        txn.setPatientAmount(amt);
        patientTransactionRepository.save(txn);
        acc.setTotalWriteoff(nz(acc.getTotalWriteoff()).add(amt));
        acc.setTotalOutstanding(nz(acc.getTotalOutstanding()).subtract(amt).max(BigDecimal.ZERO));
        acc.setLastActivity(txn.getTxnDate());
        patientAccountRepository.save(acc);
        return txn;
    }

    @Transactional
    public PatientTransactionEntity recordRefund(String patientCpid, String billId, BigDecimal amount) {
        TrustContext ctx = TrustContextHolder.require();
        BillHeaderEntity bill = billHeaderRepository.findById(billId)
                .orElseThrow(() -> new IllegalArgumentException("Bill not found"));
        if (!bill.getTenantId().equals(ctx.tenantId())) {
            throw new IllegalArgumentException("Bill tenant mismatch");
        }
        validatePatientOnBill(bill, patientCpid);
        PatientAccountEntity acc = getOrCreateAccount(ctx.tenantId(), patientCpid, bill.getCurrency());
        BigDecimal amt = nz(amount);
        if (amt.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        PatientTransactionEntity txn = baseTxn(acc, bill, patientCpid, TXN_REFUND,
                "Refund to patient", bill.getFacilityId(), bill.getEncounterId());
        txn.setPatientAmount(amt);
        patientTransactionRepository.save(txn);
        acc.setTotalPaid(nz(acc.getTotalPaid()).subtract(amt).max(BigDecimal.ZERO));
        acc.setTotalOutstanding(nz(acc.getTotalOutstanding()).add(amt));
        acc.setLastActivity(txn.getTxnDate());
        patientAccountRepository.save(acc);
        return txn;
    }

    private void validatePatientOnBill(BillHeaderEntity bill, String patientCpid) {
        if (bill.getEncounterId() == null) {
            throw new IllegalArgumentException("Bill has no encounter");
        }
        EncounterEntity e = encounterRepository.findById(bill.getEncounterId())
                .orElseThrow(() -> new IllegalArgumentException("Encounter not found"));
        if (!patientCpid.equals(e.getPatientCpid())) {
            throw new IllegalArgumentException("Bill does not belong to patient");
        }
    }

    private PatientTransactionEntity baseTxn(PatientAccountEntity acc, BillHeaderEntity bill, String patientCpid,
                                             String type, String description, UUID facilityId, String encounterId) {
        PatientTransactionEntity txn = new PatientTransactionEntity();
        txn.setTenantId(acc.getTenantId());
        txn.setPatientCpid(patientCpid);
        txn.setAccountId(acc.getAccountId());
        txn.setTxnType(type);
        txn.setFacilityId(facilityId);
        txn.setEncounterId(encounterId);
        txn.setBillId(bill.getBillId());
        txn.setDescription(description);
        txn.setTxnDate(OffsetDateTime.now());
        return txn;
    }

    @Transactional(readOnly = true)
    public Page<PatientTransactionRowDto> listTransactions(String patientCpid, OffsetDateTime from,
                                                             OffsetDateTime to, Pageable pageable) {
        TrustContext ctx = TrustContextHolder.require();
        List<PatientTransactionEntity> all = patientTransactionRepository.findForPatient(
                ctx.tenantId(), patientCpid, from, to, Pageable.unpaged()).getContent();
        all.sort(Comparator.comparing(PatientTransactionEntity::getTxnDate)
                .thenComparing(PatientTransactionEntity::getId));

        Map<Long, BigDecimal> runningById = new HashMap<>();
        BigDecimal running = BigDecimal.ZERO;
        for (PatientTransactionEntity t : all) {
            running = running.add(owedDelta(t));
            runningById.put(t.getId(), running);
        }

        all.sort(Comparator.comparing(PatientTransactionEntity::getTxnDate).reversed()
                .thenComparing(Comparator.comparing(PatientTransactionEntity::getId).reversed()));

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), all.size());
        List<PatientTransactionRowDto> slice = new ArrayList<>();
        for (int i = start; i < end; i++) {
            PatientTransactionEntity t = all.get(i);
            slice.add(toRow(t, runningById.get(t.getId())));
        }
        return new PageImpl<>(slice, pageable, all.size());
    }

    @Transactional(readOnly = true)
    public List<OutstandingBillDto> listOutstandingBills(String patientCpid) {
        TrustContext ctx = TrustContextHolder.require();
        List<BillHeaderEntity> bills = billHeaderRepository.findByPatientAndStatus(
                ctx.tenantId(), patientCpid, BillStatus.FINAL);
        List<OutstandingBillDto> out = new ArrayList<>();
        for (BillHeaderEntity b : bills) {
            BigDecimal patientDue = nz(b.getPatientPayable());
            BigDecimal paid = sumPaidOnBill(b.getBillId());
            BigDecimal balance = patientDue.subtract(paid).setScale(2, RoundingMode.HALF_UP);
            if (balance.compareTo(BigDecimal.ZERO) > 0) {
                out.add(new OutstandingBillDto(
                        b.getBillId(),
                        b.getFacilityId(),
                        b.getEncounterId(),
                        patientDue,
                        paid,
                        balance,
                        b.getCurrency()));
            }
        }
        return out;
    }

    private BigDecimal sumPaidOnBill(String billId) {
        List<PaymentEntity> pays = paymentRepository.findByBillId(billId);
        BigDecimal sum = BigDecimal.ZERO;
        for (PaymentEntity p : pays) {
            if (p.getStatus() == PaymentStatus.PAID) {
                BigDecimal v = nz(p.getPaidAmount());
                if (v.compareTo(BigDecimal.ZERO) <= 0) {
                    v = nz(p.getAmount());
                }
                sum = sum.add(v);
            }
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    /** Change in patient amount owed (positive = owes more). */
    private BigDecimal owedDelta(PatientTransactionEntity t) {
        String type = t.getTxnType();
        BigDecimal p = nz(t.getPatientAmount());
        if (TXN_PAYMENT.equals(type) || TXN_INSURER_PAYMENT.equals(type) || TXN_WRITE_OFF.equals(type)) {
            return p.negate();
        }
        if (TXN_REFUND.equals(type)) {
            return p;
        }
        return p;
    }

    private PatientTransactionRowDto toRow(PatientTransactionEntity t, BigDecimal running) {
        return new PatientTransactionRowDto(
                t.getTxnId(),
                t.getTxnType(),
                t.getDescription(),
                t.getFacilityId(),
                t.getEncounterId(),
                t.getBillId(),
                t.getGrossAmount(),
                t.getInsurerAmount(),
                t.getPatientAmount(),
                t.getPaymentMethod(),
                t.getPaymentRef(),
                t.getTxnDate(),
                running != null ? running.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
    }

    private PatientAccountSummaryDto toSummary(PatientAccountEntity acc) {
        return new PatientAccountSummaryDto(
                acc.getAccountId(),
                acc.getPatientCpid(),
                nz(acc.getTotalBilled()),
                nz(acc.getTotalPaid()),
                nz(acc.getTotalInsurer()),
                nz(acc.getTotalOutstanding()),
                nz(acc.getTotalWriteoff()),
                acc.getCurrency(),
                acc.getLastActivity());
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static OffsetDateTime nzTime(OffsetDateTime t) {
        return t != null ? t : OffsetDateTime.now();
    }
}
