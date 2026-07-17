package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * HTTP client for the VARAPI (Provider Registry) sovereign service.
 *
 * <p>Provides access to provider profiles, licenses, and council
 * affiliations. VARAPI manages the canonical provider lifecycle
 * with license verification and registration tracking.</p>
 */
@Component
public class VarapiServiceClient {

    private static final Logger log = LoggerFactory.getLogger(VarapiServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public VarapiServiceClient(RestTemplate serviceRestTemplate,
                               ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.varapiBaseUrl();
    }

    /**
     * Get licenses for a provider.
     */
    public JsonNode getProviderLicenses(String providerId) {
        String url = baseUrl + "/v1/internal/providers/" + providerId + "/licenses";
        log.info("VARAPI: Getting licenses for provider={}", providerId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Get provider profile.
     */
    public JsonNode getProvider(String providerId) {
        String url = baseUrl + "/v1/internal/providers/" + providerId;
        log.info("VARAPI: Getting provider={}", providerId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * PIC eligibility assessment snapshot (G30): VARAPI is SoR for the point-in-time provider
     * eligibility that a TUSO nomination captured verbatim. Read-only pass-through by snapshotId.
     */
    public JsonNode getEligibilitySnapshot(String snapshotId) {
        String url = baseUrl + "/v1/internal/interop/eligibility/assessments/" + snapshotId;
        log.info("VARAPI: Getting eligibility snapshot={}", snapshotId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    // ── Certificates ────────────────────────────────────────────────────────

    /** Self-service: the authenticated provider's own certificates (resolved from X-Actor-ID). */
    public JsonNode portalListCertificates() {
        ResponseEntity<JsonNode> response =
                restTemplate.getForEntity(baseUrl + "/v1/portal/certificates", JsonNode.class);
        return extractData(response);
    }

    /** Self-service certificate PDF (byte[]) — varapi status-gates before streaming. */
    public ResponseEntity<byte[]> portalDownloadCertificate(long certificateId) {
        return restTemplate.getForEntity(
                baseUrl + "/v1/portal/certificates/" + certificateId + "/download", byte[].class);
    }

    /** Registrar: certificate lifecycle for a provider (numeric registry key). */
    public JsonNode getCertificatesByProvider(long providerId) {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                baseUrl + "/v1/internal/providers/certificates/provider/" + providerId, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getCurrentCertificate(long providerId) {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                baseUrl + "/v1/internal/providers/certificates/provider/" + providerId + "/current", JsonNode.class);
        return extractData(response);
    }

    public JsonNode getExpiringCertificates(int daysAhead) {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                baseUrl + "/v1/internal/providers/certificates/expiring?daysAhead=" + daysAhead, JsonNode.class);
        return extractData(response);
    }

    public JsonNode createCertificate(Map<String, Object> body) {
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl + "/v1/internal/providers/certificates", body, JsonNode.class);
        return extractData(response);
    }

    /** action ∈ issue | renew | suspend | reinstate | revoke. */
    public JsonNode certificateAction(long certificateId, String action, Map<String, Object> body) {
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl + "/v1/internal/providers/certificates/" + certificateId + "/" + action, body, JsonNode.class);
        return extractData(response);
    }

    // ── Lifecycle transitions (G10) ─────────────────────────────────────────

    public JsonNode getLifecycleTransitions(String providerPublicId) {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                baseUrl + "/v1/internal/providers/" + providerPublicId + "/lifecycle-transitions", JsonNode.class);
        return extractData(response);
    }

    public JsonNode transitionLifecycle(String providerPublicId, Map<String, Object> body) {
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl + "/v1/internal/providers/" + providerPublicId + "/lifecycle-transition", body, JsonNode.class);
        return extractData(response);
    }

    // ── Licence renewal (G8) ────────────────────────────────────────────────

    public JsonNode startLicenseRenewal(String providerPublicId, long licenseId) {
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl + "/v1/internal/providers/" + providerPublicId + "/licenses/" + licenseId + "/start-renewal",
                null, JsonNode.class);
        return extractData(response);
    }

    public JsonNode startLicenseRestoration(String providerPublicId, long licenseId) {
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl + "/v1/internal/providers/" + providerPublicId + "/licenses/" + licenseId + "/start-restoration",
                null, JsonNode.class);
        return extractData(response);
    }

    // ── Compliance actions (G9) ─────────────────────────────────────────────

    public JsonNode getComplianceByProvider(long providerId) {
        return extractData(restTemplate.getForEntity(
                baseUrl + "/v1/internal/providers/compliance/provider/" + providerId, JsonNode.class));
    }

    public JsonNode getOutstandingCompliance(long providerId) {
        return extractData(restTemplate.getForEntity(
                baseUrl + "/v1/internal/providers/compliance/provider/" + providerId + "/outstanding", JsonNode.class));
    }

    public JsonNode createComplianceAction(Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(
                baseUrl + "/v1/internal/providers/compliance", body, JsonNode.class));
    }

    /** op ∈ fulfil | exempt | escalate. */
    public JsonNode complianceActionOp(long actionId, String op, Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(
                baseUrl + "/v1/internal/providers/compliance/" + actionId + "/" + op, body, JsonNode.class));
    }

    // ── Disciplinary cases (G9) ─────────────────────────────────────────────

    public JsonNode getDisciplinaryByProvider(long providerId) {
        return extractData(restTemplate.getForEntity(
                baseUrl + "/v1/internal/providers/disciplinary-cases/provider/" + providerId, JsonNode.class));
    }

    public JsonNode openDisciplinaryCase(Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(
                baseUrl + "/v1/internal/providers/disciplinary-cases", body, JsonNode.class));
    }

    public JsonNode recordDisciplinaryAction(long caseId, Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(
                baseUrl + "/v1/internal/providers/disciplinary-cases/" + caseId + "/record-action", body, JsonNode.class));
    }

    public JsonNode closeDisciplinaryCase(long caseId, Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(
                baseUrl + "/v1/internal/providers/disciplinary-cases/" + caseId + "/close", body, JsonNode.class));
    }

    // ── Qualifications (G11) ────────────────────────────────────────────────

    public JsonNode getQualificationsByProvider(long providerId) {
        return extractData(restTemplate.getForEntity(
                baseUrl + "/v1/internal/providers/qualifications/provider/" + providerId, JsonNode.class));
    }

    public JsonNode getPendingQualifications(long providerId) {
        return extractData(restTemplate.getForEntity(
                baseUrl + "/v1/internal/providers/qualifications/provider/" + providerId + "/pending-verification", JsonNode.class));
    }

    public JsonNode createQualification(Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(
                baseUrl + "/v1/internal/providers/qualifications", body, JsonNode.class));
    }

    public JsonNode verifyQualification(long qualificationId, Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(
                baseUrl + "/v1/internal/providers/qualifications/" + qualificationId + "/verify", body, JsonNode.class));
    }

    // ── Practice contexts (G11) ─────────────────────────────────────────────

    public JsonNode getPracticeContextsByProvider(long providerId) {
        return extractData(restTemplate.getForEntity(
                baseUrl + "/v1/internal/providers/practice-contexts/provider/" + providerId, JsonNode.class));
    }

    public JsonNode createPracticeContext(Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(
                baseUrl + "/v1/internal/providers/practice-contexts", body, JsonNode.class));
    }

    public JsonNode practiceContextOp(long contextId, String op, Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(
                baseUrl + "/v1/internal/providers/practice-contexts/" + contextId + "/" + op, body, JsonNode.class));
    }

    // ── Affiliation writes + privilege decide (G11 — BFF-reachable) ─────────

    public JsonNode affiliationOp(long affiliationId, String op, Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(
                baseUrl + "/v1/internal/providers/affiliations/" + affiliationId + "/" + op, body, JsonNode.class));
    }

    public JsonNode getPendingPrivileges(int page, int size) {
        return extractData(restTemplate.getForEntity(
                baseUrl + "/v1/internal/privileges/pending?page=" + page + "&size=" + size, JsonNode.class));
    }

    public JsonNode decidePrivilege(long privilegeId, Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(
                baseUrl + "/v1/internal/privileges/" + privilegeId + "/decide", body, JsonNode.class));
    }

    /**
     * Create a provider profile in the canonical registry.
     */
    public JsonNode createProvider(Map<String, Object> request) {
        String url = baseUrl + "/v1/internal/providers";
        log.info("VARAPI: Creating provider [givenName={}, familyName={}, profession={}]",
                request.get("givenName"), request.get("familyName"), request.get("profession"));
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    /** Submit a self-service provider-access request (IATG Journey D). Applicant = forwarded X-Actor-ID. */
    public JsonNode submitProviderAccessRequest(Map<String, Object> request) {
        String url = baseUrl + "/v1/internal/providers/access-requests";
        log.info("VARAPI: Submitting provider access request [type={}]", request.get("requestType"));
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    /** List the calling applicant's provider-access requests (tenant + X-Actor-ID scoped in varapi). */
    public JsonNode listProviderAccessRequests() {
        String url = baseUrl + "/v1/internal/providers/access-requests";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /** Read a single provider-access request by its opaque public id. */
    public JsonNode getProviderAccessRequest(String publicId) {
        String url = baseUrl + "/v1/internal/providers/access-requests/" + publicId;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Reviewer queue (IATG Trust Console): tenant-scoped provider-access requests in the
     * given statuses (defaults to every still-decidable status when none supplied).
     */
    public JsonNode listProviderAccessRequestsForReview(List<String> statuses) {
        StringBuilder sb = new StringBuilder(baseUrl).append("/v1/internal/providers/access-requests/review");
        if (statuses != null && !statuses.isEmpty()) {
            sb.append("?");
            sb.append(statuses.stream()
                    .map(s -> "status=" + URLEncoder.encode(s, StandardCharsets.UTF_8))
                    .collect(java.util.stream.Collectors.joining("&")));
        }
        log.info("VARAPI: listing provider access requests for review");
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(sb.toString(), JsonNode.class);
        return extractData(response);
    }

    /** Record a reviewer decision on a provider-access request (Trust Console). */
    public JsonNode decideProviderAccessRequest(String publicId, Map<String, Object> body) {
        String url = baseUrl + "/v1/internal/providers/access-requests/"
                + URLEncoder.encode(publicId, StandardCharsets.UTF_8) + "/decision";
        log.info("VARAPI: deciding provider access request {}", publicId);
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(body), JsonNode.class);
        return extractData(response);
    }

    /**
     * Search-before-create for provider registry (VARAPI internal).
     */
    public JsonNode searchProviders(Map<String, Object> requestBody) {
        String url = baseUrl + "/v1/internal/providers/search";
        log.info("VARAPI: Searching providers");
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(requestBody), JsonNode.class);
        return extractData(response);
    }

    /**
     * Get council affiliations for a provider.
     */
    public JsonNode getProviderCouncilAffiliations(String providerId) {
        String url = baseUrl + "/v1/internal/providers/" + providerId + "/affiliations";
        log.info("VARAPI: Getting affiliations for provider={}", providerId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Get privileges (facility affiliations) for a provider.
     */
    public JsonNode getProviderPrivileges(String providerId) {
        String url = baseUrl + "/v1/internal/providers/" + providerId + "/privileges";
        log.info("VARAPI: Getting privileges for provider={}", providerId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Get CPD summary for a provider.
     */
    public JsonNode getProviderCpdSummary(String providerId) {
        String url = baseUrl + "/v1/internal/providers/" + providerId + "/cpd/summary";
        log.info("VARAPI: Getting CPD summary for provider={}", providerId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Look up a provider registration by the person's Health ID (actor ID).
     *
     * <p>Used by the post-login identity resolution flow to discover whether
     * the person has a linked Provider ID in the canonical registry.</p>
     */
    public JsonNode getProviderByHealthId(String healthId) {
        String url = baseUrl + "/v1/internal/providers/by-health-id/" + healthId;
        log.info("VARAPI: Looking up provider by healthId={}", healthId);
        try {
            ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
            return extractData(response);
        } catch (Exception e) {
            log.debug("VARAPI: No provider found for healthId={}: {}", healthId, e.getMessage());
            return null;
        }
    }

    /**
     * Recognition read-model by Health ID — {recognised, licenceStatus,
     * profession, cadre}. Enumeration-resistant upstream (unknown ids yield
     * a generic recognised=false body). Returns null on transport failure so
     * callers can fail open to "not recognised" (advantages must never block
     * a journey).
     */
    public JsonNode getRecognitionByHealthId(String healthId) {
        String url = baseUrl + "/v1/internal/providers/by-health-id/" + healthId + "/recognition";
        log.debug("VARAPI: Resolving provider recognition by health id");
        try {
            ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
            return extractData(response);
        } catch (Exception e) {
            log.debug("VARAPI: recognition lookup unavailable: {}", e.getMessage());
            return null;
        }
    }

    /**
     * List facility affiliations for a provider identified by Health ID.
     */
    public JsonNode getProviderAffiliations(String healthId) {
        String url = baseUrl + "/v1/internal/providers/by-health-id/" + healthId + "/affiliations";
        log.info("VARAPI: Listing affiliations for healthId={}", healthId);
        try {
            ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
            return extractData(response);
        } catch (Exception e) {
            log.debug("VARAPI: No affiliations for healthId={}: {}", healthId, e.getMessage());
            return null;
        }
    }

    /**
     * List professional notices (licence renewals, CPD, compliance alerts)
     * for a provider identified by Health ID.
     */
    public JsonNode getProviderNotices(String healthId) {
        String url = baseUrl + "/v1/internal/providers/by-health-id/" + healthId + "/notices";
        log.info("VARAPI: Listing notices for healthId={}", healthId);
        try {
            ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
            return extractData(response);
        } catch (Exception e) {
            log.debug("VARAPI: No notices for healthId={}: {}", healthId, e.getMessage());
            return null;
        }
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }

    public ResponseEntity<String> rawGet(String path) {
        return restTemplate.getForEntity(baseUrl + path, String.class);
    }

    public ResponseEntity<String> rawPost(String path, Object body) {
        return restTemplate.postForEntity(baseUrl + path, body, String.class);
    }

    /** Council-regulated payment obligations (Varapi). */
    public JsonNode getProviderCouncilObligations(long providerId) {
        String url = baseUrl + "/v1/internal/provider-council/obligations?providerId=" + providerId;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    /** Create MusheX payment intent for a council fee obligation (Varapi). */
    public JsonNode createMusheXIntentForObligation(long obligationId) {
        String url = baseUrl + "/v1/internal/provider-council/obligations/" + obligationId + "/mushex-intent";
        log.info("VARAPI: create MusheX intent for obligationId={}", obligationId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, HttpEntity.EMPTY, JsonNode.class);
        return response.getBody();
    }

    /** Sync council obligation payment status from MusheX (Varapi). */
    public JsonNode syncCouncilObligationPayment(long obligationId) {
        String url = baseUrl + "/v1/internal/provider-council/obligations/" + obligationId + "/sync-payment";
        log.info("VARAPI: sync council obligation payment obligationId={}", obligationId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, HttpEntity.EMPTY, JsonNode.class);
        return response.getBody();
    }

    /** Council workflow queue (Varapi). */
    public JsonNode getProviderCouncilOpenApplications(long councilId, String workflowStates) {
        StringBuilder sb = new StringBuilder(baseUrl)
                .append("/v1/internal/provider-council/applications/open?councilId=")
                .append(councilId);
        if (workflowStates != null && !workflowStates.isBlank()) {
            sb.append("&workflowStates=").append(URLEncoder.encode(workflowStates, StandardCharsets.UTF_8));
        }
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(sb.toString(), JsonNode.class);
        return response.getBody();
    }

    /** Fundo-linked CPD candidates pending council acceptance (Varapi). */
    public JsonNode getFundoCpdCandidates(long providerId) {
        String url = baseUrl + "/v1/internal/provider-council/fundo-cpd-candidates?providerId=" + providerId;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    /** Fundo-linked CPD candidates keyed by the provider public identifier. */
    public JsonNode getFundoCpdCandidatesByPublicId(String providerPublicId) {
        String url = baseUrl + "/v1/internal/provider-council/fundo-cpd-candidates?providerPublicId="
                + URLEncoder.encode(providerPublicId, StandardCharsets.UTF_8);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    /** Per-council regulatory JSON configuration (Varapi V009). */
    public JsonNode getCouncilRegulatoryConfig(long councilId) {
        String url = baseUrl + "/v1/internal/provider-council/council-regulatory-config?councilId=" + councilId;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    public JsonNode putCouncilRegulatoryConfig(Map<String, Object> body) {
        String url = baseUrl + "/v1/internal/provider-council/council-regulatory-config";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.PUT, new HttpEntity<>(body, headers), JsonNode.class);
        return response.getBody();
    }

    /**
     * Change provider lifecycle status (ACTIVE, SUSPENDED, REVOKED, PENDING, etc.).
     */
    public JsonNode changeProviderStatus(String providerPublicId, Map<String, Object> body) {
        String url = baseUrl + "/v1/internal/providers/" + providerPublicId + "/status";
        log.info("VARAPI: changeProviderStatus provider={}", providerPublicId);
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(body), JsonNode.class);
        return extractData(response);
    }

    /** Council import reconciliation queue (Varapi). */
    public JsonNode getReconciliationQueue(String status, int page, int size) {
        StringBuilder sb = new StringBuilder(baseUrl)
                .append("/v1/internal/reconciliation/queue?page=")
                .append(page)
                .append("&size=")
                .append(size);
        if (status != null && !status.isBlank()) {
            sb.append("&status=").append(URLEncoder.encode(status, StandardCharsets.UTF_8));
        }
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(sb.toString(), JsonNode.class);
        return response.getBody();
    }

    /** Decide a council import reconciliation case (ACCEPT / REJECT / MERGE / DEFER). */
    public JsonNode decideReconciliationCase(long caseId, Map<String, Object> body) {
        String url = baseUrl + "/v1/internal/reconciliation/" + caseId + "/decision";
        log.info("VARAPI: decideReconciliationCase caseId={}", caseId);
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new org.springframework.http.HttpEntity<>(body), JsonNode.class);
        return extractData(response);
    }

    /** Council intake: SUBMITTED → UNDER_ADMIN_REVIEW. */
    public JsonNode advanceCouncilApplicationToUnderAdminReview(long applicationId) {
        String url = baseUrl + "/v1/internal/provider-council/applications/" + applicationId + "/under-admin-review";
        log.info("VARAPI: advanceCouncilApplicationToUnderAdminReview applicationId={}", applicationId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, HttpEntity.EMPTY, JsonNode.class);
        return response.getBody();
    }

    /** Council fee gate: eligible → AWAITING_PAYMENT. */
    public JsonNode advanceCouncilApplicationToAwaitingPayment(long applicationId) {
        String url = baseUrl + "/v1/internal/provider-council/applications/" + applicationId + "/awaiting-payment";
        log.info("VARAPI: advanceCouncilApplicationToAwaitingPayment applicationId={}", applicationId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, HttpEntity.EMPTY, JsonNode.class);
        return response.getBody();
    }

    /** After MusheX settlement: AWAITING_PAYMENT → READY_FOR_REVIEW. */
    public JsonNode advanceCouncilApplicationAfterFeePaid(long applicationId) {
        String url = baseUrl + "/v1/internal/provider-council/applications/" + applicationId + "/fee-paid";
        log.info("VARAPI: advanceCouncilApplicationAfterFeePaid applicationId={}", applicationId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, HttpEntity.EMPTY, JsonNode.class);
        return response.getBody();
    }

    /** Append council staff review row and update application review_state. */
    public JsonNode recordCouncilReview(Map<String, Object> body) {
        String url = baseUrl + "/v1/internal/provider-council/reviews";
        log.info("VARAPI: recordCouncilReview applicationId={}", body.get("applicationId"));
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(body), JsonNode.class);
        return response.getBody();
    }

    /** Accept Fundo-linked CPD candidate into governed CPD ledger. */
    public JsonNode acceptFundoCpdCandidate(long candidateId) {
        String url = baseUrl + "/v1/internal/provider-council/fundo-cpd-candidates/" + candidateId + "/accept";
        log.info("VARAPI: acceptFundoCpdCandidate candidateId={}", candidateId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, HttpEntity.EMPTY, JsonNode.class);
        return response.getBody();
    }

    /** Reject Fundo-linked CPD candidate. */
    public JsonNode rejectFundoCpdCandidate(long candidateId, Map<String, Object> body) {
        String url = baseUrl + "/v1/internal/provider-council/fundo-cpd-candidates/" + candidateId + "/reject";
        log.info("VARAPI: rejectFundoCpdCandidate candidateId={}", candidateId);
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(body != null ? body : Map.of()), JsonNode.class);
        return response.getBody();
    }

    // ── Provider self-claim / recovery glue (IATG WS-F) ───────────────────────

    /**
     * Preview what a bootstrap claim token would claim (read-only, token in
     * the body so it stays out of access logs). 404 for any non-redeemable
     * token — Varapi never leaks why.
     */
    public JsonNode claimPreview(Map<String, Object> body) {
        String url = baseUrl + "/v1/internal/providers/bootstrap/claim/preview";
        log.info("VARAPI: previewing provider claim token");
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(body), JsonNode.class);
        return response.getBody();
    }

    /**
     * Redeem a bootstrap claim token onto the claimant's own Health ID.
     * Downstream burn is atomic and single-use; 409s propagate to the caller.
     */
    public JsonNode claim(Map<String, Object> body) {
        String url = baseUrl + "/v1/internal/providers/bootstrap/claim";
        log.info("VARAPI: claiming provider profile for claimant");
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(body), JsonNode.class);
        return response.getBody();
    }

    /**
     * Initiate provider profile recovery (recover-not-reissue): issues a
     * single-use recovery token bound to the person's EXISTING profile.
     */
    public JsonNode recoveryInitiate(Map<String, Object> body) {
        String url = baseUrl + "/v1/internal/providers/recovery/initiate";
        log.info("VARAPI: initiating provider profile recovery");
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(body), JsonNode.class);
        return response.getBody();
    }

    /**
     * Complete provider profile recovery: atomically redeems the recovery
     * token and re-links the login to the SAME providerPublicId.
     */
    public JsonNode recoveryComplete(Map<String, Object> body) {
        String url = baseUrl + "/v1/internal/providers/recovery/complete";
        log.info("VARAPI: completing provider profile recovery");
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(body), JsonNode.class);
        return response.getBody();
    }

    /**
     * Record a verification attempt on a provider's trust ledger (WS-C trust
     * API — HTTP contract: {type, source, ref, outcome, confidence}). The ref
     * MUST arrive pre-masked from the experience layer.
     */
    public JsonNode recordVerificationAttempt(String providerId, Map<String, Object> body) {
        String url = baseUrl + "/v1/internal/providers/"
                + URLEncoder.encode(providerId, StandardCharsets.UTF_8) + "/verification-attempts";
        log.info("VARAPI: recording verification attempt for provider={}", providerId);
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(body), JsonNode.class);
        return response.getBody();
    }

    // ── Public gateway lane (anonymous-safe) ──────────────────────────────────

    private static final String PUBLIC_DEFAULT_TENANT = "00000000-0000-0000-0000-000000000001";

    /**
     * Public practitioner register verification (disclosure-limited register
     * facts; anonymous-safe). Downstream returns 200 with a uniform
     * NOT_FOUND-status shape on a miss (no existence oracle).
     */
    public JsonNode publicPractitionerVerify(String registrationNumber) {
        String url = baseUrl + "/v1/public/practitioners/verify/"
                + URLEncoder.encode(registrationNumber, StandardCharsets.UTF_8);
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                java.net.URI.create(url), HttpMethod.GET, anonymousSafeEntity(), JsonNode.class);
        return extractData(response);
    }

    /**
     * Verified providers at a facility (public register facts; anonymous-safe). Varapi owns the
     * gate and returns an empty {@code ApiResponse.data} list for an unknown/empty facility (no
     * 404, no existence oracle); {@link #extractData} unwraps it to a JSON array node.
     */
    public JsonNode publicFacilityPractitioners(long facilityId) {
        String url = baseUrl + "/v1/public/facilities/" + facilityId + "/practitioners";
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                java.net.URI.create(url), HttpMethod.GET, anonymousSafeEntity(), JsonNode.class);
        return extractData(response);
    }

    /**
     * Downstream registries reject requests missing the mandatory trust headers (400).
     * Anonymous public-lane requests arrive with actor/trust headers stripped at the
     * edge, so the BFF supplies service-originated defaults here; when the caller DID
     * send platform headers (the web client always does), the shared forwarding
     * interceptor overwrites these defaults with the inbound values.
     */
    private HttpEntity<Void> anonymousSafeEntity() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-ID", PUBLIC_DEFAULT_TENANT);
        headers.set("X-Actor-ID", "public-gateway");
        headers.set("X-Actor-Type", "SYSTEM");
        headers.set("X-Purpose-Of-Use", "PUBLIC_ACCESS");
        headers.set("X-Device-Fingerprint", "public-gateway");
        headers.set("X-Correlation-ID", java.util.UUID.randomUUID().toString());
        headers.set("X-Request-ID", java.util.UUID.randomUUID().toString());
        return new HttpEntity<>(headers);
    }

    /**
     * Resolve a council registration number to its provider reference
     * (anti-enumeration: a miss is an empty resolution, not a 404).
     */
    public JsonNode resolveCouncilNumber(String registrationNumber, String councilCode) {
        StringBuilder sb = new StringBuilder(baseUrl)
                .append("/v1/internal/providers/resolve-council-number?registrationNumber=")
                .append(URLEncoder.encode(registrationNumber, StandardCharsets.UTF_8));
        if (councilCode != null && !councilCode.isBlank()) {
            sb.append("&councilCode=").append(URLEncoder.encode(councilCode, StandardCharsets.UTF_8));
        }
        log.info("VARAPI: resolving council registration number");
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(sb.toString(), JsonNode.class);
        return extractData(response);
    }
}
