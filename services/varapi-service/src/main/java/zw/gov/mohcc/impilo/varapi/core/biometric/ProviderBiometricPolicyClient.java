package zw.gov.mohcc.impilo.varapi.core.biometric;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
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

@Component
public class ProviderBiometricPolicyClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final boolean policyEnabled;
    private final String tshepoPolicyBaseUrl;

    public ProviderBiometricPolicyClient(
            @Value("${varapi.biometric.policy-enabled:true}") boolean policyEnabled,
            @Value("${varapi.biometric.tshepo-policy-base-url:http://localhost:8081}") String tshepoPolicyBaseUrl) {
        this.policyEnabled = policyEnabled;
        this.tshepoPolicyBaseUrl = trim(tshepoPolicyBaseUrl);
    }

    public TshepoBiometricPolicyResponse evaluate(
            String subjectType, String workflowType, String contextType, String modality, String intent) {
        if (!policyEnabled) {
            return new TshepoBiometricPolicyResponse(
                    "ALLOWED",
                    true,
                    true,
                    true,
                    true,
                    true,
                    null,
                    List.of("varapi.biometric.policy-enabled=false — skipped"));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("subjectType", subjectType);
        body.put("workflowType", workflowType);
        body.put("contextType", contextType);
        body.put("biometricIntent", intent);
        if (modality != null && !modality.isBlank()) {
            body.put("modality", modality);
        }
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
        String url = tshepoPolicyBaseUrl + "/v1/biometric-policy/evaluate";
        return restTemplate.postForEntity(url, new HttpEntity<>(body, headers), TshepoBiometricPolicyResponse.class)
                .getBody();
    }

    private static String trim(String u) {
        if (u == null || u.isBlank()) {
            return "http://localhost:8081";
        }
        return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
    }

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
