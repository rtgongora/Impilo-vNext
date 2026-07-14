package zw.gov.mohcc.impilo.oros.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.oros.api.dto.HistopathReportRequest;
import zw.gov.mohcc.impilo.oros.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.HistopathReportEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.ResultEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.HistopathReportRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Histopathology report depth (spec §11) — pathology narrative capture plus a pathologist sign-out
 * gate on top of a clinical order/result.
 *
 * <p>A report is built up as a DRAFT (gross / microscopic / diagnosis / ancillary tests). On
 * <b>sign-out</b> the report is finalized <em>and</em> routed through {@link ReportService} so the
 * canonical versioned FINAL {@link ResultEntity}, the BUTANO DiagnosticReport, and the outbound
 * HL7 ORU are produced for free (no duplicated result/versioning/Butano logic). The resulting
 * {@code resultId} and a stable SHR DiagnosticReport reference are stored back on the histopath row
 * for lineage; a critical sign-out additionally flags the linked result critical, and
 * acknowledgement closes the loop on both the histopath row and the linked result.</p>
 */
@Service
public class HistopathReportService {

    private static final Logger log = LoggerFactory.getLogger(HistopathReportService.class);

    /** FHIR business-identifier system BUTANO indexes DiagnosticReports under (see ButanoIntegration). */
    private static final String SHR_RESULT_ID_SYSTEM = "https://impilo.gov.zw/oros/result-id";

    private final HistopathReportRepository repository;
    private final ReportService reportService;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public HistopathReportService(HistopathReportRepository repository,
                                  ReportService reportService,
                                  EventOutboxRepository outboxRepository,
                                  ObjectMapper objectMapper) {
        this.repository = repository;
        this.reportService = reportService;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Create or update the working DRAFT histopath report for an order. If the latest report for the
     * order is still a DRAFT it is updated in place; otherwise (none yet, or the prior one already
     * signed out FINAL) a fresh DRAFT is started.
     */
    @Transactional
    public HistopathReportEntity draft(String orderId, HistopathReportRequest body) {
        TrustContext ctx = TrustContextHolder.require();
        HistopathReportEntity report = repository.findFirstByOrderIdOrderByCreatedAtDesc(orderId)
                .filter(r -> "DRAFT".equals(r.getSignOutStatus()))
                .orElseGet(() -> {
                    HistopathReportEntity r = new HistopathReportEntity();
                    r.setOrderId(orderId);
                    r.setTenantId(ctx.tenantId());
                    r.setSignOutStatus("DRAFT");
                    return r;
                });
        applySections(report, body);
        report = repository.save(report);
        log.info("Histopath draft saved: orderId={}, reportId={}", orderId, report.getReportId());
        return report;
    }

    /**
     * Pathologist sign-out: finalize the histopath report and produce the canonical FINAL result via
     * {@link ReportService#createFinal}. Stores the returned {@code resultId} and a stable SHR
     * DiagnosticReport reference back on the row; on a critical sign-out flags the linked result
     * critical. Emits {@code oros.histopath.signed_out} (and {@code oros.histopath.critical}).
     */
    @Transactional
    public HistopathReportEntity signOut(String orderId, HistopathReportRequest body) {
        TrustContext ctx = TrustContextHolder.require();
        HistopathReportEntity report = repository.findFirstByOrderIdOrderByCreatedAtDesc(orderId)
                .filter(r -> "DRAFT".equals(r.getSignOutStatus()))
                .orElseGet(() -> {
                    HistopathReportEntity r = new HistopathReportEntity();
                    r.setOrderId(orderId);
                    r.setTenantId(ctx.tenantId());
                    r.setSignOutStatus("DRAFT");
                    return r;
                });
        // Allow sign-out to re-supply/finalize any section.
        applySections(report, body);

        String pathologist = body != null && body.reportingPathologist() != null
                ? body.reportingPathologist() : ctx.actorId();
        report.setReportingPathologist(pathologist);
        report.setSignOutStatus("FINAL");
        report.setSignedOutAt(OffsetDateTime.now());

        boolean critical = body != null && Boolean.TRUE.equals(body.critical());
        String criticalReason = body != null ? body.criticalReason() : null;
        if (critical) {
            report.setCritical(true);
            report.setCriticalReason(criticalReason);
        }

        // Route the FINAL through the versioned reporting lifecycle: produces the canonical
        // ResultEntity + BUTANO DiagnosticReport + HL7 ORU (spec §10). Diagnosis is the impression.
        String summaryJson = buildSummaryJson(report);
        String docIds = body != null ? json(body.docIds()) : null;
        String ziboCodes = body != null ? body.ziboResultCodes() : null;
        String recommendations = body != null ? body.recommendations() : null;

        ResultEntity result = reportService.createFinal(
                orderId, summaryJson, report.getDiagnosis(), recommendations, docIds, ziboCodes);
        report.setResultId(result.getResultId());
        report.setButanoReportRef(shrReportRef(result.getResultId()));

        if (critical && result.getResultId() != null) {
            reportService.flagCritical(result.getResultId(), criticalReason);
        }

        report = repository.save(report);
        publishEvent(report, "oros.histopath.signed_out");
        if (critical) {
            publishEvent(report, "oros.histopath.critical");
        }
        log.info("Histopath signed out: orderId={}, reportId={}, resultId={}, pathologist={}, critical={}",
                orderId, report.getReportId(), result.getResultId(), pathologist, critical);
        return report;
    }

    /**
     * Acknowledge the latest histopath report for an order (closes the loop). If a critical result is
     * linked, the linked result is acknowledged through {@link ReportService} as well. Emits
     * {@code oros.histopath.acknowledged}.
     */
    @Transactional
    public HistopathReportEntity acknowledge(String orderId, String note) {
        TrustContext ctx = TrustContextHolder.require();
        HistopathReportEntity report = repository.findFirstByOrderIdOrderByCreatedAtDesc(orderId)
                .orElseThrow(() -> new IllegalArgumentException("No histopath report for order " + orderId));
        report.setAcknowledgedAt(OffsetDateTime.now());
        report.setAcknowledgedBy(ctx.actorId());
        report = repository.save(report);

        if (report.isCritical() && report.getResultId() != null) {
            reportService.acknowledge(report.getResultId(), note);
        }
        publishEvent(report, "oros.histopath.acknowledged");
        log.info("Histopath acknowledged: orderId={}, reportId={}, by={}, note={}",
                orderId, report.getReportId(), ctx.actorId(), note);
        return report;
    }

    /** Latest histopath report for an order as a flat map (all fields), or {@code null} if none. */
    @Transactional(readOnly = true)
    public Map<String, Object> get(String orderId) {
        return repository.findFirstByOrderIdOrderByCreatedAtDesc(orderId)
                .map(HistopathReportService::toMap)
                .orElse(null);
    }

    // ── internals ────────────────────────────────────────────────────────

    /** Copy any non-null narrative sections from the request onto the entity. */
    private void applySections(HistopathReportEntity report, HistopathReportRequest body) {
        if (body == null) {
            return;
        }
        if (body.specimenRef() != null) report.setSpecimenRef(body.specimenRef());
        if (body.grossDescription() != null) report.setGrossDescription(body.grossDescription());
        if (body.microscopicDescription() != null) report.setMicroscopicDescription(body.microscopicDescription());
        if (body.diagnosis() != null) report.setDiagnosis(body.diagnosis());
        if (body.ancillaryTests() != null) report.setAncillaryTests(json(body.ancillaryTests()));
        if (body.reportingPathologist() != null) report.setReportingPathologist(body.reportingPathologist());
    }

    /** Snapshot the pathology sections into the result summary JSONB for SHR writeback / back-compat. */
    private String buildSummaryJson(HistopathReportEntity report) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("kind", "HISTOPATHOLOGY");
        summary.put("specimenRef", report.getSpecimenRef());
        summary.put("grossDescription", report.getGrossDescription());
        summary.put("microscopicDescription", report.getMicroscopicDescription());
        summary.put("diagnosis", report.getDiagnosis());
        summary.put("ancillaryTests", report.getAncillaryTests());
        summary.put("reportingPathologist", report.getReportingPathologist());
        return json(summary);
    }

    /** Stable, resolvable SHR reference to the DiagnosticReport BUTANO indexes by result-id identifier. */
    private static String shrReportRef(UUID resultId) {
        return resultId == null ? null
                : "DiagnosticReport?identifier=" + SHR_RESULT_ID_SYSTEM + "|" + resultId;
    }

    public static Map<String, Object> toMap(HistopathReportEntity r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reportId", r.getReportId() != null ? r.getReportId().toString() : null);
        m.put("orderId", r.getOrderId());
        m.put("resultId", r.getResultId() != null ? r.getResultId().toString() : null);
        m.put("tenantId", r.getTenantId() != null ? r.getTenantId().toString() : null);
        m.put("specimenRef", r.getSpecimenRef());
        m.put("grossDescription", r.getGrossDescription());
        m.put("microscopicDescription", r.getMicroscopicDescription());
        m.put("diagnosis", r.getDiagnosis());
        m.put("ancillaryTests", r.getAncillaryTests());
        m.put("reportingPathologist", r.getReportingPathologist());
        m.put("signOutStatus", r.getSignOutStatus());
        m.put("signedOutAt", r.getSignedOutAt() != null ? r.getSignedOutAt().toString() : null);
        m.put("critical", r.isCritical());
        m.put("criticalReason", r.getCriticalReason());
        m.put("acknowledgedAt", r.getAcknowledgedAt() != null ? r.getAcknowledgedAt().toString() : null);
        m.put("acknowledgedBy", r.getAcknowledgedBy());
        m.put("butanoReportRef", r.getButanoReportRef());
        m.put("createdAt", r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
        m.put("updatedAt", r.getUpdatedAt() != null ? r.getUpdatedAt().toString() : null);
        return m;
    }

    private void publishEvent(HistopathReportEntity report, String eventType) {
        try {
            EventOutboxEntity event = new EventOutboxEntity();
            event.setAggregateType("HISTOPATH_REPORT");
            event.setAggregateId(report.getReportId() != null
                    ? report.getReportId().toString() : report.getOrderId());
            event.setEventType(eventType);
            event.setPayload(objectMapper.writeValueAsString(toMap(report)));
            event.setTenantId(report.getTenantId());
            outboxRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to write histopath outbox event {}: {}", eventType, e.getMessage(), e);
        }
    }

    private String json(Object value) {
        if (value == null) return null;
        if (value instanceof String s) return s;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("Failed to serialize histopath field to JSON, using toString()", e);
            return value.toString();
        }
    }
}
