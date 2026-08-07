package zw.gov.mohcc.impilo.tuso.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.persistence.entity.RegulatoryFeeScheduleEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.RegulatoryFeeScheduleRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import zw.gov.mohcc.impilo.shared.auth.ActorTypeGuard;

/**
 * Governance + resolution for the regulatory fee schedule (SI 78 of 2017).
 *
 * <p>Resolution NEVER falls back to a pending row or a code default for money —
 * a fee is CONFIGURED only when an ACTIVE schedule row carries an amount.
 * Otherwise the outcome is NOT_CONFIGURED, recorded honestly rather than
 * silently skipped. Amounts are supplied through governed approval, never
 * invented in code.</p>
 */
@Service
public class RegulatoryFeeScheduleService {

    private static final Logger log = LoggerFactory.getLogger(RegulatoryFeeScheduleService.class);

    /** Only these actor types may author/approve fee schedule rows. */
    /**
     * Administering the regulatory fee schedule.
     *
     * <p>Was {@code {HPA_REGISTRAR, HPA_ADMIN, SYSTEM_ADMIN}}, none of them emittable, and skipped
     * when the actor type was absent. Now fails closed.</p>
     */
    static final ActorTypeGuard.Duty ADMINISTER_FEE_SCHEDULE = new ActorTypeGuard.Duty(
            "administering the regulatory fee schedule",
            ActorTypeGuard.BACK_OFFICE_WRITERS,
            Set.of("HPA_REGISTRAR", "HPA_ADMIN", "SYSTEM_ADMIN"));

    public enum Kind { NOT_REQUIRED, CONFIGURED, NOT_CONFIGURED }

    /** The outcome of resolving a fee for an application. */
    public record FeeResolution(Kind kind, UUID feeId, String feeCode, BigDecimal amount, String currency,
                                String sourceInstrument) {
        public static FeeResolution notRequired() {
            return new FeeResolution(Kind.NOT_REQUIRED, null, null, null, null, null);
        }
        public static FeeResolution notConfigured() {
            return new FeeResolution(Kind.NOT_CONFIGURED, null, null, null, null, null);
        }
    }

    private final RegulatoryFeeScheduleRepository repository;

    public RegulatoryFeeScheduleService(RegulatoryFeeScheduleRepository repository) {
        this.repository = repository;
    }

    /**
     * Resolve the fee for a fee-required application of the given catalogue type
     * and (optional) HPA class. CONFIGURED only when an ACTIVE row carries an
     * amount; otherwise NOT_CONFIGURED.
     */
    @Transactional(readOnly = true)
    public FeeResolution resolve(String catalogueCode, String classCode) {
        if (catalogueCode == null || catalogueCode.isBlank()) {
            return FeeResolution.notConfigured();
        }
        List<RegulatoryFeeScheduleEntity> candidates = repository.findActiveForResolution(catalogueCode, classCode);
        for (RegulatoryFeeScheduleEntity row : candidates) {
            if (row.getAmount() != null) {
                return new FeeResolution(Kind.CONFIGURED, row.getFeeId(), row.getFeeCode(),
                        row.getAmount(), row.getCurrency() != null ? row.getCurrency() : "USD",
                        row.getSourceInstrument());
            }
        }
        return FeeResolution.notConfigured();
    }

    @Transactional(readOnly = true)
    public RegulatoryFeeScheduleEntity require(UUID feeId) {
        return repository.findById(feeId)
                .orElseThrow(() -> new IllegalArgumentException("Fee schedule row not found: " + feeId));
    }

    @Transactional(readOnly = true)
    public List<RegulatoryFeeScheduleEntity> listBySchedule(String scheduleCode) {
        return repository.findByScheduleCodeOrderByFeeCodeAscVersionDesc(
                scheduleCode == null || scheduleCode.isBlank() ? "SI_78_2017" : scheduleCode);
    }

    /** Author a new (or amended) fee schedule row — starts PENDING_REGULATOR_APPROVAL. */
    @Transactional
    public RegulatoryFeeScheduleEntity create(String scheduleCode, String feeCode, String catalogueCode,
                                              String classCode, BigDecimal amount, String currency,
                                              String sourceInstrument, String sourceSection, Integer version,
                                              String notes) {
        assertRole("author a fee schedule row");
        RegulatoryFeeScheduleEntity e = new RegulatoryFeeScheduleEntity();
        e.setScheduleCode(scheduleCode != null && !scheduleCode.isBlank() ? scheduleCode : "SI_78_2017");
        e.setFeeCode(feeCode);
        e.setAppliesToCatalogueCode(catalogueCode);
        e.setAppliesToClassCode(blank(classCode) ? null : classCode);
        e.setAmount(amount);
        e.setCurrency(currency);
        e.setSourceInstrument(sourceInstrument != null && !sourceInstrument.isBlank() ? sourceInstrument : "SI 78 of 2017");
        e.setSourceSection(sourceSection);
        e.setVersion(version != null ? version : nextVersion(e.getScheduleCode(), feeCode, catalogueCode));
        e.setStatus("PENDING_REGULATOR_APPROVAL");
        e.setNotes(notes);
        return repository.save(e);
    }

    /** Approve a pending row → ACTIVE. Amounts are recorded here through governance, never invented in code. */
    @Transactional
    public RegulatoryFeeScheduleEntity approve(UUID feeId, LocalDate effectiveFrom) {
        TrustContext ctx = assertRole("approve a fee schedule row");
        RegulatoryFeeScheduleEntity e = require(feeId);
        e.setStatus("ACTIVE");
        e.setApprovedBy(ctx.actorId());
        e.setApprovedAt(Instant.now());
        e.setEffectiveFrom(effectiveFrom != null ? effectiveFrom : LocalDate.now());
        log.info("Regulatory fee schedule {} ({}/{}) approved ACTIVE by {}",
                feeId, e.getFeeCode(), e.getAppliesToCatalogueCode(), ctx.actorId());
        return repository.save(e);
    }

    private int nextVersion(String scheduleCode, String feeCode, String catalogueCode) {
        return repository.findByAppliesToCatalogueCodeOrderByVersionDesc(catalogueCode).stream()
                .filter(r -> r.getScheduleCode().equals(scheduleCode) && r.getFeeCode().equals(feeCode))
                .mapToInt(RegulatoryFeeScheduleEntity::getVersion).max().orElse(0) + 1;
    }

    private TrustContext assertRole(String action) {
        TrustContext ctx = TrustContextHolder.require();
        ActorTypeGuard.require(ctx, ADMINISTER_FEE_SCHEDULE);
        return ctx;
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }
}
