package zw.gov.mohcc.impilo.vito.core.biometric.policy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import jakarta.servlet.http.HttpServletRequest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Calls Tshepo biometric policy evaluation with the current {@link TrustContext} headers.
 */
@Component
public class BiometricPolicyClient {

    private static final Logger log = LoggerFactory.getLogger(BiometricPolicyClient.class);

    private final RestTemplate restTemplate = new RestTemplate();

    private final boolean policyEnabled;
    private final String tshepoPolicyBaseUrl;

    public BiometricPolicyClient(
            @Value("${vito.biometric.policy-enabled:true}") boolean policyEnabled,
            @Value("${vito.biometric.tshepo-policy-base-url:http://localhost:8079}") String tshepoPolicyBaseUrl) {
        this.policyEnabled = policyEnabled;
        this.tshepoPolicyBaseUrl = trimSlash(tshepoPolicyBaseUrl);
    }

    public TshepoBiometricPolicyResponse evaluate(
            String subjectType,
            String workflowType,
            String contextType,
            String modality,
            String biometricIntent) {
        if (!policyEnabled) {
            return new TshepoBiometricPolicyResponse(
                    "ALLOWED",
                    true,
                    true,
                    true,
                    true,
                    true,
                    null,
                    List.of("vito.biometric.policy-enabled=false — Tshepo evaluation skipped (development only)"));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("subjectType", subjectType);
        body.put("workflowType", workflowType);
        body.put("contextType", contextType);
        if (modality != null && !modality.isBlank()) {
            body.put("modality", modality);
        }
        body.put("biometricIntent", biometricIntent);

        TrustContext ctx = TrustContextHolder.require();
        if (ctx.actorType() != null && !ctx.actorType().isBlank()) {
            body.put("actorType", ctx.actorType());
        }
        Integer assurance = parseAssuranceFromInboundRequest();
        if (assurance != null) {
            body.put("assuranceLevel", assurance);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(TrustContext.H_TENANT_ID, ctx.tenantId().toString());
        headers.set(TrustContext.H_CORRELATION_ID, ctx.correlationId().toString());
        headers.set(TrustContext.H_ACTOR_ID, ctx.actorId());
        headers.set(TrustContext.H_ACTOR_TYPE, ctx.actorType());
        if (ctx.purposeOfUse() != null) {
            headers.set(TrustContext.H_PURPOSE_OF_USE, ctx.purposeOfUse());
        }
        if (ctx.facilityId() != null) {
            headers.set(TrustContext.H_FACILITY_ID, ctx.facilityId().toString());
        }
        if (ctx.workspaceId() != null) {
            headers.set(TrustContext.H_WORKSPACE_ID, ctx.workspaceId().toString());
        }

        String url = tshepoPolicyBaseUrl + "/v1/biometric-policy/evaluate";
        try {
            return restTemplate.postForEntity(
                            url,
                            new HttpEntity<>(body, headers),
                            TshepoBiometricPolicyResponse.class)
                    .getBody();
        } catch (RestClientException ex) {
            log.warn("Tshepo biometric policy call failed: {}", ex.getMessage());
            throw ex;
        }
    }

    private static String trimSlash(String base) {
        if (base == null || base.isBlank()) {
            return "http://localhost:8079";
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    /**
     * Reads {@code X-Assurance-Level} when the request is bound (LOA3 → 3).
     */
    private static Integer parseAssuranceFromInboundRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes sra)) {
            return null;
        }
        HttpServletRequest req = sra.getRequest();
        String raw = req.getHeader("X-Assurance-Level");
        if (raw == null || raw.isBlank()) {
            raw = req.getHeader("x-assurance-level");
        }
        if (raw == null || raw.isBlank()) {
            return null;
        }
        raw = raw.trim();
        if (raw.toUpperCase(Locale.ROOT).startsWith("LOA")) {
            try {
                return Integer.parseInt(raw.substring(3).trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
