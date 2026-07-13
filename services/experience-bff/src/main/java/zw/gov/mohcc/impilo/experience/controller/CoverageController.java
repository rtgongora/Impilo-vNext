package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.CoverageServiceClient;

import java.util.Map;

/**
 * Coverage BFF Controller — bridges experience UI to the
 * coverage sovereign service for plans, eligibility, claims,
 * preauth, and remittance.
 */
@RestController
@RequestMapping("/internal/v1/coverage")
public class CoverageController {

    private static final Logger log = LoggerFactory.getLogger(CoverageController.class);
    private final CoverageServiceClient coverageClient;

    public CoverageController(CoverageServiceClient coverageClient) {
        this.coverageClient = coverageClient;
    }

    @GetMapping("/plans")
    public ResponseEntity<Map<String, Object>> listPlans(
            @RequestParam(required = false, name = "member_cpid") String memberCpid,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = memberCpid != null && !memberCpid.isBlank()
                    ? coverageClient.listPlansForMember(memberCpid)
                    : coverageClient.listPlans();
            return ResponseEntity.ok(Map.of("data", data != null ? data : new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return upstreamFailure("COVERAGE_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @GetMapping("/plans/{id}")
    public ResponseEntity<Map<String, Object>> getPlan(@PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = coverageClient.getPlan(id);
            return ResponseEntity.ok(Map.of("data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return ResponseEntity.status(404).body(Map.of("error", Map.of("code", "NOT_FOUND")));
        }
    }

    @GetMapping("/member/{clientId}")
    public ResponseEntity<Map<String, Object>> getMemberCoverage(@PathVariable String clientId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = coverageClient.getMemberCoverage(clientId);
            return ResponseEntity.ok(Map.of("data", data != null ? data : new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return upstreamFailure("COVERAGE_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @GetMapping("/eligibility")
    public ResponseEntity<Map<String, Object>> listEligibilityForMember(
            @RequestParam(name = "member_cpid") String memberCpid,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = coverageClient.listEligibilityForMember(memberCpid);
            return ResponseEntity.ok(Map.of("data", data != null ? data : new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return upstreamFailure("COVERAGE_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/eligibility")
    public ResponseEntity<Map<String, Object>> checkEligibility(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = coverageClient.checkEligibility(body);
            return ResponseEntity.ok(Map.of("data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Eligibility check failed: {}", e.getMessage());
            return ResponseEntity.status(400).body(Map.of("error", Map.of("code", "CHECK_FAILED", "message", e.getMessage())));
        }
    }

    @PostMapping("/eligibility/check")
    public ResponseEntity<Map<String, Object>> checkEligibilityAlias(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = coverageClient.checkEligibilityCheckPath(body);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Eligibility check failed: {}", e.getMessage());
            return ResponseEntity.status(400).body(Map.of("error", Map.of("code", "CHECK_FAILED", "message", e.getMessage())));
        }
    }

    @PostMapping("/eligibility/enrollment")
    public ResponseEntity<Map<String, Object>> checkEnrollmentEligibility(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = coverageClient.checkEnrollmentEligibility(body);
            return ResponseEntity.ok(Map.of("data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Enrollment eligibility check failed: {}", e.getMessage());
            return ResponseEntity.status(400).body(Map.of("error", Map.of("code", "CHECK_FAILED", "message", e.getMessage())));
        }
    }

    @GetMapping("/subsidies")
    public ResponseEntity<Map<String, Object>> listSubsidies(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = coverageClient.listSubsidies();
            return ResponseEntity.ok(Map.of("data", data != null ? data : new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return upstreamFailure("COVERAGE_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    // ── Subsidy value enrolment + annual-cap drawdown (Model X) ──────────────

    @GetMapping("/subsidies/enrolments")
    public ResponseEntity<Map<String, Object>> listSubsidyEnrolments(
            @RequestParam(name = "member_cpid") String memberCpid,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = coverageClient.listSubsidyEnrolments(memberCpid);
            return ResponseEntity.ok(Map.of("data", data != null ? data : new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return upstreamFailure("COVERAGE_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @GetMapping("/subsidies/enrolments/{id}")
    public ResponseEntity<Map<String, Object>> getSubsidyEnrolment(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return upstreamWrite("SUBSIDY_ENROLMENT_NOT_FOUND",
                () -> coverageClient.getSubsidyEnrolment(id), HttpStatus.OK, requestId, correlationId);
    }

    @PostMapping("/subsidies/enrolments")
    public ResponseEntity<Map<String, Object>> enrolSubsidy(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return upstreamWrite("SUBSIDY_ENROL_FAILED",
                () -> coverageClient.enrolSubsidy(body), HttpStatus.CREATED, requestId, correlationId);
    }

    @PostMapping("/subsidies/enrolments/{id}/consume")
    public ResponseEntity<Map<String, Object>> consumeSubsidy(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        // Cap-exceeded surfaces as an honest 400 from the engine — passed through verbatim.
        return upstreamWrite("SUBSIDY_CAP_EXCEEDED",
                () -> coverageClient.consumeSubsidy(id, body), HttpStatus.OK, requestId, correlationId);
    }

    // ── Subsidy exemption-category enrolment (Model Y — costing waivers) ─────

    @GetMapping("/subsidies/enrollments")
    public ResponseEntity<Map<String, Object>> listSubsidyExemptions(
            @RequestParam(name = "member_cpid") String memberCpid,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = coverageClient.listSubsidyExemptions(memberCpid);
            return ResponseEntity.ok(Map.of("data", data != null ? data : new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return upstreamFailure("COVERAGE_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/subsidies/enrollments")
    public ResponseEntity<Map<String, Object>> enrollSubsidyExemption(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return upstreamWrite("SUBSIDY_EXEMPTION_FAILED",
                () -> coverageClient.enrollSubsidyExemption(body), HttpStatus.CREATED, requestId, correlationId);
    }

    @PostMapping("/subsidies/enrollments/{id}/end")
    public ResponseEntity<Map<String, Object>> endSubsidyExemption(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return upstreamWrite("SUBSIDY_EXEMPTION_END_FAILED",
                () -> coverageClient.endSubsidyExemption(id), HttpStatus.OK, requestId, correlationId);
    }

    @PostMapping("/claims")
    public ResponseEntity<Map<String, Object>> submitClaim(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = coverageClient.submitClaim(body);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Claim submission failed: {}", e.getMessage());
            return ResponseEntity.status(400).body(Map.of("error", Map.of("code", "SUBMIT_FAILED", "message", e.getMessage())));
        }
    }

    @GetMapping("/claims/{id}")
    public ResponseEntity<Map<String, Object>> getClaim(@PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = coverageClient.getClaim(id);
            return ResponseEntity.ok(Map.of("data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return ResponseEntity.status(404).body(Map.of("error", Map.of("code", "NOT_FOUND")));
        }
    }

    @GetMapping("/claims")
    public ResponseEntity<Map<String, Object>> listClaims(
            @RequestParam(required = false) String coverageId,
            @RequestParam(required = false, name = "member_cpid") String memberCpid,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data;
            if (memberCpid != null && !memberCpid.isBlank()) {
                data = coverageClient.listClaimsForMember(memberCpid);
            } else if (coverageId != null) {
                data = coverageClient.listClaims(coverageId);
            } else {
                data = coverageClient.listPlans();
            }
            return ResponseEntity.ok(Map.of("data", data != null ? data : new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return upstreamFailure("COVERAGE_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @GetMapping("/contributions")
    public ResponseEntity<Map<String, Object>> listContributions(
            @RequestParam(name = "member_cpid") String memberCpid,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = coverageClient.listContributionsForMember(memberCpid);
            return ResponseEntity.ok(Map.of("data", data != null ? data : new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return upstreamFailure("COVERAGE_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @GetMapping("/preauths")
    public ResponseEntity<Map<String, Object>> listPreauths(
            @RequestParam(name = "member_cpid") String memberCpid,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = coverageClient.listPreauthsForMember(memberCpid);
            return ResponseEntity.ok(Map.of("data", data != null ? data : new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return upstreamFailure("COVERAGE_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @GetMapping("/utilization")
    public ResponseEntity<Map<String, Object>> listUtilization(
            @RequestParam(name = "member_cpid") String memberCpid,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = coverageClient.listUtilizationForMember(memberCpid);
            return ResponseEntity.ok(Map.of("data", data != null ? data : new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return upstreamFailure("COVERAGE_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @GetMapping("/appeals")
    public ResponseEntity<Map<String, Object>> listAppeals(
            @RequestParam(name = "appellant_id") String appellantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = coverageClient.listAppealsForAppellant(appellantId);
            return ResponseEntity.ok(Map.of("data", data != null ? data : new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return upstreamFailure("COVERAGE_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/preauth")
    public ResponseEntity<Map<String, Object>> createPreauth(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = coverageClient.createPreauth(body);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Preauth creation failed: {}", e.getMessage());
            return ResponseEntity.status(400).body(Map.of("error", Map.of("code", "PREAUTH_FAILED", "message", e.getMessage())));
        }
    }

    @GetMapping("/preauth/{id}")
    public ResponseEntity<Map<String, Object>> getPreauth(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return upstreamWrite("PREAUTH_NOT_FOUND",
                () -> coverageClient.getPreauth(id), HttpStatus.OK, requestId, correlationId);
    }

    /**
     * Reviewer decision on a preauth (G15). PENDING → APPROVED|DENIED; the engine applies
     * utilization cap-denial and rejects a non-PENDING preauth with 409 — both surfaced verbatim.
     */
    @PutMapping("/preauth/{id}/decision")
    public ResponseEntity<Map<String, Object>> decidePreauth(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return upstreamWrite("PREAUTH_DECISION_REJECTED",
                () -> coverageClient.decidePreauth(id, body), HttpStatus.OK, requestId, correlationId);
    }

    @GetMapping("/remittances")
    public ResponseEntity<Map<String, Object>> listRemittances(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = coverageClient.listRemittances();
            return ResponseEntity.ok(Map.of("data", data != null ? data : new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return upstreamFailure("COVERAGE_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    // ── Membership ───────────────────────────────────────────────

    @PostMapping("/members")
    public ResponseEntity<Map<String, Object>> enrollMember(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = coverageClient.enrollMember(body);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Member enrollment failed: {}", e.getMessage());
            return ResponseEntity.status(400).body(Map.of("error", Map.of("code", "ENROLL_FAILED", "message", e.getMessage())));
        }
    }

    @GetMapping("/members")
    public ResponseEntity<Map<String, Object>> listMembers(
            @RequestParam(required = false, name = "plan_id") String planId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = planId != null ? coverageClient.listMembers(planId) : coverageClient.listPlans();
            return ResponseEntity.ok(Map.of("data", data != null ? data : new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return upstreamFailure("COVERAGE_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    private ResponseEntity<Map<String, Object>> upstreamFailure(
            String code, String message, String requestId, String correlationId) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", Map.of("code", code, "message", message != null ? message : "Coverage upstream unavailable"),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    /**
     * Surface the coverage engine's honest 4xx rejections verbatim (subsidy cap-exceeded 400,
     * non-PENDING preauth 409) so the UI can show the real reason, and fall back to 502 for
     * transport failures.
     */
    private ResponseEntity<Map<String, Object>> upstreamWrite(
            String code, java.util.concurrent.Callable<JsonNode> call, HttpStatus successStatus,
            String requestId, String correlationId) {
        try {
            JsonNode data = call.call();
            return ResponseEntity.status(successStatus).body(Map.of(
                    "data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.warn("Coverage write {} rejected upstream ({}): {}", code, e.getStatusCode(), e.getResponseBodyAsString());
            return ResponseEntity.status(e.getStatusCode()).body(Map.of(
                    "error", Map.of("code", code, "message", e.getResponseBodyAsString()),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return upstreamFailure("COVERAGE_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }
}
