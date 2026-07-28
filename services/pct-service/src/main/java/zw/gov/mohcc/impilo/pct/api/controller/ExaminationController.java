package zw.gov.mohcc.impilo.pct.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.pct.core.clinical.ExaminationService;
import zw.gov.mohcc.impilo.pct.core.clinical.ExaminationVocabulary;
import zw.gov.mohcc.impilo.pct.persistence.entity.ExaminationEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Structured physical examinations (brief.md §7).
 *
 * <p>Authorization is enforced upstream at Envoy ext_authz; the care relationship is enforced in
 * {@link ExaminationService} via {@code ClinicalAccessGuard}.</p>
 */
@RestController
@RequestMapping("/v1/examinations")
public class ExaminationController {

    private final ExaminationService service;

    public ExaminationController(ExaminationService service) {
        this.service = service;
    }

    /**
     * The vocabularies a client needs to render the form.
     *
     * <p>Served rather than duplicated in the UI so a region or state added to the migration reaches
     * the form without a second edit — two hand-maintained copies of a clinical vocabulary drift, and
     * the drift shows up as a value the server refuses after a clinician has typed it.</p>
     */
    @GetMapping("/vocabulary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> vocabulary() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("regions", ExaminationVocabulary.REGIONS.stream().sorted().toList());
        out.put("states", ExaminationVocabulary.STATES.stream().sorted().toList());
        out.put("examined_states", ExaminationVocabulary.EXAMINED_STATES.stream().sorted().toList());
        out.put("states_needing_detail", ExaminationVocabulary.STATES.stream()
                .filter(ExaminationVocabulary::requiresDetail).sorted().toList());
        out.put("graphics", ExaminationVocabulary.GRAPHICS.stream().sorted().toList());
        out.put("laterality", ExaminationVocabulary.LATERALITY.stream().sorted().toList());
        out.put("not_examined_is_not_normal",
                "NOT_EXAMINED, UNABLE_TO_EXAMINE and DEFERRED are not findings of normality. A form "
                + "that groups them with NORMAL turns 'nobody looked' into 'nothing wrong'.");
        return ResponseEntity.ok(ApiResponse.ok(out, correlationId()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list(
            @RequestParam(name = "subject_cpid") String subjectCpid) {
        List<Map<String, Object>> out = service.list(subjectCpid).stream()
                .map(ExaminationController::toSummary).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(out, correlationId()));
    }

    @GetMapping("/{examinationId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> get(@PathVariable UUID examinationId) {
        return ResponseEntity.ok(ApiResponse.ok(service.get(examinationId), correlationId()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> record(@RequestBody Map<String, Object> body) {
        ExaminationEntity e = service.record(body);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.get(e.getExaminationId()), correlationId()));
    }

    private static Map<String, Object> toSummary(ExaminationEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("examination_id", e.getExaminationId());
        m.put("subject_cpid", e.getSubjectCpid());
        m.put("journey_id", e.getJourneyId());
        m.put("encounter_id", e.getEncounterId());
        m.put("examined_at", e.getExaminedAt());
        m.put("examined_by", e.getExaminedBy());
        m.put("context_note", e.getContextNote());
        return m;
    }

    private static String correlationId() {
        return TrustContextHolder.require().correlationId().toString();
    }
}
