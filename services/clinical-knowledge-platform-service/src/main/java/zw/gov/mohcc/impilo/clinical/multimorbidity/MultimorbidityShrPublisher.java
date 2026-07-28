package zw.gov.mohcc.impilo.clinical.multimorbidity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.clinical.events.ClinicalOutboxWriter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Puts the §9 multimorbidity engine's detected issues onto the shared health record (brief.md §19).
 *
 * <p><strong>Why this route, and why nothing new was built for it.</strong> The obvious objection is
 * that CKP is a stateless reasoning service — it is handed a body, it reasons, it answers, it stores
 * nothing — so an event spine here looks like an invention. It is not. CKP already owns one:
 * {@code clinical.event_outbox}, written through {@link ClinicalOutboxWriter}, routed by
 * {@code ClinicalKafkaTopics} and drained by {@code ClinicalKafkaOutboxRelay}. Pathway completions,
 * guidance recommendations and fired alerts already travel it. This adds one event type and one topic
 * entry to that existing spine and no new machinery whatsoever.</p>
 *
 * <p>The alternatives were each strictly worse. Emitting from pct-service — which owns the outbox the
 * examination producer uses — would require PCT to hold the medicine list, the appointment book and
 * the investigation history, none of which it has, and to re-run the reasoning that already ran here;
 * it would be a second implementation of §9. Emitting from experience-bff would put clinical fact
 * production in a composition layer, which the architecture guardrails forbid outright. Writing to
 * PCT's outbox table from CKP would be a cross-service database write. The producer of a fact is the
 * service that determined it, and the engine that determined it lives here.</p>
 *
 * <p><strong>Only DETECTED findings are emitted.</strong> NOT_DETECTED and UNDETERMINED are not
 * absences of an issue in the same sense and must not be archived as resolved issues — but neither is
 * silence about them a claim that they are clear. The distinction lives in the view the clinician
 * reads; the SHR carries the positive findings only, and this publisher never writes a "nothing
 * found" resource that a later reader could mistake for a completed check.</p>
 */
@Service
public class MultimorbidityShrPublisher {

    private static final Logger log = LoggerFactory.getLogger(MultimorbidityShrPublisher.class);

    /** Routed to {@code impilo.clinical.multimorbidity.issue.detected} by {@code ClinicalKafkaTopics}. */
    public static final String EVENT_TYPE = "MULTIMORBIDITY_ISSUE_DETECTED";

    private final ClinicalOutboxWriter outboxWriter;

    public MultimorbidityShrPublisher(ClinicalOutboxWriter outboxWriter) {
        this.outboxWriter = outboxWriter;
    }

    /**
     * Emits one event per detected finding.
     *
     * @param subjectCpid the CPID the assessment was run for. Null when the caller did not state one,
     *                    in which case nothing is emitted — an issue with no subject cannot be filed
     *                    against a patient, and guessing one is not an option that exists.
     * @return the number of events written, so the caller can log what actually happened
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
        String detectedAt = OffsetDateTime.now().toString();
        int written = 0;
        for (MultimorbidityView.Detection detection : view.detections()) {
            if (detection.state() != MultimorbidityView.DetectionState.DETECTED) {
                continue;
            }
            for (MultimorbidityView.Finding finding : detection.findings()) {
                if (finding.code() == null || finding.code().isBlank()) {
                    // A DetectedIssue with no code cannot be queried, mapped or deduplicated — the
                    // same reason the Condition producer refuses an uncoded problem.
                    log.warn("multimorbidity.shr.skipped reason=no_code detection={}", detection.key());
                    continue;
                }
                outboxWriter.enqueue(EVENT_TYPE, tenantId, "MultimorbidityIssue",
                        subjectCpid + "|" + finding.code(),
                        payload(subjectCpid, tenantId, detection, finding, view.contentVersion(), detectedAt));
                written++;
            }
        }
        log.info("multimorbidity.shr.published subject_present=true issues={} ", written);
        return written;
    }

    /**
     * The exact set of fields that crosses into the shared health record.
     *
     * <p><strong>The engine's prose is deliberately absent.</strong> {@code message},
     * {@code explanation} and {@code requiredAction} are generated by interpolating live patient data
     * into a template: the medicine names in a duplication finding, the clinic names in a visit-burden
     * finding, and in {@code repeatedInvestigations} the name of whoever ordered each test — "Ordered
     * by Dr Moyo then Dr Ncube". That is identifying data about a provider, and free text is the one
     * shape that can carry identifying data about the patient too if a future template changes. The
     * SHR holds a CPID and coded facts; the prose stays in the service that generated it, where the
     * clinician who opened the view can already read it in full.</p>
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
}
