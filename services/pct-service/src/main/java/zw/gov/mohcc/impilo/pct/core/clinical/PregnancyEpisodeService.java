package zw.gov.mohcc.impilo.pct.core.clinical;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.reproductive.confidentiality.ConfidentialityCategory;
import zw.gov.mohcc.impilo.reproductive.dating.DatingBasis;
import zw.gov.mohcc.impilo.reproductive.dating.EddCalculator;
import zw.gov.mohcc.impilo.reproductive.dating.PregnancyDating;
import zw.gov.mohcc.impilo.reproductive.dating.PregnancyDatingResolver;
import zw.gov.mohcc.impilo.reproductive.dating.RedatingPolicy;
import zw.gov.mohcc.impilo.pct.persistence.entity.PregnancyDatingRevisionEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.PregnancyEpisodeEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.PregnancyDatingRevisionRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.PregnancyEpisodeRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The pregnancy episode: opening one, dating it, finding the one a clinical record belongs to.
 *
 * <p>Two behaviours here carry most of the clinical weight.
 *
 * <p><b>Dating is stamped, not recomputed.</b> This service resolves the estimated delivery date
 * once, writes it with its basis and confidence, and everything downstream reads the stored value.
 * The rules engine deliberately cannot re-derive it — the same boundary that keeps a stored growth
 * z-score and its interpretation from drifting apart. Two components that can each compute a
 * gestational age will eventually disagree about how pregnant a woman is, and the disagreement will
 * surface as a contradiction between a chart and an alert.
 *
 * <p><b>A duplicate is never a 500.</b> Three database constraints prevent two episodes for one
 * pregnancy, and each means something different: a repeated offline id is an idempotent replay and
 * returns the existing episode; an existing ongoing episode or a matching booking week is a genuine
 * conflict that a human must reconcile. Letting either surface as an unhandled exception would lose
 * a booking captured in a clinic with no network, which is precisely the case the offline mechanism
 * exists to serve.
 *
 * <p><b>An episode carries the SRH category but does not inherit its outcome's class.</b> Protecting
 * the whole episode because it ended in a termination would hide the antenatal care from the
 * maternity team; the TOP rows carry the confidence instead. See
 * {@code docs/clinical-governance/rmnp/srh-confidentiality-stamping.md} §3.
 */
@Service
public class PregnancyEpisodeService {

    private static final Logger log = LoggerFactory.getLogger(PregnancyEpisodeService.class);

    /**
     * How long after a pregnancy ends a clinical record may still be attributed to it. A delivery
     * is routinely recorded minutes to days after it happened, and a postnatal observation days
     * later still belongs to the pregnancy that produced it.
     */
    static final int LINKAGE_GRACE_DAYS = 14;

    private final PregnancyEpisodeRepository episodes;
    private final PregnancyDatingRevisionRepository revisions;
    private final RedatingPolicy redatingPolicy;
    private final ConfidentialCarePolicyProvider confidentiality;
    private final ConfidentialRecordGuard confidentialRecords;

    public PregnancyEpisodeService(PregnancyEpisodeRepository episodes,
                                   PregnancyDatingRevisionRepository revisions,
                                   ConfidentialCarePolicyProvider confidentiality,
                                   ConfidentialRecordGuard confidentialRecords) {
        this.episodes = episodes;
        this.revisions = revisions;
        this.redatingPolicy = RedatingPolicy.engineeringSeed();
        this.confidentiality = confidentiality;
        this.confidentialRecords = confidentialRecords;
    }

    /** The woman's current pregnancy, if she has one — guarded, so a withheld episode reads as none. */
    @Transactional(readOnly = true)
    public Optional<PregnancyEpisodeEntity> currentPregnancy(UUID tenantId, String subjectCpid) {
        return confidentialRecords.visible(ongoing(tenantId, subjectCpid),
                PregnancyEpisodeEntity::getSensitivityClass,
                PregnancyEpisodeEntity::getConfidentialityCategory);
    }

    /**
     * The ongoing episode WITHOUT the confidentiality guard.
     *
     * <p>Exists only for the two boolean/derived answers below, which are called from clinical
     * decision support with no request context. It must never be used to return a record, or to
     * back an endpoint: everything that hands an episode to a caller goes through
     * {@link #currentPregnancy}.
     */
    private Optional<PregnancyEpisodeEntity> ongoing(UUID tenantId, String subjectCpid) {
        if (tenantId == null || subjectCpid == null || subjectCpid.isBlank()) {
            return Optional.empty();
        }
        return episodes.findOngoing(tenantId, subjectCpid);
    }

    /**
     * Whether this person is currently pregnant, as far as the record knows.
     *
     * <p>Returns null — never false — when there is no episode. "No pregnancy has been recorded" and
     * "she is not pregnant" are different statements, and the second is an assertion nobody made.
     * The form resolver relies on that distinction: a null leaves an applicability gate undecided
     * rather than silently excluding a form.
     *
     * <p><b>Deliberately UNGUARDED, and a known bounded leak.</b> It answers a boolean the record
     * itself would refuse. {@code FormResolverService} calls it with no request context, so the
     * fail-closed guard would return null for every requester and blank clinical decision support
     * outright — a form that silently stops applying is worse than a boolean that says more than the
     * record would. Recorded in the stamping doc §8 with a named owner rather than silently guarded;
     * do not "fix" it by routing it through {@link #currentPregnancy}.
     */
    @Transactional(readOnly = true)
    public Boolean pregnant(UUID tenantId, String subjectCpid) {
        return ongoing(tenantId, subjectCpid).isPresent() ? Boolean.TRUE : null;
    }

    /**
     * Gestational age in completed days today, or null when undated.
     *
     * <p>Unguarded for the same reason as {@link #pregnant}: gestation drives dose bands, referral
     * thresholds and form applicability, and blanking it would degrade decision support rather than
     * protect anyone. It returns a number, not the record.
     */
    @Transactional(readOnly = true)
    public Integer gestationalAgeDays(UUID tenantId, String subjectCpid, LocalDate asOf) {
        return ongoing(tenantId, subjectCpid)
                .map(e -> EddCalculator.gestationalAgeDays(e.getEstimatedDeliveryDate(), asOf))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<PregnancyEpisodeEntity> history(UUID tenantId, String subjectCpid) {
        return confidentialRecords.filter(episodes.findBySubject(tenantId, subjectCpid),
                PregnancyEpisodeEntity::getSensitivityClass,
                PregnancyEpisodeEntity::getConfidentialityCategory);
    }

    /**
     * The pregnancy a clinical record dated {@code on} belongs to.
     *
     * <p><b>Returns empty unless exactly one candidate matches.</b> Two overlapping episodes mean
     * the history is ambiguous, and attaching a partograph to the wrong pregnancy is worse than
     * attaching it to none: the wrong one is invisible, and the right one looks like it was never
     * charted.
     *
     * <p><b>Guarded, and it has no production callers yet — that is why it is guarded now.</b> It
     * hands back an episode, so it is a disclosure read. Added guarded from birth for the same reason
     * as {@code TerminationService.proceduresForSubject}: if the safe version does not exist before
     * anyone needs it, the person who needs it writes the unguarded one. A future caller that needs
     * linkage with no request context must not reach for this method — it needs its own accessor
     * returning an identifier, on the model of {@link #pregnant}, so the choice stays visible.
     */
    @Transactional(readOnly = true)
    public Optional<PregnancyEpisodeEntity> episodeCovering(UUID tenantId, String subjectCpid, LocalDate on) {
        if (tenantId == null || subjectCpid == null || on == null) {
            return Optional.empty();
        }
        List<PregnancyEpisodeEntity> candidates =
                episodes.findCoveringDate(tenantId, subjectCpid, on, on.minusDays(LINKAGE_GRACE_DAYS));
        if (candidates.size() == 1) {
            return confidentialRecords.visible(Optional.of(candidates.get(0)),
                    PregnancyEpisodeEntity::getSensitivityClass,
                    PregnancyEpisodeEntity::getConfidentialityCategory);
        }
        if (candidates.size() > 1) {
            log.warn("pregnancy.linkage.ambiguous subject={} on={} candidates={} — leaving unlinked",
                    subjectCpid, on, candidates.size());
        }
        return Optional.empty();
    }

    /**
     * The outcome of a booking attempt, saying WHICH of the two things happened.
     *
     * <p>A replay and a new booking both return a saved episode with an id, so a caller cannot tell
     * them apart by looking at the row. An HTTP surface must: a replay is the packet arriving twice
     * and answers 200, a new booking answers 201, and the difference is what stops an offline client
     * from either double-booking a pregnancy or treating its own successful sync as a failure.
     * Inferring it from a timestamp would be right most of the time, which is the worst kind of right.
     */
    public record OpenOutcome(PregnancyEpisodeEntity episode, boolean replayed) {
    }

    /**
     * Open a pregnancy episode, or return the one that already exists, discarding which happened.
     *
     * @throws DuplicatePregnancyException when the woman already has an ongoing pregnancy, or one
     *                                     booked in the same week — a conflict a human must resolve
     */
    @Transactional
    public PregnancyEpisodeEntity open(OpenPregnancyCommand command) {
        return openReporting(command).episode();
    }

    /**
     * Open a pregnancy episode, reporting whether it was created or replayed.
     *
     * @throws DuplicatePregnancyException when the woman already has an ongoing pregnancy, or one
     *                                     booked in the same week — a conflict a human must resolve
     */
    @Transactional
    public OpenOutcome openReporting(OpenPregnancyCommand command) {
        if (command.clientOfflineId() != null) {
            Optional<PregnancyEpisodeEntity> replayed =
                    episodes.findByTenantIdAndClientOfflineId(command.tenantId(), command.clientOfflineId());
            if (replayed.isPresent()) {
                // Idempotent replay: the packet arrived twice, not the pregnancy.
                log.info("pregnancy.episode.replayed offlineId={} episode={}",
                        command.clientOfflineId(), replayed.get().getPregnancyEpisodeId());
                return new OpenOutcome(replayed.get(), true);
            }
        }

        Optional<PregnancyEpisodeEntity> ongoing =
                episodes.findOngoing(command.tenantId(), command.subjectCpid());
        if (ongoing.isPresent()) {
            throw new DuplicatePregnancyException(
                    ongoing.get().getPregnancyEpisodeId(),
                    "This person already has an ongoing pregnancy episode. A woman cannot be "
                            + "pregnant twice at once; a heterotopic pregnancy is one episode with "
                            + "two fetus records. Reconcile before opening another.");
        }

        PregnancyDating dating = PregnancyDatingResolver.resolve(
                command.datingBases(), command.recordedOn(), redatingPolicy);
        if (dating == null) {
            throw new UndatablePregnancyException(
                    "No usable dating basis was supplied. A pregnancy episode must carry a start "
                            + "date: without one, every gestation-scoped rule downstream would "
                            + "silently fail to apply rather than reporting that it could not.");
        }

        PregnancyEpisodeEntity episode = new PregnancyEpisodeEntity();
        episode.setPregnancyEpisodeId(UUID.randomUUID());
        episode.setTenantId(command.tenantId());
        episode.setSubjectCpid(command.subjectCpid());
        episode.setFacilityId(command.facilityId());
        episode.setOpeningJourneyId(command.journeyId());
        episode.setOpeningEncounterId(command.encounterId());
        episode.setStatus(PregnancyEpisodeEntity.STATUS_ONGOING);
        episode.setConfirmationBasis(command.confirmationBasis() == null
                ? "NOT_CONFIRMED" : command.confirmationBasis());
        episode.setConfirmedOn(command.confirmedOn());

        applyDating(episode, dating);
        // Immutable: the booking-week duplicate key is computed from this, so a later redating
        // cannot move a pregnancy out from under its own duplicate check.
        episode.setInitialPregnancyStartDate(dating.pregnancyStartDate());
        episode.setDatingRevisionCount(1);

        episode.setPlurality(command.plurality() == null ? 1 : command.plurality());
        episode.setPluralityBasis(command.plurality() == null ? "UNKNOWN" : "CLINICAL");
        episode.setIntendedPregnancy("NOT_ASSESSED");
        episode.setConceptionMode(command.conceptionMode() == null ? "SPONTANEOUS" : command.conceptionMode());
        episode.setRiskStatus("NOT_ASSESSED");
        episode.setIntervalAdequacy("UNKNOWN");
        episode.setHivStatusAtBooking("UNKNOWN");
        episode.setOnArt("NOT_ASSESSED");
        episode.setGpDerivationComplete(false);
        episode.setGpUncountableCount(0);
        episode.setGpDiscrepancy(false);
        applyStamp(episode, dating.pregnancyStartDate());
        episode.setClientOfflineId(command.clientOfflineId());
        episode.setCapturedAt(command.capturedAt());
        episode.setRecordedBy(command.recordedBy());
        episode.setCreatedAt(OffsetDateTime.now());
        episode.setUpdatedAt(OffsetDateTime.now());

        PregnancyEpisodeEntity saved = episodes.save(episode);
        revisions.save(revisionOf(saved, dating, 1, command.recordedBy()));

        log.info("pregnancy.episode.opened episode={} subject={} edd={} method={} confidence={}",
                saved.getPregnancyEpisodeId(), saved.getSubjectCpid(),
                saved.getEstimatedDeliveryDate(), saved.getDatingMethod(), saved.getDatingConfidence());
        return new OpenOutcome(saved, false);
    }

    /**
     * Consider a new dating basis.
     *
     * <p>Always records a revision, including when the basis was rejected. An obstetric record must
     * be able to say "we saw the scan and kept the last-menstrual-period dating", otherwise the next
     * clinician sees a scan that appears to have been ignored.
     */
    @Transactional
    public PregnancyDating revise(UUID tenantId, UUID episodeId, DatingBasis candidate,
                                  LocalDate asOf, String recordedBy) {
        PregnancyEpisodeEntity episode = episodes.findById(episodeId)
                .filter(e -> e.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("pregnancy episode not found"));

        PregnancyDating current = datingOf(episode);
        PregnancyDating revised = PregnancyDatingResolver.revise(current, candidate, asOf, redatingPolicy);

        int next = revisions.countByPregnancyEpisodeId(episodeId) + 1;
        revisions.save(revisionOf(episode, revised, next, recordedBy));

        if (revised.redatingApplied()) {
            applyDating(episode, revised);
            episode.setDatingRevisionCount(next);
            episode.setUpdatedAt(OffsetDateTime.now());
            episodes.save(episode);
            log.info("pregnancy.dating.revised episode={} newEdd={} previousEdd={} discrepancyDays={}",
                    episodeId, revised.estimatedDeliveryDate(),
                    current == null ? null : current.estimatedDeliveryDate(), revised.discrepancyDays());
        } else {
            log.info("pregnancy.dating.considered episode={} basis={} kept={} reason={}",
                    episodeId, candidate.method(), episode.getEstimatedDeliveryDate(), revised.rationale());
        }
        return revised;
    }

    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Stamp the SRH category, and the protection class only once the governance flip enables it.
     *
     * <p>The act date is the pregnancy start date, not today — a pregnancy booked when she was 17 was
     * booked under a promise of confidentiality that does not lapse when she turns 18 mid-pregnancy.
     *
     * <p>The legacy {@code confidentiality} column this replaced held {@code ROUTINE} from a third
     * vocabulary. V437 left it nullable and unwritten with a COMMENT saying so; nothing here writes
     * it, and nothing estate-wide reads it.
     */
    private void applyStamp(PregnancyEpisodeEntity episode, LocalDate pregnancyStartDate) {
        var category = ConfidentialityCategory.SEXUAL_REPRODUCTIVE_HEALTH;
        var policy = confidentiality.policyAsOf(episode.getTenantId(), pregnancyStartDate);
        var subject = confidentiality.subjectAt(
                episode.getTenantId(), episode.getSubjectCpid(), category, pregnancyStartDate);
        var stamp = ConfidentialityStamper.gated(
                ConfidentialityStamper.ageGated(category, subject, pregnancyStartDate, policy),
                confidentiality.classStampingEnabled());
        episode.setSensitivityClass(stamp.sensitivityClass());
        episode.setConfidentialityCategory(stamp.category());
        episode.setConfidentialityBasis(stamp.basis());
        episode.setConfidentialityPolicyVersion(stamp.policyVersion());
    }

    private void applyDating(PregnancyEpisodeEntity episode, PregnancyDating dating) {
        episode.setEstimatedDeliveryDate(dating.estimatedDeliveryDate());
        episode.setPregnancyStartDate(dating.pregnancyStartDate());
        episode.setDatingMethod(dating.method() == null ? null : dating.method().name());
        episode.setDatingConfidence(dating.confidence() == null ? "UNKNOWN" : dating.confidence().name());
        episode.setDatingPlusMinusDays(dating.plusOrMinusDays());
        episode.setDatedOn(dating.datedOn());
        episode.setDatingContentVersion(dating.contentVersion());
    }

    private PregnancyDating datingOf(PregnancyEpisodeEntity episode) {
        if (episode.getEstimatedDeliveryDate() == null || episode.getDatingMethod() == null) {
            return null;
        }
        DatingBasis adopted = new DatingBasis(
                zw.gov.mohcc.impilo.reproductive.dating.DatingMethod.valueOf(episode.getDatingMethod()),
                episode.getDatedOn(),
                null, null,
                EddCalculator.gestationalAgeDays(episode.getEstimatedDeliveryDate(), episode.getDatedOn()),
                null, null, null);
        return new PregnancyDating(
                episode.getEstimatedDeliveryDate(),
                episode.getPregnancyStartDate(),
                adopted.method(),
                PregnancyDating.DatingConfidence.valueOf(episode.getDatingConfidence()),
                episode.getDatingPlusMinusDays(),
                episode.getDatedOn(),
                adopted, null, null, null, false, List.of(adopted),
                "Current dating on the episode.",
                episode.getDatingContentVersion());
    }

    private PregnancyDatingRevisionEntity revisionOf(PregnancyEpisodeEntity episode, PregnancyDating dating,
                                                     int revisionNumber, String recordedBy) {
        PregnancyDatingRevisionEntity revision = new PregnancyDatingRevisionEntity();
        revision.setRevisionId(UUID.randomUUID());
        revision.setTenantId(episode.getTenantId());
        revision.setPregnancyEpisodeId(episode.getPregnancyEpisodeId());
        revision.setSubjectCpid(episode.getSubjectCpid());
        revision.setRevisionNumber(revisionNumber);

        DatingBasis basis = dating.adoptedBasis();
        revision.setDatingMethod(basis.method().name());
        revision.setBasisObservedOn(basis.observedOn());
        revision.setLastMenstrualPeriod(basis.lastMenstrualPeriod());
        revision.setLmpCertainty(basis.lastMenstrualPeriodCertain() == null ? null
                : (basis.lastMenstrualPeriodCertain() ? "CERTAIN" : "UNCERTAIN"));
        revision.setMeasuredGaDays(basis.measuredGestationalAgeDays());
        revision.setConceptionDate(basis.conceptionDate());
        revision.setEmbryoAgeDaysAtTransfer(basis.embryoAgeDaysAtTransfer());
        revision.setUltrasoundRef(basis.sourceRef());

        revision.setEstimatedDeliveryDate(dating.estimatedDeliveryDate());
        revision.setDatingConfidence(dating.confidence().name());
        revision.setPlusMinusDays(dating.plusOrMinusDays());

        if (dating.supersededBasis() != null) {
            revision.setSupersededMethod(dating.supersededBasis().method().name());
            revision.setSupersededEdd(dating.supersededBasis().estimatedDeliveryDate());
        }
        revision.setDiscrepancyDays(dating.discrepancyDays());
        revision.setRedatingToleranceDays(dating.toleranceDays());
        revision.setRedatingApplied(dating.redatingApplied());
        revision.setRationale(dating.rationale());
        revision.setContentVersion(dating.contentVersion());
        revision.setRecordedBy(recordedBy);
        revision.setCreatedAt(OffsetDateTime.now());
        return revision;
    }

    /** Everything needed to open an episode. */
    public record OpenPregnancyCommand(
            UUID tenantId,
            String subjectCpid,
            UUID facilityId,
            String journeyId,
            String encounterId,
            List<DatingBasis> datingBases,
            LocalDate recordedOn,
            LocalDate confirmedOn,
            String confirmationBasis,
            Integer plurality,
            String conceptionMode,
            String clientOfflineId,
            OffsetDateTime capturedAt,
            String recordedBy) {
    }

    /** A conflict a human must reconcile — never a 500, and never a silently discarded booking. */
    public static class DuplicatePregnancyException extends RuntimeException {
        private final UUID existingEpisodeId;

        public DuplicatePregnancyException(UUID existingEpisodeId, String message) {
            super(message);
            this.existingEpisodeId = existingEpisodeId;
        }

        public UUID existingEpisodeId() {
            return existingEpisodeId;
        }
    }

    /** No usable dating basis: refused rather than defaulted. */
    public static class UndatablePregnancyException extends RuntimeException {
        public UndatablePregnancyException(String message) {
            super(message);
        }
    }
}
