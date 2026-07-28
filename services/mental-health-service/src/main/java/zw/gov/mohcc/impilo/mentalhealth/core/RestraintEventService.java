package zw.gov.mohcc.impilo.mentalhealth.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.mentalhealth.events.OutboxEventEntity;
import zw.gov.mohcc.impilo.mentalhealth.events.OutboxEventRepository;
import zw.gov.mohcc.impilo.mentalhealth.persistence.entity.ReferralEntity;
import zw.gov.mohcc.impilo.mentalhealth.persistence.entity.RestraintEventEntity;
import zw.gov.mohcc.impilo.mentalhealth.persistence.repository.ReferralRepository;
import zw.gov.mohcc.impilo.mentalhealth.persistence.repository.RestraintEventRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A restraint-REDUCTION register, not a restraint log. Every event carries a mandatory
 * de-escalation-attempted record at the point of recording (value-or-reason, this pack's R12
 * discipline applied to a safety context rather than a clinical mandatory-content gate) and is
 * queued for review until reviewed together — see V001__init.sql.
 */
@Service
public class RestraintEventService {

    private static final Logger log = LoggerFactory.getLogger(RestraintEventService.class);
    private static final Set<String> RESTRAINT_TYPES = Set.of("PHYSICAL", "MECHANICAL", "CHEMICAL", "SECLUSION");

    private final ReferralRepository referralRepository;
    private final RestraintEventRepository repository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public RestraintEventService(ReferralRepository referralRepository,
                                 RestraintEventRepository repository,
                                 OutboxEventRepository outboxRepository,
                                 ObjectMapper objectMapper) {
        this.referralRepository = referralRepository;
        this.repository = repository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RestraintEventEntity record(UUID tenantId, UUID referralId, String restraintType, String reason,
                                       String deEscalationAttempted, String initiatedBy) {
        ReferralEntity referral = referralRepository.findByIdAndTenantId(referralId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Mental health referral not found in this tenant: " + referralId));
        require(restraintType != null && RESTRAINT_TYPES.contains(restraintType),
                "restraintType must be one of " + RESTRAINT_TYPES);
        require(reason != null && !reason.isBlank(), "reason is required");
        require(deEscalationAttempted != null && !deEscalationAttempted.isBlank(),
                "deEscalationAttempted is required — a restraint-reduction register records what was "
                        + "tried first, never just that restraint was used");
        require(initiatedBy != null && !initiatedBy.isBlank(), "initiatedBy is required");

        RestraintEventEntity e = new RestraintEventEntity();
        e.setId(UUID.randomUUID());
        e.setTenantId(tenantId);
        e.setReferralId(referralId);
        e.setRestraintType(restraintType);
        e.setReason(reason);
        e.setDeEscalationAttempted(deEscalationAttempted);
        e.setInitiatedBy(initiatedBy);
        e.setInitiatedAt(OffsetDateTime.now());
        e.setCreatedAt(OffsetDateTime.now());
        RestraintEventEntity saved = repository.save(e);
        emit(saved, "MH_RESTRAINT_EVENT_RECORDED");
        return saved;
    }

    @Transactional
    public RestraintEventEntity end(UUID id, UUID tenantId) {
        RestraintEventEntity e = load(id, tenantId);
        require(e.getEndedAt() == null, "Restraint event " + id + " already ended");
        e.setEndedAt(OffsetDateTime.now());
        return repository.save(e);
    }

    /** Every restraint event must be reviewed — the reduction register's own accountability loop. */
    @Transactional
    public RestraintEventEntity review(UUID id, UUID tenantId, String reviewedBy, String reviewOutcome) {
        RestraintEventEntity e = load(id, tenantId);
        require(e.getReviewedAt() == null, "Restraint event " + id + " already reviewed");
        require(reviewedBy != null && !reviewedBy.isBlank(), "reviewedBy is required");
        require(reviewOutcome != null && !reviewOutcome.isBlank(), "reviewOutcome is required");

        e.setReviewedBy(reviewedBy);
        e.setReviewedAt(OffsetDateTime.now());
        e.setReviewOutcome(reviewOutcome);
        RestraintEventEntity saved = repository.save(e);
        emit(saved, "MH_RESTRAINT_EVENT_REVIEWED");
        return saved;
    }

    public List<RestraintEventEntity> history(UUID tenantId, UUID referralId) {
        return repository.findByTenantIdAndReferralIdOrderByInitiatedAtDesc(tenantId, referralId);
    }

    /** The unreviewed queue — this register's own accountability worklist. */
    public List<RestraintEventEntity> unreviewed(UUID tenantId) {
        return repository.findByTenantIdAndReviewedAtIsNullOrderByInitiatedAtAsc(tenantId);
    }

    private RestraintEventEntity load(UUID id, UUID tenantId) {
        return repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Restraint event not found in this tenant: " + id));
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private void emit(RestraintEventEntity e, String eventType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("restraintEventId", e.getId().toString());
        payload.put("referralId", e.getReferralId().toString());
        payload.put("restraintType", e.getRestraintType());
        try {
            String json = objectMapper.writeValueAsString(payload);
            outboxRepository.save(OutboxEventEntity.create(
                    "MH_RESTRAINT_EVENT", e.getId().toString(), eventType, null,
                    e.getTenantId(), "national-spine", e.getId().toString(), "MH_RESTRAINT_EVENT", json));
        } catch (JsonProcessingException ex) {
            log.error("Restraint event payload could not be serialised: {}", ex.getMessage());
        }
    }
}
