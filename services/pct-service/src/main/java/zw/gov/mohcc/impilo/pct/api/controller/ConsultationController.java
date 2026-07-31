package zw.gov.mohcc.impilo.pct.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.pct.core.clinical.ConsultationService;
import zw.gov.mohcc.impilo.pct.persistence.entity.ConsultationEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.MdtDecisionEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Consultation and multidisciplinary decision (brief.md §14).
 *
 * <p>Every consultation response carries {@code owning_service} and a sentence saying what it means,
 * because the failure this record exists to prevent is a reader assuming the answering team took the
 * patient.</p>
 */
@RestController
@RequestMapping("/v1/consultations")
public class ConsultationController {

    private final ConsultationService service;

    public ConsultationController(ConsultationService service) {
        this.service = service;
    }

    /** Declared before /{consultationId} so "worklist" is never parsed as a UUID. */
    @GetMapping("/worklist")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> worklist(
            @RequestParam("service") String service_) {
        return ResponseEntity.ok(ApiResponse.ok(
                service.worklist(service_).stream().map(ConsultationController::toMap)
                        .collect(Collectors.toList()),
                correlationId()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list(
            @RequestParam(name = "subject_cpid") String subjectCpid) {
        return ResponseEntity.ok(ApiResponse.ok(
                service.listForSubject(subjectCpid).stream().map(ConsultationController::toMap)
                        .collect(Collectors.toList()),
                correlationId()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> request(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(toMap(service.request(body)), correlationId()));
    }

    @PostMapping("/{consultationId}/accept")
    public ResponseEntity<ApiResponse<Map<String, Object>>> accept(@PathVariable UUID consultationId) {
        return ResponseEntity.ok(ApiResponse.ok(toMap(service.accept(consultationId)), correlationId()));
    }

    @PostMapping("/{consultationId}/answer")
    public ResponseEntity<ApiResponse<Map<String, Object>>> answer(
            @PathVariable UUID consultationId, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.ok(toMap(service.answer(consultationId, body)), correlationId()));
    }

    @PostMapping("/{consultationId}/decline")
    public ResponseEntity<ApiResponse<Map<String, Object>>> decline(
            @PathVariable UUID consultationId, @RequestBody Map<String, Object> body) {
        String reason = body.get("reason") == null ? null : String.valueOf(body.get("reason"));
        return ResponseEntity.ok(ApiResponse.ok(toMap(service.decline(consultationId, reason)), correlationId()));
    }

    // ── MDT ──────────────────────────────────────────────────────────────────────

    @GetMapping("/mdt")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listDecisions(
            @RequestParam(name = "subject_cpid") String subjectCpid) {
        return ResponseEntity.ok(ApiResponse.ok(
                service.listDecisions(subjectCpid).stream().map(ConsultationController::mdtToMap)
                        .collect(Collectors.toList()),
                correlationId()));
    }

    @GetMapping("/mdt/{decisionId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> decision(@PathVariable UUID decisionId) {
        return ResponseEntity.ok(ApiResponse.ok(service.decision(decisionId), correlationId()));
    }

    @PostMapping("/mdt")
    public ResponseEntity<ApiResponse<Map<String, Object>>> recordDecision(
            @RequestBody Map<String, Object> body) {
        MdtDecisionEntity e = service.recordDecision(body);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.decision(e.getDecisionId()), correlationId()));
    }

    private static Map<String, Object> toMap(ConsultationEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("consultation_id", e.getConsultationId());
        m.put("subject_cpid", e.getSubjectCpid());
        m.put("requesting_service", e.getRequestingService());
        m.put("requesting_actor", e.getRequestingActor());
        m.put("asked_of_service", e.getAskedOfService());
        m.put("question", e.getQuestion());
        m.put("clinical_summary", e.getClinicalSummary());
        m.put("urgency", e.getUrgency());
        m.put("status", e.getStatus());
        m.put("owning_service", e.getOwningService());
        // Said on every response, because the failure is a reader assuming the answering team took
        // the patient. The record cannot rely on the reader knowing that it does not work that way.
        m.put("owning_service_note",
                e.getOwningService() + " holds this patient. A consultation asks a question and never "
                + "transfers care; a takeover is a separate, explicit act.");
        m.put("advice", e.getAdvice());
        m.put("responded_by", e.getRespondedBy());
        m.put("responded_at", e.getRespondedAt());
        m.put("takeover_recommended", e.isTakeoverRecommended());
        if (e.isTakeoverRecommended()) {
            m.put("takeover_note",
                    "A takeover was RECOMMENDED and has not happened. " + e.getOwningService()
                    + " still holds this patient until a care transfer is accepted with the "
                    + "receiving service's own record id.");
        }
        m.put("decline_reason", e.getDeclineReason());
        m.put("requested_at", e.getRequestedAt());
        return m;
    }

    private static Map<String, Object> mdtToMap(MdtDecisionEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("decision_id", e.getDecisionId());
        m.put("subject_cpid", e.getSubjectCpid());
        m.put("meeting_type", e.getMeetingType());
        m.put("met_on", e.getMetOn());
        m.put("chaired_by", e.getChairedBy());
        m.put("participants", e.getParticipants());
        m.put("decision", e.getDecision());
        m.put("treatment_intent", e.getTreatmentIntent());
        m.put("next_action", e.getNextAction());
        m.put("responsible_service", e.getResponsibleService());
        m.put("case_item_id", e.getCaseItemId() == null ? null : e.getCaseItemId().toString());
        return m;
    }

    private static String correlationId() {
        return TrustContextHolder.require().correlationId().toString();
    }
}
