package zw.gov.mohcc.impilo.vito.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class VarapiCollaborationClient {

    private static final Logger log = LoggerFactory.getLogger(VarapiCollaborationClient.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final String varapiBaseUrl;

    public VarapiCollaborationClient(
            @Value("${vito.patient-share.varapi-base-url:http://localhost:8083}") String varapiBaseUrl) {
        this.varapiBaseUrl = trimSlash(varapiBaseUrl);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> createExternalProviderProfile(TrustContext ctx, CreateExternalProviderProfilePayload p) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenantId", p.tenantId());
        body.put("vitoAccessGrantRef", p.vitoAccessGrantRef());
        body.put("councilId", p.councilId());
        body.put("councilRegistrationNumber", p.councilRegistrationNumber());
        body.put("givenName", p.givenName());
        body.put("familyName", p.familyName());
        body.put("professionOrCadre", p.professionOrCadre());
        body.put("email", p.email());
        body.put("phone", p.phone());
        body.put("organisationHint", p.organisationHint());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (ctx != null && ctx.tenantId() != null) {
            headers.set(TrustContext.H_TENANT_ID, ctx.tenantId().toString());
        }
        if (ctx != null) {
            headers.set(TrustContext.H_CORRELATION_ID, ctx.correlationId().toString());
        }

        String url = varapiBaseUrl + "/v1/internal/collaboration/external-providers";
        try {
            Map<?, ?> res = restTemplate.postForObject(url, new HttpEntity<>(body, headers), Map.class);
            Map<String, Object> out = new LinkedHashMap<>();
            if (res != null) {
                res.forEach((k, v) -> out.put(String.valueOf(k), v));
            }
            return out;
        } catch (Exception ex) {
            log.warn("Varapi external provider profile create failed: {}", ex.getMessage());
            throw new IllegalStateException("Varapi collaboration call failed", ex);
        }
    }

    /**
     * Lists active councils for a tenant (council picker). Varapi internal API.
     */
    public List<Map<String, Object>> listCouncils(TrustContext ctx, UUID tenantId) {
        String url = varapiBaseUrl + "/v1/internal/collaboration/councils?tenantId=" + tenantId;

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (ctx != null && ctx.tenantId() != null) {
            headers.set(TrustContext.H_TENANT_ID, ctx.tenantId().toString());
        }
        if (ctx != null) {
            headers.set(TrustContext.H_CORRELATION_ID, ctx.correlationId().toString());
        }

        try {
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            List<Map<String, Object>> body = response.getBody();
            return body != null ? body : List.of();
        } catch (Exception ex) {
            log.warn("Varapi councils list failed: {}", ex.getMessage());
            throw new IllegalStateException("Varapi collaboration councils call failed", ex);
        }
    }

    private static String trimSlash(String base) {
        if (base == null || base.isBlank()) {
            return "http://localhost:8083";
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    public record CreateExternalProviderProfilePayload(
            UUID tenantId,
            String vitoAccessGrantRef,
            long councilId,
            String councilRegistrationNumber,
            String givenName,
            String familyName,
            String professionOrCadre,
            String email,
            String phone,
            String organisationHint) {}
}
