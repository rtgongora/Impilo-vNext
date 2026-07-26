package zw.gov.mohcc.impilo.pct.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.pct.core.clinical.ImamService;
import zw.gov.mohcc.impilo.pct.persistence.entity.ImamEpisodeEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.ImamVisitEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * IMAM episode API — the treatment the IMNCI nutrition classifications prescribe.
 *
 * <p>Field names are snake_case, matching the contract the BFF and the other clinical registries in
 * this service already use.</p>
 */
@RestController
@RequestMapping("/v1/imam")
public class ImamController {

    private final ImamService imamService;

    public ImamController(ImamService imamService) {
        this.imamService = imamService;
    }

    @GetMapping("/episodes")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list(
            @RequestParam(name = "patient_id", required = false) String patientId,
            @RequestParam(name = "subject_cpid", required = false) String subjectCpid) {
        String subject = subjectCpid != null && !subjectCpid.isBlank() ? subjectCpid : patientId;
        List<Map<String, Object>> out = imamService.list(subject)
                .stream().map(ImamController::episodeToMap).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(out, correlationId()));
    }

    /**
     * The episode with its reviews and its live assessment. The assessment is evaluated on read
     * rather than served from the last stored value, because a child becomes a defaulter by the
     * passage of time, not by anyone writing something down.
     */
    @GetMapping("/episodes/{episodeId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> get(
            @PathVariable UUID episodeId,
            @RequestParam(name = "as_of", required = false) String asOf) {
        ImamEpisodeEntity episode = imamService.get(episodeId);
        Map<String, Object> out = episodeToMap(episode);
        out.put("visits", imamService.visits(episodeId).stream()
                .map(ImamController::visitToMap).collect(Collectors.toList()));
        out.put("assessment", imamService.assess(episodeId, parseDate(asOf)));
        return ResponseEntity.ok(ApiResponse.ok(out, correlationId()));
    }

    @GetMapping("/episodes/{episodeId}/assessment")
    public ResponseEntity<ApiResponse<Map<String, Object>>> assessment(
            @PathVariable UUID episodeId,
            @RequestParam(name = "as_of", required = false) String asOf) {
        return ResponseEntity.ok(ApiResponse.ok(imamService.assess(episodeId, parseDate(asOf)), correlationId()));
    }

    /**
     * Enrols a child. Pass {@code source_classification} with the IMNCI nutrition classification to
     * enrol straight off an assessment — the programme, review interval, ration and discharge
     * criteria then come from the same governed pack that produced the classification.
     */
    @PostMapping("/episodes")
    public ResponseEntity<ApiResponse<Map<String, Object>>> enrol(@RequestBody Map<String, Object> body) {
        // Authz: RBAC at ext_authz; subject relationship bound in ImamService via ClinicalAccessGuard,
        // the same discipline as growth, observations and the problem list.
        ImamEpisodeEntity episode = imamService.enrol(body);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(episodeToMap(episode), correlationId()));
    }

    /** Records a scheduled review. Post {@code "attended": false} to record one the child missed. */
    @PostMapping("/episodes/{episodeId}/visits")
    public ResponseEntity<ApiResponse<Map<String, Object>>> recordVisit(
            @PathVariable UUID episodeId, @RequestBody Map<String, Object> body) {
        ImamVisitEntity visit = imamService.recordVisit(episodeId, body);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(visitToMap(visit), correlationId()));
    }

    /**
     * Closes the episode with an outcome. Recording CURED when the discharge criteria are not met —
     * or could not be evaluated — requires {@code discharge_override_reason}.
     */
    @PostMapping("/episodes/{episodeId}/outcome")
    public ResponseEntity<ApiResponse<Map<String, Object>>> closeOutcome(
            @PathVariable UUID episodeId, @RequestBody Map<String, Object> body) {
        ImamEpisodeEntity episode = imamService.closeOutcome(episodeId, body);
        return ResponseEntity.ok(ApiResponse.ok(episodeToMap(episode), correlationId()));
    }

    /**
     * The defaulter tracing worklist: children whose review has come and gone, most overdue first,
     * with those never reviewed at all flagged.
     */
    @GetMapping("/tracing-queue")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> tracingQueue(
            @RequestParam(name = "facility_id", required = false) UUID facilityId,
            @RequestParam(name = "as_of", required = false) String asOf) {
        return ResponseEntity.ok(ApiResponse.ok(
                imamService.tracingQueue(facilityId, parseDate(asOf)), correlationId()));
    }

    private static Map<String, Object> episodeToMap(ImamEpisodeEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("imam_episode_id", e.getImamEpisodeId().toString());
        m.put("patient_id", e.getSubjectCpid());
        m.put("subject_cpid", e.getSubjectCpid());
        m.put("journey_id", e.getJourneyId());
        m.put("encounter_id", e.getEncounterId());
        m.put("facility_id", e.getFacilityId() == null ? null : e.getFacilityId().toString());

        m.put("programme", e.getProgramme());
        m.put("status", e.getStatus());
        m.put("enrolled_at", e.getEnrolledAt() == null ? null : e.getEnrolledAt().toString());
        m.put("enrolled_by", e.getEnrolledBy());
        m.put("enrolment_source", e.getEnrolmentSource());
        m.put("source_classification", e.getSourceClassification());
        m.put("source_ref", e.getSourceRef());

        m.put("admission_criterion", e.getAdmissionCriterion());
        m.put("admission_age_days", e.getAdmissionAgeDays());
        m.put("admission_muac_cm", e.getAdmissionMuacCm());
        m.put("admission_weight_kg", e.getAdmissionWeightKg());
        m.put("admission_height_cm", e.getAdmissionHeightCm());
        m.put("admission_weight_for_height_z", e.getAdmissionWeightForHeightZ());
        m.put("admission_oedema", e.getAdmissionOedema());
        m.put("admission_appetite_test", e.getAdmissionAppetiteTest());
        m.put("admission_complications", e.getAdmissionComplications());

        m.put("content_pack_id", e.getContentPackId());
        m.put("content_version", e.getContentVersion());
        m.put("approval_status", e.getApprovalStatus());

        m.put("review_interval_days", e.getReviewIntervalDays());
        m.put("attendance_grace_days", e.getAttendanceGraceDays());
        m.put("trace_after_missed_visits", e.getTraceAfterMissedVisits());
        m.put("defaulter_missed_visits", e.getDefaulterMissedVisits());
        m.put("last_contact_on", e.getLastContactOn() == null ? null : e.getLastContactOn().toString());
        m.put("next_review_due", e.getNextReviewDue() == null ? null : e.getNextReviewDue().toString());

        m.put("outcome", e.getOutcome());
        m.put("outcome_at", e.getOutcomeAt() == null ? null : e.getOutcomeAt().toString());
        m.put("outcome_by", e.getOutcomeBy());
        m.put("outcome_note", e.getOutcomeNote());

        m.put("discharge_muac_cm", e.getDischargeMuacCm());
        m.put("discharge_weight_kg", e.getDischargeWeightKg());
        m.put("discharge_oedema", e.getDischargeOedema());
        m.put("discharge_criteria_met", e.getDischargeCriteriaMet());
        m.put("discharge_criteria_detail", e.getDischargeCriteriaDetail());
        m.put("discharge_override_reason", e.getDischargeOverrideReason());

        m.put("created_at", e.getCreatedAt() == null ? null : e.getCreatedAt().toString());
        m.put("updated_at", e.getUpdatedAt() == null ? null : e.getUpdatedAt().toString());
        return m;
    }

    private static Map<String, Object> visitToMap(ImamVisitEntity v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("imam_visit_id", v.getImamVisitId().toString());
        m.put("imam_episode_id", v.getImamEpisodeId().toString());
        m.put("patient_id", v.getSubjectCpid());
        m.put("subject_cpid", v.getSubjectCpid());
        m.put("encounter_id", v.getEncounterId());
        m.put("visit_number", v.getVisitNumber());
        m.put("scheduled_for", v.getScheduledFor() == null ? null : v.getScheduledFor().toString());
        m.put("attended", v.isAttended());
        m.put("visit_date", v.getVisitDate() == null ? null : v.getVisitDate().toString());
        m.put("recorded_by", v.getRecordedBy());

        m.put("muac_cm", v.getMuacCm());
        m.put("weight_kg", v.getWeightKg());
        m.put("height_cm", v.getHeightCm());
        m.put("weight_for_height_z", v.getWeightForHeightZ());
        m.put("oedema", v.getOedema());
        m.put("appetite_test", v.getAppetiteTest());
        m.put("danger_signs_present", v.getDangerSignsPresent());

        m.put("rutf_product_code", v.getRutfProductCode());
        m.put("rutf_sachets_issued", v.getRutfSachetsIssued());
        m.put("rutf_sachets_recommended", v.getRutfSachetsRecommended());

        m.put("clinical_note", v.getClinicalNote());
        m.put("next_review_due", v.getNextReviewDue() == null ? null : v.getNextReviewDue().toString());

        m.put("discharge_eligible", v.getDischargeEligible());
        m.put("attendance_status", v.getAttendanceStatus());
        m.put("response_status", v.getResponseStatus());
        m.put("assessment_note", v.getAssessmentNote());
        m.put("assessment_content_version", v.getAssessmentContentVersion());

        m.put("created_at", v.getCreatedAt() == null ? null : v.getCreatedAt().toString());
        return m;
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value.trim().substring(0, 10));
    }

    private static String correlationId() {
        return TrustContextHolder.require().correlationId().toString();
    }
}
