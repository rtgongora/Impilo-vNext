package zw.gov.mohcc.impilo.pct.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.pct.core.clinical.MedicalEpisodeService;
import zw.gov.mohcc.impilo.pct.persistence.entity.MedicalEpisodeEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.MedicalEpisodeProblemEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Medical episodes — a course of medical care over time.
 *
 * <p>Authorization is enforced upstream at Envoy ext_authz; the subject relationship is enforced in
 * {@link MedicalEpisodeService} via {@code ClinicalAccessGuard}, because ext_authz cannot bind the
 * body subject to a care relationship. Every write is audited via the outbox.</p>
 */
@RestController
@RequestMapping("/v1/medical-episodes")
public class MedicalEpisodeController {

    private final MedicalEpisodeService episodeService;

    public MedicalEpisodeController(MedicalEpisodeService episodeService) {
        this.episodeService = episodeService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list(
            @RequestParam(name = "subject_cpid") String subjectCpid,
            @RequestParam(name = "open_only", defaultValue = "false") boolean openOnly) {
        List<Map<String, Object>> out = episodeService.list(subjectCpid, openOnly)
                .stream().map(this::toMap).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(out, correlationId()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> open(@RequestBody Map<String, Object> body) {
        MedicalEpisodeEntity e = episodeService.open(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(toMap(e), correlationId()));
    }

    @GetMapping("/{episodeId}/problems")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> problems(@PathVariable UUID episodeId) {
        List<Map<String, Object>> out = episodeService.problemsOf(episodeId).stream()
                .map(MedicalEpisodeController::linkToMap).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(out, correlationId()));
    }

    @PostMapping("/{episodeId}/problems")
    public ResponseEntity<ApiResponse<Map<String, Object>>> attachProblem(
            @PathVariable UUID episodeId,
            @RequestBody Map<String, Object> body) {
        MedicalEpisodeProblemEntity link = episodeService.attachProblem(
                episodeId,
                UUID.fromString(String.valueOf(body.get("problem_id"))),
                body.get("role") == null ? null : String.valueOf(body.get("role")));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(linkToMap(link), correlationId()));
    }

    @PostMapping("/{episodeId}/close")
    public ResponseEntity<ApiResponse<Map<String, Object>>> close(
            @PathVariable UUID episodeId,
            @RequestBody Map<String, Object> body) {
        MedicalEpisodeEntity e = episodeService.close(
                episodeId,
                body.get("status") == null ? null : String.valueOf(body.get("status")),
                body.get("end_reason") == null ? null : String.valueOf(body.get("end_reason")),
                body.get("ended_on") == null ? null : String.valueOf(body.get("ended_on")));
        return ResponseEntity.ok(ApiResponse.ok(toMap(e), correlationId()));
    }

    @PostMapping("/{episodeId}/reopen")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reopen(@PathVariable UUID episodeId) {
        return ResponseEntity.ok(ApiResponse.ok(toMap(episodeService.reopen(episodeId)), correlationId()));
    }

    private static String correlationId() {
        return TrustContextHolder.require().correlationId().toString();
    }

    private Map<String, Object> toMap(MedicalEpisodeEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("episode_id", e.getEpisodeId().toString());
        m.put("subject_cpid", e.getSubjectCpid());
        m.put("episode_type", e.getEpisodeType());
        m.put("status", e.getStatus());
        m.put("title", e.getTitle());
        m.put("responsible_service", e.getResponsibleService());
        m.put("responsible_actor", e.getResponsibleActor());
        m.put("managing_facility_id", e.getManagingFacilityId());
        m.put("started_on", e.getStartedOn() != null ? e.getStartedOn().toString() : null);
        m.put("ended_on", e.getEndedOn() != null ? e.getEndedOn().toString() : null);
        m.put("end_reason", e.getEndReason());
        m.put("emergency_episode_id",
                e.getEmergencyEpisodeId() == null ? null : e.getEmergencyEpisodeId().toString());
        m.put("notes", e.getNotes());
        m.put("created_by", e.getCreatedBy());
        m.put("created_at", e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        return m;
    }

    private static Map<String, Object> linkToMap(MedicalEpisodeProblemEntity link) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("episode_id", link.getId().getEpisodeId().toString());
        m.put("problem_id", link.getId().getProblemId().toString());
        m.put("role", link.getRole());
        m.put("added_at", link.getAddedAt() != null ? link.getAddedAt().toString() : null);
        return m;
    }
}
