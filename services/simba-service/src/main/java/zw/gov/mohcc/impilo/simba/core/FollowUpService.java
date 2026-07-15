package zw.gov.mohcc.impilo.simba.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.simba.persistence.entity.FollowUpActionEntity;
import zw.gov.mohcc.impilo.simba.persistence.repository.FollowUpActionRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Provider wellness follow-up actions. Created from a touchpoint / care-linkage / alert review /
 * assessment. Each creation writes a FOLLOW_UP timeline row and emits an outbox event; status changes
 * emit too. Simba owns the wellness follow-up lifecycle only — clinical work stays with PCT.
 */
@Service
public class FollowUpService {

    private final FollowUpActionRepository repository;
    private final TimelineService timeline;
    private final SimbaEventEmitter events;

    public FollowUpService(FollowUpActionRepository repository,
                           TimelineService timeline,
                           SimbaEventEmitter events) {
        this.repository = repository;
        this.timeline = timeline;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<FollowUpActionEntity> forPerson(UUID tenantId, String personCpid) {
        return repository.findByTenantIdAndPersonCpidOrderByCreatedAtDesc(tenantId, personCpid);
    }

    @Transactional(readOnly = true)
    public List<FollowUpActionEntity> forProvider(UUID tenantId, String providerId) {
        return repository.findByTenantIdAndCreatedByProviderOrderByCreatedAtDesc(tenantId, providerId);
    }

    @Transactional
    public FollowUpActionEntity create(UUID tenantId, String personCpid, String providerId,
                                       String originType, String originRef, String actionType,
                                       String title, String detail, LocalDate dueDate, String severity) {
        FollowUpActionEntity fu = new FollowUpActionEntity();
        fu.setTenantId(tenantId);
        fu.setPersonCpid(personCpid);
        fu.setCreatedByProvider(providerId);
        fu.setOriginType(originType == null ? "TOUCHPOINT" : originType);
        fu.setOriginRef(originRef);
        fu.setActionType(actionType == null ? "REVIEW" : actionType);
        fu.setTitle(title);
        fu.setDetail(detail);
        fu.setDueDate(dueDate);
        if (severity != null && !severity.isBlank()) {
            fu.setSeverity(severity);
        }
        fu = repository.save(fu);

        timeline.record(tenantId, personCpid, "FOLLOW_UP", title,
                detail != null ? detail : "A provider has planned a wellness follow-up with you.",
                "EMERGENCY".equals(fu.getSeverity()) ? "URGENT" : "ATTENTION",
                "FOLLOW_UP", fu.getFollowUpId().toString(), null);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("follow_up_id", fu.getFollowUpId().toString());
        payload.put("person_cpid", personCpid);
        payload.put("origin_type", fu.getOriginType());
        payload.put("action_type", fu.getActionType());
        payload.put("severity", fu.getSeverity());
        events.emit(tenantId, "WELLNESS_FOLLOW_UP", fu.getFollowUpId().toString(),
                "impilo.simba.wellness.followup.created.v1", personCpid,
                "simba:follow-up:" + fu.getFollowUpId(), payload);
        return fu;
    }

    @Transactional
    public FollowUpActionEntity updateStatus(UUID tenantId, UUID followUpId, String status) {
        FollowUpActionEntity fu = repository.findByFollowUpIdAndTenantId(followUpId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Follow-up not found"));
        fu.setStatus(status);
        if ("DONE".equals(status)) {
            fu.setCompletedAt(OffsetDateTime.now());
        }
        fu = repository.save(fu);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("follow_up_id", followUpId.toString());
        payload.put("status", status);
        events.emit(tenantId, "WELLNESS_FOLLOW_UP", followUpId.toString(),
                "impilo.simba.wellness.followup.updated.v1", fu.getPersonCpid(),
                "simba:follow-up:" + followUpId + ":" + status, payload);
        return fu;
    }
}
