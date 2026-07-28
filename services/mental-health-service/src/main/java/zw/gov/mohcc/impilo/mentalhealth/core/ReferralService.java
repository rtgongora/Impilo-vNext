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
import zw.gov.mohcc.impilo.mentalhealth.integration.PctHandoverClient;
import zw.gov.mohcc.impilo.mentalhealth.persistence.entity.ReferralEntity;
import zw.gov.mohcc.impilo.mentalhealth.persistence.repository.ReferralRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The accepting side of a psychiatric emergency handover.
 *
 * <p>Intake creates a PENDING referral from pct's handover request — it does NOT touch pct. Only
 * {@link #accept} / {@link #decline} write back to pct's {@code emergency_handover}, because
 * responsibility transfers only when the accepting party writes back with its own record id — the
 * same acceptance-handshake invariant every other counterparty in this pack honours. A degraded
 * PCT never blocks the LOCAL clinical decision (a referral is accepted or declined regardless); it
 * only means pct's own handover record diverges until reconciled.
 */
@Service
public class ReferralService {

    private static final Logger log = LoggerFactory.getLogger(ReferralService.class);

    private final ReferralRepository referralRepository;
    private final OutboxEventRepository outboxRepository;
    private final PctHandoverClient pctHandoverClient;
    private final ObjectMapper objectMapper;

    public ReferralService(ReferralRepository referralRepository,
                           OutboxEventRepository outboxRepository,
                           PctHandoverClient pctHandoverClient,
                           ObjectMapper objectMapper) {
        this.referralRepository = referralRepository;
        this.outboxRepository = outboxRepository;
        this.pctHandoverClient = pctHandoverClient;
        this.objectMapper = objectMapper;
    }

    /** Idempotent on handover_id: a redelivered request returns the existing referral, not a duplicate. */
    @Transactional
    public ReferralEntity intake(UUID tenantId, UUID facilityId, UUID episodeId, UUID handoverId,
                                 String subjectCpid, String requestReason, String requestedBy) {
        require(facilityId != null, "facilityId is required");
        require(episodeId != null, "episodeId is required");
        require(handoverId != null, "handoverId is required");
        require(requestedBy != null && !requestedBy.isBlank(), "requestedBy is required");

        var existing = referralRepository.findByTenantIdAndHandoverId(tenantId, handoverId);
        if (existing.isPresent()) {
            return existing.get();
        }

        OffsetDateTime now = OffsetDateTime.now();
        ReferralEntity r = new ReferralEntity();
        r.setId(UUID.randomUUID());
        r.setTenantId(tenantId);
        r.setFacilityId(facilityId);
        r.setEpisodeId(episodeId);
        r.setHandoverId(handoverId);
        r.setSubjectCpid(subjectCpid);
        r.setStatus("PENDING");
        r.setRequestReason(requestReason);
        r.setRequestedBy(requestedBy);
        r.setRequestedAt(now);
        r.setCreatedAt(now);
        r.setUpdatedAt(now);
        referralRepository.save(r);
        emit(r, "MH_REFERRAL_INTAKE");
        return r;
    }

    @Transactional
    public ReferralEntity accept(UUID id, UUID tenantId, String acceptedBy) {
        ReferralEntity r = load(id, tenantId);
        require("PENDING".equals(r.getStatus()),
                "Referral " + id + " is " + r.getStatus() + ", not PENDING — it cannot be accepted");
        require(acceptedBy != null && !acceptedBy.isBlank(), "acceptedBy is required");

        OffsetDateTime now = OffsetDateTime.now();
        r.setStatus("ACCEPTED");
        r.setReviewedBy(acceptedBy);
        r.setReviewedAt(now);
        r.setUpdatedAt(now);
        referralRepository.save(r);
        emit(r, "MH_REFERRAL_ACCEPTED");

        boolean wroteBack = pctHandoverClient.acceptHandover(tenantId, r.getHandoverId(), r.getId(), acceptedBy);
        if (!wroteBack) {
            log.warn("Referral {} accepted locally but pct's handover {} was not confirmed — "
                    + "reconciliation-worthy divergence, not a local failure", r.getId(), r.getHandoverId());
        }
        return r;
    }

    @Transactional
    public ReferralEntity decline(UUID id, UUID tenantId, String declinedBy, String reason) {
        ReferralEntity r = load(id, tenantId);
        require("PENDING".equals(r.getStatus()),
                "Referral " + id + " is " + r.getStatus() + ", not PENDING — it cannot be declined");
        require(declinedBy != null && !declinedBy.isBlank(), "declinedBy is required");
        require(reason != null && !reason.isBlank(), "a decline reason is required");

        OffsetDateTime now = OffsetDateTime.now();
        r.setStatus("DECLINED");
        r.setReviewedBy(declinedBy);
        r.setReviewedAt(now);
        r.setDeclineReason(reason);
        r.setUpdatedAt(now);
        referralRepository.save(r);
        emit(r, "MH_REFERRAL_DECLINED");

        boolean wroteBack = pctHandoverClient.declineHandover(tenantId, r.getHandoverId(), declinedBy, reason);
        if (!wroteBack) {
            log.warn("Referral {} declined locally but pct's handover {} was not confirmed — "
                    + "reconciliation-worthy divergence, not a local failure", r.getId(), r.getHandoverId());
        }
        return r;
    }

    public ReferralEntity get(UUID id, UUID tenantId) {
        return load(id, tenantId);
    }

    public List<ReferralEntity> listByStatus(UUID tenantId, String status) {
        return referralRepository.findByTenantIdAndStatusOrderByRequestedAtDesc(tenantId, status);
    }

    private ReferralEntity load(UUID id, UUID tenantId) {
        return referralRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Mental health referral not found in this tenant: " + id));
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private void emit(ReferralEntity r, String eventType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("referralId", r.getId().toString());
        payload.put("episodeId", r.getEpisodeId().toString());
        payload.put("handoverId", r.getHandoverId().toString());
        payload.put("status", r.getStatus());
        try {
            String json = objectMapper.writeValueAsString(payload);
            outboxRepository.save(OutboxEventEntity.create(
                    "MH_REFERRAL", r.getId().toString(), eventType, null,
                    r.getTenantId(), "national-spine", r.getId().toString(), "MH_REFERRAL", json));
        } catch (JsonProcessingException ex) {
            log.error("Referral event payload could not be serialised: {}", ex.getMessage());
        }
    }
}
