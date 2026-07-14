package zw.gov.mohcc.impilo.daidzai.integration;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Daidzai → credential-verification seam: issues the Ed25519-signed
 * {@code MEDICAL_CASE_ATTESTATION} for a verified assistance case. The payload is
 * ANONYMISED — subject is the case reference, never a person: donors can verify the
 * case is genuine via the public verify endpoint without learning who it is for.
 * Attestation FAILS CLOSED — a case never flips to verified without a signed artifact.
 */
@Component
public class CredentialAttestationClient {

    private static final Logger log = LoggerFactory.getLogger(CredentialAttestationClient.class);

    private final RestClient restClient;
    private final String baseUrl;

    public CredentialAttestationClient(
            @Value("${daidzai.credential.base-url:http://credential-verification-service:8094}") String baseUrl,
            @Value("${daidzai.credential.timeout-ms:5000}") int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(Math.min(timeoutMs, 3000)));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.restClient = RestClient.builder().requestFactory(factory).build();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public record CaseAttestation(String credentialId, String verificationUrl) {}

    public CaseAttestation issueCaseAttestation(UUID tenantId, String assistanceReference,
                                                String category, String verifyingOrgRef,
                                                String attestingActorId) {
        Map<String, Object> body = new HashMap<>();
        body.put("subjectType", "ASSISTANCE_CASE");
        body.put("subjectId", assistanceReference);
        // Anonymised presentation: the subject NAME is the case reference, never a person.
        body.put("subjectName", "Medical assistance case " + assistanceReference);
        body.put("credentialType", "MEDICAL_CASE_ATTESTATION");
        body.put("title", "Verified medical assistance case (" + category + ")");
        body.put("issuedBy", verifyingOrgRef);
        body.put("validFrom", LocalDate.now().toString());
        body.put("metadata", Map.of(
                "caseReference", assistanceReference,
                "category", category,
                "attestedByActor", attestingActorId == null ? "" : attestingActorId,
                "anonymised", true));

        JsonNode created = restClient.post()
                .uri(baseUrl + "/v1/internal/credentials")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    headers.set("X-Tenant-ID", tenantId.toString());
                    headers.set("X-Pod-ID", "national-spine");
                    headers.set("X-Request-ID", UUID.randomUUID().toString());
                    headers.set("X-Correlation-ID", UUID.randomUUID().toString());
                    headers.set("Idempotency-Key", UUID.randomUUID().toString());
                    String bearer = currentBearer();
                    if (bearer != null) headers.set(HttpHeaders.AUTHORIZATION, bearer);
                })
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        if (created == null || !created.hasNonNull("credentialId")) {
            throw new IllegalStateException("credential attestation issuance returned no credentialId");
        }
        return new CaseAttestation(
                created.get("credentialId").asText(),
                created.path("verificationUrl").asText(null));
    }

    private static String currentBearer() {
        try {
            RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes servletAttrs) {
                HttpServletRequest request = servletAttrs.getRequest();
                return request.getHeader(HttpHeaders.AUTHORIZATION);
            }
        } catch (Exception ignored) {
            // Outside a request — proceed without a bearer.
        }
        return null;
    }
}
