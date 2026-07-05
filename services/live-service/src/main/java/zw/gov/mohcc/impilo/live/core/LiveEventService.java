package zw.gov.mohcc.impilo.live.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.live.domain.LiveEventStatus;
import zw.gov.mohcc.impilo.live.domain.LiveMode;
import zw.gov.mohcc.impilo.live.domain.OwningService;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventEntity;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventPollEntity;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventPollResponseEntity;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventRoleAssignmentEntity;
import zw.gov.mohcc.impilo.live.persistence.repository.LiveEventPollRepository;
import zw.gov.mohcc.impilo.live.persistence.repository.LiveEventPollResponseRepository;
import zw.gov.mohcc.impilo.live.persistence.repository.LiveEventRepository;
import zw.gov.mohcc.impilo.live.persistence.repository.LiveEventRoleAssignmentRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class LiveEventService {

    private static final Logger log = LoggerFactory.getLogger(LiveEventService.class);

    private final LiveEventRepository eventRepository;
    private final LiveEventRoleAssignmentRepository roleRepository;
    private final LiveEventStateMachine stateMachine;
    private final LiveEventEmitter emitter;
    private final LiveIntegrationOrchestrator integrationOrchestrator;
    private final LiveGovernanceGuard governanceGuard;
    private final LiveEventPollRepository pollRepository;
    private final LiveEventPollResponseRepository pollResponseRepository;
    private final ObjectMapper objectMapper;

    public LiveEventService(LiveEventRepository eventRepository,
                            LiveEventRoleAssignmentRepository roleRepository,
                            LiveEventStateMachine stateMachine,
                            LiveEventEmitter emitter,
                            LiveIntegrationOrchestrator integrationOrchestrator,
                            LiveGovernanceGuard governanceGuard,
                            LiveEventPollRepository pollRepository,
                            LiveEventPollResponseRepository pollResponseRepository,
                            ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.roleRepository = roleRepository;
        this.stateMachine = stateMachine;
        this.emitter = emitter;
        this.integrationOrchestrator = integrationOrchestrator;
        this.governanceGuard = governanceGuard;
        this.pollRepository = pollRepository;
        this.pollResponseRepository = pollResponseRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Generic (non-clinical) creation path. Infers a canonical mode/owner for legacy callers and
     * enforces governance. Clinical sessions must use {@link #createClinical(LiveEventEntity)} so
     * they always carry clinical context — no free-floating clinical rooms (doctrine §2, §8).
     */
    @Transactional
    public LiveEventEntity create(LiveEventEntity event) {
        inferDoctrineDefaults(event);
        if (LiveMode.CLINICAL_SESSION.name().equals(event.getMode())) {
            throw LiveGovernanceException.invalid(
                    "CLINICAL_VIA_GENERIC_DENIED",
                    "Clinical sessions must be created via the clinical scheduling endpoint with consent and clinical context.");
        }
        return persistNew(event);
    }

    /** Clinical creation path used by Telemedicine/PCT; runs the full clinical guard. */
    @Transactional
    public LiveEventEntity createClinical(LiveEventEntity event) {
        if (event.getMode() == null) {
            event.setMode(LiveMode.CLINICAL_SESSION.name());
        }
        if (event.getOwningService() == null) {
            event.setOwningService(OwningService.TELEMEDICINE.name());
        }
        return persistNew(event);
    }

    private LiveEventEntity persistNew(LiveEventEntity event) {
        governanceGuard.validateForCreate(event);
        if (event.getStatus() == null) {
            event.setStatus(LiveEventStatus.DRAFT.name());
        }
        if (event.getTimezone() == null) {
            event.setTimezone("Africa/Harare");
        }
        if (event.getLanguage() == null) {
            event.setLanguage("en");
        }
        if (event.getOrganiserType() == null && event.getOwningService() != null) {
            event.setOrganiserType(OwningService.fromString(event.getOwningService()).legacyOrganiserType());
        }
        LiveEventEntity saved = eventRepository.save(event);
        emitEvent(saved, "impilo.live.event.created.v1");
        return saved;
    }

    private void inferDoctrineDefaults(LiveEventEntity event) {
        if (event.getMode() == null || event.getMode().isBlank()) {
            event.setMode(LiveMode.inferLegacy(
                    event.getEventType(), event.getContextType(), event.getOrganiserType()).name());
        }
        if (event.getOwningService() == null || event.getOwningService().isBlank()) {
            OwningService owner = switch (LiveMode.valueOf(event.getMode())) {
                case CLINICAL_SESSION -> OwningService.TELEMEDICINE;
                case WEBINAR_CPD -> event.getLinkedFundoCourseId() != null
                        ? OwningService.FUNDO : OwningService.STANDALONE_IMPILO_LIVE;
                case PUBLIC_BROADCAST, EMERGENCY_BRIEFING -> OwningService.STANDALONE_IMPILO_LIVE;
                default -> OwningService.STANDALONE_IMPILO_LIVE;
            };
            event.setOwningService(owner.name());
        }
    }

    /**
     * Create a governed event, assign its hosts/speakers/moderators, and schedule it in one
     * transaction. Used by the typed scheduling endpoints (clinical / Fundo / public broadcast).
     */
    @Transactional
    public LiveEventEntity createAndSchedule(LiveEventEntity event,
                                             List<RoleAssignment> roles,
                                             String actor,
                                             boolean clinical) {
        LiveEventEntity created = clinical ? createClinical(event) : create(event);
        if (roles != null) {
            for (RoleAssignment r : roles) {
                if (r == null || r.userId() == null || r.userId().isBlank()) {
                    continue;
                }
                assignRole(created.getTenantId(), created.getId(), r.userId(),
                        r.participantType() != null ? r.participantType() : "PROVIDER",
                        r.role(), actor != null ? actor : created.getCreatedBy());
            }
        }
        return schedule(created.getTenantId(), created.getId(),
                actor != null ? actor : created.getCreatedBy());
    }

    /** A role assignment to apply when scheduling an event. */
    public record RoleAssignment(String userId, String participantType, String role) {}

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
        emitPollResults(saved);
        return saved;
    }

    /**
     * W3: on event end, publish the aggregated poll results
     * ({@code impilo.live.poll.results.v1}) so learning-side consumers can bridge
     * interaction truth without querying live-service. Emitted only when the
     * event had polls; failures never block the ENDED transition.
     */
    private void emitPollResults(LiveEventEntity event) {
        try {
            List<LiveEventPollEntity> polls =
                    pollRepository.findByEventIdOrderByCreatedAtDesc(event.getId());
            if (polls.isEmpty()) {
                return;
            }
            List<Map<String, Object>> pollViews = new ArrayList<>();
            for (LiveEventPollEntity poll : polls) {
                List<LiveEventPollResponseEntity> responses =
                        pollResponseRepository.findByPollId(poll.getId());
                Map<String, Integer> counts = new LinkedHashMap<>();
                for (String option : parseOptions(poll.getOptions())) {
                    counts.put(option, 0);
                }
                for (LiveEventPollResponseEntity response : responses) {
                    counts.merge(response.getSelectedOption(), 1, Integer::sum);
                }
                Map<String, Object> view = new LinkedHashMap<>();
                view.put("pollId", poll.getId().toString());
                view.put("question", poll.getQuestion());
                view.put("options", parseOptions(poll.getOptions()));
                view.put("counts", counts);
                view.put("totalResponses", responses.size());
                pollViews.add(view);
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("eventId", event.getId().toString());
            payload.put("polls", pollViews);
            emitter.emit(event.getTenantId(), "LIVE_EVENT", event.getId().toString(),
                    "impilo.live.poll.results.v1", "LIVE_EVENT", event.getId().toString(), payload);
        } catch (Exception e) {
            log.warn("Failed to emit poll results for ended event {}: {}", event.getId(), e.getMessage());
        }
    }

    private List<String> parseOptions(String optionsJson) {
        List<String> options = new ArrayList<>();
        if (optionsJson == null || optionsJson.isBlank()) {
            return options;
        }
        try {
            JsonNode node = objectMapper.readTree(optionsJson);
            if (node.isArray()) {
                node.forEach(o -> options.add(o.isTextual() ? o.asText() : o.toString()));
            }
        } catch (Exception e) {
            log.debug("Unparseable poll options '{}' — treated as empty", optionsJson);
        }
        return options;
    }

    @Transactional
    public LiveEventEntity beginReplayProcessing(UUID tenantId, UUID eventId, String updatedBy) {
        LiveEventEntity event = get(tenantId, eventId);
        stateMachine.transition(event, LiveEventStatus.PROCESSING_REPLAY, updatedBy);
        event.setUpdatedAt(OffsetDateTime.now());
        LiveEventEntity saved = eventRepository.save(event);
        emitEvent(saved, "impilo.live.replay.processing.v1");
        return saved;
    }

    @Transactional
    public LiveEventEntity publishReplay(UUID tenantId, UUID eventId, String updatedBy) {
        return publishReplay(tenantId, eventId, updatedBy, Map.of());
    }

    /**
     * Publish the replay with extra payload merged into the emitted
     * {@code impilo.live.replay.published.v1} event (e.g. documentObjectId and
     * durationSeconds of the recording artifact backing the replay).
     */
    @Transactional
    public LiveEventEntity publishReplay(UUID tenantId, UUID eventId, String updatedBy,
                                         Map<String, Object> extraPayload) {
        LiveEventEntity event = get(tenantId, eventId);
        stateMachine.transition(event, LiveEventStatus.PUBLISHED_REPLAY, updatedBy);
        event.setUpdatedAt(OffsetDateTime.now());
        LiveEventEntity saved = eventRepository.save(event);
        emitEvent(saved, "impilo.live.replay.published.v1", extraPayload);
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
        emitEvent(event, eventType, Map.of());
    }

    private void emitEvent(LiveEventEntity event, String eventType, Map<String, Object> extraPayload) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", event.getId().toString());
        payload.put("title", event.getTitle());
        payload.put("status", event.getStatus());
        payload.put("contextType", event.getContextType());
        payload.put("facilityId", event.getFacilityId());
        if (extraPayload != null) {
            payload.putAll(extraPayload);
        }
        emitter.emit(event.getTenantId(), "LIVE_EVENT", event.getId().toString(),
                eventType, "LIVE_EVENT", event.getId().toString(), payload);
    }
}
