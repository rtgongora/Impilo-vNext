package zw.gov.mohcc.impilo.coverage.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.coverage.domain.BenefitAccumulatorEntity;
import zw.gov.mohcc.impilo.coverage.domain.BenefitDefinitionEntity;
import zw.gov.mohcc.impilo.coverage.domain.BenefitLimitEntity;
import zw.gov.mohcc.impilo.coverage.domain.BenefitMovementEntity;
import zw.gov.mohcc.impilo.coverage.domain.CoveragePlanEntity;
import zw.gov.mohcc.impilo.coverage.domain.MemberCoverageEntity;
import zw.gov.mohcc.impilo.coverage.repository.BenefitAccumulatorRepository;
import zw.gov.mohcc.impilo.coverage.repository.BenefitDefinitionRepository;
import zw.gov.mohcc.impilo.coverage.repository.BenefitLimitRepository;
import zw.gov.mohcc.impilo.coverage.repository.BenefitMovementRepository;
import zw.gov.mohcc.impilo.coverage.repository.CoveragePlanRepository;
import zw.gov.mohcc.impilo.coverage.repository.MemberCoverageRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Reservation-aware benefit accumulator engine (spec §13). reserve → consume → release,
 * each atomic and idempotent-by-reference (same H1/H2 guarantees as the subsidy ledger):
 * <ul>
 *   <li><b>H1:</b> conditional UPDATEs serialise the check-and-apply so two concurrent
 *       reservations cannot both cross the remaining-benefit boundary.</li>
 *   <li><b>H2:</b> a unique {@code (accumulator_id, reference)} ledger makes replays return
 *       the prior snapshot instead of double-applying.</li>
 * </ul>
 */
@Service
public class BenefitAccumulatorService {

    private static final Logger log = LoggerFactory.getLogger(BenefitAccumulatorService.class);

    private final BenefitDefinitionRepository benefitRepository;
    private final BenefitLimitRepository limitRepository;
    private final BenefitAccumulatorRepository accumulatorRepository;
    private final BenefitMovementRepository movementRepository;
    private final MemberCoverageRepository memberRepository;
    private final CoveragePlanRepository planRepository;
    private final CoverageEventService eventService;

    public BenefitAccumulatorService(BenefitDefinitionRepository benefitRepository,
                                     BenefitLimitRepository limitRepository,
                                     BenefitAccumulatorRepository accumulatorRepository,
                                     BenefitMovementRepository movementRepository,
                                     MemberCoverageRepository memberRepository,
                                     CoveragePlanRepository planRepository,
                                     CoverageEventService eventService) {
        this.benefitRepository = benefitRepository;
        this.limitRepository = limitRepository;
        this.accumulatorRepository = accumulatorRepository;
        this.movementRepository = movementRepository;
        this.memberRepository = memberRepository;
        this.planRepository = planRepository;
        this.eventService = eventService;
    }

    public enum MovementKind { RESERVE, CONSUME, RELEASE }

    public record MovementResult(UUID accumulatorId, String benefitCode, BigDecimal limit,
                                 BigDecimal used, BigDecimal reserved, BigDecimal remaining,
                                 boolean applied, String reason) {}

    /** Snapshot of a member's benefit position for eligibility/wallet display. */
    public record BenefitPosition(String benefitCode, String category, BigDecimal coveragePercent,
                                  BigDecimal copay, BigDecimal limit, BigDecimal used,
                                  BigDecimal reserved, BigDecimal remaining,
                                  boolean requiresAuthorisation, boolean requiresReferral,
                                  String networkRequirement) {}

    @Transactional(readOnly = true)
    public List<BenefitPosition> positionsForMember(UUID tenantId, UUID memberId, UUID planVersionId) {
        String period = String.valueOf(LocalDate.now().getYear());
        return benefitRepository.findByTenantIdAndPlanVersionId(tenantId, planVersionId).stream()
                .map(b -> {
                    BigDecimal limit = monetaryLimit(b.getId());
                    Optional<BenefitAccumulatorEntity> acc =
                            accumulatorRepository.findByMemberIdAndBenefitCodeAndPeriodKey(memberId, b.getBenefitCode(), period);
                    BigDecimal used = acc.map(BenefitAccumulatorEntity::getUsedAmount).orElse(BigDecimal.ZERO);
                    BigDecimal reserved = acc.map(BenefitAccumulatorEntity::getReservedAmount).orElse(BigDecimal.ZERO);
                    BigDecimal remaining = limit != null ? limit.subtract(used).subtract(reserved) : null;
                    return new BenefitPosition(b.getBenefitCode(), b.getCategory(), b.getCoveragePercent(),
                            b.getCopayAmount(), limit, used, reserved, remaining,
                            b.isRequiresAuthorisation(), b.isRequiresReferral(), b.getNetworkRequirement());
                })
                .toList();
    }

    /** Reserve benefit for a member/benefit (e.g. on authorisation approval). Idempotent by reference. */
    @Transactional
    public MovementResult reserve(UUID tenantId, String podId, UUID memberId, String benefitCode,
                                  BigDecimal amount, String reference, UUID correlationId) {
        return move(tenantId, podId, memberId, benefitCode, amount, reference, MovementKind.RESERVE, correlationId);
    }

    /** Consume benefit (claim adjudication). Converts a prior reservation if present, else direct. */
    @Transactional
    public MovementResult consume(UUID tenantId, String podId, UUID memberId, String benefitCode,
                                  BigDecimal amount, String reference, UUID correlationId) {
        return move(tenantId, podId, memberId, benefitCode, amount, reference, MovementKind.CONSUME, correlationId);
    }

    /** Release a prior reservation (authorisation expiry/denial). */
    @Transactional
    public MovementResult release(UUID tenantId, String podId, UUID memberId, String benefitCode,
                                  BigDecimal amount, String reference, UUID correlationId) {
        return move(tenantId, podId, memberId, benefitCode, amount, reference, MovementKind.RELEASE, correlationId);
    }

    private MovementResult move(UUID tenantId, String podId, UUID memberId, String benefitCode,
                                BigDecimal amount, String reference, MovementKind kind, UUID correlationId) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("reference is required for an idempotent movement");
        }
        String ref = reference.trim();
        MemberCoverageEntity member = memberRepository.findByIdAndTenantId(memberId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Membership not found: " + memberId));
        BenefitAccumulatorEntity acc = ensureAccumulator(tenantId, member, benefitCode);

        // H2 idempotent replay.
        Optional<BenefitMovementEntity> prior = movementRepository.findByAccumulatorIdAndReference(acc.getId(), ref);
        if (prior.isPresent()) {
            BenefitAccumulatorEntity fresh = accumulatorRepository.findById(acc.getId()).orElse(acc);
            log.info("Benefit movement {} already applied on {}; replaying", ref, acc.getId());
            return snapshot(fresh, true, "REPLAYED");
        }

        int applied = switch (kind) {
            case RESERVE -> accumulatorRepository.applyReserve(acc.getId(), amount);
            case RELEASE -> accumulatorRepository.applyRelease(acc.getId(), amount);
            case CONSUME -> {
                // Prefer converting an existing reservation; fall back to a direct consume.
                int c = accumulatorRepository.applyConsumeReserved(acc.getId(), amount);
                yield c == 1 ? c : accumulatorRepository.applyConsumeDirect(acc.getId(), amount);
            }
        };
        if (applied == 0) {
            BenefitAccumulatorEntity fresh = accumulatorRepository.findById(acc.getId()).orElse(acc);
            return snapshot(fresh, false,
                    kind == MovementKind.RELEASE ? "INSUFFICIENT_RESERVED" : "BENEFIT_LIMIT_EXCEEDED");
        }

        BenefitAccumulatorEntity fresh = accumulatorRepository.findById(acc.getId())
                .orElseThrow(() -> new IllegalStateException("Accumulator vanished after movement"));
        BenefitMovementEntity mv = BenefitMovementEntity.create(tenantId, acc.getId(), kind.name(),
                amount, ref, fresh.getUsedAmount(), fresh.getReservedAmount());
        try {
            movementRepository.saveAndFlush(mv);
        } catch (DataIntegrityViolationException dup) {
            // A concurrent movement with the same reference won; reverse our apply and replay theirs.
            reverse(acc.getId(), amount, kind);
            BenefitAccumulatorEntity after = accumulatorRepository.findById(acc.getId()).orElse(fresh);
            return snapshot(after, true, "REPLAYED");
        }

        eventService.emitBenefit(kind == MovementKind.RESERVE ? "reserved"
                        : kind == MovementKind.CONSUME ? "consumed" : "released",
                acc.getId(), correlationId, tenantId, podId, movementPayload(fresh, kind, amount, ref));
        return snapshot(fresh, true, "APPLIED");
    }

    private void reverse(UUID accId, BigDecimal amount, MovementKind kind) {
        switch (kind) {
            case RESERVE -> accumulatorRepository.applyRelease(accId, amount);
            case RELEASE -> accumulatorRepository.applyReserve(accId, amount);
            case CONSUME -> accumulatorRepository.applyRelease(accId, amount); // best-effort
        }
    }

    private BenefitAccumulatorEntity ensureAccumulator(UUID tenantId, MemberCoverageEntity member, String benefitCode) {
        String period = String.valueOf(LocalDate.now().getYear());
        var existing = accumulatorRepository.findByMemberIdAndBenefitCodeAndPeriodKey(member.getId(), benefitCode, period);
        if (existing.isPresent()) return existing.get();

        UUID planVersionId = resolvePlanVersion(member);
        if (planVersionId == null) {
            throw new IllegalArgumentException("Member's plan has no plan version — cannot accumulate benefits");
        }
        BenefitDefinitionEntity def = benefitRepository
                .findByTenantIdAndPlanVersionIdAndBenefitCode(tenantId, planVersionId, benefitCode)
                .orElseThrow(() -> new IllegalArgumentException("Benefit not defined on plan version: " + benefitCode));
        BigDecimal limit = monetaryLimit(def.getId());
        BenefitAccumulatorEntity acc = BenefitAccumulatorEntity.create(tenantId, member.getId(), planVersionId,
                benefitCode, period, limit, def.getCurrency());
        try {
            return accumulatorRepository.saveAndFlush(acc);
        } catch (DataIntegrityViolationException race) {
            return accumulatorRepository.findByMemberIdAndBenefitCodeAndPeriodKey(member.getId(), benefitCode, period)
                    .orElseThrow(() -> race);
        }
    }

    private UUID resolvePlanVersion(MemberCoverageEntity member) {
        CoveragePlanEntity plan = planRepository.findById(member.getPlanId()).orElse(null);
        return plan != null ? plan.getPlanVersionId() : null;
    }

    private BigDecimal monetaryLimit(UUID benefitId) {
        return limitRepository.findByBenefitId(benefitId).stream()
                .filter(l -> "MONETARY".equals(l.getLimitType()))
                .map(BenefitLimitEntity::getLimitValue)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private MovementResult snapshot(BenefitAccumulatorEntity a, boolean applied, String reason) {
        return new MovementResult(a.getId(), a.getBenefitCode(), a.getLimitAmount(), a.getUsedAmount(),
                a.getReservedAmount(), a.remaining(), applied, reason);
    }

    private Map<String, Object> movementPayload(BenefitAccumulatorEntity a, MovementKind kind,
                                                BigDecimal amount, String reference) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("accumulator_id", a.getId().toString());
        m.put("member_id", a.getMemberId().toString());
        m.put("benefit_code", a.getBenefitCode());
        m.put("movement", kind.name());
        m.put("amount", amount);
        m.put("reference", reference);
        m.put("used_after", a.getUsedAmount());
        m.put("reserved_after", a.getReservedAmount());
        return m;
    }
}
