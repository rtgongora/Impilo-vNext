package zw.gov.mohcc.impilo.pacs.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.pacs.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pacs.persistence.entity.ImagingStudyEntity;
import zw.gov.mohcc.impilo.pacs.persistence.entity.ImagingStudyExceptionEntity;
import zw.gov.mohcc.impilo.pacs.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pacs.persistence.repository.ImagingStudyExceptionRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Reconciliation exceptions queue for incoming imaging studies.
 *
 * <p>Doctrine: a study that cannot be safely and unambiguously attached to a patient/order is
 * never silently attached — it raises an explicit, auditable exception that an authorised user
 * must resolve. Manual imports, digitised analogue studies, and downtime captures always pass
 * through here before clinical attachment.</p>
 */
@Service
public class ImagingStudyExceptionService {

    private static final Logger log = LoggerFactory.getLogger(ImagingStudyExceptionService.class);

    public static final String AGGREGATE_IMAGING_EXCEPTION = "IMAGING_EXCEPTION";
    public static final String EVENT_EXCEPTION_RAISED = "imaging.exception.raised";
    public static final String EVENT_EXCEPTION_RESOLVED = "imaging.exception.resolved";

    // Exception types
    public static final String TYPE_STUDY_WITHOUT_ORDER = "STUDY_WITHOUT_ORDER";
    public static final String TYPE_MISSING_ACCESSION = "MISSING_ACCESSION";
    public static final String TYPE_MANUAL_IMPORT_REVIEW = "MANUAL_IMPORT_REVIEW";
    public static final String TYPE_DIGITISED_REVIEW = "DIGITISED_REVIEW";
    public static final String TYPE_DOWNTIME_CAPTURE_REVIEW = "DOWNTIME_CAPTURE_REVIEW";

    private final ImagingStudyExceptionRepository exceptionRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public ImagingStudyExceptionService(
            ImagingStudyExceptionRepository exceptionRepository,
            EventOutboxRepository outboxRepository,
            ObjectMapper objectMapper) {
        this.exceptionRepository = exceptionRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Raise the automatic exceptions a freshly registered study warrants. Called inside the
     * registration transaction; idempotent per (study, type) while an OPEN row exists.
     */
    @Transactional
    public void raiseForRegisteredStudy(ImagingStudyEntity study) {
        if (study.getOrosOrderId() == null || study.getOrosOrderId().isBlank()) {
            raise(study, TYPE_STUDY_WITHOUT_ORDER,
                    "Study received without a linked OROS order — requires correlation before clinical attachment");
        }
        if (study.getAccessionNumber() == null || study.getAccessionNumber().isBlank()) {
            raise(study, TYPE_MISSING_ACCESSION,
                    "Study received without an accession number");
        }
        String source = study.getSourceType() != null
                ? study.getSourceType().trim().toUpperCase(Locale.ROOT) : "DICOM";
        switch (source) {
            case "MANUAL_IMPORT" -> raise(study, TYPE_MANUAL_IMPORT_REVIEW,
                    "Manually imported study (CD/USB/workstation) — patient/order match must be confirmed");
            case "DIGITISED" -> raise(study, TYPE_DIGITISED_REVIEW,
                    "Digitised analogue study — high patient-matching risk, confirmation required");
            case "DOWNTIME_CAPTURE" -> raise(study, TYPE_DOWNTIME_CAPTURE_REVIEW,
                    "Downtime capture — reconcile against orders placed during downtime");
            default -> { /* network DICOM ingest raises no source-based exception */ }
        }
    }

    /**
     * Auto-resolve any OPEN {@code STUDY_WITHOUT_ORDER} exceptions when the study is correlated
     * to an order. Recorded as a system resolution — still auditable.
     */
    @Transactional
    public void resolveOrderExceptionsOnCorrelation(ImagingStudyEntity study, String actorId) {
        for (ImagingStudyExceptionEntity ex : exceptionRepository.findByStudyIdOrderByRaisedAtDesc(study.getId())) {
            if (ImagingStudyExceptionEntity.STATUS_OPEN.equals(ex.getStatus())
                    && TYPE_STUDY_WITHOUT_ORDER.equals(ex.getExceptionType())) {
                doResolve(ex, actorId != null ? actorId : "system:correlation",
                        "CORRELATED_TO_ORDER", "Resolved by correlation to order " + study.getOrosOrderId(),
                        ImagingStudyExceptionEntity.STATUS_RESOLVED, study.getTenantId());
            }
        }
    }

    @Transactional
    public ImagingStudyExceptionEntity raise(ImagingStudyEntity study, String exceptionType, String detail) {
        if (exceptionRepository.existsByStudyIdAndExceptionTypeAndStatus(
                study.getId(), exceptionType, ImagingStudyExceptionEntity.STATUS_OPEN)) {
            log.debug("Open imaging exception already present: studyId={}, type={}", study.getId(), exceptionType);
            return null;
        }
        ImagingStudyExceptionEntity ex = new ImagingStudyExceptionEntity();
        ex.setTenantId(study.getTenantId());
        ex.setStudyId(study.getId());
        ex.setExceptionType(exceptionType);
        ex.setStatus(ImagingStudyExceptionEntity.STATUS_OPEN);
        ex.setDetail(detail);
        ex.setRaisedBy("system:ingest");
        ex = exceptionRepository.save(ex);

        appendExceptionOutbox(ex, EVENT_EXCEPTION_RAISED);
        log.info("Imaging exception raised: studyId={}, type={}", study.getId(), exceptionType);
        return ex;
    }

    public List<ImagingStudyExceptionEntity> listExceptions(UUID tenantId, String status) {
        if (status != null && !status.isBlank()) {
            return exceptionRepository.findTop200ByTenantIdAndStatusOrderByRaisedAtDesc(
                    tenantId, status.trim().toUpperCase(Locale.ROOT));
        }
        return exceptionRepository.findTop200ByTenantIdOrderByRaisedAtDesc(tenantId);
    }

    public List<ImagingStudyExceptionEntity> listForStudy(Long studyId) {
        return exceptionRepository.findByStudyIdOrderByRaisedAtDesc(studyId);
    }

    /**
     * Resolve or dismiss an exception. {@code action} is the audited resolution verb
     * (e.g. PATIENT_CONFIRMED, REJECTED_WRONG_PATIENT, DISMISSED_DUPLICATE).
     */
    @Transactional
    public ImagingStudyExceptionEntity resolve(Long exceptionId, String action, String note,
                                               boolean dismiss, String actorId) {
        ImagingStudyExceptionEntity ex = exceptionRepository.findById(exceptionId)
                .orElseThrow(() -> new IllegalArgumentException("Imaging exception not found: " + exceptionId));
        if (!ImagingStudyExceptionEntity.STATUS_OPEN.equals(ex.getStatus())) {
            throw new IllegalStateException("Imaging exception already " + ex.getStatus() + ": " + exceptionId);
        }
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("Resolution action is required");
        }
        String status = dismiss
                ? ImagingStudyExceptionEntity.STATUS_DISMISSED
                : ImagingStudyExceptionEntity.STATUS_RESOLVED;
        return doResolve(ex, actorId, action.trim().toUpperCase(Locale.ROOT), note, status, ex.getTenantId());
    }

    private ImagingStudyExceptionEntity doResolve(ImagingStudyExceptionEntity ex, String actorId,
                                                  String action, String note, String status, UUID tenantId) {
        ex.setStatus(status);
        ex.setResolvedBy(actorId != null && !actorId.isBlank() ? actorId : "anonymous");
        ex.setResolvedAt(OffsetDateTime.now());
        ex.setResolutionAction(action);
        ex.setResolutionNote(note);
        ex = exceptionRepository.save(ex);

        appendExceptionOutbox(ex, EVENT_EXCEPTION_RESOLVED);
        log.info("Imaging exception {}: id={}, type={}, action={}, actor={}",
                status.toLowerCase(Locale.ROOT), ex.getId(), ex.getExceptionType(), action, ex.getResolvedBy());
        return ex;
    }

    private void appendExceptionOutbox(ImagingStudyExceptionEntity ex, String eventType) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("exception_id", ex.getId());
            payload.put("study_id", ex.getStudyId());
            payload.put("exception_type", ex.getExceptionType());
            payload.put("status", ex.getStatus());
            payload.put("resolution_action", ex.getResolutionAction());
            payload.put("resolved_by", ex.getResolvedBy());
            payload.put("tenant_id", ex.getTenantId().toString());

            EventOutboxEntity row = new EventOutboxEntity();
            row.setAggregateType(AGGREGATE_IMAGING_EXCEPTION);
            row.setAggregateId(String.valueOf(ex.getId()));
            row.setEventType(eventType);
            row.setPayload(objectMapper.writeValueAsString(payload));
            RequestContext ctx = RequestContextHolder.get();
            if (ctx != null) {
                row.setTenantId(ctx.tenantId());
                row.setPodId(ctx.podId());
                row.setCorrelationId(ctx.correlationId());
            } else {
                row.setTenantId(ex.getTenantId().toString());
                row.setPodId(System.getenv("HOSTNAME") != null ? System.getenv("HOSTNAME") : "local");
                row.setCorrelationId(UUID.randomUUID().toString());
            }
            row.setSubjectType(AGGREGATE_IMAGING_EXCEPTION);
            row.setSubjectId(String.valueOf(ex.getId()));
            row.setPartitionKey(ex.getStudyId() != null ? String.valueOf(ex.getStudyId()) : String.valueOf(ex.getId()));
            row.setOccurredAt(OffsetDateTime.now());
            row.setSchemaVersion(1);
            outboxRepository.save(row);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize " + eventType + " payload", e);
        }
    }
}
