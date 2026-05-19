package zw.gov.mohcc.impilo.varapi.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.config.VarapiProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Optional Tshepo policy evaluation for council-regulated workflows.
 * When disabled, all evaluated actions are allowed (development / bootstrap).
 */
@Component
public class CouncilRegulatoryPolicyClient {

    private static final Logger log = LoggerFactory.getLogger(CouncilRegulatoryPolicyClient.class);

    private final RestTemplate restTemplate;
    private final VarapiProperties varapiProperties;

    public CouncilRegulatoryPolicyClient(RestTemplate restTemplate, VarapiProperties varapiProperties) {
        this.restTemplate = restTemplate;
        this.varapiProperties = varapiProperties;
    }

    public boolean isPolicyEnabled() {
        return varapiProperties.getCouncilRegulatory().isPolicyEnabled();
    }

    /**
     * @param workflowAction e.g. SUBMIT_APPLICATION, CREATE_OBLIGATION, RECORD_REVIEW, ACCEPT_FUNDO_CPD
     * @return true when allowed or policy integration is off / unreachable (logged).
     */
    public boolean isAllowed(String workflowAction, Long councilId, Long applicationId, Long providerId) {
        if (!isPolicyEnabled()) {
            return true;
        }
        TrustContext ctx = TrustContextHolder.require();
        String base = trim(varapiProperties.getCouncilRegulatory().getTshepoPolicyBaseUrl());
        String url = base + "/v1/council-regulatory/evaluate";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("workflowAction", workflowAction);
        if (councilId != null) {
            body.put("councilId", councilId);
        }
        if (applicationId != null) {
            body.put("applicationId", applicationId);
        }
        if (providerId != null) {
            body.put("providerId", providerId);
        }
        if (ctx.actorId() != null) {
            body.put("actorId", ctx.actorId());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (ctx.tenantId() != null) {
            headers.set(TrustContext.H_TENANT_ID, ctx.tenantId().toString());
        }
        if (ctx.correlationId() != null) {
            headers.set(TrustContext.H_CORRELATION_ID, ctx.correlationId().toString());
        }
        if (ctx.actorId() != null) {
            headers.set(TrustContext.H_ACTOR_ID, ctx.actorId());
        }
        if (ctx.actorType() != null) {
            headers.set(TrustContext.H_ACTOR_TYPE, ctx.actorType());
        }

        boolean denyWhenUnreachable = varapiProperties.getCouncilRegulatory().isPolicyDenyWhenUnreachable();
        try {
            CouncilPolicyResponse resp = restTemplate.postForObject(
                    url, new HttpEntity<>(body, headers), CouncilPolicyResponse.class);
            if (resp == null) {
                log.warn("Tshepo council policy returned empty body for action={}", workflowAction);
                return !denyWhenUnreachable;
            }
            return resp.isAllowed();
        } catch (RestClientException e) {
            log.warn("Tshepo council policy call failed for action={}: {}", workflowAction, e.getMessage());
            return !denyWhenUnreachable;
        }
    }

    public static final class CouncilPolicyResponse {
        private boolean allowed = true;
        private String outcome;

        public boolean isAllowed() {
            return allowed;
        }

        public void setAllowed(boolean allowed) {
            this.allowed = allowed;
        }

        public String getOutcome() {
            return outcome;
        }

        public void setOutcome(String outcome) {
            this.outcome = outcome;
        }
    }

    private static String trim(String u) {
        if (u == null || u.isBlank()) {
            return "http://localhost:8081";
        }
        return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
    }
}
