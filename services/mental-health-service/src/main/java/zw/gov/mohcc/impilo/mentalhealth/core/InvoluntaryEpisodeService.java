package zw.gov.mohcc.impilo.mentalhealth.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.mentalhealth.events.OutboxEventEntity;
import zw.gov.mohcc.impilo.mentalhealth.events.OutboxEventRepository;
import zw.gov.mohcc.impilo.mentalhealth.persistence.entity.InvoluntaryEpisodeEntity;
import zw.gov.mohcc.impilo.mentalhealth.persistence.entity.ReferralEntity;
import zw.gov.mohcc.impilo.mentalhealth.persistence.repository.InvoluntaryEpisodeRepository;
import zw.gov.mohcc.impilo.mentalhealth.persistence.repository.ReferralRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Statutory detention/observation. {@code legalBasis} is required free text — see
 * V001__init.sql's rationale on why this pack does not fabricate a Zimbabwe Mental Health Act
 * category picklist: the clinician names the actual legal instrument/section.
 *
 * <p>At most one OPEN episode per referral ({@code uq_mhie_open_per_referral}) — enforced by the
 * database, not just the application, because two conflicting concurrent legal statuses for one
 * patient is a safety-relevant data-integrity failure, not merely a UX inconvenience.
 */
@Service
public class InvoluntaryEpisodeService {

    private static final Logger log = LoggerFactory.getLogger(InvoluntaryEpisodeService.class);

    private final ReferralRepository referralRepository;
    private final InvoluntaryEpisodeRepository repository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public InvoluntaryEpisodeService(ReferralRepository referralRepository,
                                     InvoluntaryEpisodeRepository repository,
                                     OutboxEventRepository outboxRepository,
                                     ObjectMapper objectMapper) {
        this.referralRepository = referralRepository;
        this.repository = repository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public InvoluntaryEpisodeEntity open(UUID tenantId, UUID referralId, String legalBasis,
                                         String authorisedBy, OffsetDateTime reviewDueAt) {
        ReferralEntity referral = referralRepository.findByIdAndTenantId(referralId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Mental health referral not found in this tenant: " + referralId));
        require(legalBasis != null && !legalBasis.isBlank(),
                "legalBasis is required — name the actual legal instrument/section");
        require(authorisedBy != null && !authorisedBy.isBlank(), "authorisedBy is required");

        InvoluntaryEpisodeEntity e = new InvoluntaryEpisodeEntity();
        e.setId(UUID.randomUUID());
        e.setTenantId(tenantId);
        e.setReferralId(referralId);
        e.setLegalBasis(legalBasis);
        e.setAuthorisedBy(authorisedBy);
        e.setAuthorisedAt(OffsetDateTime.now());
        e.setReviewDueAt(reviewDueAt);
        e.setStatus("OPEN");
        e.setCreatedAt(OffsetDateTime.now());
        try {
            InvoluntaryEpisodeEntity saved = repository.save(e);
            emit(saved, "MH_INVOLUNTARY_EPISODE_OPENED");
            return saved;
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Referral " + referralId + " already has an OPEN involuntary episode", ex);
        }
    }

    @Transactional
    public InvoluntaryEpisodeEntity close(UUID id, UUID tenantId, String endedReason) {
        InvoluntaryEpisodeEntity e = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Involuntary episode not found in this tenant: " + id));
        require("OPEN".equals(e.getStatus()), "Involuntary episode " + id + " is " + e.getStatus() + ", not OPEN");
        require(endedReason != null && !endedReason.isBlank(), "endedReason is required to close an involuntary episode");

        e.setStatus("CLOSED");
        e.setEndedAt(OffsetDateTime.now());
        e.setEndedReason(endedReason);
        InvoluntaryEpisodeEntity saved = repository.save(e);
        emit(saved, "MH_INVOLUNTARY_EPISODE_CLOSED");
        return saved;
    }

    public List<InvoluntaryEpisodeEntity> history(UUID tenantId, UUID referralId) {
        return repository.findByTenantIdAndReferralIdOrderByAuthorisedAtDesc(tenantId, referralId);
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private void emit(InvoluntaryEpisodeEntity e, String eventType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("involuntaryEpisodeId", e.getId().toString());
        payload.put("referralId", e.getReferralId().toString());
        payload.put("status", e.getStatus());
        try {
            String json = objectMapper.writeValueAsString(payload);
            outboxRepository.save(OutboxEventEntity.create(
                    "MH_INVOLUNTARY_EPISODE", e.getId().toString(), eventType, null,
                    e.getTenantId(), "national-spine", e.getId().toString(), "MH_INVOLUNTARY_EPISODE", json));
        } catch (JsonProcessingException ex) {
            log.error("Involuntary episode event payload could not be serialised: {}", ex.getMessage());
        }
    }
}
