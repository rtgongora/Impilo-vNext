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

    @GetMapping("/networks/for-member")
    public ResponseEntity<Map<String, Object>> memberNetworks(
            @RequestParam(name = "member_cpid") String memberCpid,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = coverageClient.getMemberNetworks(memberCpid);
            return ResponseEntity.ok(Map.of("data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return upstreamFailure("COVERAGE_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @GetMapping("/performance/summary")
    public ResponseEntity<Map<String, Object>> performanceSummary(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = coverageClient.getPerformanceSummary();
            return ResponseEntity.ok(Map.of("data", data != null ? data : Map.of(),
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
        // Propagate the engine's honest status verbatim — a duplicate active enrolment or a
        // suspended-payer block is a real 409, not a blanket 400, so the UI shows the true reason.
        return upstreamWrite("ENROLL_FAILED", () -> coverageClient.enrollMember(body), HttpStatus.CREATED,
                requestId, correlationId);
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

    // ════════════════════════════════════════════════════════════════════
    // Ruvimbo Wave 1 passthroughs (payers, hierarchy, membership, benefits, eligibility v2)
    // ════════════════════════════════════════════════════════════════════

    @PostMapping("/plans")
    public ResponseEntity<Map<String, Object>> createPlan(@RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return upstreamWrite("PLAN_CREATE_FAILED", () -> coverageClient.createPlan(body), HttpStatus.CREATED, rid, cid);
    }

    @GetMapping("/payers")
    public ResponseEntity<Map<String, Object>> listPayers(
            @RequestParam(required = false) String status,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return read(() -> coverageClient.listPayers(status), rid, cid);
    }

    @GetMapping("/payers/{id}")
    public ResponseEntity<Map<String, Object>> getPayer(@PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return read(() -> coverageClient.getPayer(id), rid, cid);
    }

    @PostMapping("/payers")
    public ResponseEntity<Map<String, Object>> createPayer(@RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return upstreamWrite("PAYER_CREATE_FAILED", () -> coverageClient.createPayer(body), HttpStatus.CREATED, rid, cid);
    }

    @PostMapping("/payers/{id}/suspend")
    public ResponseEntity<Map<String, Object>> suspendPayer(@PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return upstreamWrite("PAYER_SUSPEND_FAILED", () -> coverageClient.suspendPayer(id), HttpStatus.OK, rid, cid);
    }

    @PostMapping("/payers/{id}/reactivate")
    public ResponseEntity<Map<String, Object>> reactivatePayer(@PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return upstreamWrite("PAYER_REACTIVATE_FAILED", () -> coverageClient.reactivatePayer(id), HttpStatus.OK, rid, cid);
    }

    @GetMapping("/payers/{payerId}/schemes")
    public ResponseEntity<Map<String, Object>> listSchemes(@PathVariable String payerId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return read(() -> coverageClient.listSchemes(payerId), rid, cid);
    }

    @PostMapping("/payers/{payerId}/schemes")
    public ResponseEntity<Map<String, Object>> createScheme(@PathVariable String payerId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return upstreamWrite("SCHEME_CREATE_FAILED", () -> coverageClient.createScheme(payerId, body), HttpStatus.CREATED, rid, cid);
    }

    @GetMapping("/schemes/{schemeId}/products")
    public ResponseEntity<Map<String, Object>> listProducts(@PathVariable String schemeId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return read(() -> coverageClient.listProducts(schemeId), rid, cid);
    }

    @PostMapping("/schemes/{schemeId}/products")
    public ResponseEntity<Map<String, Object>> createProduct(@PathVariable String schemeId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return upstreamWrite("PRODUCT_CREATE_FAILED", () -> coverageClient.createProduct(schemeId, body), HttpStatus.CREATED, rid, cid);
    }

    @GetMapping("/products/{productId}/versions")
    public ResponseEntity<Map<String, Object>> listPlanVersions(@PathVariable String productId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return read(() -> coverageClient.listPlanVersions(productId), rid, cid);
    }

    @PostMapping("/products/{productId}/versions")
    public ResponseEntity<Map<String, Object>> createPlanVersion(@PathVariable String productId,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        Map<String, Object> b = body != null ? body : Map.of();
        return upstreamWrite("VERSION_CREATE_FAILED", () -> coverageClient.createPlanVersion(productId, b), HttpStatus.CREATED, rid, cid);
    }

    @PostMapping("/plan-versions/{versionId}/publish")
    public ResponseEntity<Map<String, Object>> publishPlanVersion(@PathVariable String versionId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return upstreamWrite("VERSION_PUBLISH_FAILED", () -> coverageClient.publishPlanVersion(versionId), HttpStatus.OK, rid, cid);
    }

    @GetMapping("/members/pending-verification")
    public ResponseEntity<Map<String, Object>> pendingVerification(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return read(coverageClient::pendingVerification, rid, cid);
    }

    @GetMapping("/members/{id}/dependants")
    public ResponseEntity<Map<String, Object>> listDependants(@PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return read(() -> coverageClient.listDependants(id), rid, cid);
    }

    @PostMapping("/members/{id}/verify")
    public ResponseEntity<Map<String, Object>> verifyMembership(@PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        Map<String, Object> b = body != null ? body : Map.of();
        return upstreamWrite("MEMBER_VERIFY_FAILED", () -> coverageClient.verifyMembership(id, b), HttpStatus.OK, rid, cid);
    }

    @PostMapping("/members/{id}/suspend")
    public ResponseEntity<Map<String, Object>> suspendMembership(@PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        Map<String, Object> b = body != null ? body : Map.of();
        return upstreamWrite("MEMBER_SUSPEND_FAILED", () -> coverageClient.transitionMembership(id, "suspend", b), HttpStatus.OK, rid, cid);
    }

    @PostMapping("/members/{id}/reinstate")
    public ResponseEntity<Map<String, Object>> reinstateMembership(@PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        Map<String, Object> b = body != null ? body : Map.of();
        return upstreamWrite("MEMBER_REINSTATE_FAILED", () -> coverageClient.transitionMembership(id, "reinstate", b), HttpStatus.OK, rid, cid);
    }

    @PostMapping("/members/{id}/terminate")
    public ResponseEntity<Map<String, Object>> terminateMembership(@PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        Map<String, Object> b = body != null ? body : Map.of();
        return upstreamWrite("MEMBER_TERMINATE_FAILED", () -> coverageClient.transitionMembership(id, "terminate", b), HttpStatus.OK, rid, cid);
    }

    @PostMapping("/members/{id}/dependants")
    public ResponseEntity<Map<String, Object>> addDependant(@PathVariable String id,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return upstreamWrite("DEPENDANT_ADD_FAILED", () -> coverageClient.addDependant(id, body), HttpStatus.CREATED, rid, cid);
    }

    @GetMapping("/plan-versions/{planVersionId}/benefits")
    public ResponseEntity<Map<String, Object>> listBenefits(@PathVariable String planVersionId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return read(() -> coverageClient.listBenefits(planVersionId), rid, cid);
    }

    @PostMapping("/plan-versions/{planVersionId}/benefits")
    public ResponseEntity<Map<String, Object>> createBenefit(@PathVariable String planVersionId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return upstreamWrite("BENEFIT_CREATE_FAILED", () -> coverageClient.createBenefit(planVersionId, body), HttpStatus.CREATED, rid, cid);
    }

    @GetMapping("/members/{id}/benefit-accumulators")
    public ResponseEntity<Map<String, Object>> memberAccumulators(@PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return read(() -> coverageClient.memberAccumulators(id), rid, cid);
    }

    @PostMapping("/members/{id}/benefits/{kind}")
    public ResponseEntity<Map<String, Object>> benefitMovement(@PathVariable String id, @PathVariable String kind,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return upstreamWrite("BENEFIT_MOVEMENT_FAILED", () -> coverageClient.benefitMovement(id, kind, body), HttpStatus.OK, rid, cid);
    }

    @PostMapping("/eligibility/v2")
    public ResponseEntity<Map<String, Object>> checkEligibilityV2(@RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return upstreamWrite("ELIGIBILITY_V2_FAILED", () -> coverageClient.checkEligibilityV2(body), HttpStatus.CREATED, rid, cid);
    }

    @PostMapping("/eligibility/tokens/verify")
    public ResponseEntity<Map<String, Object>> verifyEligibilityToken(@RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return upstreamWrite("TOKEN_VERIFY_FAILED", () -> coverageClient.verifyEligibilityToken(body), HttpStatus.OK, rid, cid);
    }

    // ════════════════════════════════════════════════════════════════════
    // Ruvimbo Wave 2 passthroughs (authorisations, referrals, liability estimates)
    // ════════════════════════════════════════════════════════════════════

    @GetMapping("/authorisations")
    public ResponseEntity<Map<String, Object>> listAuthorisations(
            @RequestParam(name = "member_cpid", required = false) String memberCpid,
            @RequestParam(required = false) String status,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return read(() -> coverageClient.listAuthorisations(memberCpid, status), rid, cid);
    }

    @GetMapping("/authorisations/{id}")
    public ResponseEntity<Map<String, Object>> getAuthorisation(@PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return read(() -> coverageClient.getAuthorisation(id), rid, cid);
    }

    @PostMapping("/authorisations")
    public ResponseEntity<Map<String, Object>> createAuthorisation(@RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return upstreamWrite("AUTH_CREATE_FAILED", () -> coverageClient.createAuthorisation(body), HttpStatus.CREATED, rid, cid);
    }

    @PostMapping("/authorisations/{id}/{action}")
    public ResponseEntity<Map<String, Object>> authorisationAction(@PathVariable String id, @PathVariable String action,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        Map<String, Object> b = body != null ? body : Map.of();
        return upstreamWrite("AUTH_ACTION_FAILED", () -> coverageClient.authorisationAction(id, action, b), HttpStatus.OK, rid, cid);
    }

    @GetMapping("/referrals")
    public ResponseEntity<Map<String, Object>> listReferrals(
            @RequestParam(name = "member_cpid") String memberCpid,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return read(() -> coverageClient.listReferrals(memberCpid), rid, cid);
    }

    @PostMapping("/referrals")
    public ResponseEntity<Map<String, Object>> createReferral(@RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return upstreamWrite("REFERRAL_CREATE_FAILED", () -> coverageClient.createReferral(body), HttpStatus.CREATED, rid, cid);
    }

    @GetMapping("/referrals/{id}/validity")
    public ResponseEntity<Map<String, Object>> referralValidity(@PathVariable String id,
            @RequestParam(name = "emergency", defaultValue = "false") boolean emergency,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return read(() -> coverageClient.referralValidity(id, emergency), rid, cid);
    }

    @PostMapping("/referrals/{id}/consume")
    public ResponseEntity<Map<String, Object>> consumeReferral(@PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return upstreamWrite("REFERRAL_CONSUME_FAILED", () -> coverageClient.consumeReferral(id), HttpStatus.OK, rid, cid);
    }

    @GetMapping("/liability-estimates")
    public ResponseEntity<Map<String, Object>> listLiabilityEstimates(
            @RequestParam(name = "member_cpid") String memberCpid,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return read(() -> coverageClient.listLiabilityEstimates(memberCpid), rid, cid);
    }

    @PostMapping("/liability-estimates")
    public ResponseEntity<Map<String, Object>> createLiabilityEstimate(@RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return upstreamWrite("ESTIMATE_FAILED", () -> coverageClient.createLiabilityEstimate(body), HttpStatus.CREATED, rid, cid);
    }

    // ════════════════════════════════════════════════════════════════════
    // Ruvimbo Wave 3 passthroughs (claims v2: adjudication, EOB, lineage)
    // ════════════════════════════════════════════════════════════════════

    @PostMapping("/v2/claims")
    public ResponseEntity<Map<String, Object>> createClaimV2(@RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return upstreamWrite("CLAIM_CREATE_FAILED", () -> coverageClient.createClaimV2(body), HttpStatus.CREATED, rid, cid);
    }

    @GetMapping("/v2/claims/{id}")
    public ResponseEntity<Map<String, Object>> getClaimV2(@PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return read(() -> coverageClient.getClaimV2(id), rid, cid);
    }

    @GetMapping("/v2/claims/{id}/{sub}")
    public ResponseEntity<Map<String, Object>> claimV2Sub(@PathVariable String id, @PathVariable String sub,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return read(() -> coverageClient.claimV2Sub(id, sub), rid, cid);
    }

    @PostMapping("/v2/claims/{id}/{action}")
    public ResponseEntity<Map<String, Object>> claimV2Action(@PathVariable String id, @PathVariable String action,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        Map<String, Object> b = body != null ? body : Map.of();
        HttpStatus ok = "replace".equals(action) ? HttpStatus.CREATED : HttpStatus.OK;
        return upstreamWrite("CLAIM_ACTION_FAILED", () -> coverageClient.claimV2Action(id, action, b), ok, rid, cid);
    }

    // ════════════════════════════════════════════════════════════════════
    // Ruvimbo Wave 4 passthroughs (COB, employer/group, fraud, capitation, gateway)
    // ════════════════════════════════════════════════════════════════════

    @GetMapping("/cob")
    public ResponseEntity<Map<String, Object>> cobList(@RequestParam(name = "member_cpid") String memberCpid,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid, @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return read(() -> coverageClient.cobList(memberCpid), rid, cid);
    }

    @PostMapping("/cob/coordinate")
    public ResponseEntity<Map<String, Object>> cobCoordinate(@RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid, @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return upstreamWrite("COB_FAILED", () -> coverageClient.cobCoordinate(body), HttpStatus.CREATED, rid, cid);
    }

    @GetMapping("/employers")
    public ResponseEntity<Map<String, Object>> listEmployers(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid, @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return read(coverageClient::listEmployers, rid, cid);
    }

    @PostMapping("/employers")
    public ResponseEntity<Map<String, Object>> registerEmployer(@RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid, @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return upstreamWrite("EMPLOYER_CREATE_FAILED", () -> coverageClient.registerEmployer(body), HttpStatus.CREATED, rid, cid);
    }

    @PostMapping("/employers/{id}/roster-batches")
    public ResponseEntity<Map<String, Object>> stageRoster(@PathVariable String id, @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid, @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return upstreamWrite("ROSTER_STAGE_FAILED", () -> coverageClient.stageRoster(id, body), HttpStatus.CREATED, rid, cid);
    }

    @GetMapping("/employers/roster-batches/{batchId}")
    public ResponseEntity<Map<String, Object>> rosterBatch(@PathVariable String batchId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid, @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return read(() -> coverageClient.rosterBatch(batchId, null), rid, cid);
    }

    @GetMapping("/employers/roster-batches/{batchId}/rows")
    public ResponseEntity<Map<String, Object>> rosterRows(@PathVariable String batchId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid, @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return read(() -> coverageClient.rosterBatch(batchId, "rows"), rid, cid);
    }

    @PostMapping("/employers/roster-batches/{batchId}/{action}")
    public ResponseEntity<Map<String, Object>> rosterAction(@PathVariable String batchId, @PathVariable String action,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid, @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return upstreamWrite("ROSTER_ACTION_FAILED", () -> coverageClient.rosterAction(batchId, action), HttpStatus.OK, rid, cid);
    }

    @GetMapping("/fraud-flags")
    public ResponseEntity<Map<String, Object>> listFraud(@RequestParam(required = false) String status,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid, @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return read(() -> coverageClient.listFraudFlags(status), rid, cid);
    }

    @PostMapping("/fraud-flags/screen-claim/{claimId}")
    public ResponseEntity<Map<String, Object>> screenClaim(@PathVariable String claimId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid, @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return upstreamWrite("FRAUD_SCREEN_FAILED", () -> coverageClient.screenClaim(claimId), HttpStatus.CREATED, rid, cid);
    }

    @PostMapping("/fraud-flags/{id}/review")
    public ResponseEntity<Map<String, Object>> reviewFraud(@PathVariable String id, @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid, @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return upstreamWrite("FRAUD_REVIEW_FAILED", () -> coverageClient.reviewFraudFlag(id, body), HttpStatus.OK, rid, cid);
    }

    @GetMapping("/capitation-reports")
    public ResponseEntity<Map<String, Object>> listCapitation(@RequestParam(name = "provider_id") String providerId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid, @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return read(() -> coverageClient.listCapitation(providerId), rid, cid);
    }

    @PostMapping("/capitation-reports")
    public ResponseEntity<Map<String, Object>> createCapitation(@RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid, @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return upstreamWrite("CAPITATION_FAILED", () -> coverageClient.createCapitation(body), HttpStatus.CREATED, rid, cid);
    }

    @GetMapping("/gateway/transactions")
    public ResponseEntity<Map<String, Object>> listGateway(@RequestParam(defaultValue = "RECEIVED") String status,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid, @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return read(() -> coverageClient.listGateway(status), rid, cid);
    }

    @PostMapping("/gateway/transactions")
    public ResponseEntity<Map<String, Object>> routeGateway(@RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String rid, @RequestHeader(CompanionHeaders.CORRELATION_ID) String cid) {
        return upstreamWrite("GATEWAY_ROUTE_FAILED", () -> coverageClient.routeGateway(body), HttpStatus.CREATED, rid, cid);
    }

    /** Read passthrough: 502 on transport failure, empty object/array preserved. */
    private ResponseEntity<Map<String, Object>> read(
            java.util.concurrent.Callable<JsonNode> call, String requestId, String correlationId) {
        try {
            JsonNode data = call.call();
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
