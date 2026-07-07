package zw.gov.mohcc.impilo.simba.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.simba.persistence.entity.AssessmentEntity;
import zw.gov.mohcc.impilo.simba.persistence.entity.AssessmentResponseEntity;
import zw.gov.mohcc.impilo.simba.persistence.entity.CareLinkageEntity;
import zw.gov.mohcc.impilo.simba.persistence.repository.AssessmentRepository;
import zw.gov.mohcc.impilo.simba.persistence.repository.AssessmentResponseRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Baseline wellness assessment lifecycle: start/resume a draft, save wizard steps (idempotent
 * per question), and complete → deterministic risk scoring. Completion writes the timeline and,
 * for HIGH/URGENT bands, routes a care linkage (PCT owns the referral; Simba records the linkage).
 */
@Service
public class AssessmentService {

    private final AssessmentRepository assessmentRepository;
    private final AssessmentResponseRepository responseRepository;
    private final RiskScoringService riskScoring;
    private final CareLinkageService careLinkage;
    private final TimelineService timeline;
    private final SimbaEventEmitter events;
    private final ObjectMapper objectMapper;

    public AssessmentService(AssessmentRepository assessmentRepository,
                             AssessmentResponseRepository responseRepository,
                             RiskScoringService riskScoring,
                             CareLinkageService careLinkage,
                             TimelineService timeline,
                             SimbaEventEmitter events,
                             ObjectMapper objectMapper) {
        this.assessmentRepository = assessmentRepository;
        this.responseRepository = responseRepository;
        this.riskScoring = riskScoring;
        this.careLinkage = careLinkage;
        this.timeline = timeline;
        this.events = events;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public AssessmentEntity get(UUID tenantId, UUID assessmentId) {
        return assessmentRepository.findByAssessmentIdAndTenantId(assessmentId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Assessment not found"));
    }

    @Transactional(readOnly = true)
    public List<AssessmentEntity> list(UUID tenantId, String personCpid) {
        return assessmentRepository.findByTenantIdAndPersonCpidOrderByCreatedAtDesc(tenantId, personCpid);
    }

    @Transactional(readOnly = true)
    public List<AssessmentResponseEntity> responses(UUID assessmentId) {
        return responseRepository.findByAssessmentIdOrderBySectionAsc(assessmentId);
    }

    /** Latest completed assessment for a person, if any (used by plan recommendation / workbench). */
    @Transactional(readOnly = true)
    public AssessmentEntity latestCompleted(UUID tenantId, String personCpid, String templateCode) {
        return assessmentRepository
                .findFirstByTenantIdAndPersonCpidAndTemplateCodeAndStatusOrderByCompletedAtDesc(
                        tenantId, personCpid, templateCode, "COMPLETED")
                .orElse(null);
    }

    /** Starts a new draft, or resumes the person's existing DRAFT/IN_PROGRESS assessment. */
    @Transactional
    public AssessmentEntity startOrResumeDraft(UUID tenantId, String personCpid, String templateCode,
                                               String startedBy) {
        String tpl = templateCode == null || templateCode.isBlank() ? "BASELINE_WELLNESS" : templateCode;
        return assessmentRepository
                .findFirstByTenantIdAndPersonCpidAndTemplateCodeAndStatusInOrderByCreatedAtDesc(
                        tenantId, personCpid, tpl, List.of("DRAFT", "IN_PROGRESS"))
                .orElseGet(() -> {
                    AssessmentEntity a = new AssessmentEntity();
                    a.setTenantId(tenantId);
                    a.setPersonCpid(personCpid);
                    a.setTemplateCode(tpl);
                    a.setStatus("DRAFT");
                    a.setStartedBy(startedBy);
                    return assessmentRepository.save(a);
                });
    }

    /** One answered question in a step. */
    public record ResponseInput(String section, String questionCode, String valueText,
                                BigDecimal valueNumeric, String unit) { }

    /** Saves a wizard step's answers (idempotent per question_code), advancing status/step/consent. */
    @Transactional
    public AssessmentEntity saveStep(UUID tenantId, UUID assessmentId, String step,
                                     Boolean consentCaptured, List<ResponseInput> inputs) {
        AssessmentEntity a = get(tenantId, assessmentId);
        if ("COMPLETED".equals(a.getStatus()) || "ABANDONED".equals(a.getStatus())) {
            throw new IllegalStateException("Assessment is already " + a.getStatus());
        }
        if (inputs != null) {
            for (ResponseInput in : inputs) {
                AssessmentResponseEntity resp = responseRepository
                        .findByAssessmentIdAndQuestionCode(assessmentId, in.questionCode())
                        .orElseGet(AssessmentResponseEntity::new);
                resp.setTenantId(tenantId);
                resp.setAssessmentId(assessmentId);
                resp.setSection(in.section());
                resp.setQuestionCode(in.questionCode());
                resp.setValueText(in.valueText());
                resp.setValueNumeric(in.valueNumeric());
                resp.setUnit(in.unit());
                responseRepository.save(resp);
            }
        }
        if (step != null) {
            a.setCurrentStep(step);
        }
        if (consentCaptured != null) {
            a.setConsentCaptured(consentCaptured);
        }
        if ("DRAFT".equals(a.getStatus())) {
            a.setStatus("IN_PROGRESS");
        }
        return assessmentRepository.save(a);
    }

    /** Result of completing an assessment: the scored assessment + whether a care linkage was routed. */
    public record CompletionResult(AssessmentEntity assessment, boolean careLinkageRouted,
                                   List<String> nextActions) { }

    /**
     * Completes the assessment: runs deterministic risk scoring, persists band/score/detail, writes
     * ASSESSMENT + (if band changed toward risk) RISK_CHANGE/ESCALATION timeline rows, and — for
     * HIGH/URGENT bands — routes a care linkage. Requires consent to have been captured.
     */
    @Transactional
    public CompletionResult complete(UUID tenantId, UUID assessmentId) {
        AssessmentEntity a = get(tenantId, assessmentId);
        if ("COMPLETED".equals(a.getStatus())) {
            return new CompletionResult(a, false, nextActionsFor(a.getRiskBand()));
        }
        if (!a.isConsentCaptured()) {
            throw new IllegalStateException("Consent must be captured before completing the assessment");
        }

        Map<String, Object> responseMap = new LinkedHashMap<>();
        for (AssessmentResponseEntity r : responseRepository.findByAssessmentIdOrderBySectionAsc(assessmentId)) {
            Object v = r.getValueNumeric() != null ? r.getValueNumeric() : r.getValueText();
            responseMap.put(r.getQuestionCode(), v);
        }

        RiskScoringService.RiskResult result =
                riskScoring.score(tenantId, a.getTemplateCode(), responseMap);

        a.setRiskBand(result.band());
        a.setRiskScore(BigDecimal.valueOf(result.score()));
        try {
            a.setRiskDetail(objectMapper.writeValueAsString(RiskScoringService.detailOf(result)));
        } catch (JsonProcessingException e) {
            a.setRiskDetail(null);
        }
        a.setStatus("COMPLETED");
        a.setCompletedAt(java.time.OffsetDateTime.now());
        a = assessmentRepository.save(a);

        // Person-facing timeline: assessment completed.
        Map<String, Object> tlDetail = new LinkedHashMap<>();
        tlDetail.put("band", result.band());
        tlDetail.put("score", result.score());
        timeline.record(tenantId, a.getPersonCpid(), "ASSESSMENT",
                "Wellness assessment completed",
                "Your wellness risk band is " + humanBand(result.band()) + ".",
                severityFor(result.band()), "ASSESSMENT", assessmentId.toString(), tlDetail);

        // Outbox event.
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("assessment_id", assessmentId.toString());
        payload.put("person_cpid", a.getPersonCpid());
        payload.put("template_code", a.getTemplateCode());
        payload.put("risk_band", result.band());
        payload.put("risk_score", result.score());
        events.emit(tenantId, "WELLNESS_ASSESSMENT", assessmentId.toString(),
                "impilo.simba.wellness.assessment.completed.v1", a.getPersonCpid(),
                "simba:assessment:" + assessmentId + ":completed", payload);

        boolean routed = false;
        if (RiskScoringService.warrantsCareLinkage(result.band())) {
            CareLinkageEntity linkage = new CareLinkageEntity();
            linkage.setTenantId(tenantId);
            linkage.setPersonCpid(a.getPersonCpid());
            linkage.setTrigger(RiskScoringService.isUrgent(result.band()) ? "RED_FLAG" : "RISK_THRESHOLD");
            linkage.setSeverity(RiskScoringService.isUrgent(result.band()) ? "URGENT" : "ROUTINE");
            linkage.setReason("Baseline wellness assessment risk band: " + result.band());
            linkage.setTargetOwner("PCT");
            linkage.setCreatedBy("SIMBA_ASSESSMENT");
            careLinkage.route(linkage);
            routed = true;

            timeline.record(tenantId, a.getPersonCpid(),
                    RiskScoringService.isUrgent(result.band()) ? "ESCALATION" : "RISK_CHANGE",
                    RiskScoringService.isUrgent(result.band())
                            ? "Urgent care linkage created" : "Care follow-up suggested",
                    "A provider will follow up on your wellness assessment.",
                    RiskScoringService.isUrgent(result.band()) ? "URGENT" : "ATTENTION",
                    "CARE_LINKAGE", linkage.getLinkageId().toString(), null);
        }

        return new CompletionResult(a, routed, nextActionsFor(result.band()));
    }

    private static String humanBand(String band) {
        return switch (band) {
            case "LOW" -> "low";
            case "MODERATE" -> "moderate";
            case "HIGH" -> "high";
            case "NEEDS_CLINICAL_REVIEW" -> "one needing clinical review";
            case "URGENT_RED_FLAG" -> "urgent";
            default -> band.toLowerCase();
        };
    }

    private static String severityFor(String band) {
        return switch (band) {
            case "LOW" -> "POSITIVE";
            case "MODERATE" -> "INFO";
            case "HIGH", "NEEDS_CLINICAL_REVIEW" -> "ATTENTION";
            case "URGENT_RED_FLAG" -> "URGENT";
            default -> "INFO";
        };
    }

    private static List<String> nextActionsFor(String band) {
        List<String> actions = new ArrayList<>();
        if (band == null) {
            return actions;
        }
        switch (band) {
            case "LOW" -> actions.add("Keep up your healthy habits — explore a wellness journey.");
            case "MODERATE" -> {
                actions.add("Consider enrolling in a preventive wellness journey.");
                actions.add("Set a reminder for a check-up in 6 months.");
            }
            case "HIGH" -> {
                actions.add("Speak with a wellness coach about your results.");
                actions.add("A provider follow-up has been suggested.");
            }
            case "NEEDS_CLINICAL_REVIEW" -> actions.add("A clinical review has been requested — a provider will reach out.");
            case "URGENT_RED_FLAG" -> actions.add("Urgent: please seek care promptly. A provider has been notified.");
            default -> { }
        }
        return actions;
    }
}
