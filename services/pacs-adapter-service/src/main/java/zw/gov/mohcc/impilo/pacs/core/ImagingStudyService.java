package zw.gov.mohcc.impilo.pacs.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.pacs.api.dto.AnnotationRequest;
import zw.gov.mohcc.impilo.pacs.api.dto.CorrelateStudyRequest;
import zw.gov.mohcc.impilo.pacs.api.dto.CreateImagingStudyRequest;
import zw.gov.mohcc.impilo.pacs.api.dto.ForwardStudyRequest;
import zw.gov.mohcc.impilo.pacs.api.dto.ImagingSearchRequest;
import zw.gov.mohcc.impilo.pacs.api.dto.LaunchViewerRequest;
import zw.gov.mohcc.impilo.pacs.api.dto.OrderLinkRequest;
import zw.gov.mohcc.impilo.pacs.api.dto.ReportLinkRequest;
import zw.gov.mohcc.impilo.pacs.domain.StudyStatus;
import zw.gov.mohcc.impilo.pacs.events.ImagingPipelineKafkaContracts;
import zw.gov.mohcc.impilo.pacs.events.PacsOutboxPublisher;
import zw.gov.mohcc.impilo.pacs.integration.PacsBackendClient;
import zw.gov.mohcc.impilo.pacs.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pacs.persistence.entity.ImagingAccessAuditEntity;
import zw.gov.mohcc.impilo.pacs.persistence.entity.ImagingAnnotationEntity;
import zw.gov.mohcc.impilo.pacs.persistence.entity.ImagingInstanceEntity;
import zw.gov.mohcc.impilo.pacs.persistence.entity.ImagingOrderLinkEntity;
import zw.gov.mohcc.impilo.pacs.persistence.entity.ImagingReportLinkEntity;
import zw.gov.mohcc.impilo.pacs.persistence.entity.ImagingSeriesEntity;
import zw.gov.mohcc.impilo.pacs.persistence.entity.ImagingStudyEntity;
import zw.gov.mohcc.impilo.pacs.persistence.entity.ImagingViewerSessionEntity;
import zw.gov.mohcc.impilo.pacs.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pacs.persistence.repository.ImagingAccessAuditRepository;
import zw.gov.mohcc.impilo.pacs.persistence.repository.ImagingAnnotationRepository;
import zw.gov.mohcc.impilo.pacs.persistence.repository.ImagingInstanceRepository;
import zw.gov.mohcc.impilo.pacs.persistence.repository.ImagingOrderLinkRepository;
import zw.gov.mohcc.impilo.pacs.persistence.repository.ImagingReportLinkRepository;
import zw.gov.mohcc.impilo.pacs.persistence.repository.ImagingSeriesRepository;
import zw.gov.mohcc.impilo.pacs.persistence.repository.ImagingStudyRepository;
import zw.gov.mohcc.impilo.pacs.persistence.repository.ImagingViewerSessionRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Core service for imaging study registration, retrieval, Orthanc forwarding, hierarchy, viewer sessions,
 * and report/order linkage. Mutations append companion outbox rows for Kafka publishing.
 */
@Service
public class ImagingStudyService {

    private static final Logger log = LoggerFactory.getLogger(ImagingStudyService.class);

    public static final String AGGREGATE_IMAGING_STUDY = "IMAGING_STUDY";
    public static final String EVENT_STUDY_AVAILABLE = "pacs.study.available";
    public static final String EVENT_STUDY_CORRELATED = "pacs.study.correlated";
    public static final String EVENT_VIEWER_LAUNCHED = "imaging.viewer.launched";
    public static final String EVENT_REPORT_LINKED = "imaging.report.linked";
    public static final String EVENT_ORDER_LINKED = "imaging.study.linked_to_order";
    public static final String EVENT_ANNOTATION_CREATED = "imaging.annotation.created";

    private final ImagingStudyRepository studyRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final ImagingHierarchySyncService hierarchySyncService;
    private final ImagingSeriesRepository seriesRepository;
    private final ImagingInstanceRepository instanceRepository;
    private final ImagingViewerSessionRepository viewerSessionRepository;
    private final ImagingAccessAuditRepository accessAuditRepository;
    private final ImagingReportLinkRepository reportLinkRepository;
    private final ImagingOrderLinkRepository orderLinkRepository;
    private final ImagingAnnotationRepository annotationRepository;
    private final PacsBackendClient pacsBackendClient;
    private final ViewerEnginePolicy viewerEnginePolicy;
    private final ObjectProvider<PacsOutboxPublisher> outboxPublisherProvider;
    private final boolean allowPlaceholderOrthancForward;

    public ImagingStudyService(
            ImagingStudyRepository studyRepository,
            EventOutboxRepository outboxRepository,
            ObjectMapper objectMapper,
            PacsBackendClient pacsBackendClient,
            ViewerEnginePolicy viewerEnginePolicy,
            ObjectProvider<PacsOutboxPublisher> outboxPublisherProvider,
            @Value("${impilo.orthanc.allow-placeholder-forward:false}") boolean allowPlaceholderOrthancForward,
            ImagingHierarchySyncService hierarchySyncService,
            ImagingSeriesRepository seriesRepository,
            ImagingInstanceRepository instanceRepository,
            ImagingViewerSessionRepository viewerSessionRepository,
            ImagingAccessAuditRepository accessAuditRepository,
            ImagingReportLinkRepository reportLinkRepository,
            ImagingOrderLinkRepository orderLinkRepository,
            ImagingAnnotationRepository annotationRepository) {
        this.studyRepository = studyRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.pacsBackendClient = pacsBackendClient;
        this.viewerEnginePolicy = viewerEnginePolicy;
        this.outboxPublisherProvider = outboxPublisherProvider;
        this.allowPlaceholderOrthancForward = allowPlaceholderOrthancForward;
        this.hierarchySyncService = hierarchySyncService;
        this.seriesRepository = seriesRepository;
        this.instanceRepository = instanceRepository;
        this.viewerSessionRepository = viewerSessionRepository;
        this.accessAuditRepository = accessAuditRepository;
        this.reportLinkRepository = reportLinkRepository;
        this.orderLinkRepository = orderLinkRepository;
        this.annotationRepository = annotationRepository;
    }

    public List<ImagingStudyEntity> listStudies() {
        return studyRepository.findAll();
    }

    public List<ImagingStudyEntity> listStudiesFiltered(String patientCpid) {
        if (patientCpid != null && !patientCpid.isBlank()) {
            return studyRepository.findByPatientCpidOrderByStudyDateDesc(patientCpid.trim());
        }
        return studyRepository.findAll();
    }

    public List<ImagingStudyEntity> searchStudies(ImagingSearchRequest request, String actorId, String actorType) {
        List<ImagingStudyEntity> base = listStudiesFiltered(request.getPatientCpid());
        OffsetDateTime from = parseOptionalDate(request.getStudyDateFrom());
        OffsetDateTime to = parseOptionalDate(request.getStudyDateTo());

        List<ImagingStudyEntity> filtered = base.stream()
                .filter(s -> request.getStudyUid() == null || request.getStudyUid().isBlank()
                        || request.getStudyUid().equalsIgnoreCase(s.getStudyUid()))
                .filter(s -> request.getAccessionNumber() == null || request.getAccessionNumber().isBlank()
                        || (s.getAccessionNumber() != null
                        && s.getAccessionNumber().equalsIgnoreCase(request.getAccessionNumber())))
                .filter(s -> request.getModality() == null || request.getModality().isBlank()
                        || request.getModality().equalsIgnoreCase(s.getModality()))
                .filter(s -> request.getFacilityId() == null || request.getFacilityId().isBlank()
                        || (s.getFacilityId() != null && s.getFacilityId().equalsIgnoreCase(request.getFacilityId())))
                .filter(s -> request.getReportStatus() == null || request.getReportStatus().isBlank()
                        || (s.getReportStatus() != null
                        && s.getReportStatus().equalsIgnoreCase(request.getReportStatus())))
                .filter(s -> from == null || !s.getStudyDate().isBefore(from))
                .filter(s -> to == null || !s.getStudyDate().isAfter(to))
                .toList();

        recordAccess(null, null, null, actorId, actorType, "SEARCH", "imaging_search");
        appendAccessRecordedOutbox(filtered.stream().findFirst().orElse(null), "SEARCH");

        return filtered;
    }

    private static OffsetDateTime parseOptionalDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public ImagingStudyEntity getStudy(Long id) {
        return studyRepository.findById(id)
                .orElseThrow(() -> new StudyNotFoundException(
                        "Imaging study not found: " + id));
    }

    public List<ImagingSeriesEntity> listSeriesForStudy(Long studyId) {
        ImagingStudyEntity study = getStudy(studyId);
        recordAccess(study.getId(), null, null, null, null, "SERIES_LIST", null);
        return seriesRepository.findByStudy_IdOrderBySeriesNumberAscIdAsc(study.getId());
    }

    public List<ImagingInstanceEntity> listInstancesForSeries(Long studyId, Long seriesId) {
        ImagingStudyEntity study = getStudy(studyId);
        ImagingSeriesEntity series = seriesRepository.findById(seriesId)
                .orElseThrow(() -> new StudyNotFoundException("Imaging series not found: " + seriesId));
        if (series.getStudy() == null || !series.getStudy().getId().equals(study.getId())) {
            throw new IllegalArgumentException("Series does not belong to study " + studyId);
        }
        recordAccess(study.getId(), series.getId(), null, null, null, "INSTANCE_LIST", null);
        return instanceRepository.findBySeries_IdOrderByInstanceNumberAscIdAsc(series.getId());
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
        study.setEncounterRef(request.getEncounterRef());
        study.setFacilityId(request.getFacilityId());
        study.setCreatedAt(OffsetDateTime.now());
        study.setUpdatedAt(OffsetDateTime.now());

        study = studyRepository.save(study);

        appendStudyAvailableOutbox(study);
        if (study.getOrosOrderId() == null || study.getOrosOrderId().isBlank()) {
            appendImagingPipelineOutbox(study, ImagingPipelineKafkaContracts.STUDY_UNMATCHED);
        }

        log.info("Imaging study registered: studyUid={}, modality={}, patient={}",
                study.getStudyUid(), study.getModality(), study.getPatientCpid());

        return study;
    }

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
            List<String> matches = pacsBackendClient.findStudyIdsByStudyInstanceUid(study.getStudyUid());
            String orthancId;
            if (!matches.isEmpty()) {
                orthancId = matches.get(0);
                if (matches.size() > 1) {
                    log.warn("Multiple Orthanc studies for StudyInstanceUID={}, using first orthancId={}",
                            study.getStudyUid(), orthancId);
                }
            } else if (allowPlaceholderOrthancForward) {
                orthancId = UUID.randomUUID().toString();
                log.warn("No Orthanc study for StudyInstanceUID={}; placeholder orthanc_id issued (dev flag)",
                        study.getStudyUid());
            } else {
                throw new IllegalStateException(
                        "No PACS study found for StudyInstanceUID=" + study.getStudyUid()
                                + " — ingest to configured PACS backend or enable impilo.orthanc.allow-placeholder-forward for dev.");
            }

            study.setOrthancId(orthancId);
            study.setStatus(StudyStatus.FORWARDED.name());
            study.setUpdatedAt(OffsetDateTime.now());
            study = studyRepository.save(study);

            log.info("Imaging study forwarded to PACS backend: studyUid={}, backendId={}, provider={}",
                    study.getStudyUid(), orthancId, pacsBackendClient.backendProvider());
        } catch (Exception e) {
            study.setStatus(StudyStatus.FAILED.name());
            study.setUpdatedAt(OffsetDateTime.now());
            studyRepository.save(study);

            log.error("Failed to forward imaging study: studyUid={}", study.getStudyUid(), e);
            throw new RuntimeException("Failed to forward study to configured PACS backend", e);
        }

        return study;
    }

    @Transactional
    public ImagingStudyEntity syncStudyHierarchy(Long id, String actorId, String actorType) {
        return hierarchySyncService.syncFromOrthanc(id, actorId, actorType);
    }

    @Transactional
    public ImagingViewerSessionEntity launchViewerSession(Long studyId, LaunchViewerRequest request,
                                                          String actorId, String actorType) {
        ImagingStudyEntity study = getStudy(studyId);
        String viewerEngine = viewerEnginePolicy.resolve(request.getViewerType());

        ImagingViewerSessionEntity session = new ImagingViewerSessionEntity();
        session.setStudyId(study.getId());
        session.setLaunchedBy(actorId);
        session.setViewerType(viewerEngine);
        session.setContextRef(request.getContextRef());
        session.setScope(request.getScope());
        RequestContext ctx = RequestContextHolder.get();
        if (ctx != null) {
            session.setCorrelationId(ctx.correlationId());
        }
        session = viewerSessionRepository.save(session);

        recordAccess(study.getId(), null, null, actorId, actorType, "VIEWER_LAUNCH", request.getContextRef());
        appendViewerLaunchedOutbox(study, session.getId(), request);

        return session;
    }

    @Transactional
    public void recordViewerAccessDenied(Long studyId, String denialCode, String actorId, String actorType) {
        ImagingStudyEntity study = getStudy(studyId);
        recordAccess(study.getId(), null, null, actorId, actorType, "VIEWER_DENIED", denialCode);
        appendImagingPipelineOutbox(study, ImagingPipelineKafkaContracts.VIEWER_ACCESS_DENIED);
    }

    public Map<String, Object> viewerLaunchContext(Long studyId, String requestedViewerType, String actorId, String actorType) {
        ImagingStudyEntity study = getStudy(studyId);
        String viewerEngine = viewerEnginePolicy.resolve(requestedViewerType);
        recordAccess(study.getId(), null, null, actorId, actorType, "VIEWER_CONTEXT", "viewer_launch_context");

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("studyId", study.getId());
        context.put("patientCpid", study.getPatientCpid());
        context.put("studyUid", study.getStudyUid());
        context.put("accessionNumber", study.getAccessionNumber());
        context.put("orosOrderId", study.getOrosOrderId());
        context.put("encounterRef", study.getEncounterRef());
        context.put("facilityId", study.getFacilityId());
        context.put("viewerEngine", viewerEngine);
        context.put("viewerPolicy", viewerEnginePolicy.describe());
        context.put("backend", Map.of(
                "provider", pacsBackendClient.backendProvider(),
                "mode", pacsBackendClient.backendMode(),
                "capabilities", pacsBackendClient.capabilities()
        ));
        return context;
    }

    @Transactional
    public ImagingReportLinkEntity linkReport(Long studyId, ReportLinkRequest request,
                                              String actorId, String actorType) {
        ImagingStudyEntity study = getStudy(studyId);

        ImagingReportLinkEntity link = new ImagingReportLinkEntity();
        link.setStudyId(study.getId());
        link.setReportRef(request.getReportRef());
        link.setReportingProviderRef(request.getReportingProviderRef());
        link.setReportStatus(request.getReportStatus());
        link = reportLinkRepository.save(link);

        study.setReportStatus(request.getReportStatus());
        study.setUpdatedAt(OffsetDateTime.now());
        studyRepository.save(study);

        recordAccess(study.getId(), null, null, actorId, actorType, "REPORT_LINK", request.getReportRef());
        appendReportLinkedOutbox(study, link.getId(), request.getReportRef());

        return link;
    }

    @Transactional
    public ImagingOrderLinkEntity linkOrder(Long studyId, OrderLinkRequest request,
                                            String actorId, String actorType) {
        ImagingStudyEntity study = getStudy(studyId);

        ImagingOrderLinkEntity link = new ImagingOrderLinkEntity();
        link.setStudyId(study.getId());
        link.setOrderRef(request.getOrderRef());
        link.setOrderingProviderRef(request.getOrderingProviderRef());
        link.setStatus(request.getStatus() != null ? request.getStatus() : "LINKED");
        link.setRequestedAt(OffsetDateTime.now());
        link = orderLinkRepository.save(link);

        recordAccess(study.getId(), null, null, actorId, actorType, "ORDER_LINK", request.getOrderRef());
        appendOrderLinkedOutbox(study, request.getOrderRef());

        return link;
    }

    public List<ImagingReportLinkEntity> listReportLinks(Long studyId) {
        getStudy(studyId);
        return reportLinkRepository.findByStudyIdOrderByCreatedAtDesc(studyId);
    }

    @Transactional
    public ImagingAnnotationEntity createAnnotation(Long studyId, AnnotationRequest request,
                                                    String actorId, String actorType) {
        ImagingStudyEntity study = getStudy(studyId);

        ImagingAnnotationEntity annotation = new ImagingAnnotationEntity();
        annotation.setStudyId(study.getId());
        annotation.setSeriesId(request.getSeriesId());
        annotation.setInstanceId(request.getInstanceId());
        annotation.setAnnotationType(request.getAnnotationType());
        annotation.setNote(request.getNote());
        annotation.setMeasurement(request.getMeasurement());
        annotation.setBody(request.getBody());
        annotation.setActorId(actorId);
        annotation.setActorType(actorType);
        annotation.setFacilityId(request.getFacilityId() != null ? request.getFacilityId() : study.getFacilityId());
        annotation = annotationRepository.save(annotation);

        recordAccess(study.getId(), request.getSeriesId(), request.getInstanceId(),
                actorId, actorType, "ANNOTATION_CREATE", request.getAnnotationType());
        appendAnnotationCreatedOutbox(study, annotation);

        return annotation;
    }

    public List<ImagingAnnotationEntity> listAnnotations(Long studyId) {
        getStudy(studyId);
        return annotationRepository.findByStudyIdOrderByCreatedAtDesc(studyId);
    }

    public List<ImagingOrderLinkEntity> listOrderLinks(Long studyId) {
        getStudy(studyId);
        return orderLinkRepository.findByStudyIdOrderByCreatedAtDesc(studyId);
    }

    public Map<String, Object> orthancConnectivityStatus() {
        return pacsBackendClient.systemStatus();
    }

    public List<ImagingStudyEntity> listUnmatchedStudies() {
        return studyRepository.findTop100ByStatusOrderByUpdatedAtDesc(StudyStatus.PENDING_ORDER.name());
    }

    public List<ImagingStudyEntity> listFailedCorrelations() {
        return studyRepository.findTop100ByStatusOrderByUpdatedAtDesc(StudyStatus.FAILED.name());
    }

    public List<Map<String, Object>> listFailedWritebacks() {
        List<Map<String, Object>> failed = new ArrayList<>();
        for (EventOutboxEntity row : outboxRepository.findTop100ByPublishErrorIsNotNullOrderByCreatedAtDesc()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("outboxId", row.getId());
            item.put("aggregateType", row.getAggregateType());
            item.put("aggregateId", row.getAggregateId());
            item.put("eventType", row.getEventType());
            item.put("tenantId", row.getTenantId());
            item.put("correlationId", row.getCorrelationId());
            item.put("publishError", row.getPublishError());
            item.put("createdAt", row.getCreatedAt());
            failed.add(item);
        }
        return failed;
    }

    @Transactional
    public Map<String, Object> retryFailedWriteback(Long outboxId) {
        EventOutboxEntity row = outboxRepository.findById(outboxId)
                .orElseThrow(() -> new IllegalArgumentException("Outbox row not found: " + outboxId));

        if (row.getPublishedAt() != null) {
            return Map.of(
                    "outboxId", outboxId,
                    "status", "ALREADY_PUBLISHED",
                    "publishedAt", row.getPublishedAt());
        }

        row.setPublishError(null);
        outboxRepository.save(row);

        int publishedNow = 0;
        PacsOutboxPublisher publisher = outboxPublisherProvider.getIfAvailable();
        if (publisher != null) {
            publishedNow = publisher.publishPendingEvents();
        }

        EventOutboxEntity refreshed = outboxRepository.findById(outboxId).orElse(row);
        String status = refreshed.getPublishedAt() != null ? "PUBLISHED" : "RETRY_QUEUED";
        try {
            studyRepository.findById(Long.parseLong(refreshed.getAggregateId())).ifPresent(study -> {
                String outcome = refreshed.getPublishedAt() != null
                        ? ImagingPipelineKafkaContracts.WRITEBACK_SUCCEEDED
                        : ImagingPipelineKafkaContracts.WRITEBACK_FAILED;
                appendImagingPipelineOutbox(study, outcome);
            });
        } catch (NumberFormatException ignored) {
            // non-study outbox rows skip imaging writeback projection
        }

        return Map.of(
                "outboxId", outboxId,
                "status", status,
                "publishedAt", refreshed.getPublishedAt(),
                "publishError", refreshed.getPublishError(),
                "publishedNow", publishedNow);
    }

    @Transactional
    public Map<String, Object> retryAllFailedWritebacks() {
        List<EventOutboxEntity> failed = outboxRepository.findTop100ByPublishErrorIsNotNullOrderByCreatedAtDesc();
        for (EventOutboxEntity row : failed) {
            row.setPublishError(null);
            outboxRepository.save(row);
        }

        int publishedNow = 0;
        PacsOutboxPublisher publisher = outboxPublisherProvider.getIfAvailable();
        if (publisher != null) {
            publishedNow = publisher.publishPendingEvents();
        }

        return Map.of(
                "failedRowsRequeued", failed.size(),
                "publishedNow", publishedNow,
                "status", "RETRY_REQUESTED");
    }

    private void recordAccess(Long studyId, Long seriesId, Long instanceId,
                              String actorId, String actorType, String accessType, String workflowContext) {
        ImagingAccessAuditEntity row = new ImagingAccessAuditEntity();
        row.setStudyId(studyId);
        row.setSeriesId(seriesId);
        row.setInstanceId(instanceId);
        row.setActorId(actorId);
        row.setActorType(actorType);
        row.setAccessType(accessType);
        row.setWorkflowContext(workflowContext);
        RequestContext ctx = RequestContextHolder.get();
        if (ctx != null) {
            row.setCorrelationId(ctx.correlationId());
        }
        accessAuditRepository.save(row);
    }

    private void appendViewerLaunchedOutbox(ImagingStudyEntity study, Long sessionId, LaunchViewerRequest req) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("study_id", study.getId());
            payload.put("viewer_session_id", sessionId);
            payload.put("patient_cpid", study.getPatientCpid());
            payload.put("studyInstanceUid", study.getStudyUid());
            payload.put("viewer_type", viewerEnginePolicy.resolve(req.getViewerType()));
            payload.put("context_ref", req.getContextRef());
            payload.put("scope", req.getScope());

            EventOutboxEntity row = new EventOutboxEntity();
            row.setAggregateType(AGGREGATE_IMAGING_STUDY);
            row.setAggregateId(String.valueOf(study.getId()));
            row.setEventType(EVENT_VIEWER_LAUNCHED);
            row.setPayload(objectMapper.writeValueAsString(payload));
            applyContext(row, study.getTenantId().toString());
            row.setSubjectType(AGGREGATE_IMAGING_STUDY);
            row.setSubjectId(String.valueOf(study.getId()));
            row.setPartitionKey(String.valueOf(study.getId()));
            row.setOccurredAt(OffsetDateTime.now());
            row.setSchemaVersion(1);
            outboxRepository.save(row);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize imaging.viewer.launched payload", e);
        }
    }

    private void appendReportLinkedOutbox(ImagingStudyEntity study, Long linkId, String reportRef) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("study_id", study.getId());
            payload.put("report_link_id", linkId);
            payload.put("report_ref", reportRef);
            payload.put("patient_cpid", study.getPatientCpid());
            payload.put("accession_number", study.getAccessionNumber());
            payload.put("studyInstanceUid", study.getStudyUid());
            payload.put("oros_order_id", study.getOrosOrderId());

            EventOutboxEntity row = new EventOutboxEntity();
            row.setAggregateType(AGGREGATE_IMAGING_STUDY);
            row.setAggregateId(String.valueOf(study.getId()));
            row.setEventType(EVENT_REPORT_LINKED);
            row.setPayload(objectMapper.writeValueAsString(payload));
            applyContext(row, study.getTenantId().toString());
            row.setSubjectType(AGGREGATE_IMAGING_STUDY);
            row.setSubjectId(String.valueOf(study.getId()));
            row.setPartitionKey(String.valueOf(study.getId()));
            row.setOccurredAt(OffsetDateTime.now());
            row.setSchemaVersion(1);
            outboxRepository.save(row);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize imaging.report.linked payload", e);
        }
    }

    private void appendOrderLinkedOutbox(ImagingStudyEntity study, String orderRef) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("study_id", study.getId());
            payload.put("order_ref", orderRef);
            payload.put("patient_cpid", study.getPatientCpid());

            EventOutboxEntity row = new EventOutboxEntity();
            row.setAggregateType(AGGREGATE_IMAGING_STUDY);
            row.setAggregateId(String.valueOf(study.getId()));
            row.setEventType(EVENT_ORDER_LINKED);
            row.setPayload(objectMapper.writeValueAsString(payload));
            applyContext(row, study.getTenantId().toString());
            row.setSubjectType(AGGREGATE_IMAGING_STUDY);
            row.setSubjectId(String.valueOf(study.getId()));
            row.setPartitionKey(orderRef);
            row.setOccurredAt(OffsetDateTime.now());
            row.setSchemaVersion(1);
            outboxRepository.save(row);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize imaging.study.linked_to_order payload", e);
        }
    }

    private void appendAnnotationCreatedOutbox(ImagingStudyEntity study, ImagingAnnotationEntity annotation) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("study_id", study.getId());
            payload.put("annotation_id", annotation.getId());
            payload.put("annotation_type", annotation.getAnnotationType());
            payload.put("patient_cpid", study.getPatientCpid());
            payload.put("series_id", annotation.getSeriesId());
            payload.put("instance_id", annotation.getInstanceId());

            EventOutboxEntity row = new EventOutboxEntity();
            row.setAggregateType(AGGREGATE_IMAGING_STUDY);
            row.setAggregateId(String.valueOf(study.getId()));
            row.setEventType(EVENT_ANNOTATION_CREATED);
            row.setPayload(objectMapper.writeValueAsString(payload));
            applyContext(row, study.getTenantId().toString());
            row.setSubjectType(AGGREGATE_IMAGING_STUDY);
            row.setSubjectId(String.valueOf(study.getId()));
            row.setPartitionKey(String.valueOf(annotation.getId()));
            row.setOccurredAt(OffsetDateTime.now());
            row.setSchemaVersion(1);
            outboxRepository.save(row);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize imaging.annotation.created payload", e);
        }
    }

    private void appendAccessRecordedOutbox(ImagingStudyEntity studyOrNull, String accessKind) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("access_kind", accessKind);
            if (studyOrNull != null) {
                payload.put("study_id", studyOrNull.getId());
            }

            EventOutboxEntity row = new EventOutboxEntity();
            row.setAggregateType(AGGREGATE_IMAGING_STUDY);
            row.setAggregateId(studyOrNull != null ? String.valueOf(studyOrNull.getId()) : "SEARCH");
            row.setEventType("imaging.access.recorded");
            row.setPayload(objectMapper.writeValueAsString(payload));
            applyContext(row, studyOrNull != null ? studyOrNull.getTenantId().toString() : "unknown");
            row.setSubjectType(AGGREGATE_IMAGING_STUDY);
            row.setSubjectId(studyOrNull != null ? String.valueOf(studyOrNull.getId()) : "SEARCH");
            row.setPartitionKey(studyOrNull != null ? String.valueOf(studyOrNull.getId()) : "SEARCH");
            row.setOccurredAt(OffsetDateTime.now());
            row.setSchemaVersion(1);
            outboxRepository.save(row);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize imaging.access.recorded payload", e);
        }
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
            payload.put("patient_cpid", study.getPatientCpid());
            payload.put("tenant_id", study.getTenantId().toString());
            payload.put("modality", study.getModality());
            payload.put("accession_number", study.getAccessionNumber());
            payload.put("studyInstanceUid", study.getStudyUid());

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

    private void appendImagingPipelineOutbox(ImagingStudyEntity study, String eventType) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("study_id", study.getId());
            payload.put("patient_cpid", study.getPatientCpid());
            payload.put("tenant_id", study.getTenantId().toString());
            payload.put("topic", eventType);

            EventOutboxEntity row = new EventOutboxEntity();
            row.setAggregateType(AGGREGATE_IMAGING_STUDY);
            row.setAggregateId(String.valueOf(study.getId()));
            row.setEventType(eventType);
            row.setPayload(objectMapper.writeValueAsString(payload));
            applyContext(row, study.getTenantId().toString());
            row.setSubjectType(AGGREGATE_IMAGING_STUDY);
            row.setSubjectId(String.valueOf(study.getId()));
            row.setPartitionKey(study.getOrosOrderId() != null ? study.getOrosOrderId() : String.valueOf(study.getId()));
            row.setOccurredAt(OffsetDateTime.now());
            row.setSchemaVersion(1);
            outboxRepository.save(row);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize imaging pipeline payload for " + eventType, e);
        }
    }

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
