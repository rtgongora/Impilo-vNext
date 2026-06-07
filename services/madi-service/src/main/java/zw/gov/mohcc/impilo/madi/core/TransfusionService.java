package zw.gov.mohcc.impilo.madi.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.madi.domain.TransfusionStatus;
import zw.gov.mohcc.impilo.madi.events.MadiEventEmitter;
import zw.gov.mohcc.impilo.madi.integration.ButanoIntegration;
import zw.gov.mohcc.impilo.madi.persistence.entity.TransfusionEpisodeEntity;
import zw.gov.mohcc.impilo.madi.persistence.entity.TransfusionObservationEntity;
import zw.gov.mohcc.impilo.madi.persistence.entity.TransfusionOutcomeEntity;
import zw.gov.mohcc.impilo.madi.persistence.repository.TransfusionEpisodeRepository;
import zw.gov.mohcc.impilo.madi.persistence.repository.TransfusionObservationRepository;
import zw.gov.mohcc.impilo.madi.persistence.repository.TransfusionOutcomeRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class TransfusionService {

    private final TransfusionEpisodeRepository episodeRepository;
    private final TransfusionObservationRepository observationRepository;
    private final TransfusionOutcomeRepository outcomeRepository;
    private final ButanoIntegration butanoIntegration;
    private final MadiEventEmitter eventEmitter;

    public TransfusionService(TransfusionEpisodeRepository episodeRepository,
                              TransfusionObservationRepository observationRepository,
                              TransfusionOutcomeRepository outcomeRepository,
                              ButanoIntegration butanoIntegration,
                              MadiEventEmitter eventEmitter) {
        this.episodeRepository = episodeRepository;
        this.observationRepository = observationRepository;
        this.outcomeRepository = outcomeRepository;
        this.butanoIntegration = butanoIntegration;
        this.eventEmitter = eventEmitter;
    }

    @Transactional
    public TransfusionEpisodeEntity startEpisode(UUID tenantId, UUID orderId, UUID issueId,
                                                 String patientCpid, String startedBy,
                                                 UUID facilityId, UUID wardId) {
        TransfusionEpisodeEntity episode = new TransfusionEpisodeEntity();
        episode.setTenantId(tenantId);
        episode.setOrderId(orderId);
        episode.setIssueId(issueId);
        episode.setPatientCpid(patientCpid);
        episode.setStartedBy(startedBy);
        episode.setFacilityId(facilityId);
        episode.setWardId(wardId);
        episode.setStatus(TransfusionStatus.IN_PROGRESS.name());
        episode.setStartedAt(OffsetDateTime.now());
        episode.setUpdatedAt(OffsetDateTime.now());
        TransfusionEpisodeEntity saved = episodeRepository.save(episode);
        eventEmitter.emit("TRANSFUSION", saved.getEpisodeId().toString(), "TRANSFUSION_STARTED",
                "TRANSFUSION", saved.getEpisodeId().toString(),
                Map.of("patientCpid", patientCpid), tenantId);
        return saved;
    }

    @Transactional
    public TransfusionObservationEntity recordObservation(UUID tenantId, UUID episodeId,
                                                          String observationType, BigDecimal valueNumeric,
                                                          String valueText, String unit, String observedBy,
                                                          UUID facilityId) {
        requireEpisode(tenantId, episodeId);
        TransfusionObservationEntity obs = new TransfusionObservationEntity();
        obs.setTenantId(tenantId);
        obs.setEpisodeId(episodeId);
        obs.setObservationType(observationType);
        obs.setValueNumeric(valueNumeric);
        obs.setValueText(valueText);
        obs.setUnit(unit);
        obs.setObservedBy(observedBy);
        obs.setFacilityId(facilityId);
        obs.setObservedAt(OffsetDateTime.now());
        TransfusionObservationEntity saved = observationRepository.save(obs);
        butanoIntegration.recordObservation(episodeId.toString(), observationType, valueNumeric, valueText);
        return saved;
    }

    @Transactional
    public TransfusionEpisodeEntity complete(UUID tenantId, UUID episodeId, String outcomeStatus,
                                             String outcomeNotes, String recordedBy, UUID facilityId) {
        TransfusionEpisodeEntity episode = requireEpisode(tenantId, episodeId);
        TransfusionOutcomeEntity outcome = new TransfusionOutcomeEntity();
        outcome.setTenantId(tenantId);
        outcome.setEpisodeId(episodeId);
        outcome.setOutcomeStatus(outcomeStatus);
        outcome.setOutcomeNotes(outcomeNotes);
        outcome.setRecordedBy(recordedBy);
        outcome.setFacilityId(facilityId);
        outcome.setRecordedAt(OffsetDateTime.now());
        outcomeRepository.save(outcome);
        episode.setStatus(TransfusionStatus.COMPLETED.name());
        episode.setCompletedAt(OffsetDateTime.now());
        episode.setUpdatedAt(OffsetDateTime.now());
        TransfusionEpisodeEntity saved = episodeRepository.save(episode);
        eventEmitter.emit("TRANSFUSION", episodeId.toString(), "TRANSFUSION_COMPLETED", "TRANSFUSION",
                episodeId.toString(), Map.of("outcome", outcomeStatus), tenantId);
        return saved;
    }

    @Transactional
    public TransfusionEpisodeEntity verify(UUID tenantId, UUID episodeId, String verifiedBy) {
        TransfusionEpisodeEntity episode = requireEpisode(tenantId, episodeId);
        if (!TransfusionStatus.COMPLETED.name().equals(episode.getStatus())) {
            throw new IllegalStateException("Episode must be completed before verification");
        }
        episode.setStatus(TransfusionStatus.VERIFIED.name());
        episode.setUpdatedAt(OffsetDateTime.now());
        TransfusionEpisodeEntity saved = episodeRepository.save(episode);
        butanoIntegration.recordTransfusionVerified(episode.getPatientCpid(), episodeId.toString());
        eventEmitter.emit("TRANSFUSION", episodeId.toString(), "TRANSFUSION_VERIFIED", "TRANSFUSION",
                episodeId.toString(), Map.of(), tenantId);
        return saved;
    }

    private TransfusionEpisodeEntity requireEpisode(UUID tenantId, UUID episodeId) {
        return episodeRepository.findByEpisodeIdAndTenantId(episodeId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Transfusion episode not found"));
    }
}
