package zw.gov.mohcc.impilo.pacs.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pacs.api.dto.CreateImagingStudyRequest;
import zw.gov.mohcc.impilo.pacs.api.dto.ForwardStudyRequest;
import zw.gov.mohcc.impilo.pacs.domain.StudyStatus;
import zw.gov.mohcc.impilo.pacs.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pacs.persistence.entity.ImagingStudyEntity;
import zw.gov.mohcc.impilo.pacs.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pacs.persistence.repository.ImagingStudyRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Core service for imaging study registration, retrieval, and forwarding to Orthanc.
 * Each mutation appends an event to the outbox for reliable Kafka publishing.
 */
@Service
public class ImagingStudyService {

    private static final Logger log = LoggerFactory.getLogger(ImagingStudyService.class);

    private final ImagingStudyRepository studyRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public ImagingStudyService(ImagingStudyRepository studyRepository,
                               EventOutboxRepository outboxRepository,
                               ObjectMapper objectMapper) {
        this.studyRepository = studyRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Lists all imaging studies.
     */
    public List<ImagingStudyEntity> listStudies() {
        return studyRepository.findAll();
    }

    /**
     * Retrieves a single imaging study by its database ID.
     */
    public ImagingStudyEntity getStudy(Long id) {
        return studyRepository.findById(id)
                .orElseThrow(() -> new StudyNotFoundException(
                        "Imaging study not found: " + id));
    }

    /**
     * Registers a new imaging study and writes a STUDY_REGISTERED outbox event.
     */
    @Transactional
    public ImagingStudyEntity registerStudy(CreateImagingStudyRequest request) {
        ImagingStudyEntity study = new ImagingStudyEntity();
        study.setTenantId(request.getTenantId());
        study.setPatientCpid(request.getPatientCpid());
        study.setStudyUid(request.getStudyUid());
        study.setModality(request.getModality());
        study.setDescription(request.getDescription());
        study.setStudyDate(request.getStudyDate());
        study.setStatus(StudyStatus.RECEIVED.name());
        study.setMetadata(request.getMetadata());
        study.setCreatedAt(OffsetDateTime.now());
        study.setUpdatedAt(OffsetDateTime.now());

        study = studyRepository.save(study);

        appendOutboxEvent(study.getTenantId(), "STUDY_REGISTERED", buildStudyPayload(study));

        log.info("Imaging study registered: studyUid={}, modality={}, patient={}",
                study.getStudyUid(), study.getModality(), study.getPatientCpid());

        return study;
    }

    /**
     * Forwards an imaging study to Orthanc. Updates status to FORWARDING,
     * then to FORWARDED upon success (or FAILED on error).
     * Writes a STUDY_FORWARDED outbox event.
     */
    @Transactional
    public ImagingStudyEntity forwardStudy(Long id, ForwardStudyRequest request) {
        ImagingStudyEntity study = getStudy(id);

        if (StudyStatus.FORWARDED.name().equals(study.getStatus())) {
            throw new IllegalStateException("Study already forwarded: " + id);
        }

        study.setStatus(StudyStatus.FORWARDING.name());
        study.setUpdatedAt(OffsetDateTime.now());
        study = studyRepository.save(study);

        try {
            // Simulate Orthanc forwarding — in production this would be an HTTP call
            String orthancId = UUID.randomUUID().toString();
            study.setOrthancId(orthancId);
            study.setStatus(StudyStatus.FORWARDED.name());
            study.setUpdatedAt(OffsetDateTime.now());
            study = studyRepository.save(study);

            Map<String, Object> payload = buildStudyPayload(study);
            payload.put("orthancUrl", request.getOrthancUrl());
            appendOutboxEvent(study.getTenantId(), "STUDY_FORWARDED", payload);

            log.info("Imaging study forwarded to Orthanc: studyUid={}, orthancId={}",
                    study.getStudyUid(), orthancId);
        } catch (Exception e) {
            study.setStatus(StudyStatus.FAILED.name());
            study.setUpdatedAt(OffsetDateTime.now());
            studyRepository.save(study);

            log.error("Failed to forward imaging study: studyUid={}", study.getStudyUid(), e);
            throw new RuntimeException("Failed to forward study to Orthanc", e);
        }

        return study;
    }

    private Map<String, Object> buildStudyPayload(ImagingStudyEntity study) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", study.getId());
        payload.put("tenantId", study.getTenantId().toString());
        payload.put("patientCpid", study.getPatientCpid());
        payload.put("studyUid", study.getStudyUid());
        payload.put("modality", study.getModality());
        payload.put("description", study.getDescription());
        payload.put("studyDate", study.getStudyDate() != null ? study.getStudyDate().toString() : null);
        payload.put("status", study.getStatus());
        payload.put("orthancId", study.getOrthancId());
        return payload;
    }

    private void appendOutboxEvent(UUID tenantId, String eventType, Map<String, Object> payload) {
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setTenantId(tenantId.toString());
        outbox.setPodId(System.getenv("HOSTNAME") != null ? System.getenv("HOSTNAME") : "local");
        outbox.setRequestId(UUID.randomUUID().toString());
        outbox.setCorrelationId(UUID.randomUUID().toString());
        outbox.setEventType(eventType);
        outbox.setPublished(false);
        try {
            outbox.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize outbox payload", e);
        }
        outboxRepository.save(outbox);
    }
}
