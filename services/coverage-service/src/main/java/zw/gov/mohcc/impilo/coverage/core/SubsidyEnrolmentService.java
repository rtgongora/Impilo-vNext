package zw.gov.mohcc.impilo.coverage.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.coverage.api.dto.SubsidyConsumeRequest;
import zw.gov.mohcc.impilo.coverage.api.dto.SubsidyEnrolmentRequest;
import zw.gov.mohcc.impilo.coverage.api.dto.SubsidyEnrolmentResponse;
import zw.gov.mohcc.impilo.coverage.domain.SubsidyBalanceEntity;
import zw.gov.mohcc.impilo.coverage.domain.SubsidyEnrolmentEntity;
import zw.gov.mohcc.impilo.coverage.domain.SubsidyProgramEntity;
import zw.gov.mohcc.impilo.coverage.repository.SubsidyBalanceRepository;
import zw.gov.mohcc.impilo.coverage.repository.SubsidyEnrolmentRepository;
import zw.gov.mohcc.impilo.coverage.repository.SubsidyProgramRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Subsidy enrolment (member↔subsidy link) + annual-cap balance enforcement.
 *
 * <p>Coverage owns eligibility/subsidy (SoR). The annual cap is enforced here at
 * enrolment, at consumption (drawdown), and at eligibility-check time via
 * {@link #checkCap(UUID, String, BigDecimal)}.
 */
@Service
public class SubsidyEnrolmentService {

    private static final Logger log = LoggerFactory.getLogger(SubsidyEnrolmentService.class);

    private final SubsidyProgramRepository programRepository;
    private final SubsidyEnrolmentRepository enrolmentRepository;
    private final SubsidyBalanceRepository balanceRepository;

    public SubsidyEnrolmentService(SubsidyProgramRepository programRepository,
                                   SubsidyEnrolmentRepository enrolmentRepository,
                                   SubsidyBalanceRepository balanceRepository) {
        this.programRepository = programRepository;
        this.enrolmentRepository = enrolmentRepository;
        this.balanceRepository = balanceRepository;
    }

    @Transactional
    public SubsidyEnrolmentResponse enrol(UUID tenantId, SubsidyEnrolmentRequest req) {
        SubsidyProgramEntity program = resolveProgram(tenantId, req.subsidyProgramId(), req.programCode());
        if (!"ACTIVE".equalsIgnoreCase(program.getStatus())) {
            throw new IllegalArgumentException("Subsidy programme is not ACTIVE: " + program.getProgramCode());
        }
        if (enrolmentRepository.existsByTenantIdAndSubsidyProgramIdAndMemberCpidAndStatus(
                tenantId, program.getId(), req.memberCpid().trim(), "ACTIVE")) {
            throw new IllegalArgumentException("Member already actively enrolled in subsidy: " + program.getProgramCode());
        }

        SubsidyEnrolmentEntity e = new SubsidyEnrolmentEntity();
        e.setTenantId(tenantId);
        e.setSubsidyProgramId(program.getId());
        e.setMemberCpid(req.memberCpid().trim());
        e.setStatus("ACTIVE");
        e.setAnnualCapOverride(req.annualCapOverride());
        e.setCurrency(req.currency() != null && !req.currency().isBlank()
                ? req.currency() : program.getCurrency());
        e.setEnrolledBy(req.enrolledBy());
        e.setEffectiveFrom(LocalDate.now());
        e = enrolmentRepository.save(e);

        BigDecimal cap = effectiveCap(e, program);
        log.info("Enrolled member {} into subsidy {} (cap={} {})",
                e.getMemberCpid(), program.getProgramCode(), cap, e.getCurrency());
        return SubsidyEnrolmentResponse.of(e, cap, BigDecimal.ZERO,
                cap != null ? cap : null);
    }

    @Transactional(readOnly = true)
    public List<SubsidyEnrolmentResponse> listForMember(UUID tenantId, String memberCpid) {
        return enrolmentRepository.findByTenantIdAndMemberCpid(tenantId, memberCpid).stream()
                .map(e -> toResponse(tenantId, e))
                .toList();
    }

    @Transactional(readOnly = true)
    public SubsidyEnrolmentResponse get(UUID tenantId, UUID enrolmentId) {
        SubsidyEnrolmentEntity e = enrolmentRepository.findByIdAndTenantId(enrolmentId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Subsidy enrolment not found: " + enrolmentId));
        return toResponse(tenantId, e);
    }

    /**
     * Draw down subsidy value against the annual cap. Rejects (does not partially apply) when
     * the cap would be exceeded — callers should first {@link #checkCap}.
     */
    @Transactional
    public SubsidyEnrolmentResponse consume(UUID tenantId, UUID enrolmentId, SubsidyConsumeRequest req) {
        SubsidyEnrolmentEntity e = enrolmentRepository.findByIdAndTenantId(enrolmentId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Subsidy enrolment not found: " + enrolmentId));
        if (!"ACTIVE".equalsIgnoreCase(e.getStatus())) {
            throw new IllegalArgumentException("Subsidy enrolment is not ACTIVE: " + enrolmentId);
        }
        SubsidyProgramEntity program = programRepository.findByIdAndTenantId(e.getSubsidyProgramId(), tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Subsidy programme missing for enrolment"));
        BigDecimal cap = effectiveCap(e, program);
        int year = LocalDate.now().getYear();
        SubsidyBalanceEntity balance = balanceRepository.findByEnrolmentIdAndPeriodYear(enrolmentId, year)
                .orElseGet(() -> newBalance(tenantId, e, cap, year));

        BigDecimal newConsumed = balance.getConsumedAmount().add(req.amount());
        if (cap != null && newConsumed.compareTo(cap) > 0) {
            throw new IllegalArgumentException("Subsidy annual cap exceeded: cap=" + cap
                    + ", consumed=" + balance.getConsumedAmount() + ", requested=" + req.amount());
        }
        balance.setCapAmount(cap);
        balance.setConsumedAmount(newConsumed);
        balanceRepository.save(balance);
        log.info("Subsidy enrolment {} consumed {} (total {}/{} {})",
                enrolmentId, req.amount(), newConsumed, cap, e.getCurrency());

        BigDecimal remaining = cap != null ? cap.subtract(newConsumed) : null;
        return SubsidyEnrolmentResponse.of(e, cap, newConsumed, remaining);
    }

    /**
     * Cap check for eligibility time: is there subsidy headroom for {@code requestedAmount}
     * (or any headroom when amount is null) on an ACTIVE enrolment for this member?
     */
    @Transactional(readOnly = true)
    public SubsidyCapResult checkCap(UUID tenantId, String memberCpid, BigDecimal requestedAmount) {
        List<SubsidyEnrolmentEntity> active =
                enrolmentRepository.findByTenantIdAndMemberCpidAndStatus(tenantId, memberCpid, "ACTIVE");
        if (active.isEmpty()) {
            return new SubsidyCapResult(false, null, null, null, null, "NO_ACTIVE_SUBSIDY");
        }
        int year = LocalDate.now().getYear();
        // Pick the enrolment with the most remaining headroom.
        SubsidyCapResult best = null;
        for (SubsidyEnrolmentEntity e : active) {
            SubsidyProgramEntity program = programRepository.findByIdAndTenantId(e.getSubsidyProgramId(), tenantId)
                    .orElse(null);
            if (program == null) continue;
            BigDecimal cap = effectiveCap(e, program);
            BigDecimal consumed = balanceRepository.findByEnrolmentIdAndPeriodYear(e.getId(), year)
                    .map(SubsidyBalanceEntity::getConsumedAmount).orElse(BigDecimal.ZERO);
            BigDecimal remaining = cap != null ? cap.subtract(consumed) : null;
            boolean hasHeadroom = remaining == null
                    || (requestedAmount == null ? remaining.signum() > 0
                                                : remaining.compareTo(requestedAmount) >= 0);
            SubsidyCapResult candidate = new SubsidyCapResult(
                    hasHeadroom, e.getId(), cap, consumed, remaining,
                    hasHeadroom ? "SUBSIDY_HEADROOM_AVAILABLE" : "SUBSIDY_CAP_EXHAUSTED");
            if (candidate.eligible()) {
                return candidate;  // first eligible wins
            }
            if (best == null) best = candidate;
        }
        return best;
    }

    private SubsidyEnrolmentResponse toResponse(UUID tenantId, SubsidyEnrolmentEntity e) {
        SubsidyProgramEntity program = programRepository.findByIdAndTenantId(e.getSubsidyProgramId(), tenantId)
                .orElse(null);
        BigDecimal cap = program != null ? effectiveCap(e, program) : e.getAnnualCapOverride();
        int year = LocalDate.now().getYear();
        BigDecimal consumed = balanceRepository.findByEnrolmentIdAndPeriodYear(e.getId(), year)
                .map(SubsidyBalanceEntity::getConsumedAmount).orElse(BigDecimal.ZERO);
        BigDecimal remaining = cap != null ? cap.subtract(consumed) : null;
        return SubsidyEnrolmentResponse.of(e, cap, consumed, remaining);
    }

    private SubsidyBalanceEntity newBalance(UUID tenantId, SubsidyEnrolmentEntity e, BigDecimal cap, int year) {
        SubsidyBalanceEntity b = new SubsidyBalanceEntity();
        b.setTenantId(tenantId);
        b.setEnrolmentId(e.getId());
        b.setPeriodYear(year);
        b.setCapAmount(cap);
        b.setConsumedAmount(BigDecimal.ZERO);
        b.setCurrency(e.getCurrency());
        return b;
    }

    private static BigDecimal effectiveCap(SubsidyEnrolmentEntity e, SubsidyProgramEntity program) {
        return e.getAnnualCapOverride() != null ? e.getAnnualCapOverride() : program.getAnnualCap();
    }

    private SubsidyProgramEntity resolveProgram(UUID tenantId, UUID programId, String programCode) {
        if (programId != null) {
            return programRepository.findByIdAndTenantId(programId, tenantId)
                    .orElseThrow(() -> new IllegalArgumentException("Subsidy programme not found: " + programId));
        }
        if (programCode != null && !programCode.isBlank()) {
            return programRepository.findByTenantIdAndProgramCode(tenantId, programCode.trim())
                    .orElseThrow(() -> new IllegalArgumentException("Subsidy programme not found: " + programCode));
        }
        throw new IllegalArgumentException("subsidy_program_id or program_code is required");
    }

    /** Result of a subsidy annual-cap check at eligibility time. */
    public record SubsidyCapResult(
            boolean eligible,
            UUID enrolmentId,
            BigDecimal cap,
            BigDecimal consumed,
            BigDecimal remaining,
            String reason
    ) {}
}
