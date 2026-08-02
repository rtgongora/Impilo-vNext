package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.AuthzResponse;
import zw.gov.mohcc.impilo.tshepo.contracts.v1.TrustChallengeOutcome;
import zw.gov.mohcc.impilo.tshepo.contracts.v1.adapter.AuthzResponseChallengeAdapter;
import java.util.ArrayList;
import java.util.List;

/**
 * HTTP client for the TSHEPO Authorization sovereign service.
 *
 * <p>Provides access to policy rules, break-glass review workflows, and device
 * trust management. TSHEPO Authz is the legitimacy engine that enforces the
 * Health OS trust model — every request flows through ext_authz before reaching
 * any downstream service.</p>
 *
 * <p>Trust headers are automatically forwarded by the shared
 * {@link ServiceClientConfig} interceptor.</p>
 */
@Component

public class TshepoAuthzServiceClient {

    private static final Logger log = LoggerFactory.getLogger(TshepoAuthzServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TshepoAuthzServiceClient(RestTemplate serviceRestTemplate,
                                     ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.tshepoAuthzBaseUrl();
    }

    /**
     * List all policy rules configured in TSHEPO.
     */
    public JsonNode listPolicyRules() {
        String url = baseUrl + "/v1/policies";
        log.info("TSHEPO-AUTHZ: Listing policy rules");
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Create a new policy rule.
     */
    public JsonNode createPolicyRule(Map<String, Object> request) {
        String url = baseUrl + "/v1/policies";
        log.info("TSHEPO-AUTHZ: Creating policy rule");
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    /**
     * Request break-glass emergency access.
     */
    public JsonNode requestBreakGlass(Map<String, Object> request) {
        String url = baseUrl + "/v1/break-glass";
        log.info("TSHEPO-AUTHZ: Requesting break-glass for actor={}", request.get("actorId"));
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    /**
     * Get pending break-glass access reviews.
     */
    public JsonNode getPendingBreakGlassReviews() {
        String url = baseUrl + "/v1/break-glass/review";
        log.info("TSHEPO-AUTHZ: Getting pending break-glass reviews");
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Review (approve/deny) a break-glass access request.
     */
    public JsonNode reviewBreakGlass(String id, Map<String, Object> request) {
        String url = baseUrl + "/v1/break-glass/review/" + id;
        log.info("TSHEPO-AUTHZ: Reviewing break-glass id={}", id);
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    /**
     * Get device trust profile by fingerprint.
     */
    public JsonNode getDeviceProfile(String fingerprint) {
        String url = baseUrl + "/v1/devices/" + fingerprint;
        log.info("TSHEPO-AUTHZ: Getting device profile fingerprint={}", fingerprint);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Block a device by fingerprint.
     */
    public JsonNode blockDevice(String fingerprint) {
        String url = baseUrl + "/v1/devices/" + fingerprint + "/block";
        log.info("TSHEPO-AUTHZ: Blocking device fingerprint={}", fingerprint);
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, null, JsonNode.class);
        return extractData(response);
    }

    /**
     * Unblock a device by fingerprint (removes the block).
     */
    public void unblockDevice(String fingerprint) {
        String url = baseUrl + "/v1/devices/" + fingerprint + "/block";
        log.info("TSHEPO-AUTHZ: Unblocking device fingerprint={}", fingerprint);
        restTemplate.delete(url);
    }

    /**
     * Synthetic ext_authz check for Health Intelligence plane queries.
     * Uses {@code :path} {@code /internal/v1/intelligence-plane} so {@code PolicyEngine}
     * derives resource type {@code intelligence-plane} (see tshepo-authz V006 migration).
     */
    public boolean intelligencePlaneQueryAllowed() {
        return syntheticAuthorizeVerdict("POST", "/internal/v1/intelligence-plane");
    }

    /**
     * Synthetic ext_authz check for Experience registry intake mutations.
     * Uses {@code :method} / {@code :path} headers so {@code PolicyEngine} derives
     * resource type {@code registry-intake} (see tshepo-authz V005 migration).
     */
    public boolean registryIntakeMutationAllowed() {
        return syntheticAuthorizeVerdict("POST", "/internal/v1/registry-intake");
    }

    /**
     * Synthetic ext_authz for governed imaging reads (search, study detail, series list).
     * Resource type {@code imaging-governed-read} (see PolicyEngine / Tshepo migrations).
     */
    public boolean imagingGovernedReadAllowed() {
        return syntheticAuthorizeVerdict("GET", "/internal/v1/imaging-governed-read");
    }

    /**
     * Synthetic ext_authz for governed imaging mutations (correlate, sync, report/order links).
     */
    public boolean imagingGovernedMutateAllowed() {
        return syntheticAuthorizeVerdict("POST", "/internal/v1/imaging-governed-mutate");
    }

    /**
     * Synthetic ext_authz for DICOM viewer session creation.
     */
    public boolean imagingViewerLaunchAllowed() {
        return syntheticAuthorizeVerdict("POST", "/internal/v1/imaging-viewer-launch");
    }

    /**
     * Synthetic ext_authz for telemedicine read operations.
     */
    public boolean telemedicineReadAllowed() {
        return syntheticAuthorizeVerdict("GET", "/internal/v1/telemedicine-governed-read");
    }

    /**
     * Synthetic ext_authz for telemedicine lifecycle mutations.
     */
    public boolean telemedicineMutateAllowed() {
        return syntheticAuthorizeVerdict("POST", "/internal/v1/telemedicine-governed-mutate");
    }

    /**
     * Synthetic ext_authz for telemedicine break-glass overrides.
     */
    public boolean telemedicineBreakGlassAllowed() {
        return syntheticAuthorizeVerdict("POST", "/internal/v1/telemedicine-break-glass-override");
    }

    public boolean publicHealthGovernedReadAllowed() {
        return syntheticAuthorizeVerdict("GET", "/internal/v1/public-health-governed-read");
    }

    public boolean publicHealthGovernedMutateAllowed() {
        return syntheticAuthorizeVerdict("POST", "/internal/v1/public-health-governed-mutate");
    }

    public boolean publicHealthGovernedExportAllowed() {
        return syntheticAuthorizeVerdict("POST", "/internal/v1/public-health-governed-export");
    }

    /**
     * Synthetic ext_authz for Experience enterprise-domain finance routes (billing workspace or MusheX platform).
     * Resource type is derived from the last path segment (see tshepo-authz V007).
     */
    public boolean financePlaneAllowed(String httpMethod, String syntheticPath) {
        return syntheticAuthorizeVerdict(httpMethod, syntheticPath);
    }

    /**
     * Synthetic ext_authz for Experience shell workspace persistence (pins / recents).
     * Tshepo PolicyEngine maps the last non-UUID URL segment of {@code :path} to resource type
     * {@code workspace-state} for {@code /internal/v1/shell/workspace-state}
     * (tshepo-authz migration {@code V008__shell_workspace_policy_rules.sql}).
     */
    public boolean shellWorkspaceStateAllowed(String httpMethod) {
        return syntheticAuthorizeVerdict(httpMethod, "/internal/v1/shell/workspace-state");
    }

    // ── Typed decisions ───────────────────────────────────────────────────────────────────
    // These sit beside the boolean accessors and reuse their exact synthetic paths. The paths are
    // what tshepo-authz matches its policy rules on, so they are declared HERE and nowhere else --
    // a caller that spelled its own would silently stop matching any rule.

    public TrustDecisionResult telemedicineRead() {
        return authorize("GET", "/internal/v1/telemedicine-governed-read");
    }

    public TrustDecisionResult telemedicineMutate() {
        return authorize("POST", "/internal/v1/telemedicine-governed-mutate");
    }

    public TrustDecisionResult telemedicineBreakGlass() {
        return authorize("POST", "/internal/v1/telemedicine-break-glass-override");
    }

    /**
     * @deprecated the boolean discards the decision. Prefer {@link #authorize(String, String)},
     *     which preserves the reason code and — critically — distinguishes a refusal from a PDP
     *     outage. Retained so the fourteen existing call sites keep compiling unchanged.
     */
    @Deprecated
    private boolean syntheticAuthorizeVerdict(String method, String path) {
        return authorize(method, path).allowed();
    }

    /**
     * Ask the PDP, and keep the answer.
     *
     * <p>Every caller in this class used to reduce this to {@code boolean}, so a controller could
     * never emit a trust challenge: {@code reasonCode}, {@code stepUpMethods} and
     * {@code requiredAssurance} were discarded before any of them ran. The legacy wire has only
     * three verdicts, so an actionable outcome such as {@code CONSENT_REQUIRED} arrives as a DENY
     * carrying that error code; {@link AuthzResponseChallengeAdapter} promotes it back to its real
     * decision.</p>
     *
     * <p>The transport catch-all no longer shares a return value with a refusal. It fails closed —
     * {@code allowed()} is false either way — but reports {@code TEMPORARILY_UNAVAILABLE}, so a
     * user is never told they lack permissions because the policy service was unreachable.</p>
     */
    public TrustDecisionResult authorize(String method, String path) {
        String url = baseUrl + "/v1/authorize";
        HttpHeaders headers = new HttpHeaders();
        headers.set(":method", method);
        headers.set(":path", path);
        try {
            ResponseEntity<JsonNode> response =
                    restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(headers),
                            JsonNode.class);
            JsonNode body = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful() || body == null || !body.has("verdict")) {
                // A 2xx with no verdict is a producer fault, not an answer. Calling it a denial
                // would report a broken contract as a permissions decision.
                log.warn("TSHEPO-AUTHZ: unusable authorize response path={} status={}",
                        path, response.getStatusCode());
                return TrustDecisionResult.unavailable("AUTHZ_RESPONSE_UNUSABLE");
            }
            return TrustDecisionResult.of(toOutcome(body));
        } catch (HttpClientErrorException e) {
            int status = e.getStatusCode().value();
            if (status == 401 || status == 403) {
                // The PDP answered; the transport carried it as an error status.
                log.debug("TSHEPO-AUTHZ: synthetic authorize denied path={} status={}", path, status);
                return TrustDecisionResult.of(toOutcome(readBody(e)));
            }
            log.warn("TSHEPO-AUTHZ: authorize failed path={} status={}", path, status);
            return TrustDecisionResult.unavailable("AUTHZ_UPSTREAM_ERROR");
        } catch (Exception e) {
            log.warn("TSHEPO-AUTHZ: synthetic authorize failed path={}: {}", path, e.getMessage());
            return TrustDecisionResult.unavailable("AUTHZ_UNREACHABLE");
        }
    }

    private JsonNode readBody(HttpClientErrorException e) {
        try {
            return objectMapper.readTree(e.getResponseBodyAsString());
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Maps the legacy authorize body onto the canonical outcome, preserving the reason code. */
    private TrustChallengeOutcome toOutcome(JsonNode body) {
        if (body == null || !body.has("verdict")) {
            // Reached only when a 401/403 carried no parseable body. AUTHENTICATION_REQUIRED would
            // be a guess; DENY with an explicit code is the honest reading.
            return TrustChallengeOutcome.deny("AUTHZ_DENIED_NO_DETAIL", "trust.deny.generic", null);
        }
        String verdict = body.path("verdict").asText("");
        String errorCode = body.path("errorCode").isMissingNode() || body.path("errorCode").isNull()
                ? null : body.path("errorCode").asText();
        List<String> methods = new ArrayList<>();
        body.path("stepUpMethods").forEach(n -> methods.add(n.asText()));

        AuthzResponse legacy;
        if ("ALLOW".equalsIgnoreCase(verdict)) {
            legacy = AuthzResponse.allow(null, 0, null);
        } else if ("STEP_UP_REQUIRED".equalsIgnoreCase(verdict)) {
            legacy = AuthzResponse.stepUp(methods, 0);
        } else {
            legacy = AuthzResponse.deny(errorCode == null ? "DENIED" : errorCode,
                    body.path("errorMessage").asText("Denied"), 0);
        }
        return AuthzResponseChallengeAdapter.toCanonical(
                legacy, body.path("decisionId").asText(null), body.path("policyVersion").asText(null));
    }

    /**
     * List the GDHCN readiness assessment (full domain catalogue) for the caller's tenant.
     * Trust headers (tenant/actor/JWT) are forwarded by the shared interceptor.
     */
    public JsonNode listGdhcnReadiness() {
        String url = baseUrl + "/v1/gdhcn-readiness";
        log.info("TSHEPO-AUTHZ: Listing GDHCN readiness");
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Update one GDHCN readiness domain. The authz service enforces admin role + step-up.
     */
    public JsonNode updateGdhcnReadiness(String domainKey, Map<String, Object> request) {
        String url = baseUrl + "/v1/gdhcn-readiness/" + domainKey;
        log.info("TSHEPO-AUTHZ: Updating GDHCN readiness domain {}", domainKey);
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    // ── Step-up challenge engine (citizen self-service completion loop, G-CZO-17) ──────
    // The verified-challenge state is read by PolicyEngine.hasRecentStepUp at the gateway,
    // so completing a challenge here unlocks the retried sensitive action — no token needed.

    /** Issue a step-up challenge. Body carries the SERVER-RESOLVED tenant/actor (never client-supplied). */
    public JsonNode issueStepUpChallenge(Map<String, Object> request) {
        String url = baseUrl + "/v1/step-up/challenge";
        log.info("TSHEPO-AUTHZ: issuing step-up challenge type={}", request.get("challengeType"));
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    /** Verify (complete) a step-up challenge. Body carries the SERVER-RESOLVED tenant/actor. */
    public JsonNode verifyStepUpChallenge(Map<String, Object> request) {
        String url = baseUrl + "/v1/step-up/verify";
        log.info("TSHEPO-AUTHZ: verifying step-up challenge {}", request.get("challengeId"));
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    /** Read the status of a step-up challenge. */
    public JsonNode getStepUpStatus(String challengeId) {
        String url = baseUrl + "/v1/step-up/status/" + challengeId;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    // ── WorkMode catalog (Phase B — mode as a governed dimension) ─────────────

    /** Full WorkMode catalog for the caller's tenant (V054 work_mode_catalog). */
    public JsonNode workModeCatalog() {
        String url = baseUrl + "/v1/authz/work-modes";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Modes a role_template_id may hold, per the seeded {@code role_template_mode}
     * catalog. An empty {@code modes} array means "no seeded entry" — the caller
     * must fall back to the enum-level derivation in {@code WorkMode} (Phase C),
     * never assume {@code CLINICAL_CARE} on a miss.
     */
    public JsonNode workModesForRole(String roleTemplateId) {
        String url = baseUrl + "/v1/authz/work-modes/for-role?roleTemplateId="
                + java.net.URLEncoder.encode(roleTemplateId, java.nio.charset.StandardCharsets.UTF_8);
        try {
            ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
            return extractData(response);
        } catch (Exception e) {
            log.warn("TSHEPO-AUTHZ: work-modes for-role lookup failed roleTemplateId={}: {}", roleTemplateId, e.getMessage());
            return null;
        }
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
