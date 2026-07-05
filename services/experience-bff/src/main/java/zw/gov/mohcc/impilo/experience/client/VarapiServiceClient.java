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
}
