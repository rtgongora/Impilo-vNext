package zw.gov.mohcc.impilo.costa.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.costa.domain.entity.BillHeaderEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.BillPartyEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.EncounterEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.ReceivableEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.WriteOffEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.PartyType;
import zw.gov.mohcc.impilo.costa.domain.repository.BillPartyRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.EncounterRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.ReceivableRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.WriteOffRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class ReceivablesService {

    private static final List<String> PAYMENT_PRIORITY = List.of(
            PartyType.PATIENT.name(),
            PartyType.INSURER.name(),
            PartyType.SUBSIDY.name(),
            PartyType.PROGRAM.name(),
            PartyType.DONOR.name()
    );

    private final ReceivableRepository receivableRepository;
    private final BillPartyRepository billPartyRepository;
    private final EncounterRepository encounterRepository;
    private final WriteOffRepository writeOffRepository;

    public ReceivablesService(ReceivableRepository receivableRepository,
                              BillPartyRepository billPartyRepository,
                              EncounterRepository encounterRepository,
                              WriteOffRepository writeOffRepository) {
        this.receivableRepository = receivableRepository;
        this.billPartyRepository = billPartyRepository;
        this.encounterRepository = encounterRepository;
        this.writeOffRepository = writeOffRepository;
    }

    /**
     * Creates one receivable per bill party (excluding WRITE_OFF) when a bill is finalized.
     */
    @Transactional
    public List<ReceivableEntity> createFromFinalizedBill(BillHeaderEntity bill) {
        List<BillPartyEntity> parties = billPartyRepository.findByBillId(bill.getBillId());
        EncounterEntity encounter = bill.getEncounterId() != null
                ? encounterRepository.findById(bill.getEncounterId()).orElse(null)
                : null;
        String patientCpid = encounter != null ? encounter.getPatientCpid() : null;

        List<ReceivableEntity> created = new ArrayList<>();
        for (BillPartyEntity party : parties) {
            if (party.getPartyType() == PartyType.WRITE_OFF) {
                continue;
            }
            if (party.getAmount() == null || party.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            ReceivableEntity r = new ReceivableEntity();
            r.setTenantId(bill.getTenantId());
            r.setFacilityId(bill.getFacilityId());
            r.setDebtorType(party.getPartyType().name());
            r.setDebtorRef(resolveDebtorRef(party, patientCpid, bill.getEncounterId()));
            r.setDebtorName(resolveDebtorName(party, patientCpid));
            r.setBillId(bill.getBillId());
            r.setOriginalAmount(party.getAmount());
            r.setPaidAmount(BigDecimal.ZERO);
            r.setOutstanding(party.getAmount());
            r.setCurrency(bill.getCurrency());
            r.setDueDate(LocalDate.now().plusDays(dueDaysFor(party.getPartyType())));
            r.setStatus("OPEN");
            refreshAging(r);
            created.add(receivableRepository.save(r));
        }
        return created;
    }

    @Transactional
    public void applyPaymentToBill(BillHeaderEntity bill, BigDecimal paidAmount) {
        if (paidAmount == null || paidAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        List<ReceivableEntity> rows = new ArrayList<>(
                receivableRepository.findByTenantIdAndBillIdOrderByCreatedAtAsc(bill.getTenantId(), bill.getBillId()));
        rows.sort(Comparator.comparingInt(r -> priorityIndex(r.getDebtorType())));

        BigDecimal remaining = paidAmount;
        for (ReceivableEntity r : rows) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            if (!"OPEN".equals(r.getStatus()) && !"PARTIAL".equals(r.getStatus())) {
                continue;
            }
            if (r.getOutstanding().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal apply = remaining.min(r.getOutstanding());
            r.setPaidAmount(r.getPaidAmount().add(apply));
            r.setOutstanding(r.getOutstanding().subtract(apply));
            remaining = remaining.subtract(apply);
            refreshAging(r);
            if (r.getOutstanding().compareTo(BigDecimal.ZERO) == 0) {
                r.setStatus("PAID");
            } else {
                r.setStatus("PARTIAL");
            }
            receivableRepository.save(r);
        }
    }

    @Transactional
    public void attachInvoice(UUID tenantId, String billId, String invoiceId) {
        List<ReceivableEntity> list = receivableRepository.findByTenantIdAndBillIdOrderByCreatedAtAsc(tenantId, billId);
        for (ReceivableEntity r : list) {
            r.setInvoiceId(invoiceId);
            receivableRepository.save(r);
        }
    }

    @Transactional(readOnly = true)
    public Page<ReceivableEntity> search(UUID tenantId, UUID facilityId, String debtorRef, String debtorType,
                                         String status, String agingBucket, boolean outstandingOnly, Pageable pageable) {
        Page<ReceivableEntity> page = receivableRepository.search(
                tenantId, facilityId, debtorRef, debtorType, status, agingBucket, outstandingOnly, pageable);
        page.getContent().forEach(this::refreshAging);
        return page;
    }

    @Transactional(readOnly = true)
    public List<ReceivableEntity> listOutstandingByDebtor(UUID tenantId, String debtorRef, String debtorType) {
        List<ReceivableEntity> list = receivableRepository.findByTenantIdAndDebtorRefAndDebtorType(
                tenantId, debtorRef, debtorType);
        list.forEach(this::refreshAging);
        return list.stream().filter(r -> r.getOutstanding().compareTo(BigDecimal.ZERO) > 0).toList();
    }

    @Transactional
    public WriteOffEntity requestWriteOff(UUID tenantId, UUID facilityId, UUID receivableId,
                                           BigDecimal amount, String reason) {
        ReceivableEntity rec = receivableRepository.findByReceivableId(receivableId)
                .filter(r -> r.getTenantId().equals(tenantId) && r.getFacilityId().equals(facilityId))
                .orElseThrow(() -> new IllegalArgumentException("Receivable not found"));
        if (amount.compareTo(rec.getOutstanding()) > 0) {
            throw new IllegalArgumentException("Write-off exceeds outstanding balance");
        }
        WriteOffEntity w = new WriteOffEntity();
        w.setTenantId(tenantId);
        w.setFacilityId(facilityId);
        w.setReceivableId(receivableId);
        w.setAmount(amount);
        w.setReason(reason);
        w.setStatus("PENDING");
        return writeOffRepository.save(w);
    }

    @Transactional
    public ReceivableEntity recordPaymentOnReceivable(UUID tenantId, UUID receivableId, BigDecimal amount) {
        ReceivableEntity r = receivableRepository.findByReceivableId(receivableId)
                .filter(x -> x.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Receivable not found"));
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (amount.compareTo(r.getOutstanding()) > 0) {
            throw new IllegalArgumentException("Payment exceeds outstanding");
        }
        r.setPaidAmount(r.getPaidAmount().add(amount));
        r.setOutstanding(r.getOutstanding().subtract(amount));
        refreshAging(r);
        if (r.getOutstanding().compareTo(BigDecimal.ZERO) == 0) {
            r.setStatus("PAID");
        } else {
            r.setStatus("PARTIAL");
        }
        return receivableRepository.save(r);
    }

    public void refreshAging(ReceivableEntity r) {
        r.setAgingBucket(computeAgingBucket(r.getDueDate(), LocalDate.now()));
    }

    public static String computeAgingBucket(LocalDate dueDate, LocalDate today) {
        if (dueDate == null || !dueDate.isBefore(today)) {
            return "CURRENT";
        }
        long daysPast = ChronoUnit.DAYS.between(dueDate, today);
        if (daysPast <= 30) {
            return "30_DAY";
        }
        if (daysPast <= 60) {
            return "60_DAY";
        }
        if (daysPast <= 90) {
            return "90_DAY";
        }
        return "120_PLUS";
    }

    private static int priorityIndex(String debtorType) {
        int idx = PAYMENT_PRIORITY.indexOf(debtorType);
        return idx < 0 ? PAYMENT_PRIORITY.size() : idx;
    }

    private static long dueDaysFor(PartyType partyType) {
        return switch (partyType) {
            case INSURER -> 45L;
            default -> 30L;
        };
    }

    private static String resolveDebtorRef(BillPartyEntity party, String patientCpid, String encounterId) {
        if (party.getPartyRef() != null && !party.getPartyRef().isBlank()) {
            return party.getPartyRef();
        }
        if (party.getPartyType() == PartyType.PATIENT && patientCpid != null) {
            return patientCpid;
        }
        return encounterId != null ? encounterId : "UNKNOWN";
    }

    private static String resolveDebtorName(BillPartyEntity party, String patientCpid) {
        if (party.getPartyType() == PartyType.PATIENT && patientCpid != null) {
            return patientCpid;
        }
        return party.getPartyType().name();
    }
}
