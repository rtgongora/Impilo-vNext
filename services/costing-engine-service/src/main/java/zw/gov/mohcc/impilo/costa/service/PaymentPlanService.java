package zw.gov.mohcc.impilo.costa.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.costa.api.dto.finance.CreatePaymentPlanRequest;
import zw.gov.mohcc.impilo.costa.api.dto.finance.PayInstallmentRequest;
import zw.gov.mohcc.impilo.costa.domain.entity.PaymentPlanEntity;
import zw.gov.mohcc.impilo.costa.domain.repository.PaymentPlanRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentPlanService {

    private final PaymentPlanRepository paymentPlanRepository;

    public PaymentPlanService(PaymentPlanRepository paymentPlanRepository) {
        this.paymentPlanRepository = paymentPlanRepository;
    }

    @Transactional
    public PaymentPlanEntity createPlan(CreatePaymentPlanRequest req) {
        var ctx = TrustContextHolder.require();
        PaymentPlanEntity p = new PaymentPlanEntity();
        p.setTenantId(ctx.tenantId());
        p.setPatientCpid(req.patientCpid());
        p.setTotalAmount(req.totalAmount());
        p.setPaidAmount(BigDecimal.ZERO);
        p.setInstallmentAmount(req.installmentAmount());
        p.setFrequency(req.frequency() != null && !req.frequency().isBlank() ? req.frequency() : "MONTHLY");
        p.setNextDueDate(req.nextDueDate());
        p.setInstallmentsTotal(req.installmentsTotal());
        p.setInstallmentsPaid(0);
        p.setStatus("ACTIVE");
        return paymentPlanRepository.save(p);
    }

    @Transactional(readOnly = true)
    public List<PaymentPlanEntity> listForPatient(String patientCpid) {
        var ctx = TrustContextHolder.require();
        return paymentPlanRepository.findByTenantIdAndPatientCpidOrderByCreatedAtDesc(
                ctx.tenantId(), patientCpid);
    }

    @Transactional(readOnly = true)
    public PaymentPlanEntity getPlan(UUID planId) {
        var ctx = TrustContextHolder.require();
        return paymentPlanRepository.findByTenantIdAndPlanId(ctx.tenantId(), planId)
                .orElseThrow(() -> new IllegalArgumentException("Payment plan not found: " + planId));
    }

    @Transactional
    public PaymentPlanEntity recordInstallment(UUID planId, PayInstallmentRequest body) {
        PaymentPlanEntity p = getPlan(planId);
        if (!"ACTIVE".equalsIgnoreCase(p.getStatus())) {
            throw new IllegalStateException("Plan is not active");
        }
        BigDecimal pay = body != null && body.amount() != null && body.amount().compareTo(BigDecimal.ZERO) > 0
                ? body.amount()
                : p.getInstallmentAmount();
        BigDecimal newPaid = p.getPaidAmount().add(pay).setScale(2, RoundingMode.HALF_UP);
        if (newPaid.compareTo(p.getTotalAmount()) > 0) {
            throw new IllegalArgumentException("Installment exceeds remaining plan balance");
        }
        p.setPaidAmount(newPaid);
        int paidCount = p.getInstallmentsPaid() == null ? 0 : p.getInstallmentsPaid();
        p.setInstallmentsPaid(paidCount + 1);
        if (p.getNextDueDate() != null) {
            p.setNextDueDate(bumpDueDate(p.getNextDueDate(), p.getFrequency()));
        }
        if (newPaid.compareTo(p.getTotalAmount()) >= 0) {
            p.setStatus("COMPLETED");
            p.setNextDueDate(null);
        }
        return paymentPlanRepository.save(p);
    }

    @Transactional(readOnly = true)
    public List<PaymentPlanEntity> listOverduePlans(String patientCpid) {
        LocalDate today = LocalDate.now();
        return listForPatient(patientCpid).stream()
                .filter(pl -> "ACTIVE".equalsIgnoreCase(pl.getStatus())
                        && pl.getNextDueDate() != null
                        && pl.getNextDueDate().isBefore(today))
                .toList();
    }

    private static LocalDate bumpDueDate(LocalDate current, String frequency) {
        if (frequency == null) {
            return current.plusMonths(1);
        }
        return switch (frequency.toUpperCase()) {
            case "WEEKLY" -> current.plusWeeks(1);
            case "BIWEEKLY" -> current.plusWeeks(2);
            case "QUARTERLY" -> current.plusMonths(3);
            default -> current.plusMonths(1);
        };
    }
}
