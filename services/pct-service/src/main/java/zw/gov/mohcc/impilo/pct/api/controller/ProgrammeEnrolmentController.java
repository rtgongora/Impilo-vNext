package zw.gov.mohcc.impilo.pct.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.pct.core.clinical.DuplicateEnrolmentException;
import zw.gov.mohcc.impilo.pct.core.clinical.ProgrammeEnrolmentService;
import zw.gov.mohcc.impilo.pct.core.clinical.ProgrammeVocabulary;
import zw.gov.mohcc.impilo.pct.persistence.entity.ProgrammeEnrolmentEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.TreatmentRegimenEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * HIV and TB programme enrolment API (PCT).
 *
 * <p>Authorization is enforced upstream at Envoy ext_authz; the subject relationship is enforced in
 * {@link ProgrammeEnrolmentService} via {@code ClinicalAccessGuard}. Every write is audited via the
 * outbox.</p>
 *
 * <p><strong>Confidentiality.</strong> This route carries both HIV (specially protected) and TB (not
 * protected) enrolments, and each response says whether it is {@code confidential}. HIV's specially
 * protected disclosure lane is served separately under a {@code /confidential/} path consumed by the
 * SPECIALLY_PROTECTED guard; that seam ships in SHADOW pending an MoHCC governance act, and the
 * ENFORCE gap is stated in the pack deliverable. This controller does not stamp a protection class
 * it cannot yet honour.</p>
 */
@RestController
@RequestMapping("/v1/programme-enrolments")
public class ProgrammeEnrolmentController {

    private final ProgrammeEnrolmentService service;

    public ProgrammeEnrolmentController(ProgrammeEnrolmentService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list(
            @RequestParam(name = "subject_cpid") String subjectCpid,
            @RequestParam(name = "open_only", defaultValue = "false") boolean openOnly) {
        List<Map<String, Object>> out = service.list(subjectCpid, openOnly)
                .stream().map(ProgrammeEnrolmentController::toMap).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(out, correlationId()));
    }

    /**
     * Programme cohort counts — the medicine analytics read.
     *
     * <p>Served from the system of record because reporting-service holds a separate database and
     * cannot see {@code pct.*}; a report definition naming these tables would be ACTIVE and
     * unrunnable. Counts enrolments rather than people, and says so on the response.</p>
     */
    @GetMapping("/cohort-counts")
    public ResponseEntity<Map<String, Object>> cohortCounts(
            @RequestParam(value = "facility_id", required = false) String facilityId) {
        return ResponseEntity.ok(Map.of("data", service.cohortCounts(facilityId)));
    }

    @GetMapping("/{enrolmentId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> get(@PathVariable UUID enrolmentId) {
        return ResponseEntity.ok(ApiResponse.ok(toMap(service.get(enrolmentId)), correlationId()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> enrol(@RequestBody Map<String, Object> body) {
        ProgrammeEnrolmentEntity e = service.enrol(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(toMap(e), correlationId()));
    }

    @PostMapping("/{enrolmentId}/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateStatus(
            @PathVariable UUID enrolmentId, @RequestBody Map<String, Object> body) {
        ProgrammeEnrolmentEntity e = service.updateStatus(enrolmentId,
                body.get("status") == null ? null : String.valueOf(body.get("status")));
        return ResponseEntity.ok(ApiResponse.ok(toMap(e), correlationId()));
    }

    @PostMapping("/{enrolmentId}/exit")
    public ResponseEntity<ApiResponse<Map<String, Object>>> exit(
            @PathVariable UUID enrolmentId, @RequestBody Map<String, Object> body) {
        ProgrammeEnrolmentEntity e = service.exit(enrolmentId,
                str(body.get("exit_reason"), body.get("exitReason")),
                str(body.get("exit_on"), body.get("exitOn")));
        return ResponseEntity.ok(ApiResponse.ok(toMap(e), correlationId()));
    }

    @GetMapping("/{enrolmentId}/regimens")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> regimens(@PathVariable UUID enrolmentId) {
        List<Map<String, Object>> out = service.regimensOf(enrolmentId).stream()
                .map(ProgrammeEnrolmentController::regimenToMap).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(out, correlationId()));
    }

    @PostMapping("/{enrolmentId}/regimens")
    public ResponseEntity<ApiResponse<Map<String, Object>>> startRegimen(
            @PathVariable UUID enrolmentId, @RequestBody Map<String, Object> body) {
        TreatmentRegimenEntity r = service.startRegimen(enrolmentId, body);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(regimenToMap(r), correlationId()));
    }

    /**
     * A duplicate active enrolment is a hard 409 pointing at the enrolment already open — not an
     * offer of resolutions, because unlike a duplicate problem there is only one right answer: exit
     * the existing enrolment first.
     */
    @ExceptionHandler(DuplicateEnrolmentException.class)
    public ResponseEntity<Map<String, Object>> duplicate(DuplicateEnrolmentException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "programme_already_enrolled");
        body.put("message", e.getMessage());
        body.put("programme", e.getProgramme());
        body.put("active_enrolment_id", e.getActiveEnrolmentId().toString());
        body.put("correlation_id", correlationId());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    private static String correlationId() {
        return TrustContextHolder.require().correlationId().toString();
    }

    private static Map<String, Object> toMap(ProgrammeEnrolmentEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enrolment_id", e.getEnrolmentId().toString());
        m.put("subject_cpid", e.getSubjectCpid());
        m.put("programme", e.getProgramme());
        m.put("status", e.getStatus());
        m.put("exit_reason", e.getExitReason());
        m.put("anchor_problem_id", e.getAnchorProblemId().toString());
        m.put("episode_id", e.getEpisodeId() == null ? null : e.getEpisodeId().toString());
        m.put("pregnancy_episode_id",
                e.getPregnancyEpisodeId() == null ? null : e.getPregnancyEpisodeId().toString());
        m.put("programme_number", e.getProgrammeNumber());
        m.put("enrolled_on", e.getEnrolledOn() != null ? e.getEnrolledOn().toString() : null);
        m.put("exit_on", e.getExitOn() != null ? e.getExitOn().toString() : null);
        m.put("managing_facility_id", e.getManagingFacilityId());
        m.put("responsible_actor", e.getResponsibleActor());
        m.put("notes", e.getNotes());
        // Derived, single source of truth (the programme). Lets the read path route HIV through the
        // confidential lane without a second stored flag that could drift.
        m.put("confidential", ProgrammeVocabulary.isConfidential(e.getProgramme()));
        m.put("created_by", e.getCreatedBy());
        m.put("created_at", e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        return m;
    }

    private static Map<String, Object> regimenToMap(TreatmentRegimenEntity r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("regimen_id", r.getRegimenId().toString());
        m.put("enrolment_id", r.getEnrolmentId().toString());
        m.put("subject_cpid", r.getSubjectCpid());
        m.put("regimen_code", r.getRegimenCode());
        m.put("regimen_display", r.getRegimenDisplay());
        m.put("regimen_stage", r.getRegimenStage());
        m.put("started_on", r.getStartedOn() != null ? r.getStartedOn().toString() : null);
        m.put("ended_on", r.getEndedOn() != null ? r.getEndedOn().toString() : null);
        m.put("change_reason", r.getChangeReason());
        m.put("current", r.getEndedOn() == null);
        m.put("notes", r.getNotes());
        m.put("recorded_by", r.getRecordedBy());
        m.put("recorded_at", r.getRecordedAt() != null ? r.getRecordedAt().toString() : null);
        return m;
    }

    private static String str(Object... candidates) {
        for (Object v : candidates) {
            if (v == null) continue;
            String s = String.valueOf(v).trim();
            if (!s.isEmpty()) return s;
        }
        return null;
    }
}
