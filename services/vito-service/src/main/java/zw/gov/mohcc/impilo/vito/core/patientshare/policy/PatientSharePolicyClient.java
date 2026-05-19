package zw.gov.mohcc.impilo.vito.core.patientshare.policy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class PatientSharePolicyClient {

    private static final Logger log = LoggerFactory.getLogger(PatientSharePolicyClient.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final boolean policyEnabled;
    private final String tshepoPolicyBaseUrl;

    public PatientSharePolicyClient(
            @Value("${vito.patient-share.policy-enabled:true}") boolean policyEnabled,
            @Value("${vito.patient-share.tshepo-policy-base-url:http://localhost:8081}") String tshepoPolicyBaseUrl) {
        this.policyEnabled = policyEnabled;
        this.tshepoPolicyBaseUrl = trimSlash(tshepoPolicyBaseUrl);
    }

    public PatientSharePolicyEvaluationResponse evaluate(TrustContext ctx, PatientSharePolicyEvaluateRequest request) {
        if (!policyEnabled) {
            return new PatientSharePolicyEvaluationResponse(
                    "ALLOWED",
                    true,
                    true,
                    true,
                    false,
                    null,
                    List.of("vito.patient-share.policy-enabled=false — Tshepo evaluation skipped (development only)"));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("workflowAction", request.workflowAction());
        body.put("scopeType", request.scopeType());
        body.put("actorType", request.actorType());
        body.put("trustLevel", request.trustLevel());
        body.put("councilRegistrationSupplied", request.councilRegistrationSupplied());
        body.put("councilRegistrationMatched", request.councilRegistrationMatched());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (ctx != null) {
            if (ctx.tenantId() != null) {
                headers.set(TrustContext.H_TENANT_ID, ctx.tenantId().toString());
            }
            headers.set(TrustContext.H_CORRELATION_ID, ctx.correlationId().toString());
            if (ctx.actorId() != null) {
                headers.set(TrustContext.H_ACTOR_ID, ctx.actorId());
            }
            if (ctx.actorType() != null) {
                headers.set(TrustContext.H_ACTOR_TYPE, ctx.actorType());
            }
            if (ctx.purposeOfUse() != null) {
                headers.set(TrustContext.H_PURPOSE_OF_USE, ctx.purposeOfUse());
            }
        }

        String url = tshepoPolicyBaseUrl + "/v1/patient-share-policy/evaluate";
        try {
            return restTemplate.postForEntity(
                            url,
                            new HttpEntity<>(body, headers),
                            PatientSharePolicyEvaluationResponse.class)
                    .getBody();
        } catch (RestClientException ex) {
            log.warn("Tshepo patient-share policy call failed: {}", ex.getMessage());
            throw ex;
        }
    }

    private static String trimSlash(String base) {
        if (base == null || base.isBlank()) {
            return "http://localhost:8081";
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}
