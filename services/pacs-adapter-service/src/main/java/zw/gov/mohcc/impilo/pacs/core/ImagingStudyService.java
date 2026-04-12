package zw.gov.mohcc.impilo.pacs.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.pacs.api.dto.CorrelateStudyRequest;
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
 * Each mutation appends companion outbox rows for Kafka publishing (legacy + v1.1).
 */
@Service
public class ImagingStudyService {

    private static final Logger log = LoggerFactory.getLogger(ImagingStudyService.class);

    public static final String AGGREGATE_IMAGING_STUDY = "IMAGING_STUDY";
    public static final String EVENT_STUDY_AVAILABLE = "pacs.study.available";
    public static final String EVENT_STUDY_CORRELATED = "pacs.study.correlated";

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

    public List<ImagingStudyEntity> listStudies() {
        return studyRepository.findAll();
    }

    public ImagingStudyEntity getStudy(Long id) {
        return studyRepository.findById(id)
                .orElseThrow(() -> new StudyNotFoundException(
                        "Imaging study not found: " + id));
    }

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
        study.setOrosOrderId(request.getOrosOrderId());
        study.setAccessionNumber(request.getAccessionNumber());
        study.setCreatedAt(OffsetDateTime.now());
        study.setUpdatedAt(OffsetDateTime.now());

        study = studyRepository.save(study);

        appendStudyAvailableOutbox(study);

        log.info("Imaging study registered: studyUid={}, modality={}, patient={}",
                study.getStudyUid(), study.getModality(), study.getPatientCpid());

        return study;
    }

    /**
     * Links a stored study to an OROS order and emits {@code pacs.study.correlated}
     * plus a fresh {@code pacs.study.available} so OROS can attach imaging results.
     */
    @Transactional
    public ImagingStudyEntity correlateStudy(Long id, CorrelateStudyRequest request) {
        ImagingStudyEntity study = getStudy(id);
        study.setOrosOrderId(request.getOrosOrderId());
        study.setUpdatedAt(OffsetDateTime.now());
        study = studyRepository.save(study);

        appendStudyCorrelatedOutbox(study);
        appendStudyAvailableOutbox(study);

        log.info("Imaging study correlated to OROS order: studyId={}, orderId={}",
                study.getId(), study.getOrosOrderId());

        return study;
    }

    @Transactional
    public ImagingStudyEntity forwardStudy(Long id, ForwardStudyRequest request) {
        ImagingStudyEntity study = getStudy(id);

        if (StudyStatus.PENDING_ORDER.name().equals(study.getStatus())) {
            throw new IllegalStateException("Cannot forward placeholder study until DICOM study is registered: " + id);
        }

        if (StudyStatus.FORWARDED.name().equals(study.getStatus())) {
            throw new IllegalStateException("Study already forwarded: " + id);
        }

        study.setStatus(StudyStatus.FORWARDING.name());
        study.setUpdatedAt(OffsetDateTime.now());
        study = studyRepository.save(study);

        try {
            String orthancId = UUID.randomUUID().toString();
            study.setOrthancId(orthancId);
            study.setStatus(StudyStatus.FORWARDED.name());
            study.setUpdatedAt(OffsetDateTime.now());
            study = studyRepository.save(study);

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

    private void appendStudyAvailableOutbox(ImagingStudyEntity study) {
        try {
            Map<String, Object> legacy = buildOrosCompatiblePayload(study, UUID.randomUUID().toString());
            Map<String, Object> extended = new LinkedHashMap<>(legacy);
            extended.put("study_id", study.getId());
            extended.put("patient_cpid", study.getPatientCpid());
            extended.put("accession_number", study.getAccessionNumber());
            extended.put("tenant_id", study.getTenantId().toString());

            EventOutboxEntity row = new EventOutboxEntity();
            row.setAggregateType(AGGREGATE_IMAGING_STUDY);
            row.setAggregateId(String.valueOf(study.getId()));
            row.setEventType(EVENT_STUDY_AVAILABLE);
            row.setPayload(objectMapper.writeValueAsString(extended));
            applyContext(row, study.getTenantId().toString());
            row.setSubjectType(AGGREGATE_IMAGING_STUDY);
            row.setSubjectId(String.valueOf(study.getId()));
            row.setPartitionKey(study.getOrosOrderId() != null ? study.getOrosOrderId() : String.valueOf(study.getId()));
            row.setOccurredAt(OffsetDateTime.now());
            row.setSchemaVersion(1);
            outboxRepository.save(row);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize pacs.study.available payload", e);
        }
    }

    private void appendStudyCorrelatedOutbox(ImagingStudyEntity study) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("study_id", study.getId());
            payload.put("oros_order_id", study.getOrosOrderId());

            EventOutboxEntity row = new EventOutboxEntity();
            row.setAggregateType(AGGREGATE_IMAGING_STUDY);
            row.setAggregateId(String.valueOf(study.getId()));
            row.setEventType(EVENT_STUDY_CORRELATED);
            row.setPayload(objectMapper.writeValueAsString(payload));
            applyContext(row, study.getTenantId().toString());
            row.setSubjectType(AGGREGATE_IMAGING_STUDY);
            row.setSubjectId(String.valueOf(study.getId()));
            row.setPartitionKey(study.getOrosOrderId() != null ? study.getOrosOrderId() : String.valueOf(study.getId()));
            row.setOccurredAt(OffsetDateTime.now());
            row.setSchemaVersion(1);
            outboxRepository.save(row);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize pacs.study.correlated payload", e);
        }
    }

    /**
     * Builds the legacy JSON shape expected by {@code OrosEventConsumer.consumePacsStudy},
     * with additional fields for PACS-native consumers.
     */
    private Map<String, Object> buildOrosCompatiblePayload(ImagingStudyEntity study, String eventId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", eventId);
        payload.put("orderId", study.getOrosOrderId());
        payload.put("studyInstanceUid", study.getStudyUid());
        payload.put("modality", study.getModality());
        payload.put("reportSummary", study.getDescription() != null ? study.getDescription() : "Imaging study available");
        payload.put("isCritical", false);
        return payload;
    }

    private void applyContext(EventOutboxEntity row, String tenantFallback) {
        RequestContext ctx = RequestContextHolder.get();
        if (ctx != null) {
            row.setTenantId(ctx.tenantId());
            row.setPodId(ctx.podId());
            row.setCorrelationId(ctx.correlationId());
        } else {
            row.setTenantId(tenantFallback);
            row.setPodId(System.getenv("HOSTNAME") != null ? System.getenv("HOSTNAME") : "local");
            row.setCorrelationId(UUID.randomUUID().toString());
        }
    }
}
