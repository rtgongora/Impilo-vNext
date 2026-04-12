package zw.gov.mohcc.impilo.community.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.community.api.dto.CompleteVisitRequest;
import zw.gov.mohcc.impilo.community.api.dto.CreateOutreachVisitRequest;
import zw.gov.mohcc.impilo.community.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.community.persistence.entity.OutreachVisitEntity;
import zw.gov.mohcc.impilo.community.persistence.repository.CommunityUnitRepository;
import zw.gov.mohcc.impilo.community.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.community.persistence.repository.OutreachVisitRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OutreachVisitService {

    private final OutreachVisitRepository outreachVisitRepository;
    private final CommunityUnitRepository communityUnitRepository;
    private final EventOutboxRepository eventOutboxRepository;
    private final ObjectMapper objectMapper;

    public OutreachVisitService(
            OutreachVisitRepository outreachVisitRepository,
            CommunityUnitRepository communityUnitRepository,
            EventOutboxRepository eventOutboxRepository,
            ObjectMapper objectMapper) {
        this.outreachVisitRepository = outreachVisitRepository;
        this.communityUnitRepository = communityUnitRepository;
        this.eventOutboxRepository = eventOutboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OutreachVisitEntity createVisit(CreateOutreachVisitRequest request) {
        if (communityUnitRepository.findByUnitId(request.unitId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown unit: " + request.unitId());
        }
        OutreachVisitEntity visit = new OutreachVisitEntity();
        visit.setTenantId(request.tenantId());
        visit.setUnitId(request.unitId());
        visit.setPatientCpid(request.patientCpid());
        visit.setProviderId(request.providerId());
        visit.setJourneyId(request.journeyId());
        visit.setEncounterId(request.encounterId());
        visit.setVisitType(request.visitType());
        visit.setLocationLat(request.locationLat());
        visit.setLocationLon(request.locationLon());
        visit.setScheduledAt(request.scheduledAt());
        visit.setStatus("SCHEDULED");
        return outreachVisitRepository.save(visit);
    }

    public List<OutreachVisitEntity> listVisits(UUID tenantId, UUID unitId) {
        if (tenantId != null && unitId != null) {
            return outreachVisitRepository.findByTenantIdAndUnitIdOrderByCreatedAtDesc(tenantId, unitId);
        }
        if (tenantId != null) {
            return outreachVisitRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        }
        return outreachVisitRepository.findAll();
    }

    public OutreachVisitEntity getVisit(UUID visitId) {
        return outreachVisitRepository
                .findByVisitId(visitId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Visit not found: " + visitId));
    }

    @Transactional
    public OutreachVisitEntity startVisit(UUID visitId) {
        OutreachVisitEntity visit = getVisit(visitId);
        if (!"SCHEDULED".equals(visit.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Visit cannot be started from status: " + visit.getStatus());
        }
        visit.setStatus("IN_PROGRESS");
        visit.setStartedAt(OffsetDateTime.now());
        visit = outreachVisitRepository.save(visit);

        appendOutboxEvent(
                "OUTREACH_VISIT",
                visit.getVisitId().toString(),
                "community.outreach_visit.started",
                visit.getTenantId(),
                visit.getPatientCpid(),
                buildVisitPayload(visit),
                visit.getVisitId() + "-start");

        return visit;
    }

    @Transactional
    public OutreachVisitEntity completeVisit(UUID visitId, CompleteVisitRequest body) {
        OutreachVisitEntity visit = getVisit(visitId);
        if (!"IN_PROGRESS".equals(visit.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Visit cannot be completed from status: " + visit.getStatus());
        }
        visit.setStatus("COMPLETED");
        visit.setCompletedAt(OffsetDateTime.now());
        if (body != null && body.notes() != null) {
            visit.setNotes(body.notes());
        }
        visit = outreachVisitRepository.save(visit);

        appendOutboxEvent(
                "OUTREACH_VISIT",
                visit.getVisitId().toString(),
                "community.outreach_visit.completed",
                visit.getTenantId(),
                visit.getPatientCpid(),
                buildVisitPayload(visit),
                visit.getVisitId() + "-complete");

        return visit;
    }

    private Map<String, Object> buildVisitPayload(OutreachVisitEntity visit) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("visit_id", visit.getVisitId().toString());
        m.put("tenant_id", visit.getTenantId().toString());
        m.put("unit_id", visit.getUnitId().toString());
        m.put("patient_cpid", visit.getPatientCpid());
        m.put("provider_id", visit.getProviderId());
        if (visit.getJourneyId() != null) {
            m.put("journey_id", visit.getJourneyId().toString());
        }
        if (visit.getEncounterId() != null) {
            m.put("encounter_id", visit.getEncounterId().toString());
        }
        m.put("visit_type", visit.getVisitType());
        m.put("status", visit.getStatus());
        if (visit.getScheduledAt() != null) {
            m.put("scheduled_at", visit.getScheduledAt().toString());
        }
        if (visit.getStartedAt() != null) {
            m.put("started_at", visit.getStartedAt().toString());
        }
        if (visit.getCompletedAt() != null) {
            m.put("completed_at", visit.getCompletedAt().toString());
        }
        if (visit.getLocationLat() != null) {
            m.put("location_lat", visit.getLocationLat().toPlainString());
        }
        if (visit.getLocationLon() != null) {
            m.put("location_lon", visit.getLocationLon().toPlainString());
        }
        return m;
    }

    private void appendOutboxEvent(
            String aggregateType,
            String aggregateId,
            String eventType,
            UUID tenantId,
            String subjectId,
            Map<String, Object> payload,
            String idempotencyKey) {
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType(aggregateType);
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setSchemaVersion(1);
        outbox.setTenantId(tenantId);
        outbox.setPodId(System.getenv("HOSTNAME") != null ? System.getenv("HOSTNAME") : "national-spine");
        outbox.setCorrelationId(UUID.randomUUID());
        outbox.setIdempotencyKey(idempotencyKey);
        outbox.setSubjectId(subjectId);
        outbox.setSubjectType("PATIENT");
        outbox.setPartitionKey(aggregateId);
        outbox.setOccurredAt(OffsetDateTime.now());
        try {
            outbox.setPayloadJson(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize outbox payload", e);
        }
        eventOutboxRepository.save(outbox);
    }
}
