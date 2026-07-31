package zw.gov.mohcc.impilo.telemonitoring.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * CONTRIBUTES a monitoring indication into PCT's problems list — never a copy of it. PCT
 * (pct-service) is the SoR for condition/diagnosis/certainty/severity/staging/recurrence; this
 * client calls pct-service's {@code POST /v1/problems} with {@code responsible_service=telemonitoring}.
 *
 * <p>Best-effort, mirroring surgery-service's {@code PctProblemContributionClient}: a PCT outage
 * must not block plan activation, which stays the local truth either way.
 * {@code MonitoringPlanEntity.pctProblemRef} is null until this succeeds, and
 * {@code idx_tm_plans_uncontributed} (V006) is the reconciliation query for "not yet".</p>
 *
 * <p>Forwards the current clinician's trust context when available — pct-service's care-relationship
 * guard binds the write to an actor holding an active care context for the patient.</p>
 */
@Service
public class PctProblemContributionClient {

    private static final Logger log = LoggerFactory.getLogger(PctProblemContributionClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    @Autowired
    public PctProblemContributionClient(
            @Value("${telemonitoring.pct.base-url:http://pct-service:8088}") String baseUrl,
            @Value("${telemonitoring.pct.timeout-ms:4000}") int timeoutMs) {
        this(buildRestTemplate(timeoutMs), baseUrl);
    }

    /** Package-visible for unit tests; also used when a shared {@link RestTemplate} bean is added. */
    PctProblemContributionClient(RestTemplate restTemplate, String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = normalizeBaseUrl(baseUrl);
    }

    private static RestTemplate buildRestTemplate(int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(Math.min(timeoutMs, 2000)));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        return new RestTemplate(factory);
    }

    private static String normalizeBaseUrl(String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public record ContributionResult(UUID problemId, boolean contributed) {
        public static ContributionResult unavailable() { return new ContributionResult(null, false); }
    }

    /**
     * @param display              required by pct_problems — the condition in the clinician's own words
     * @param diagnosticCertainty  CONFIRMED | PROVISIONAL | DIFFERENTIAL | RULED_OUT, or null (unstated)
     */
    @SuppressWarnings("unchecked")
    public ContributionResult contributeCondition(String subjectCpid, String journeyId, String encounterId,
                                                  String display, String diagnosticCertainty, String evidence) {
        if (subjectCpid == null || subjectCpid.isBlank() || display == null || display.isBlank()) {
            return ContributionResult.unavailable();
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("subject_cpid", subjectCpid);
            if (journeyId != null) payload.put("journey_id", journeyId);
            if (encounterId != null) payload.put("encounter_id", encounterId);
            payload.put("display", display);
            payload.put("category", "DIAGNOSIS");
            payload.put("clinical_status", "ACTIVE");
            if (diagnosticCertainty != null) payload.put("diagnostic_certainty", diagnosticCertainty);
            if (evidence != null) payload.put("evidence", evidence);
            payload.put("responsible_service", "telemonitoring");

            ResponseEntity<Map> resp = restTemplate.postForEntity(baseUrl + "/v1/problems",
                    new HttpEntity<>(payload, trustHeaders()), Map.class);
            UUID problemId = extractProblemId(resp.getBody());
            if (problemId != null) {
                log.info("TELEMONITORING: contributed condition to pct_problems id={} subject={}",
                        problemId, subjectCpid);
                return new ContributionResult(problemId, true);
            }
            return ContributionResult.unavailable();
        } catch (RestClientException | IllegalStateException e) {
            log.warn("TELEMONITORING: pct_problems contribution unavailable (best-effort): {}", e.getMessage());
            return ContributionResult.unavailable();
        }
    }

    @SuppressWarnings("unchecked")
    private static UUID extractProblemId(Map<String, Object> body) {
        if (body == null) return null;
        Object data = body.getOrDefault("data", body);
        if (!(data instanceof Map<?, ?> m)) return null;
        Object id = ((Map<String, Object>) m).get("problem_id");
        if (id == null) return null;
        try {
            return UUID.fromString(id.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private HttpHeaders trustHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Pod-ID", System.getenv("HOSTNAME") != null ? System.getenv("HOSTNAME") : "telemonitoring");
        headers.set("X-Request-ID", UUID.randomUUID().toString());
        try {
            TrustContext ctx = TrustContextHolder.require();
            if (ctx.tenantId() != null) headers.set(TrustContext.H_TENANT_ID, ctx.tenantId().toString());
            if (ctx.actorId() != null) headers.set(TrustContext.H_ACTOR_ID, ctx.actorId());
            headers.set(TrustContext.H_ACTOR_TYPE, ctx.actorType() != null ? ctx.actorType() : "PROVIDER");
            headers.set(TrustContext.H_CORRELATION_ID,
                    ctx.correlationId() != null ? ctx.correlationId().toString() : UUID.randomUUID().toString());
            if (ctx.facilityId() != null) headers.set(TrustContext.H_FACILITY_ID, ctx.facilityId().toString());
            headers.set(TrustContext.H_PURPOSE_OF_USE, ctx.purposeOfUse() != null ? ctx.purposeOfUse() : "TREATMENT");
        } catch (IllegalStateException ignored) {
            headers.set(TrustContext.H_ACTOR_TYPE, "PROVIDER");
            headers.set(TrustContext.H_CORRELATION_ID, UUID.randomUUID().toString());
            headers.set(TrustContext.H_PURPOSE_OF_USE, "TREATMENT");
        }
        return headers;
    }
}
