package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

/**
 * HTTP client for the Coverage sovereign service.
 * Provides access to plans, eligibility, claims, preauth, and remittance.
 */
@Component
public class CoverageServiceClient {

    private static final Logger log = LoggerFactory.getLogger(CoverageServiceClient.class);
    private final RestTemplate restTemplate;
    private final String baseUrl;

    public CoverageServiceClient(RestTemplate serviceRestTemplate, ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.coverageBaseUrl();
    }

    public JsonNode listPlans() {
        String url = baseUrl + "/internal/v1/coverage";
        log.info("COVERAGE: Listing plans");
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    /** Public plan catalogue (anonymous-safe) — ACTIVE plans, allow-listed, care-plane. Raw array. */
    public JsonNode publicCoveragePlans() {
        return restTemplate.getForEntity(baseUrl + "/v1/public/coverage/plans", JsonNode.class).getBody();
    }

    /** Public benefits for a plan version (anonymous-safe) — plan-level, no member data. Raw array. */
    public JsonNode publicPlanBenefits(String planVersionId) {
        String url = baseUrl + "/v1/public/coverage/plans/"
                + org.springframework.web.util.UriUtils.encodePathSegment(planVersionId, java.nio.charset.StandardCharsets.UTF_8)
                + "/benefits";
        return restTemplate.getForEntity(url, JsonNode.class).getBody();
    }

    public JsonNode listPlansForMember(String memberCpid) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/coverage/plans")
                .queryParam("member_cpid", memberCpid)
                .toUriString();
        log.info("COVERAGE: Listing plans for member");
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode getPlan(String id) {
        String url = baseUrl + "/internal/v1/coverage/" + id;
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode getMemberCoverage(String clientId) {
        String url = baseUrl + "/internal/v1/coverage/member/" + clientId;
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    /**
     * Resolve a patient's billing category (e.g. PRIVATE / CASH) from their active coverage.
     * Used to enrich downstream costing context (COSTA charging rules).
     */
    public JsonNode resolvePatientCategory(String clientId) {
        String url = baseUrl + "/internal/v1/coverage/patient-category/" + clientId;
        log.info("COVERAGE: Resolving patient billing category");
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode checkEligibility(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/coverage/eligibility";
        log.info("COVERAGE: Checking eligibility");
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    public JsonNode checkEligibilityCheckPath(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/coverage/eligibility/check";
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    public JsonNode checkEnrollmentEligibility(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/coverage/eligibility/enrollment";
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    public JsonNode listSubsidies() {
        String url = baseUrl + "/internal/v1/coverage/subsidies";
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    /**
     * Health-worker recognition subsidy self opt-in. Coverage performs the
     * recognition check SERVER-SIDE (fail-closed); this is a pure proxy.
     */
    public JsonNode healthWorkerSubsidyOptIn(String programId, String healthId) {
        String url = baseUrl + "/internal/v1/coverage/subsidies/" + programId + "/enrolments";
        log.info("COVERAGE: Health-worker subsidy opt-in");
        return extractData(restTemplate.postForEntity(url, Map.of("health_id", healthId), JsonNode.class));
    }

    // ── Subsidy value enrolment + annual-cap drawdown (Model X: cv_subsidy_enrolments) ──

    public JsonNode enrolSubsidy(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/coverage/subsidies/enrolments";
        log.info("COVERAGE: Enrolling member into subsidy (value lane)");
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    public JsonNode listSubsidyEnrolments(String memberCpid) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/coverage/subsidies/enrolments")
                .queryParam("member_cpid", memberCpid)
                .toUriString();
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode getSubsidyEnrolment(String id) {
        String url = baseUrl + "/internal/v1/coverage/subsidies/enrolments/" + id;
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    /** Draw down subsidy value against the annual cap — the engine enforces the cap atomically. */
    public JsonNode consumeSubsidy(String id, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/coverage/subsidies/enrolments/" + id + "/consume";
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    // ── Subsidy exemption-category enrolment (Model Y: cv_subsidy_enrollments) ──

    public JsonNode enrollSubsidyExemption(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/coverage/subsidies/enrollments";
        log.info("COVERAGE: Enrolling member exemption category (costing lane)");
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    public JsonNode listSubsidyExemptions(String memberCpid) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/coverage/subsidies/enrollments")
                .queryParam("member_cpid", memberCpid)
                .toUriString();
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode endSubsidyExemption(String id) {
        String url = baseUrl + "/internal/v1/coverage/subsidies/enrollments/" + id + "/end";
        return extractData(restTemplate.postForEntity(url, Map.of(), JsonNode.class));
    }

    public JsonNode listEligibilityForMember(String memberCpid) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/coverage/eligibility")
                .queryParam("member_cpid", memberCpid)
                .toUriString();
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode submitClaim(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/coverage/claims";
        log.info("COVERAGE: Submitting claim");
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    public JsonNode getClaim(String id) {
        String url = baseUrl + "/internal/v1/coverage/claims/" + id;
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode listClaims(String coverageId) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/coverage/claims")
                .queryParam("coverageId", coverageId)
                .toUriString();
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode listClaimsForMember(String memberCpid) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/coverage/claims")
                .queryParam("member_cpid", memberCpid)
                .toUriString();
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode listContributionsForMember(String memberCpid) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/coverage/contributions")
                .queryParam("member_cpid", memberCpid)
                .toUriString();
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode listPreauthsForMember(String memberCpid) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/coverage/preauths")
                .queryParam("member_cpid", memberCpid)
                .toUriString();
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode listAppealsForAppellant(String appellantId) {
        // AppealController is mounted at /internal/v1/appeals (NOT under the /coverage
        // prefix) and its query param is `appellantId`. Calling the old
        // /internal/v1/coverage/appeals?appellant_id path 404s upstream, which the caller
        // re-emits as COVERAGE_UNAVAILABLE — the source of the member "Appeals" red box.
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/appeals")
                .queryParam("appellantId", appellantId)
                .toUriString();
        JsonNode data = extractData(restTemplate.getForEntity(url, JsonNode.class));
        // list() returns ApiResponse<Page<…>> → data is a Spring Page envelope
        // {content:[…]}. Unwrap to the plain array the member UI expects, so appeals
        // matches every sibling coverage list (which return a bare List).
        if (data != null && data.has("content")) {
            return data.get("content");
        }
        return data;
    }

    public JsonNode createAppeal(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/appeals";
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    public JsonNode reviewAppeal(String appealId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/appeals/" + appealId + "/review";
        return extractData(restTemplate.exchange(url, HttpMethod.PUT, new HttpEntity<>(body), JsonNode.class));
    }

    public JsonNode decideAppeal(String appealId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/appeals/" + appealId + "/decide";
        return extractData(restTemplate.exchange(url, HttpMethod.PUT, new HttpEntity<>(body), JsonNode.class));
    }

    public JsonNode createPreauth(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/coverage/preauth";
        log.info("COVERAGE: Creating preauth");
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    public JsonNode getPreauth(String id) {
        String url = baseUrl + "/internal/v1/coverage/preauth/" + id;
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    /**
     * Reviewer decision on a preauth (PENDING → APPROVED|DENIED). The engine applies utilization
     * cap-denial (an APPROVED submission can be flipped to DENIED with reason
     * UTILIZATION_LIMIT_EXCEEDED) and rejects a non-PENDING preauth with 409.
     */
    public JsonNode decidePreauth(String id, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/coverage/preauth/" + id + "/decision";
        log.info("COVERAGE: Deciding preauth {}", id);
        return extractData(restTemplate.exchange(url, HttpMethod.PUT, new HttpEntity<>(body), JsonNode.class));
    }

    public JsonNode enrollMember(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/coverage/members";
        log.info("COVERAGE: Enrolling member");
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    public JsonNode listMembers(String planId) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/coverage/members")
                .queryParam("plan_id", planId)
                .toUriString();
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode listRemittances() {
        String url = baseUrl + "/internal/v1/coverage/remittances";
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode listUtilizationForMember(String memberCpid) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/coverage/utilization")
                .queryParam("member_cpid", memberCpid)
                .toUriString();
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode listProviderContracts(String providerId, String payerId, String status) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/provider-contracts");
        if (providerId != null && !providerId.isBlank()) {
            b.queryParam("provider_id", providerId);
        }
        if (payerId != null && !payerId.isBlank()) {
            b.queryParam("payer_id", payerId);
        }
        if (status != null && !status.isBlank()) {
            b.queryParam("status", status);
        }
        return extractData(restTemplate.getForEntity(b.toUriString(), JsonNode.class));
    }

    public JsonNode createProviderContract(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/provider-contracts";
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    public JsonNode suspendProviderContract(String contractId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/provider-contracts/" + contractId + "/suspend";
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    public JsonNode reinstateProviderContract(String contractId) {
        String url = baseUrl + "/internal/v1/provider-contracts/" + contractId + "/reinstate";
        return extractData(restTemplate.postForEntity(url, Map.of(), JsonNode.class));
    }

    public JsonNode listProviderNetworks(String payerId, String status) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/provider-networks");
        if (payerId != null && !payerId.isBlank()) {
            b.queryParam("payer_id", payerId);
        }
        if (status != null && !status.isBlank()) {
            b.queryParam("status", status);
        }
        return extractData(restTemplate.getForEntity(b.toUriString(), JsonNode.class));
    }

    public JsonNode createProviderNetwork(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/provider-networks";
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    public JsonNode listNetworkMembers(String networkId) {
        String url = baseUrl + "/internal/v1/provider-networks/" + networkId + "/members";
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode addNetworkMember(String networkId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/provider-networks/" + networkId + "/members";
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    public void removeNetworkMember(String networkId, String providerId) {
        String url = baseUrl + "/internal/v1/provider-networks/" + networkId + "/members/" + providerId;
        restTemplate.delete(url);
    }

    /** Get coverage plans for a citizen by CPID. */
    public JsonNode getCitizenCoverage(String cpid) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/coverage/citizen/" + cpid)
                .toUriString();
        log.info("COVERAGE: Getting citizen coverage for cpid={}", cpid);
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    /**
     * Mesh-internal eligibility check — {@code POST /internal/v1/eligibility/check}
     * (aligned with mushex-service coverage integration; forwards {@code X-Tenant-ID} via RestTemplate interceptor).
     */
    public JsonNode meshInternalEligibilityCheck(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/eligibility/check";
        log.info("COVERAGE: mesh internal eligibility check");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        if (response.getBody() != null) {
            return response.getBody();
        }
        return null;
    }

    // ── Ruvimbo Wave 1: create a flat live plan (optionally linked to a payer + version) ──

    public JsonNode createPlan(Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(baseUrl + "/internal/v1/coverage", body, JsonNode.class));
    }

    // ── Ruvimbo Wave 1: payer registry ─────────────────────────────────────

    public JsonNode listPayers(String status) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/coverage/payers");
        if (status != null && !status.isBlank()) b.queryParam("status", status);
        return extractData(restTemplate.getForEntity(b.toUriString(), JsonNode.class));
    }

    public JsonNode getPayer(String id) {
        return extractData(restTemplate.getForEntity(baseUrl + "/internal/v1/coverage/payers/" + id, JsonNode.class));
    }

    public JsonNode createPayer(Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(baseUrl + "/internal/v1/coverage/payers", body, JsonNode.class));
    }

    public JsonNode suspendPayer(String id) {
        return extractData(restTemplate.postForEntity(
                baseUrl + "/internal/v1/coverage/payers/" + id + "/suspend", Map.of(), JsonNode.class));
    }

    public JsonNode reactivatePayer(String id) {
        return extractData(restTemplate.postForEntity(
                baseUrl + "/internal/v1/coverage/payers/" + id + "/reactivate", Map.of(), JsonNode.class));
    }

    // ── Scheme -> Product -> PlanVersion hierarchy ─────────────────────────

    public JsonNode listSchemes(String payerId) {
        return extractData(restTemplate.getForEntity(
                baseUrl + "/internal/v1/coverage/payers/" + payerId + "/schemes", JsonNode.class));
    }

    public JsonNode createScheme(String payerId, Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(
                baseUrl + "/internal/v1/coverage/payers/" + payerId + "/schemes", body, JsonNode.class));
    }

    public JsonNode listProducts(String schemeId) {
        return extractData(restTemplate.getForEntity(
                baseUrl + "/internal/v1/coverage/schemes/" + schemeId + "/products", JsonNode.class));
    }

    public JsonNode createProduct(String schemeId, Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(
                baseUrl + "/internal/v1/coverage/schemes/" + schemeId + "/products", body, JsonNode.class));
    }

    public JsonNode listPlanVersions(String productId) {
        return extractData(restTemplate.getForEntity(
                baseUrl + "/internal/v1/coverage/products/" + productId + "/versions", JsonNode.class));
    }

    public JsonNode createPlanVersion(String productId, Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(
                baseUrl + "/internal/v1/coverage/products/" + productId + "/versions", body, JsonNode.class));
    }

    public JsonNode publishPlanVersion(String versionId) {
        return extractData(restTemplate.postForEntity(
                baseUrl + "/internal/v1/coverage/plan-versions/" + versionId + "/publish", Map.of(), JsonNode.class));
    }

    // ── Membership lifecycle: verification, transitions, dependants ────────

    public JsonNode pendingVerification() {
        return extractData(restTemplate.getForEntity(
                baseUrl + "/internal/v1/coverage/members/pending-verification", JsonNode.class));
    }

    public JsonNode listDependants(String membershipId) {
        return extractData(restTemplate.getForEntity(
                baseUrl + "/internal/v1/coverage/members/" + membershipId + "/dependants", JsonNode.class));
    }

    public JsonNode verifyMembership(String membershipId, Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(
                baseUrl + "/internal/v1/coverage/members/" + membershipId + "/verify", body, JsonNode.class));
    }

    public JsonNode transitionMembership(String membershipId, String action, Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(
                baseUrl + "/internal/v1/coverage/members/" + membershipId + "/" + action, body, JsonNode.class));
    }

    public JsonNode addDependant(String membershipId, Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(
                baseUrl + "/internal/v1/coverage/members/" + membershipId + "/dependants", body, JsonNode.class));
    }

    // ── Benefits + reservation-aware accumulators ──────────────────────────

    public JsonNode listBenefits(String planVersionId) {
        return extractData(restTemplate.getForEntity(
                baseUrl + "/internal/v1/coverage/plan-versions/" + planVersionId + "/benefits", JsonNode.class));
    }

    public JsonNode createBenefit(String planVersionId, Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(
                baseUrl + "/internal/v1/coverage/plan-versions/" + planVersionId + "/benefits", body, JsonNode.class));
    }

    public JsonNode memberAccumulators(String membershipId) {
        return extractData(restTemplate.getForEntity(
                baseUrl + "/internal/v1/coverage/members/" + membershipId + "/benefit-accumulators", JsonNode.class));
    }

    public JsonNode benefitMovement(String membershipId, String kind, Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(
                baseUrl + "/internal/v1/coverage/members/" + membershipId + "/benefits/" + kind, body, JsonNode.class));
    }

    // ── Eligibility v2 + signed tokens ─────────────────────────────────────

    public JsonNode checkEligibilityV2(Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(
                baseUrl + "/internal/v1/coverage/eligibility/v2", body, JsonNode.class));
    }

    public JsonNode verifyEligibilityToken(Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(
                baseUrl + "/internal/v1/coverage/eligibility/tokens/verify", body, JsonNode.class));
    }

    // ── Ruvimbo Wave 2: authorisations, referrals, liability estimates ──────

    public JsonNode listAuthorisations(String memberCpid, String status) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/coverage/authorisations");
        if (memberCpid != null && !memberCpid.isBlank()) b.queryParam("member_cpid", memberCpid);
        if (status != null && !status.isBlank()) b.queryParam("status", status);
        return extractData(restTemplate.getForEntity(b.toUriString(), JsonNode.class));
    }

    public JsonNode getAuthorisation(String id) {
        return extractData(restTemplate.getForEntity(baseUrl + "/internal/v1/coverage/authorisations/" + id, JsonNode.class));
    }

    public JsonNode createAuthorisation(Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(baseUrl + "/internal/v1/coverage/authorisations", body, JsonNode.class));
    }

    public JsonNode authorisationAction(String id, String action, Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(
                baseUrl + "/internal/v1/coverage/authorisations/" + id + "/" + action, body, JsonNode.class));
    }

    public JsonNode listReferrals(String memberCpid) {
        return extractData(restTemplate.getForEntity(UriComponentsBuilder
                .fromHttpUrl(baseUrl + "/internal/v1/coverage/referrals")
                .queryParam("member_cpid", memberCpid).toUriString(), JsonNode.class));
    }

    public JsonNode createReferral(Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(baseUrl + "/internal/v1/coverage/referrals", body, JsonNode.class));
    }

    public JsonNode referralValidity(String id, boolean emergency) {
        return extractData(restTemplate.getForEntity(UriComponentsBuilder
                .fromHttpUrl(baseUrl + "/internal/v1/coverage/referrals/" + id + "/validity")
                .queryParam("emergency", emergency).toUriString(), JsonNode.class));
    }

    public JsonNode consumeReferral(String id) {
        return extractData(restTemplate.postForEntity(
                baseUrl + "/internal/v1/coverage/referrals/" + id + "/consume", Map.of(), JsonNode.class));
    }

    public JsonNode listLiabilityEstimates(String memberCpid) {
        return extractData(restTemplate.getForEntity(UriComponentsBuilder
                .fromHttpUrl(baseUrl + "/internal/v1/coverage/liability-estimates")
                .queryParam("member_cpid", memberCpid).toUriString(), JsonNode.class));
    }

    public JsonNode createLiabilityEstimate(Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(
                baseUrl + "/internal/v1/coverage/liability-estimates", body, JsonNode.class));
    }

    // ── Ruvimbo Wave 3: claims v2 (line-level adjudication, EOB, lineage) ───

    public JsonNode createClaimV2(Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(baseUrl + "/internal/v1/coverage/v2/claims", body, JsonNode.class));
    }

    public JsonNode getClaimV2(String id) {
        return extractData(restTemplate.getForEntity(baseUrl + "/internal/v1/coverage/v2/claims/" + id, JsonNode.class));
    }

    public JsonNode claimV2Action(String id, String action, Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(
                baseUrl + "/internal/v1/coverage/v2/claims/" + id + "/" + action, body, JsonNode.class));
    }

    public JsonNode claimV2Sub(String id, String sub) {
        return extractData(restTemplate.getForEntity(
                baseUrl + "/internal/v1/coverage/v2/claims/" + id + "/" + sub, JsonNode.class));
    }

    // ── Ruvimbo Wave 4: COB, employer/group, fraud, capitation, gateway ─────

    public JsonNode cobList(String memberCpid) {
        return extractData(restTemplate.getForEntity(UriComponentsBuilder
                .fromHttpUrl(baseUrl + "/internal/v1/coverage/cob").queryParam("member_cpid", memberCpid).toUriString(), JsonNode.class));
    }
    public JsonNode cobCoordinate(Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(baseUrl + "/internal/v1/coverage/cob/coordinate", body, JsonNode.class));
    }

    public JsonNode listEmployers() {
        return extractData(restTemplate.getForEntity(baseUrl + "/internal/v1/coverage/employers", JsonNode.class));
    }
    public JsonNode registerEmployer(Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(baseUrl + "/internal/v1/coverage/employers", body, JsonNode.class));
    }
    public JsonNode stageRoster(String employerId, Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(
                baseUrl + "/internal/v1/coverage/employers/" + employerId + "/roster-batches", body, JsonNode.class));
    }
    public JsonNode rosterBatch(String batchId, String sub) {
        String url = baseUrl + "/internal/v1/coverage/employers/roster-batches/" + batchId + (sub != null ? "/" + sub : "");
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }
    public JsonNode rosterAction(String batchId, String action) {
        return extractData(restTemplate.postForEntity(
                baseUrl + "/internal/v1/coverage/employers/roster-batches/" + batchId + "/" + action, Map.of(), JsonNode.class));
    }

    public JsonNode listFraudFlags(String status) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/coverage/fraud-flags");
        if (status != null && !status.isBlank()) b.queryParam("status", status);
        return extractData(restTemplate.getForEntity(b.toUriString(), JsonNode.class));
    }
    public JsonNode screenClaim(String claimId) {
        return extractData(restTemplate.postForEntity(
                baseUrl + "/internal/v1/coverage/fraud-flags/screen-claim/" + claimId, Map.of(), JsonNode.class));
    }
    public JsonNode reviewFraudFlag(String id, Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(
                baseUrl + "/internal/v1/coverage/fraud-flags/" + id + "/review", body, JsonNode.class));
    }

    public JsonNode listCapitation(String providerId) {
        return extractData(restTemplate.getForEntity(UriComponentsBuilder
                .fromHttpUrl(baseUrl + "/internal/v1/coverage/capitation-reports").queryParam("provider_id", providerId).toUriString(), JsonNode.class));
    }
    public JsonNode createCapitation(Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(baseUrl + "/internal/v1/coverage/capitation-reports", body, JsonNode.class));
    }

    public JsonNode listGateway(String status) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/coverage/gateway/transactions");
        if (status != null && !status.isBlank()) b.queryParam("status", status);
        return extractData(restTemplate.getForEntity(b.toUriString(), JsonNode.class));
    }
    public JsonNode routeGateway(Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(baseUrl + "/internal/v1/coverage/gateway/transactions", body, JsonNode.class));
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) return response.getBody().get("data");
        return response.getBody();
    }
}
