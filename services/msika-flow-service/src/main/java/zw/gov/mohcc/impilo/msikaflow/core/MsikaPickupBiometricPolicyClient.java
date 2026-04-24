package zw.gov.mohcc.impilo.msikaflow.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.msikaflow.api.dto.PickupClaimTrust;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Optional Tshepo biometric policy gate for regulated marketplace pickup (LOGISTICS / PICKUP_RELEASE).
 */
@Component
public class MsikaPickupBiometricPolicyClient {

    private static final Logger log = LoggerFactory.getLogger(MsikaPickupBiometricPolicyClient.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final boolean enabled;
    private final String tshepoPolicyBaseUrl;

    public MsikaPickupBiometricPolicyClient(
            @Value("${msika-flow.biometric-policy.enabled:false}") boolean enabled,
            @Value("${msika-flow.biometric-policy.tshepo-base-url:http://localhost:8079}") String tshepoPolicyBaseUrl) {
        this.enabled = enabled;
        this.tshepoPolicyBaseUrl = trim(tshepoPolicyBaseUrl);
    }

    /**
     * When enabled, denies the claim if Tshepo returns PROHIBITED or verification is not allowed.
     */
    public void assertPickupClaimAllowed(PickupClaimTrust trust) {
        if (!enabled) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("subjectType", "CLIENT");
        body.put("workflowType", "PICKUP_RELEASE");
        body.put("contextType", "LOGISTICS");
        body.put("biometricIntent", "VERIFY");
        if (trust.actorType() != null && !trust.actorType().isBlank()) {
            body.put("actorType", trust.actorType());
        }
        if (trust.assuranceLevel() != null) {
            body.put("assuranceLevel", trust.assuranceLevel());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(TrustContext.H_TENANT_ID, trust.tenantId().toString());
        headers.set(TrustContext.H_ACTOR_ID, trust.actorId());
        headers.set(TrustContext.H_ACTOR_TYPE, trust.actorType());
        String cid = trust.correlationId();
        if (cid != null && !cid.isBlank()) {
            try {
                headers.set(TrustContext.H_CORRELATION_ID, UUID.fromString(cid.trim()).toString());
            } catch (IllegalArgumentException ex) {
                headers.set(TrustContext.H_CORRELATION_ID, UUID.randomUUID().toString());
            }
        } else {
            headers.set(TrustContext.H_CORRELATION_ID, UUID.randomUUID().toString());
        }
        if (trust.deviceFingerprint() != null && !trust.deviceFingerprint().isBlank()) {
            headers.set(TrustContext.H_DEVICE_FINGERPRINT, trust.deviceFingerprint());
        }

        String url = tshepoPolicyBaseUrl + "/v1/biometric-policy/evaluate";
        try {
            TshepoBiometricPolicyResponse res = restTemplate
                    .postForEntity(url, new HttpEntity<>(body, headers), TshepoBiometricPolicyResponse.class)
                    .getBody();
            if (res == null) {
                throw new IllegalStateException("Tshepo returned empty biometric policy payload");
            }
            if ("PROHIBITED".equalsIgnoreCase(res.policyOutcome()) || !res.verificationAllowed()) {
                throw new IllegalStateException(
                        "Pickup blocked by biometric policy: outcome=" + res.policyOutcome());
            }
        } catch (RestClientException ex) {
            log.warn("Msika pickup biometric policy call failed: {}", ex.getMessage());
            throw new IllegalStateException("Biometric policy service unavailable for pickup", ex);
        }
    }

    private static String trim(String u) {
        if (u == null || u.isBlank()) {
            return "http://localhost:8079";
        }
        return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TshepoBiometricPolicyResponse(
            String policyOutcome,
            boolean enrollmentAllowed,
            boolean verificationAllowed,
            boolean identificationAllowed,
            boolean dedupSupportAllowed,
            boolean fallbackAllowed,
            Long matchedRuleId,
            List<String> reasons) {}
}
