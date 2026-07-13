package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * BFF mirror of the tuso HPA regulatory operations surface (V018): catalogues,
 * rules, premises + shared occupancy, RFI cycle, external council reviews, PIC
 * nominations, inspection visits + structured responses. Explicit whitelisted
 * routes over a generic transport — no free-form proxying.
 */
@RestController
@RequestMapping("/internal/v1/facility-registry")
public class HpaRegulatoryBffController {

    private static final Logger log = LoggerFactory.getLogger(HpaRegulatoryBffController.class);

    private final TusoServiceClient tusoClient;
    private final VarapiServiceClient varapiClient;

    public HpaRegulatoryBffController(TusoServiceClient tusoClient, VarapiServiceClient varapiClient) {
        this.tusoClient = tusoClient;
        this.varapiClient = varapiClient;
    }

    // ---- Catalogues + rules ------------------------------------------------

    @GetMapping("/application-types")
    public ResponseEntity<Map<String, Object>> applicationTypes(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardGet("/application-types", requestId, correlationId);
    }

    @GetMapping("/classifications")
    public ResponseEntity<Map<String, Object>> classifications(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardGet("/classifications", requestId, correlationId);
    }

    @GetMapping("/rules")
    public ResponseEntity<Map<String, Object>> rules(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardGet("/rules", requestId, correlationId);
    }

    @PostMapping("/rules/{ruleId}/approve")
    public ResponseEntity<Map<String, Object>> approveRule(
            @PathVariable UUID ruleId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardPost("/rules/" + ruleId + "/approve", null, requestId, correlationId);
    }

    // ---- Fee schedule + application fee (SI 78 of 2017) ----------------------

    @GetMapping("/fee-schedules")
    public ResponseEntity<Map<String, Object>> feeSchedules(
            @RequestParam(required = false, defaultValue = "SI_78_2017") String scheduleCode,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardGet("/fee-schedules?scheduleCode=" + scheduleCode, requestId, correlationId);
    }

    @PostMapping("/fee-schedules")
    public ResponseEntity<Map<String, Object>> createFeeSchedule(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardPost("/fee-schedules", body, requestId, correlationId);
    }

    @PostMapping("/fee-schedules/{feeId}/approve")
    public ResponseEntity<Map<String, Object>> approveFeeSchedule(
            @PathVariable UUID feeId,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardPost("/fee-schedules/" + feeId + "/approve", body, requestId, correlationId);
    }

    @GetMapping("/applications/{applicationId}/fee")
    public ResponseEntity<Map<String, Object>> applicationFee(
            @PathVariable UUID applicationId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardGet("/applications/" + applicationId + "/fee", requestId, correlationId);
    }

    @PostMapping("/applications/{applicationId}/fee/payment-intent")
    public ResponseEntity<Map<String, Object>> createFeePaymentIntent(
            @PathVariable UUID applicationId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardPost("/applications/" + applicationId + "/fee/payment-intent", null, requestId, correlationId);
    }

    @PostMapping("/applications/{applicationId}/fee/waive")
    public ResponseEntity<Map<String, Object>> waiveFee(
            @PathVariable UUID applicationId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardPost("/applications/" + applicationId + "/fee/waive", body, requestId, correlationId);
    }

    // ---- Premises ------------------------------------------------------------

    @GetMapping("/premises")
    public ResponseEntity<Map<String, Object>> listPremises(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardGet("/premises", requestId, correlationId);
    }

    @PostMapping("/premises")
    public ResponseEntity<Map<String, Object>> createPremises(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardPost("/premises", body, requestId, correlationId);
    }

    @PostMapping("/premises/{premisesId}/occupancies")
    public ResponseEntity<Map<String, Object>> assignOccupancy(
            @PathVariable UUID premisesId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardPost("/premises/" + premisesId + "/occupancies", body, requestId, correlationId);
    }

    @GetMapping("/premises/{premisesId}/occupancies")
    public ResponseEntity<Map<String, Object>> occupants(
            @PathVariable UUID premisesId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardGet("/premises/" + premisesId + "/occupancies", requestId, correlationId);
    }

    @GetMapping("/facilities/{facilityId}/occupancy-history")
    public ResponseEntity<Map<String, Object>> occupancyHistory(
            @PathVariable Long facilityId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardGet("/facilities/" + facilityId + "/occupancy-history", requestId, correlationId);
    }

    // ---- RFI cycle -------------------------------------------------------------

    @PostMapping("/applications/{applicationId}/information-requests")
    public ResponseEntity<Map<String, Object>> openRfi(
            @PathVariable UUID applicationId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardPost("/applications/" + applicationId + "/information-requests", body, requestId, correlationId);
    }

    @GetMapping("/applications/{applicationId}/information-requests")
    public ResponseEntity<Map<String, Object>> listRfis(
            @PathVariable UUID applicationId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardGet("/applications/" + applicationId + "/information-requests", requestId, correlationId);
    }

    @PostMapping("/information-requests/{rfiId}/respond")
    public ResponseEntity<Map<String, Object>> respondRfi(
            @PathVariable UUID rfiId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardPost("/information-requests/" + rfiId + "/respond", body, requestId, correlationId);
    }

    @PostMapping("/information-requests/{rfiId}/close")
    public ResponseEntity<Map<String, Object>> closeRfi(
            @PathVariable UUID rfiId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardPost("/information-requests/" + rfiId + "/close", null, requestId, correlationId);
    }

    // ---- External council reviews ------------------------------------------------

    @PostMapping("/applications/{applicationId}/council-reviews")
    public ResponseEntity<Map<String, Object>> recordCouncilReview(
            @PathVariable UUID applicationId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardPost("/applications/" + applicationId + "/council-reviews", body, requestId, correlationId);
    }

    @GetMapping("/applications/{applicationId}/council-reviews")
    public ResponseEntity<Map<String, Object>> listCouncilReviews(
            @PathVariable UUID applicationId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardGet("/applications/" + applicationId + "/council-reviews", requestId, correlationId);
    }

    // ---- PIC nominations -----------------------------------------------------------

    @PostMapping("/pic-nominations")
    public ResponseEntity<Map<String, Object>> nominate(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardPost("/pic-nominations", body, requestId, correlationId);
    }

    @PostMapping("/pic-nominations/{nominationId}/respond")
    public ResponseEntity<Map<String, Object>> respondNomination(
            @PathVariable UUID nominationId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardPost("/pic-nominations/" + nominationId + "/respond", body, requestId, correlationId);
    }

    @PostMapping("/pic-nominations/{nominationId}/review")
    public ResponseEntity<Map<String, Object>> reviewNomination(
            @PathVariable UUID nominationId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardPost("/pic-nominations/" + nominationId + "/review", body, requestId, correlationId);
    }

    @PostMapping("/pic-nominations/{nominationId}/activate")
    public ResponseEntity<Map<String, Object>> activateNomination(
            @PathVariable UUID nominationId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardPost("/pic-nominations/" + nominationId + "/activate", null, requestId, correlationId);
    }

    @PostMapping("/pic-nominations/{nominationId}/withdraw")
    public ResponseEntity<Map<String, Object>> withdrawNomination(
            @PathVariable UUID nominationId,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardPost("/pic-nominations/" + nominationId + "/withdraw", body, requestId, correlationId);
    }

    @GetMapping("/facilities/{facilityId}/pic-nominations")
    public ResponseEntity<Map<String, Object>> facilityNominations(
            @PathVariable Long facilityId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardGet("/facilities/" + facilityId + "/pic-nominations", requestId, correlationId);
    }

    @GetMapping("/pic-nominations/by-provider/{providerPublicId}")
    public ResponseEntity<Map<String, Object>> providerNominations(
            @PathVariable String providerPublicId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardGet("/pic-nominations/by-provider/"
                + java.net.URLEncoder.encode(providerPublicId, java.nio.charset.StandardCharsets.UTF_8),
                requestId, correlationId);
    }

    @PostMapping("/pic-assignments/{assignmentId}/resolve-review")
    public ResponseEntity<Map<String, Object>> resolvePicReview(
            @PathVariable Long assignmentId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardPost("/pic-assignments/" + assignmentId + "/resolve-review", body, requestId, correlationId);
    }

    /**
     * PIC eligibility snapshot behind a nomination (G30). TUSO owns the facility-effective
     * assignment but snapshots VARAPI's point-in-time eligibility assessment verbatim onto each
     * nomination (eligibilitySnapshotRef). This read-only route lets the reviewer inspect that
     * assessment — VARAPI is SoR for the snapshot, so it is fetched from VARAPI, not TUSO.
     */
    @GetMapping("/pic-nominations/eligibility-snapshots/{snapshotId}")
    public ResponseEntity<Map<String, Object>> eligibilitySnapshot(
            @PathVariable String snapshotId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = varapiClient.getEligibilitySnapshot(snapshotId);
            return ResponseEntity.ok(envelope(data, requestId, correlationId));
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            return upstreamError(e, requestId, correlationId);
        } catch (Exception e) {
            log.warn("PIC eligibility snapshot {} failed: {}", snapshotId, e.getMessage());
            return badGateway(requestId, correlationId, e.getMessage());
        }
    }

    // ---- Inspection visits + responses ----------------------------------------------

    @PostMapping("/inspections/{inspectionId}/visits")
    public ResponseEntity<Map<String, Object>> createVisit(
            @PathVariable UUID inspectionId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardPost("/inspections/" + inspectionId + "/visits", body, requestId, correlationId);
    }

    @GetMapping("/inspections/{inspectionId}/visits")
    public ResponseEntity<Map<String, Object>> listVisits(
            @PathVariable UUID inspectionId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardGet("/inspections/" + inspectionId + "/visits", requestId, correlationId);
    }

    @PostMapping("/visits/{visitId}/responses")
    public ResponseEntity<Map<String, Object>> recordResponses(
            @PathVariable UUID visitId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardPost("/visits/" + visitId + "/responses", body, requestId, correlationId);
    }

    @GetMapping("/visits/{visitId}/responses")
    public ResponseEntity<Map<String, Object>> listResponses(
            @PathVariable UUID visitId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardGet("/visits/" + visitId + "/responses", requestId, correlationId);
    }

    @PostMapping("/visits/{visitId}/complete")
    public ResponseEntity<Map<String, Object>> completeVisit(
            @PathVariable UUID visitId,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardPost("/visits/" + visitId + "/complete", body, requestId, correlationId);
    }

    // ---- transport -------------------------------------------------------------------

    // --- Manual-derived inspection content catalogue (V019) ---

    @GetMapping("/inspection-modules")
    public ResponseEntity<Map<String, Object>> inspectionModules(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardGet("/inspection-modules", requestId, correlationId);
    }

    @GetMapping("/inspection-compositions")
    public ResponseEntity<Map<String, Object>> inspectionCompositions(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardGet("/inspection-compositions", requestId, correlationId);
    }

    @GetMapping("/inspection-applicability")
    public ResponseEntity<Map<String, Object>> inspectionApplicability(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardGet("/inspection-applicability", requestId, correlationId);
    }

    @GetMapping("/inspections/{inspectionId}/checklist")
    public ResponseEntity<Map<String, Object>> inspectionChecklist(
            @PathVariable String inspectionId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardGet("/inspections/" + inspectionId + "/checklist", requestId, correlationId);
    }

    @GetMapping("/visits/{visitId}/progress")
    public ResponseEntity<Map<String, Object>> visitProgress(
            @PathVariable String visitId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardGet("/visits/" + visitId + "/progress", requestId, correlationId);
    }

    private ResponseEntity<Map<String, Object>> forwardGet(String subPath, String requestId, String correlationId) {
        try {
            JsonNode data = tusoClient.regulatoryGet(subPath);
            return ResponseEntity.ok(envelope(data, requestId, correlationId));
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            return upstreamError(e, requestId, correlationId);
        } catch (Exception e) {
            log.warn("HPA regulatory GET {} failed: {}", subPath, e.getMessage());
            return badGateway(requestId, correlationId, e.getMessage());
        }
    }

    private ResponseEntity<Map<String, Object>> forwardPost(String subPath, Map<String, Object> body,
                                                            String requestId, String correlationId) {
        try {
            JsonNode data = tusoClient.regulatoryPost(subPath, body);
            return ResponseEntity.ok(envelope(data, requestId, correlationId));
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            return upstreamError(e, requestId, correlationId);
        } catch (Exception e) {
            log.warn("HPA regulatory POST {} failed: {}", subPath, e.getMessage());
            return badGateway(requestId, correlationId, e.getMessage());
        }
    }

    private static Map<String, Object> envelope(JsonNode data, String requestId, String correlationId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("data", data);
        out.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return out;
    }

    /** Surface tuso's honest rejections (guarded transitions, authz) verbatim. */
    private static ResponseEntity<Map<String, Object>> upstreamError(
            org.springframework.web.client.HttpStatusCodeException e, String requestId, String correlationId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("error", Map.of("code", "REGULATORY_ACTION_REJECTED", "message", e.getResponseBodyAsString()));
        out.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.status(e.getStatusCode()).body(out);
    }

    private static ResponseEntity<Map<String, Object>> badGateway(String requestId, String correlationId, String message) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("error", Map.of("code", "TUSO_UNAVAILABLE", "message", message != null ? message : "upstream unavailable"));
        out.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(out);
    }
}
