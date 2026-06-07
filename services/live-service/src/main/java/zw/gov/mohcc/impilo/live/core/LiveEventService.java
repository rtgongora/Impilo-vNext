package zw.gov.mohcc.impilo.live.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.live.domain.LiveEventStatus;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventEntity;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventRoleAssignmentEntity;
import zw.gov.mohcc.impilo.live.persistence.repository.LiveEventRepository;
import zw.gov.mohcc.impilo.live.persistence.repository.LiveEventRoleAssignmentRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class LiveEventService {

    private final LiveEventRepository eventRepository;
    private final LiveEventRoleAssignmentRepository roleRepository;
    private final LiveEventStateMachine stateMachine;
    private final LiveEventEmitter emitter;
    private final LiveIntegrationOrchestrator integrationOrchestrator;

    public LiveEventService(LiveEventRepository eventRepository,
                            LiveEventRoleAssignmentRepository roleRepository,
                            LiveEventStateMachine stateMachine,
                            LiveEventEmitter emitter,
                            LiveIntegrationOrchestrator integrationOrchestrator) {
        this.eventRepository = eventRepository;
        this.roleRepository = roleRepository;
        this.stateMachine = stateMachine;
        this.emitter = emitter;
        this.integrationOrchestrator = integrationOrchestrator;
    }

    @Transactional
    public LiveEventEntity create(LiveEventEntity event) {
        if (event.getStatus() == null) {
            event.setStatus(LiveEventStatus.DRAFT.name());
        }
        if (event.getTimezone() == null) {
            event.setTimezone("Africa/Harare");
        }
        if (event.getLanguage() == null) {
            event.setLanguage("en");
        }
        LiveEventEntity saved = eventRepository.save(event);
        emitEvent(saved, "impilo.live.event.created.v1");
        return saved;
    }

    @Transactional(readOnly = true)
    public LiveEventEntity get(UUID tenantId, UUID eventId) {
        return eventRepository.findByIdAndTenantId(eventId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));
    }

    @Transactional
    public LiveEventEntity update(UUID tenantId, UUID eventId, LiveEventEntity updates, String updatedBy) {
        LiveEventEntity event = get(tenantId, eventId);
        if (updates.getTitle() != null) event.setTitle(updates.getTitle());
        if (updates.getDescription() != null) event.setDescription(updates.getDescription());
        if (updates.getStartTime() != null) event.setStartTime(updates.getStartTime());
        if (updates.getEndTime() != null) event.setEndTime(updates.getEndTime());
        if (updates.getAgenda() != null) event.setAgenda(updates.getAgenda());
        if (updates.getObjectives() != null) event.setObjectives(updates.getObjectives());
        if (updates.getMaxParticipants() != null) event.setMaxParticipants(updates.getMaxParticipants());
        event.setUpdatedBy(updatedBy);
        event.setUpdatedAt(OffsetDateTime.now());
        LiveEventEntity saved = eventRepository.save(event);
        emitEvent(saved, "impilo.live.event.updated.v1");
        return saved;
    }

    @Transactional
    public LiveEventEntity schedule(UUID tenantId, UUID eventId, String updatedBy) {
        LiveEventEntity event = get(tenantId, eventId);
        stateMachine.transition(event, LiveEventStatus.SCHEDULED, updatedBy);
        event.setUpdatedAt(OffsetDateTime.now());
        LiveEventEntity saved = eventRepository.save(event);
        emitEvent(saved, "impilo.live.event.scheduled.v1");
        integrationOrchestrator.notifyEventScheduled(saved);
        return saved;
    }

    @Transactional
    public LiveEventEntity cancel(UUID tenantId, UUID eventId, String updatedBy) {
        LiveEventEntity event = get(tenantId, eventId);
        stateMachine.transition(event, LiveEventStatus.CANCELLED, updatedBy);
        event.setUpdatedAt(OffsetDateTime.now());
        LiveEventEntity saved = eventRepository.save(event);
        emitEvent(saved, "impilo.live.event.cancelled.v1");
        return saved;
    }

    @Transactional
    public LiveEventEntity goLive(UUID tenantId, UUID eventId, String updatedBy) {
        LiveEventEntity event = get(tenantId, eventId);
        stateMachine.transition(event, LiveEventStatus.LIVE, updatedBy);
        event.setUpdatedAt(OffsetDateTime.now());
        LiveEventEntity saved = eventRepository.save(event);
        emitEvent(saved, "impilo.live.event.started.v1");
        return saved;
    }

    @Transactional
    public LiveEventEntity end(UUID tenantId, UUID eventId, String updatedBy) {
        LiveEventEntity event = get(tenantId, eventId);
        stateMachine.transition(event, LiveEventStatus.ENDED, updatedBy);
        event.setUpdatedAt(OffsetDateTime.now());
        LiveEventEntity saved = eventRepository.save(event);
        emitEvent(saved, "impilo.live.event.ended.v1");
        return saved;
    }

    @Transactional(readOnly = true)
    public List<LiveEventEntity> listByStatus(UUID tenantId, String status) {
        return eventRepository.findByTenantIdAndStatusOrderByStartTimeAsc(tenantId, status);
    }

    @Transactional(readOnly = true)
    public List<LiveEventEntity> discoverByContext(UUID tenantId, String contextType) {
        return eventRepository.findByTenantIdAndContextTypeAndStatus(
                tenantId, contextType, LiveEventStatus.SCHEDULED.name());
    }

    @Transactional(readOnly = true)
    public List<LiveEventEntity> discoverByFacility(UUID tenantId, String facilityId) {
        return eventRepository.findByTenantIdAndFacilityIdAndStatus(
                tenantId, facilityId, LiveEventStatus.SCHEDULED.name());
    }

    @Transactional(readOnly = true)
    public List<LiveEventEntity> discoverByRole(UUID tenantId, String userId, String role) {
        List<UUID> eventIds = roleRepository.findByUserIdAndRole(userId, role).stream()
                .map(LiveEventRoleAssignmentEntity::getEventId)
                .distinct()
                .toList();
        return eventIds.stream()
                .map(id -> eventRepository.findByIdAndTenantId(id, tenantId).orElse(null))
                .filter(e -> e != null && LiveEventStatus.SCHEDULED.name().equals(e.getStatus()))
                .toList();
    }

    @Transactional
    public LiveEventRoleAssignmentEntity assignRole(UUID tenantId, UUID eventId,
                                                     String userId, String participantType,
                                                     String role, String assignedBy) {
        get(tenantId, eventId);
        LiveEventRoleAssignmentEntity assignment = roleRepository
                .findByEventIdAndUserIdAndRole(eventId, userId, role)
                .orElseGet(LiveEventRoleAssignmentEntity::new);
        assignment.setEventId(eventId);
        assignment.setUserId(userId);
        assignment.setParticipantType(participantType);
        assignment.setRole(role);
        assignment.setAssignedBy(assignedBy);
        return roleRepository.save(assignment);
    }

    private void emitEvent(LiveEventEntity event, String eventType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", event.getId().toString());
        payload.put("title", event.getTitle());
        payload.put("status", event.getStatus());
        payload.put("contextType", event.getContextType());
        payload.put("facilityId", event.getFacilityId());
        emitter.emit(event.getTenantId(), "LIVE_EVENT", event.getId().toString(),
                eventType, "LIVE_EVENT", event.getId().toString(), payload);
    }
}
