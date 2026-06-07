package zw.gov.mohcc.impilo.live.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.live.domain.RegistrationStatus;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventEntity;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventRegistrationEntity;
import zw.gov.mohcc.impilo.live.persistence.repository.LiveEventRegistrationRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RegistrationService {

    private final LiveEventService eventService;
    private final LiveEventRegistrationRepository registrationRepository;
    private final LiveEventEmitter emitter;
    private final LiveIntegrationOrchestrator integrationOrchestrator;

    public RegistrationService(LiveEventService eventService,
                               LiveEventRegistrationRepository registrationRepository,
                               LiveEventEmitter emitter,
                               LiveIntegrationOrchestrator integrationOrchestrator) {
        this.eventService = eventService;
        this.registrationRepository = registrationRepository;
        this.emitter = emitter;
        this.integrationOrchestrator = integrationOrchestrator;
    }

    @Transactional
    public LiveEventRegistrationEntity register(UUID tenantId, UUID eventId,
                                                String participantId, String participantType,
                                                String inviteSource) {
        LiveEventEntity event = eventService.get(tenantId, eventId);
        if (event.getMaxParticipants() != null) {
            long count = registrationRepository.countByEventIdAndRegistrationStatus(
                    eventId, RegistrationStatus.REGISTERED.name());
            if (count >= event.getMaxParticipants()) {
                throw new IllegalStateException("Event is at capacity");
            }
        }
        LiveEventRegistrationEntity reg = registrationRepository
                .findByEventIdAndParticipantId(eventId, participantId)
                .orElseGet(LiveEventRegistrationEntity::new);
        reg.setEventId(eventId);
        reg.setParticipantId(participantId);
        reg.setParticipantType(participantType);
        reg.setRegistrationStatus(RegistrationStatus.REGISTERED.name());
        reg.setInviteSource(inviteSource);
        reg.setCancelledAt(null);
        LiveEventRegistrationEntity saved = registrationRepository.save(reg);
        emit(saved, event.getTenantId(), "impilo.live.registration.created.v1");
        integrationOrchestrator.notifyRegistration(event, participantId);
        return saved;
    }

    @Transactional
    public LiveEventRegistrationEntity cancel(UUID tenantId, UUID eventId, String participantId) {
        LiveEventEntity event = eventService.get(tenantId, eventId);
        LiveEventRegistrationEntity reg = registrationRepository
                .findByEventIdAndParticipantId(eventId, participantId)
                .orElseThrow(() -> new IllegalArgumentException("Registration not found"));
        reg.setRegistrationStatus(RegistrationStatus.CANCELLED.name());
        reg.setCancelledAt(OffsetDateTime.now());
        LiveEventRegistrationEntity saved = registrationRepository.save(reg);
        emit(saved, event.getTenantId(), "impilo.live.registration.cancelled.v1");
        return saved;
    }

    @Transactional
    public LiveEventRegistrationEntity saveBookmark(UUID tenantId, UUID eventId, String participantId, boolean saved) {
        eventService.get(tenantId, eventId);
        LiveEventRegistrationEntity reg = registrationRepository
                .findByEventIdAndParticipantId(eventId, participantId)
                .orElseGet(() -> {
                    LiveEventRegistrationEntity r = new LiveEventRegistrationEntity();
                    r.setEventId(eventId);
                    r.setParticipantId(participantId);
                    r.setParticipantType("PROVIDER");
                    r.setRegistrationStatus(RegistrationStatus.REGISTERED.name());
                    return r;
                });
        reg.setSaved(saved);
        return registrationRepository.save(reg);
    }

    @Transactional(readOnly = true)
    public List<LiveEventRegistrationEntity> listForEvent(UUID tenantId, UUID eventId) {
        eventService.get(tenantId, eventId);
        return registrationRepository.findByEventIdAndRegistrationStatus(
                eventId, RegistrationStatus.REGISTERED.name());
    }

    @Transactional(readOnly = true)
    public List<LiveEventRegistrationEntity> listForParticipant(String participantId) {
        return registrationRepository.findByParticipantIdAndRegistrationStatus(
                participantId, RegistrationStatus.REGISTERED.name());
    }

    @Transactional(readOnly = true)
    public List<LiveEventRegistrationEntity> listSaved(String participantId) {
        return registrationRepository.findByParticipantIdAndSavedTrue(participantId);
    }

    private void emit(LiveEventRegistrationEntity reg, UUID tenantId, String eventType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("registrationId", reg.getId().toString());
        payload.put("eventId", reg.getEventId().toString());
        payload.put("participantId", reg.getParticipantId());
        payload.put("status", reg.getRegistrationStatus());
        emitter.emit(tenantId, "LIVE_REGISTRATION", reg.getId().toString(),
                eventType, "LIVE_EVENT", reg.getEventId().toString(), payload);
    }
}
