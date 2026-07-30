package zw.gov.mohcc.impilo.pct.core.clinical;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.persistence.entity.ContraceptiveAdministrationEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.ContraceptiveEpisodeEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.ContraceptiveAdministrationRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.ContraceptiveEpisodeRepository;
import zw.gov.mohcc.impilo.reproductive.confidentiality.ConfidentialityCategory;
import zw.gov.mohcc.impilo.reproductive.contraception.ContraceptiveCoverageCalculator;
import zw.gov.mohcc.impilo.reproductive.contraception.ContraceptiveMethod;
import zw.gov.mohcc.impilo.reproductive.contraception.ContraceptiveMethodCatalog;
import zw.gov.mohcc.impilo.reproductive.contraception.CoverageStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Contraceptive method use as a record: what she is on, since when, and whether it is still working.
 *
 * <p><b>Coverage is computed, never stored.</b> A stored "covered" flag is only true until the day
 * it stops being true, and nothing updates it — a woman lapses precisely by nobody touching her
 * record, so the flag would report her protected forever. The arithmetic lives in
 * {@link ContraceptiveCoverageCalculator} and runs per read.
 *
 * <p><b>Absence is never coverage, and never its opposite either.</b> A method with no profile, or a
 * scheduled method with no recorded dose, resolves to {@link CoverageStatus#UNKNOWN}. Condoms and
 * sterilisation resolve to {@link CoverageStatus#NOT_APPLICABLE} rather than COVERED, because a
 * condom protects when it is used and the record cannot see that.
 *
 * <p><b>Contraceptive use is SRH content.</b> Every episode is stamped with the
 * {@code SEXUAL_REPRODUCTIVE_HEALTH} category on the way in and every read passes through
 * {@link ConfidentialRecordGuard} on the way out — the decision table in
 * {@code docs/clinical-governance/rmnp/srh-confidentiality-stamping.md} §3 names this record, and
 * until now the table named it while the code did not implement it.
 */
@Service
public class ContraceptiveEpisodeService {

    private static final Logger log = LoggerFactory.getLogger(ContraceptiveEpisodeService.class);

    private final ContraceptiveEpisodeRepository episodes;
    private final ContraceptiveAdministrationRepository administrations;
    private final ContraceptiveMethodCatalog catalog;
    private final ConfidentialCarePolicyProvider confidentiality;
    private final ConfidentialRecordGuard confidentialRecords;

    public ContraceptiveEpisodeService(ContraceptiveEpisodeRepository episodes,
                                       ContraceptiveAdministrationRepository administrations,
                                       ConfidentialCarePolicyProvider confidentiality,
                                       ConfidentialRecordGuard confidentialRecords) {
        this.episodes = episodes;
        this.administrations = administrations;
        this.catalog = ContraceptiveMethodCatalog.engineeringSeed();
        this.confidentiality = confidentiality;
        this.confidentialRecords = confidentialRecords;
    }

    /** Raised when a method she is already on is started again. Carries the existing episode. */
    public static class DuplicateContraceptiveEpisodeException extends RuntimeException {
        private final UUID existingEpisodeId;

        public DuplicateContraceptiveEpisodeException(UUID existingEpisodeId, String message) {
            super(message);
            this.existingEpisodeId = existingEpisodeId;
        }

        public UUID getExistingEpisodeId() {
            return existingEpisodeId;
        }
    }

    /**
     * What she is using, with coverage resolved as at a date.
     *
     * @param status              coverage on {@code asOf}; UNKNOWN where it cannot be computed
     * @param daysRemaining       protection left in days, negative once lapsed, null when unknowable
     * @param lastAdministeredOn  the most recent dose or insertion, null if none is recorded
     * @param coverageUnknownWhy  plain reason the status is UNKNOWN, null otherwise — a status with
     *                            no explanation gets rendered as a shrug and ignored
     */
    public record MethodCoverage(
            ContraceptiveEpisodeEntity episode,
            CoverageStatus status,
            Integer daysRemaining,
            LocalDate lastAdministeredOn,
            LocalDate nextDueOn,
            String coverageUnknownWhy,
            String contentVersion) {
    }

    @Transactional(readOnly = true)
    public List<ContraceptiveEpisodeEntity> current(UUID tenantId, String subjectCpid) {
        if (tenantId == null || subjectCpid == null || subjectCpid.isBlank()) {
            return List.of();
        }
        return guarded(episodes.findCurrent(tenantId, subjectCpid));
    }

    @Transactional(readOnly = true)
    public List<ContraceptiveEpisodeEntity> history(UUID tenantId, String subjectCpid) {
        if (tenantId == null || subjectCpid == null || subjectCpid.isBlank()) {
            return List.of();
        }
        return guarded(episodes.findBySubject(tenantId, subjectCpid));
    }

    /**
     * Coverage for every method she is currently using, as at a date.
     *
     * <p><b>Filters before it computes.</b> This delegates to the guarded {@link #current} rather
     * than to the repository, so a withheld episode never reaches the arithmetic. Computing first
     * and filtering the summaries afterwards would leak the withheld episode's existence — a
     * coverage list with a gap in it, or a "she is protected until March" derived from a method the
     * requester may not know about, says as much as returning the row.
     */
    @Transactional(readOnly = true)
    public List<MethodCoverage> coverage(UUID tenantId, String subjectCpid, LocalDate asOf) {
        LocalDate on = asOf == null ? LocalDate.now() : asOf;
        List<MethodCoverage> out = new ArrayList<>();
        for (ContraceptiveEpisodeEntity episode : current(tenantId, subjectCpid)) {
            out.add(coverageFor(episode, on));
        }
        return List.copyOf(out);
    }

    private List<ContraceptiveEpisodeEntity> guarded(List<ContraceptiveEpisodeEntity> rows) {
        return confidentialRecords.filter(rows,
                ContraceptiveEpisodeEntity::getSensitivityClass,
                ContraceptiveEpisodeEntity::getConfidentialityCategory);
    }

    MethodCoverage coverageFor(ContraceptiveEpisodeEntity episode, LocalDate on) {
        ContraceptiveMethod method = parseMethod(episode.getMethodCode());
        ContraceptiveMethodCatalog.ContraceptiveMethodProfile profile =
                method == null ? null : catalog.profile(method);

        List<ContraceptiveAdministrationEntity> acts =
                administrations.findForEpisode(episode.getContraceptiveEpisodeId());
        LocalDate lastDose = acts.stream()
                .filter(a -> !ContraceptiveAdministrationEntity.TYPE_REMOVAL
                        .equals(a.getAdministrationType()))
                .map(ContraceptiveAdministrationEntity::getAdministeredOn)
                .filter(java.util.Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElse(null);
        LocalDate nextDue = acts.stream()
                .map(ContraceptiveAdministrationEntity::getNextDueOn)
                .filter(java.util.Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElse(null);

        CoverageStatus status = ContraceptiveCoverageCalculator.coverageOn(
                profile, episode.getStartedOn(), lastDose, on);
        Integer remaining = ContraceptiveCoverageCalculator.daysOfProtectionRemaining(
                profile, episode.getStartedOn(), lastDose, on);

        return new MethodCoverage(episode, status, remaining, lastDose, nextDue,
                unknownReason(status, method, profile, lastDose),
                ContraceptiveCoverageCalculator.CONTENT_VERSION);
    }

    /**
     * Why coverage could not be computed, in words a clinician can act on.
     *
     * <p>UNKNOWN with no reason is the same defect as an empty guidance panel: it renders as a shrug
     * and gets ignored, and the one case that matters — a woman months past her injection — looks
     * exactly like a data-entry gap.
     */
    private static String unknownReason(CoverageStatus status, ContraceptiveMethod method,
                                        ContraceptiveMethodCatalog.ContraceptiveMethodProfile profile,
                                        LocalDate lastDose) {
        if (status != CoverageStatus.UNKNOWN) {
            return null;
        }
        if (method == null) {
            return "The method recorded is not one this version recognises, so its protection "
                    + "cannot be worked out. Do not read this as protected.";
        }
        if (profile == null) {
            return "There is no duration or schedule recorded for this method, so how long it "
                    + "protects her is not something this record can say.";
        }
        if (profile.scheduled() && lastDose == null) {
            return "No dose has been recorded, so there is no date to count from. She may be due, "
                    + "or overdue — ask when she last had it.";
        }
        return "Protection could not be computed from what is recorded.";
    }

    /**
     * Start a method.
     *
     * <p>A replayed offline packet returns the episode it already created rather than making a
     * second one, and starting a method she is already on raises
     * {@link DuplicateContraceptiveEpisodeException} carrying the existing id — the caller answers
     * 409 with a reconciliation task. Neither may surface as a 500: a 500 here loses an offline
     * record of a method that was actually given.
     */
    @Transactional
    public ContraceptiveEpisodeEntity start(ContraceptiveEpisodeEntity episode) {
        if (episode.getClientOfflineId() != null && !episode.getClientOfflineId().isBlank()) {
            Optional<ContraceptiveEpisodeEntity> replayed = episodes
                    .findByTenantIdAndClientOfflineId(episode.getTenantId(),
                            episode.getClientOfflineId());
            if (replayed.isPresent()) {
                log.info("Contraceptive episode {} replayed from offline id {}; returning the "
                                + "existing record", replayed.get().getContraceptiveEpisodeId(),
                        episode.getClientOfflineId());
                return replayed.get();
            }
        }

        episodes.findCurrentForMethod(episode.getTenantId(), episode.getSubjectCpid(),
                episode.getMethodCode()).ifPresent(existing -> {
            throw new DuplicateContraceptiveEpisodeException(
                    existing.getContraceptiveEpisodeId(),
                    "She is already recorded as using " + episode.getMethodCode()
                            + " since " + existing.getStartedOn()
                            + ". Reconcile with that record rather than opening a second one.");
        });

        if (episode.getContraceptiveEpisodeId() == null) {
            episode.setContraceptiveEpisodeId(UUID.randomUUID());
        }
        if (episode.getStatus() == null) {
            episode.setStatus(ContraceptiveEpisodeEntity.STATUS_ACTIVE);
        }
        if (episode.getRecordedAt() == null) {
            episode.setRecordedAt(OffsetDateTime.now());
        }
        if (episode.getEligibilityOverridden() == null) {
            episode.setEligibilityOverridden(Boolean.FALSE);
        }
        if (episode.getDeferredInsertionOwed() == null) {
            episode.setDeferredInsertionOwed(Boolean.FALSE);
        }
        applyExpectedEnd(episode);
        applyStamp(episode);
        return episodes.save(episode);
    }

    /**
     * Stamp the SRH category, and the protection class only once the governance flip enables it.
     *
     * <p>The age is taken at {@code started_on}, not today: a woman who was 17 when the method was
     * started and is 19 now keeps the protection the record was created under.
     *
     * <p>Deliberately applied on {@link #start} only. {@link #discontinue} does not re-decide the
     * stamp, because the stamp is a fact about the act date under a named policy version — re-running
     * the decision months later would silently rewrite that history under a newer policy, and the
     * whole point of carrying {@code confidentiality_policy_version} is that a past stamp stays
     * re-explainable.
     */
    private void applyStamp(ContraceptiveEpisodeEntity episode) {
        var category = ConfidentialityCategory.SEXUAL_REPRODUCTIVE_HEALTH;
        LocalDate actDate = episode.getStartedOn();
        var policy = confidentiality.policyAsOf(episode.getTenantId(), actDate);
        var subject = confidentiality.subjectAt(
                episode.getTenantId(), episode.getSubjectCpid(), category, actDate);
        var stamp = ConfidentialityStamper.gated(
                ConfidentialityStamper.ageGated(category, subject, actDate, policy),
                confidentiality.classStampingEnabled());
        episode.setSensitivityClass(stamp.sensitivityClass());
        episode.setConfidentialityCategory(stamp.category());
        episode.setConfidentialityBasis(stamp.basis());
        episode.setConfidentialityPolicyVersion(stamp.policyVersion());
    }

    /** Stamp the removal-due date from the method's own duration, if it has one. */
    private void applyExpectedEnd(ContraceptiveEpisodeEntity episode) {
        if (episode.getExpectedEndOn() != null || episode.getStartedOn() == null) {
            return;
        }
        ContraceptiveMethod method = parseMethod(episode.getMethodCode());
        if (method == null) {
            return;
        }
        episode.setExpectedEndOn(ContraceptiveCoverageCalculator.expiresOn(
                catalog.profile(method), episode.getStartedOn()));
    }

    /**
     * Stop a method.
     *
     * <p>The reason is required by the service and by a database constraint. Why she stopped is the
     * most useful single fact in family planning — a woman who stopped because the bleeding was
     * unacceptable needs a different conversation from one who stopped because she wants a
     * pregnancy — and "no longer on it" with no reason throws it away.
     */
    @Transactional
    public ContraceptiveEpisodeEntity discontinue(UUID tenantId, UUID episodeId, LocalDate endedOn,
                                                  String reason, String detail, String actor) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "A method cannot be recorded as stopped without a reason.");
        }
        ContraceptiveEpisodeEntity episode = episodes.findById(episodeId)
                .filter(e -> e.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException(
                        "No contraceptive episode " + episodeId));

        episode.setStatus(ContraceptiveEpisodeEntity.STATUS_DISCONTINUED);
        episode.setEndedOn(endedOn == null ? LocalDate.now() : endedOn);
        episode.setDiscontinuationReason(reason);
        episode.setDiscontinuationDetail(detail);
        episode.setUpdatedBy(actor);
        episode.setUpdatedAt(OffsetDateTime.now());
        return episodes.save(episode);
    }

    /**
     * Record an insertion, injection, resupply or removal.
     *
     * <p>A removal that got everything out ends the episode. One that did not, does not — and the
     * distinction is enforced below the service as well, because a partial removal recorded as
     * complete leaves a woman carrying a device fragment while her record says she has no method.
     */
    @Transactional
    public ContraceptiveAdministrationEntity record(ContraceptiveAdministrationEntity act) {
        if (act.getClientOfflineId() != null && !act.getClientOfflineId().isBlank()) {
            Optional<ContraceptiveAdministrationEntity> replayed = administrations
                    .findByTenantIdAndClientOfflineId(act.getTenantId(), act.getClientOfflineId());
            if (replayed.isPresent()) {
                return replayed.get();
            }
        }
        if (act.getAdministrationId() == null) {
            act.setAdministrationId(UUID.randomUUID());
        }
        if (act.getRecordedAt() == null) {
            act.setRecordedAt(OffsetDateTime.now());
        }
        if (act.getRetainedFragment() == null) {
            act.setRetainedFragment(Boolean.FALSE);
        }
        ContraceptiveAdministrationEntity saved = administrations.save(act);

        if (saved.endsProtection()) {
            episodes.findById(saved.getContraceptiveEpisodeId()).ifPresent(episode -> {
                episode.setStatus(ContraceptiveEpisodeEntity.STATUS_COMPLETED);
                episode.setEndedOn(saved.getAdministeredOn());
                episode.setUpdatedAt(OffsetDateTime.now());
                episodes.save(episode);
            });
        }
        return saved;
    }

    /**
     * Women carrying a retained device fragment. A worklist, not a statistic.
     *
     * <p>Guarded through the owning episode, because the administration row carries no stamp of its
     * own. A tenant-wide worklist is the most disclosive read in this service: it names women rather
     * than answering a question about one, so a requester with no SRH grant must not receive rows
     * whose episode is withheld from her. An administration whose episode cannot be resolved is
     * treated as unstamped and kept — a missing episode is a data-integrity problem, and dropping the
     * row would hide a woman carrying a device fragment.
     */
    @Transactional(readOnly = true)
    public List<ContraceptiveAdministrationEntity> retainedFragments(UUID tenantId) {
        List<ContraceptiveAdministrationEntity> acts = administrations.findRetainedFragments(tenantId);
        if (acts.isEmpty()) {
            return List.of();
        }
        Map<UUID, ContraceptiveEpisodeEntity> owning = new HashMap<>();
        for (ContraceptiveEpisodeEntity e : episodes.findAllById(acts.stream()
                .map(ContraceptiveAdministrationEntity::getContraceptiveEpisodeId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList())) {
            owning.put(e.getContraceptiveEpisodeId(), e);
        }
        return confidentialRecords.filter(acts,
                a -> stampOf(owning, a, ContraceptiveEpisodeEntity::getSensitivityClass),
                a -> stampOf(owning, a, ContraceptiveEpisodeEntity::getConfidentialityCategory));
    }

    private static String stampOf(Map<UUID, ContraceptiveEpisodeEntity> owning,
                                  ContraceptiveAdministrationEntity act,
                                  java.util.function.Function<ContraceptiveEpisodeEntity, String> of) {
        ContraceptiveEpisodeEntity episode = owning.get(act.getContraceptiveEpisodeId());
        return episode == null ? null : of.apply(episode);
    }

    /** Insertions deferred because pregnancy could not be excluded, and still owed. */
    @Transactional(readOnly = true)
    public List<ContraceptiveEpisodeEntity> deferredInsertionsOwed(UUID tenantId) {
        return guarded(episodes.findDeferredInsertionsOwed(tenantId));
    }

    private static ContraceptiveMethod parseMethod(String code) {
        if (code == null) {
            return null;
        }
        try {
            return ContraceptiveMethod.valueOf(code);
        } catch (IllegalArgumentException e) {
            // An unrecognised method is reported as unknown coverage, never treated as no method.
            return null;
        }
    }
}
