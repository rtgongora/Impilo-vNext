package zw.gov.mohcc.impilo.pct.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.pct.core.clinical.CareTransferService;
import zw.gov.mohcc.impilo.pct.persistence.entity.CareTransferEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Transfer of care (brief.md §14). Ownership moves only on {@code /accept} with an
 * {@code accepting_ref}; requesting never hands over the patient.
 */
@RestController
@RequestMapping("/v1/care-transfers")
public class CareTransferController {

    private final CareTransferService service;

    public CareTransferController(CareTransferService service) {
        this.service = service;
    }

    @GetMapping("/inbox")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> inbox(
            @RequestParam("service") String service_) {
        return ResponseEntity.ok(ApiResponse.ok(
                service.inbox(service_).stream().map(CareTransferController::toMap)
                        .collect(Collectors.toList()),
                correlationId()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list(
            @RequestParam(name = "subject_cpid") String subjectCpid) {
        return ResponseEntity.ok(ApiResponse.ok(
                service.listForSubject(subjectCpid).stream().map(CareTransferController::toMap)
                        .collect(Collectors.toList()),
                correlationId()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> request(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(toMap(service.request(body)), correlationId()));
    }

    @PostMapping("/{transferId}/accept")
    public ResponseEntity<ApiResponse<Map<String, Object>>> accept(
            @PathVariable UUID transferId, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.ok(toMap(service.accept(transferId, body)), correlationId()));
    }

    @PostMapping("/{transferId}/decline")
    public ResponseEntity<ApiResponse<Map<String, Object>>> decline(
            @PathVariable UUID transferId, @RequestBody Map<String, Object> body) {
        String reason = body.get("reason") == null ? null : String.valueOf(body.get("reason"));
        return ResponseEntity.ok(ApiResponse.ok(toMap(service.decline(transferId, reason)), correlationId()));
    }

    @PostMapping("/{transferId}/withdraw")
    public ResponseEntity<ApiResponse<Map<String, Object>>> withdraw(@PathVariable UUID transferId) {
        return ResponseEntity.ok(ApiResponse.ok(toMap(service.withdraw(transferId)), correlationId()));
    }

    static Map<String, Object> toMap(CareTransferEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("transfer_id", e.getTransferId().toString());
        m.put("subject_cpid", e.getSubjectCpid());
        m.put("journey_id", e.getJourneyId() == null ? null : e.getJourneyId().toString());
        m.put("encounter_id", e.getEncounterId() == null ? null : e.getEncounterId().toString());
        m.put("consultation_id", e.getConsultationId() == null ? null : e.getConsultationId().toString());
        m.put("from_service", e.getFromService());
        m.put("to_service", e.getToService());
        m.put("requested_by", e.getRequestedBy());
        m.put("requested_at", e.getRequestedAt() == null ? null : e.getRequestedAt().toString());
        m.put("reason", e.getReason());
        m.put("status", e.getStatus());
        m.put("accepted_by", e.getAcceptedBy());
        m.put("accepted_at", e.getAcceptedAt() == null ? null : e.getAcceptedAt().toString());
        m.put("accepting_ref", e.getAcceptingRef());
        m.put("declined_by", e.getDeclinedBy());
        m.put("declined_at", e.getDeclinedAt() == null ? null : e.getDeclinedAt().toString());
        m.put("decline_reason", e.getDeclineReason());
        m.put("withdrawn_by", e.getWithdrawnBy());
        m.put("withdrawn_at", e.getWithdrawnAt() == null ? null : e.getWithdrawnAt().toString());
        m.put("ownership_note", ownershipNote(e));
        return m;
    }

    private static String ownershipNote(CareTransferEntity e) {
        return switch (e.getStatus()) {
            case "PENDING" -> e.getFromService() + " still holds this patient. A transfer has been "
                    + "requested of " + e.getToService() + " and has not been accepted.";
            case "ACCEPTED" -> e.getToService() + " accepted this transfer (ref "
                    + e.getAcceptingRef() + ") and now holds this patient.";
            case "DECLINED" -> e.getFromService() + " still holds this patient. "
                    + e.getToService() + " declined the transfer.";
            case "WITHDRAWN" -> e.getFromService() + " still holds this patient. The transfer request "
                    + "was withdrawn before acceptance.";
            default -> e.getFromService() + " holds this patient.";
        };
    }

    private static String correlationId() {
        return TrustContextHolder.require().correlationId().toString();
    }
}
