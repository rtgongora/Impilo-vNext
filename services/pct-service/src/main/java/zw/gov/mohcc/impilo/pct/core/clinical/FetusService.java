package zw.gov.mohcc.impilo.pct.core.clinical;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.persistence.entity.FetusEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.PregnancyEpisodeEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.FetusRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.PregnancyEpisodeRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Fetus records, and the assertion that links a fetus to the baby it became.
 *
 * <p><b>Matching is an assertion, not an inference.</b> For a singleton it is unambiguous and the
 * service makes it automatically. For a multiple pregnancy it never does: birth order is the usual
 * basis but is not always the labelling used antenatally, and a mismatched twin is one of the few
 * clinical errors that cannot be corrected afterwards — the growth history, the anomaly findings
 * and the Doppler results follow the wrong child for the rest of their life. So a multiple
 * pregnancy requires someone to state the mapping, and the API refuses a birth record for one
 * without it.
 *
 * <p><b>A vanished twin does not reduce plurality.</b> The record must remain able to say that the
 * pregnancy started as a twin pregnancy — it changes the interpretation of everything measured
 * afterwards, and quietly decrementing the count erases it.
 */
@Service
public class FetusService {

    private static final Logger log = LoggerFactory.getLogger(FetusService.class);

    private final FetusRepository fetuses;
    private final PregnancyEpisodeRepository episodes;

    public FetusService(FetusRepository fetuses, PregnancyEpisodeRepository episodes) {
        this.fetuses = fetuses;
        this.episodes = episodes;
    }

    @Transactional(readOnly = true)
    public List<FetusEntity> forPregnancy(UUID pregnancyEpisodeId) {
        return fetuses.findByPregnancyEpisodeIdOrderByFetusLabelAsc(pregnancyEpisodeId);
    }

    /**
     * Record a fetus. Labels follow the obstetric convention (A, B, C) and are stable for the whole
     * pregnancy, because every scan report and growth chart refers to them.
     */
    @Transactional
    public FetusEntity record(UUID tenantId, UUID pregnancyEpisodeId, String motherCpid,
                              String fetusLabel, String recordedBy) {
        Optional<FetusEntity> existing =
                fetuses.findByPregnancyEpisodeIdAndFetusLabel(pregnancyEpisodeId, fetusLabel);
        if (existing.isPresent()) {
            return existing.get();
        }
        FetusEntity fetus = new FetusEntity();
        fetus.setFetusId(UUID.randomUUID());
        fetus.setTenantId(tenantId);
        fetus.setPregnancyEpisodeId(pregnancyEpisodeId);
        fetus.setMotherCpid(motherCpid);
        fetus.setFetusLabel(fetusLabel);
        fetus.setViabilityStatus("NOT_ASSESSED");
        fetus.setSexOnUltrasound("NOT_ASSESSED");
        fetus.setAnomalyScreeningStatus("NOT_ASSESSED");
        fetus.setOutcome(FetusEntity.OUTCOME_ONGOING);
        fetus.setRecordedBy(recordedBy);
        fetus.setCreatedAt(OffsetDateTime.now());
        fetus.setUpdatedAt(OffsetDateTime.now());
        return fetuses.save(fetus);
    }

    /**
     * Link a fetus to the birth record of the baby it became.
     *
     * @throws UnmatchableFetusException when the caller has not said how it knows, or when the
     *                                   fetus has no outcome that produced a baby
     */
    @Transactional
    public FetusEntity matchToNewborn(UUID tenantId, UUID fetusId, UUID newbornBirthRecordId,
                                      String matchBasis, String matchConfidence,
                                      String matchedBy, String notes) {
        FetusEntity fetus = fetuses.findById(fetusId)
                .filter(f -> f.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("fetus not found"));

        if (matchBasis == null || matchConfidence == null || matchedBy == null) {
            throw new UnmatchableFetusException(
                    "A fetus-to-newborn match must state its basis, its confidence and who made it. "
                            + "A mismatched twin cannot be corrected later — the growth history and "
                            + "anomaly findings follow the wrong child permanently — so the "
                            + "assertion has to be reviewable.");
        }
        if (FetusEntity.OUTCOME_ONGOING.equals(fetus.getOutcome())) {
            throw new UnmatchableFetusException(
                    "This fetus has no recorded outcome, so there is no birth to match it to. "
                            + "Record the outcome first.");
        }

        Optional<FetusEntity> claimed = fetuses.findByNewbornBirthRecordId(newbornBirthRecordId);
        if (claimed.isPresent() && !claimed.get().getFetusId().equals(fetusId)) {
            throw new UnmatchableFetusException(
                    "That birth record is already matched to fetus " + claimed.get().getFetusLabel()
                            + ". Two fetuses matched to one baby double-counts a birth and orphans "
                            + "the other.");
        }

        fetus.setNewbornBirthRecordId(newbornBirthRecordId);
        fetus.setMatchBasis(matchBasis);
        fetus.setMatchConfidence(matchConfidence);
        fetus.setMatchedAt(OffsetDateTime.now());
        fetus.setMatchedBy(matchedBy);
        fetus.setMatchNotes(notes);
        fetus.setUpdatedAt(OffsetDateTime.now());

        log.info("fetus.matched fetus={} label={} newborn={} basis={} confidence={}",
                fetusId, fetus.getFetusLabel(), newbornBirthRecordId, matchBasis, matchConfidence);
        return fetuses.save(fetus);
    }

    /**
     * Match automatically where it is unambiguous, and only there.
     *
     * @return the matched fetus, or empty when a human must decide
     */
    @Transactional
    public Optional<FetusEntity> autoMatchSingleton(UUID tenantId, UUID pregnancyEpisodeId,
                                                    UUID newbornBirthRecordId, String matchedBy) {
        List<FetusEntity> all = forPregnancy(pregnancyEpisodeId);
        List<FetusEntity> unmatched = all.stream().filter(f -> !f.matched()).toList();
        if (all.size() != 1 || unmatched.size() != 1) {
            log.info("fetus.match.deferred episode={} fetuses={} unmatched={} — a multiple "
                            + "pregnancy needs the attendant to state the mapping",
                    pregnancyEpisodeId, all.size(), unmatched.size());
            return Optional.empty();
        }
        return Optional.of(matchToNewborn(tenantId, unmatched.get(0).getFetusId(), newbornBirthRecordId,
                FetusEntity.MATCH_SINGLETON_UNAMBIGUOUS, "CERTAIN", matchedBy,
                "Singleton pregnancy: only one fetus and one birth record."));
    }

    /**
     * The outcome of the pregnancy as a whole, derived from its fetuses.
     *
     * <p>Derived rather than accepted from a caller for multiples, because a caller supplying one
     * outcome for a twin pregnancy has already lost the discordance. All live is a live birth; all
     * stillborn is a stillbirth; anything else is a mixed outcome and must say so.
     *
     * <p>Returns empty while any fetus is still ongoing — a pregnancy is not over because one twin
     * has been delivered.
     */
    @Transactional(readOnly = true)
    public Optional<String> derivePregnancyOutcome(UUID pregnancyEpisodeId) {
        List<FetusEntity> all = forPregnancy(pregnancyEpisodeId);
        if (all.isEmpty()) {
            return Optional.empty();
        }
        if (all.stream().anyMatch(f -> FetusEntity.OUTCOME_ONGOING.equals(f.getOutcome()))) {
            return Optional.empty();
        }
        List<FetusEntity> born = all.stream()
                .filter(f -> !FetusEntity.OUTCOME_VANISHED.equals(f.getOutcome()))
                .toList();
        if (born.isEmpty()) {
            return Optional.of("UNKNOWN");
        }
        boolean anyLive = born.stream().anyMatch(f -> FetusEntity.OUTCOME_LIVE_BIRTH.equals(f.getOutcome()));
        boolean anyStill = born.stream().anyMatch(f ->
                FetusEntity.OUTCOME_STILLBIRTH_ANTEPARTUM.equals(f.getOutcome())
                        || FetusEntity.OUTCOME_STILLBIRTH_INTRAPARTUM.equals(f.getOutcome()));
        if (anyLive && anyStill) {
            return Optional.of("MIXED_OUTCOME");
        }
        if (anyLive) {
            return Optional.of("LIVE_BIRTH");
        }
        if (anyStill) {
            return Optional.of("STILLBIRTH");
        }
        return Optional.of("UNKNOWN");
    }

    /**
     * Record that one of a multiple pregnancy vanished.
     *
     * <p>Deliberately does not touch the episode's plurality. The record must keep saying the
     * pregnancy began as a twin pregnancy: it changes how everything measured afterwards is read,
     * and a quietly decremented count erases it.
     */
    @Transactional
    public FetusEntity recordVanished(UUID tenantId, UUID fetusId, String recordedBy) {
        FetusEntity fetus = fetuses.findById(fetusId)
                .filter(f -> f.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("fetus not found"));
        fetus.setViabilityStatus("VANISHED");
        fetus.setOutcome(FetusEntity.OUTCOME_VANISHED);
        fetus.setOutcomeRecordedAt(OffsetDateTime.now());
        fetus.setUpdatedAt(OffsetDateTime.now());

        PregnancyEpisodeEntity episode = episodes.findById(fetus.getPregnancyEpisodeId()).orElse(null);
        log.info("fetus.vanished fetus={} label={} pluralityUnchanged={}",
                fetusId, fetus.getFetusLabel(), episode == null ? null : episode.getPlurality());
        return fetuses.save(fetus);
    }

    /** A match that cannot be made, or cannot be justified. */
    public static class UnmatchableFetusException extends RuntimeException {
        public UnmatchableFetusException(String message) {
            super(message);
        }
    }
}
