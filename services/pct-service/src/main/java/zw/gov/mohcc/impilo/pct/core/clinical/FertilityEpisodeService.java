package zw.gov.mohcc.impilo.pct.core.clinical;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.persistence.entity.FertilityEpisodeEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.FertilityEpisodeRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Fertility care episodes.
 *
 * <p>Two boundaries are enforced here as well as in the schema, because they are the two most likely
 * to be crossed by a well-meaning caller: a partner's record is never linked without the partner's
 * recorded consent, and a finding is never recorded against an assessment that did not happen. The
 * service raises a message a caller can act on; the database refuses the row regardless.
 */
@Service
public class FertilityEpisodeService {

    private static final Logger log = LoggerFactory.getLogger(FertilityEpisodeService.class);

    private final FertilityEpisodeRepository episodes;

    public FertilityEpisodeService(FertilityEpisodeRepository episodes) {
        this.episodes = episodes;
    }

    @Transactional(readOnly = true)
    public Optional<FertilityEpisodeEntity> open(UUID tenantId, String subjectCpid) {
        if (tenantId == null || subjectCpid == null || subjectCpid.isBlank()) {
            return Optional.empty();
        }
        return episodes.findOpen(tenantId, subjectCpid);
    }

    @Transactional(readOnly = true)
    public List<FertilityEpisodeEntity> history(UUID tenantId, String subjectCpid) {
        if (tenantId == null || subjectCpid == null || subjectCpid.isBlank()) {
            return List.of();
        }
        return episodes.findBySubject(tenantId, subjectCpid);
    }

    @Transactional
    public FertilityEpisodeEntity start(FertilityEpisodeEntity episode) {
        if (episode.getClientOfflineId() != null && !episode.getClientOfflineId().isBlank()) {
            Optional<FertilityEpisodeEntity> replayed = episodes
                    .findByTenantIdAndClientOfflineId(episode.getTenantId(),
                            episode.getClientOfflineId());
            if (replayed.isPresent()) {
                return replayed.get();
            }
        }
        requirePartnerConsent(episode);
        requireFindingsHaveAssessment(episode);

        if (episode.getFertilityEpisodeId() == null) {
            episode.setFertilityEpisodeId(UUID.randomUUID());
        }
        if (episode.getStatus() == null) {
            episode.setStatus(FertilityEpisodeEntity.STATUS_ACTIVE);
        }
        if (episode.getRecordedAt() == null) {
            episode.setRecordedAt(OffsetDateTime.now());
        }
        return episodes.save(episode);
    }

    /**
     * Couples where she was investigated and he was not. A worklist and a metric, not a report — the
     * count is the finding, and it is the number a fertility service is least comfortable seeing.
     */
    @Transactional(readOnly = true)
    public List<FertilityEpisodeEntity> womanAssessedButNotMan(UUID tenantId) {
        return episodes.findWomanAssessedButNotMan(tenantId);
    }

    private static void requirePartnerConsent(FertilityEpisodeEntity episode) {
        if (episode.getPartnerCpid() != null && !episode.getPartnerCpid().isBlank()
                && (episode.getPartnerLinkageConsent() == null
                    || episode.getPartnerLinkageConsent().isBlank())) {
            throw new IllegalArgumentException(
                    "A partner cannot be linked without their recorded consent. Naming a partner is "
                            + "not the same as being permitted to reach into their record, and a "
                            + "couple attending together is not consent.");
        }
    }

    private static void requireFindingsHaveAssessment(FertilityEpisodeEntity episode) {
        if (present(episode.getFemaleFactorFindings())
                && !FertilityEpisodeEntity.ASSESSED.equals(episode.getFemaleFactorAssessed())) {
            throw new IllegalArgumentException(
                    "Female-factor findings were recorded against no assessment. A finding is a "
                            + "claim that an assessment was made.");
        }
        if (present(episode.getMaleFactorFindings())
                && !FertilityEpisodeEntity.ASSESSED.equals(episode.getMaleFactorAssessed())) {
            throw new IllegalArgumentException(
                    "Male-factor findings were recorded against no assessment. A finding is a claim "
                            + "that an assessment was made.");
        }
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
