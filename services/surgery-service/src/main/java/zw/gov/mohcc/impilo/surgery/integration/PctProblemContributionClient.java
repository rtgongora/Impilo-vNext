package zw.gov.mohcc.impilo.surgery.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * CONTRIBUTES a surgical condition into PCT's problems list — never a copy of it (component test
 * question 2: contribute, not register). PCT (pct-service) is the SoR for
 * condition/diagnosis/certainty/severity/staging/recurrence; this client calls pct-service's own
 * real {@code POST /v1/problems} with {@code responsible_service=surgery}, the same field the
 * endpoint already accepts with no allow-list (confirmed by reading {@code ProblemService.add}
 * before writing this class — the write API needed no new pct-service surface).
 *
 * <p>Best-effort, mirroring {@code inpatient-service}'s {@code PctTeleconsultClient}: a PCT
 * outage must not block recording the surgical episode locally, which stays the local truth
 * either way. {@code SurgicalEpisodeEntity.pctProblemRef} is null until this succeeds, and
 * {@code idx_surgical_episode_uncontributed} (V002) is the reconciliation query for "not yet".</p>
 *
 * <p>Forwards the CURRENT clinician's trust context, not a service-account identity —
 * pct-service's {@code ClinicalAccessGuard.requireCareRelationship} binds the write to an actor
 * holding an active care context for the patient, which only the real acting clinician has.</p>
 */
@Service
public class PctProblemContributionClient {

    private static final Logger log = LoggerFactory.getLogger(PctProblemContributionClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public PctProblemContributionClient(RestTemplate surgeryRestTemplate,
                                        @Value("${surgery.integration.pct.base-url:http://localhost:8088}") String baseUrl) {
        this.restTemplate = surgeryRestTemplate;
        this.baseUrl = baseUrl;
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
            // The contribution this client exists for: not a copy, a CLAIM of responsibility on
            // PCT's own record.
            payload.put("responsible_service", "surgery");

            ResponseEntity<Map> resp = restTemplate.postForEntity(baseUrl + "/v1/problems",
                    new HttpEntity<>(payload, trustHeaders()), Map.class);
            UUID problemId = extractProblemId(resp.getBody());
            if (problemId != null) {
                log.info("SURGERY: contributed condition to pct_problems id={} subject={}", problemId, subjectCpid);
                return new ContributionResult(problemId, true);
            }
            return ContributionResult.unavailable();
        } catch (RestClientException | IllegalStateException e) {
            log.warn("SURGERY: pct_problems contribution unavailable (best-effort): {}", e.getMessage());
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
        headers.set("X-Pod-ID", System.getenv("HOSTNAME") != null ? System.getenv("HOSTNAME") : "surgery");
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
