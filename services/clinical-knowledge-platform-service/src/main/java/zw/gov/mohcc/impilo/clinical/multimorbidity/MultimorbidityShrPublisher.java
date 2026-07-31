package zw.gov.mohcc.impilo.clinical.multimorbidity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.clinical.events.ClinicalOutboxWriter;
import zw.gov.mohcc.impilo.clinical.persistence.entity.MultimorbidityDetectionSnapshotEntity;
import zw.gov.mohcc.impilo.clinical.persistence.repository.MultimorbidityDetectionSnapshotRepository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Puts the §9 multimorbidity engine's detected issues onto the shared health record (brief.md §19).
 *
 * <p><strong>Why this route, and why nothing new was built for it.</strong> The obvious objection is
 * that CKP is a stateless reasoning service — it is handed a body, it reasons, it answers, it stores
 * nothing — so an event spine here looks like an invention. It is not. CKP already owns one:
 * {@code clinical.event_outbox}, written through {@link ClinicalOutboxWriter}, routed by
 * {@code ClinicalKafkaTopics} and drained by {@code ClinicalKafkaOutboxRelay}. Pathway completions,
 * guidance recommendations and fired alerts already travel it. This adds event types and topic
 * entries to that existing spine and no new machinery whatsoever.</p>
 *
 * <p><strong>Only DETECTED findings are filed as open issues.</strong> NOT_DETECTED and UNDETERMINED
 * are never archived as positive findings. Retirement is a separate act: when a code that was OPEN
 * on a prior assess is answered on this assess as no longer DETECTED, a
 * {@link #RESOLVED_EVENT_TYPE} event is emitted and the snapshot row flips to RETIRED. Silence —
 * no assess at all — never retires anything; neither does an UNDETERMINED re-check of the same
 * detection (missing inputs are not clearance).</p>
 */
@Service
public class MultimorbidityShrPublisher {

    private static final Logger log = LoggerFactory.getLogger(MultimorbidityShrPublisher.class);

    /** Routed to {@code impilo.clinical.multimorbidity.issue.detected} by {@code ClinicalKafkaTopics}. */
    public static final String EVENT_TYPE = "MULTIMORBIDITY_ISSUE_DETECTED";

    /** Routed to {@code impilo.clinical.multimorbidity.issue.resolved} by {@code ClinicalKafkaTopics}. */
    public static final String RESOLVED_EVENT_TYPE = "MULTIMORBIDITY_ISSUE_RESOLVED";

    private final ClinicalOutboxWriter outboxWriter;
    private final MultimorbidityDetectionSnapshotRepository snapshotRepository;

    public MultimorbidityShrPublisher(ClinicalOutboxWriter outboxWriter,
                                      MultimorbidityDetectionSnapshotRepository snapshotRepository) {
        this.outboxWriter = outboxWriter;
        this.snapshotRepository = snapshotRepository;
    }

    /**
     * Emits DETECTED events for current findings, RESOLVED events for answered clearances, and
     * upserts the V071 snapshot accordingly.
     *
     * @param subjectCpid the CPID the assessment was run for. Null when the caller did not state one,
     *                    in which case nothing is emitted and nothing is snapshotted — an issue with
     *                    no subject cannot be filed, and guessing one is not an option that exists.
     * @return the number of outbox events written (detected + resolved)
     */
    @Transactional
    public int publish(MultimorbidityView view, String subjectCpid, String tenantId) {
        if (view == null) {
            return 0;
        }
        if (subjectCpid == null || subjectCpid.isBlank()) {
            log.debug("multimorbidity.shr.skipped reason=no_subject_cpid — the assessment was run "
                      + "without a subject, so its findings cannot be filed against a patient");
            return 0;
        }
        String tenant = (tenantId == null || tenantId.isBlank()) ? "default" : tenantId;
        Instant now = Instant.now();
        String detectedAt = OffsetDateTime.now().toString();

        Map<String, MultimorbidityView.Detection> byKey = detectionsByKey(view);
        Set<String> currentDetected = new HashSet<>(detectedCodes(view));

        int detectedWritten = 0;
        for (MultimorbidityView.Detection detection : view.detections()) {
            if (detection.state() != MultimorbidityView.DetectionState.DETECTED) {
                continue;
            }
            for (MultimorbidityView.Finding finding : detection.findings()) {
                if (finding.code() == null || finding.code().isBlank()) {
                    log.warn("multimorbidity.shr.skipped reason=no_code detection={}", detection.key());
                    continue;
                }
                outboxWriter.enqueue(EVENT_TYPE, tenant, "MultimorbidityIssue",
                        subjectCpid + "|" + finding.code(),
                        payload(subjectCpid, tenant, detection, finding, view.contentVersion(), detectedAt));
                upsertOpen(tenant, subjectCpid, detection, finding, view.contentVersion(), now);
                detectedWritten++;
            }
        }

        int resolvedWritten = 0;
        List<MultimorbidityDetectionSnapshotEntity> open =
                snapshotRepository.findByTenantIdAndSubjectCpidAndStatus(
                        tenant, subjectCpid, MultimorbidityDetectionSnapshotEntity.STATUS_OPEN);
        for (MultimorbidityDetectionSnapshotEntity row : open) {
            if (currentDetected.contains(row.getCode())) {
                continue;
            }
            MultimorbidityView.Detection detection = byKey.get(row.getDetectionKey());
            // Silence about this check, or an unanswered (UNDETERMINED) re-check, must never retire.
            if (!wasAnswered(detection)) {
                log.debug("multimorbidity.shr.retirement_withheld code={} detection_key={} reason={}",
                        row.getCode(), row.getDetectionKey(),
                        detection == null ? "detection_absent" : detection.state().name());
                continue;
            }
            outboxWriter.enqueue(RESOLVED_EVENT_TYPE, tenant, "MultimorbidityIssue",
                    subjectCpid + "|" + row.getCode(),
                    resolvedPayload(subjectCpid, tenant, row, view.contentVersion(), detectedAt));
            row.setStatus(MultimorbidityDetectionSnapshotEntity.STATUS_RETIRED);
            row.setResolvedAt(now);
            snapshotRepository.save(row);
            resolvedWritten++;
        }

        log.info("multimorbidity.shr.published subject_present=true detected={} resolved={}",
                detectedWritten, resolvedWritten);
        return detectedWritten + resolvedWritten;
    }

    /**
     * True when this assess answered the detection that owned the open issue.
     *
     * <p>NOT_DETECTED (clear) and DETECTED (still answered, this code gone) both count. UNDETERMINED
     * and a missing detection key do not — missing inputs are not clearance, and silence about a
     * check is not an answer.</p>
     */
    static boolean wasAnswered(MultimorbidityView.Detection detection) {
        if (detection == null) {
            return false;
        }
        return detection.state() == MultimorbidityView.DetectionState.NOT_DETECTED
                || detection.state() == MultimorbidityView.DetectionState.DETECTED;
    }

    private void upsertOpen(String tenant, String subjectCpid,
                            MultimorbidityView.Detection detection,
                            MultimorbidityView.Finding finding,
                            String contentVersion, Instant now) {
        MultimorbidityDetectionSnapshotEntity row = snapshotRepository
                .findByTenantIdAndSubjectCpidAndCode(tenant, subjectCpid, finding.code())
                .orElseGet(MultimorbidityDetectionSnapshotEntity::new);
        row.setTenantId(tenant);
        row.setSubjectCpid(subjectCpid);
        row.setCode(finding.code());
        row.setDetectionKey(detection.key());
        row.setSeverity(finding.severity());
        row.setContentVersion(contentVersion);
        row.setDetectedAt(now);
        row.setStatus(MultimorbidityDetectionSnapshotEntity.STATUS_OPEN);
        row.setResolvedAt(null);
        snapshotRepository.save(row);
    }

    /**
     * The exact set of fields that crosses into the shared health record for an open issue.
     *
     * <p><strong>The engine's prose is deliberately absent.</strong> {@code message},
     * {@code explanation} and {@code requiredAction} are generated by interpolating live patient data
     * into a template. The SHR holds a CPID and coded facts; the prose stays in the service that
     * generated it.</p>
     *
     * <p>Package-private and static so the boundary can be asserted without a running service.</p>
     */
    static Map<String, Object> payload(String subjectCpid, String tenantId,
                                       MultimorbidityView.Detection detection,
                                       MultimorbidityView.Finding finding,
                                       String contentVersion, String detectedAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("subjectCpid", subjectCpid);
        payload.put("tenantId", tenantId);
        payload.put("detectionKey", detection.key());
        payload.put("code", finding.code());
        payload.put("severity", finding.severity());
        payload.put("contentVersion", contentVersion);
        payload.put("detectedAt", detectedAt);
        return payload;
    }

    /**
     * Coded resolution payload — same no-prose discipline as {@link #payload}.
     *
     * <p>Package-private and static for the boundary test.</p>
     */
    static Map<String, Object> resolvedPayload(String subjectCpid, String tenantId,
                                               MultimorbidityDetectionSnapshotEntity prior,
                                               String contentVersion, String resolvedAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("subjectCpid", subjectCpid);
        payload.put("tenantId", tenantId);
        payload.put("detectionKey", prior.getDetectionKey());
        payload.put("code", prior.getCode());
        payload.put("contentVersion", contentVersion);
        payload.put("resolvedAt", resolvedAt);
        return payload;
    }

    /** The codes this view would file, in order — used by the boundary test and by diagnostics. */
    static List<String> detectedCodes(MultimorbidityView view) {
        List<String> codes = new ArrayList<>();
        for (MultimorbidityView.Detection detection : view.detections()) {
            if (detection.state() != MultimorbidityView.DetectionState.DETECTED) {
                continue;
            }
            detection.findings().forEach(f -> codes.add(f.code()));
        }
        return codes;
    }

    private static Map<String, MultimorbidityView.Detection> detectionsByKey(MultimorbidityView view) {
        Map<String, MultimorbidityView.Detection> byKey = new HashMap<>();
        for (MultimorbidityView.Detection detection : view.detections()) {
            byKey.put(detection.key(), detection);
        }
        return byKey;
    }
}
