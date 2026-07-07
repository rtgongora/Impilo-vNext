package zw.gov.mohcc.impilo.simba.core;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.simba.persistence.entity.PreventiveReminderEntity;
import zw.gov.mohcc.impilo.simba.persistence.repository.PreventiveReminderRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Preventive reminders. Simba schedules/tracks reminder state; Khuluma delivers. When a reminder is
 * due, {@link #fireDue} transitions it to FIRED, emits the Khuluma seam event
 * ({@code impilo.simba.wellness.reminder.due.v1}), writes a REMINDER timeline row, and records a
 * PENDING KHULUMA integration ledger row. If the reminder has a cadence, the next occurrence is
 * scheduled. Simba never fakes delivery — the outbox event is the functional handoff.
 */
@Service
public class PreventiveReminderService {

    private final PreventiveReminderRepository repository;
    private final SimbaEventEmitter events;
    private final TimelineService timeline;
    private final IntegrationEventService integrations;

    public PreventiveReminderService(PreventiveReminderRepository repository,
                                     SimbaEventEmitter events,
                                     TimelineService timeline,
                                     IntegrationEventService integrations) {
        this.repository = repository;
        this.events = events;
        this.timeline = timeline;
        this.integrations = integrations;
    }

    @Transactional(readOnly = true)
    public List<PreventiveReminderEntity> forPerson(UUID tenantId, String personCpid) {
        return repository.findByTenantIdAndPersonCpidOrderByDueAtAsc(tenantId, personCpid);
    }

    @Transactional
    public PreventiveReminderEntity schedule(UUID tenantId, String personCpid, String reminderType,
                                             String title, String detail, OffsetDateTime dueAt,
                                             Integer cadenceMonths, String channel, String sourceRef,
                                             String createdBy) {
        PreventiveReminderEntity r = new PreventiveReminderEntity();
        r.setTenantId(tenantId);
        r.setPersonCpid(personCpid);
        r.setReminderType(reminderType);
        r.setTitle(title);
        r.setDetail(detail);
        r.setDueAt(dueAt);
        r.setCadenceMonths(cadenceMonths);
        if (channel != null && !channel.isBlank()) {
            r.setChannel(channel);
        }
        r.setSourceRef(sourceRef);
        r.setCreatedBy(createdBy);
        return repository.save(r);
    }

    @Transactional
    public PreventiveReminderEntity snooze(UUID tenantId, UUID reminderId, OffsetDateTime until) {
        PreventiveReminderEntity r = require(tenantId, reminderId);
        r.setDueAt(until);
        r.setStatus("SNOOZED");
        return repository.save(r);
    }

    @Transactional
    public PreventiveReminderEntity complete(UUID tenantId, UUID reminderId) {
        PreventiveReminderEntity r = require(tenantId, reminderId);
        r.setStatus("COMPLETED");
        return repository.save(r);
    }

    @Transactional
    public PreventiveReminderEntity cancel(UUID tenantId, UUID reminderId) {
        PreventiveReminderEntity r = require(tenantId, reminderId);
        r.setStatus("CANCELLED");
        return repository.save(r);
    }

    /**
     * Fires all reminders whose due time has passed. Returns the count fired. Called by the scheduler;
     * kept transactional and idempotent (a fired reminder leaves the due-set immediately).
     */
    @Transactional
    public int fireDue(int batchSize) {
        List<PreventiveReminderEntity> due =
                repository.findDue(OffsetDateTime.now(), PageRequest.of(0, Math.max(1, batchSize)));
        int fired = 0;
        for (PreventiveReminderEntity r : due) {
            fireOne(r);
            fired++;
        }
        return fired;
    }

    private void fireOne(PreventiveReminderEntity r) {
        OffsetDateTime now = OffsetDateTime.now();
        r.setStatus("FIRED");
        r.setLastFiredAt(now);

        // Reschedule the next occurrence when a cadence is set (recurring screening/measurement).
        if (r.getCadenceMonths() != null && r.getCadenceMonths() > 0) {
            PreventiveReminderEntity next = new PreventiveReminderEntity();
            next.setTenantId(r.getTenantId());
            next.setPersonCpid(r.getPersonCpid());
            next.setProgrammeId(r.getProgrammeId());
            next.setReminderType(r.getReminderType());
            next.setTitle(r.getTitle());
            next.setDetail(r.getDetail());
            next.setDueAt(r.getDueAt().plusMonths(r.getCadenceMonths()));
            next.setCadenceMonths(r.getCadenceMonths());
            next.setChannel(r.getChannel());
            next.setSourceRef(r.getSourceRef());
            next.setCreatedBy(r.getCreatedBy());
            repository.save(next);
        }
        repository.save(r);

        // Khuluma delivery seam — the functional handoff is the outbox event.
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reminder_id", r.getReminderId().toString());
        payload.put("person_cpid", r.getPersonCpid());
        payload.put("reminder_type", r.getReminderType());
        payload.put("title", r.getTitle());
        payload.put("detail", r.getDetail());
        payload.put("channel", r.getChannel());
        events.emit(r.getTenantId(), "WELLNESS_REMINDER", r.getReminderId().toString(),
                "impilo.simba.wellness.reminder.due.v1", r.getPersonCpid(),
                "simba:reminder:" + r.getReminderId() + ":fired:" + now.toEpochSecond(), payload);

        // Person-facing timeline.
        timeline.record(r.getTenantId(), r.getPersonCpid(), "REMINDER",
                r.getTitle(), r.getDetail(), "INFO", "REMINDER", r.getReminderId().toString(), null);

        // Honest handoff ledger — PENDING until Khuluma acknowledges delivery.
        integrations.record(r.getTenantId(), r.getPersonCpid(), "KHULUMA",
                "wellness.reminder.deliver", "PENDING", payload, r.getReminderId().toString());
    }

    private PreventiveReminderEntity require(UUID tenantId, UUID reminderId) {
        return repository.findByReminderIdAndTenantId(reminderId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Reminder not found"));
    }
}
