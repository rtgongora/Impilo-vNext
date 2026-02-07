package zw.gov.mohcc.impilo.oros.integration;

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

import java.util.Map;
import java.util.Optional;

/**
 * Integration service for the ZIBO Terminology and Classification Service.
 *
 * <p>Validates order and result codes against the national terminology
 * service, and performs code mappings between different coding systems
 * (e.g. LIMS codes to FHIR LOINC codes).</p>
 *
 * <p>All external calls degrade gracefully: if ZIBO is unavailable,
 * validation returns true (optimistic) and mapping returns empty
 * to avoid blocking clinical workflows.</p>
 */
@Service
public class ZiboIntegration {

    private static final Logger log = LoggerFactory.getLogger(ZiboIntegration.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public ZiboIntegration(RestTemplate restTemplate,
                           @Value("${oros.integration.zibo.base-url:http://localhost:8085}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    /**
     * Validate an order code against ZIBO terminology.
     *
     * @param code the order code to validate
     * @return true if the code is valid or ZIBO is unavailable (optimistic), false if explicitly invalid
     */
    @SuppressWarnings("unchecked")
    public boolean validateOrderCode(String code) {
        try {
            String url = baseUrl + "/v1/validate/coding";

            Map<String, String> payload = Map.of(
                    "system", "urn:zibo:order-codes",
                    "code", code
            );

            HttpHeaders headers = buildTrustHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> request = new HttpEntity<>(payload, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Boolean valid = (Boolean) response.getBody().get("valid");
                log.debug("ZIBO order code validation: code={}, valid={}", code, valid);
                return valid != null && valid;
            }

            log.warn("ZIBO returned non-success status {} for order code validation: {}",
                    response.getStatusCode(), code);
            return true; // Optimistic on non-success response

        } catch (RestClientException e) {
            log.warn("ZIBO unavailable for order code validation (code={}), proceeding optimistically: {}",
                    code, e.getMessage());
            return true; // Optimistic degradation
        }
    }

    /**
     * Validate a result code against ZIBO terminology.
     *
     * @param code the result code to validate
     * @return true if the code is valid or ZIBO is unavailable (optimistic), false if explicitly invalid
     */
    @SuppressWarnings("unchecked")
    public boolean validateResultCode(String code) {
        try {
            String url = baseUrl + "/v1/validate/coding";

            Map<String, String> payload = Map.of(
                    "system", "urn:zibo:result-codes",
                    "code", code
            );

            HttpHeaders headers = buildTrustHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> request = new HttpEntity<>(payload, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Boolean valid = (Boolean) response.getBody().get("valid");
                log.debug("ZIBO result code validation: code={}, valid={}", code, valid);
                return valid != null && valid;
            }

            log.warn("ZIBO returned non-success status {} for result code validation: {}",
                    response.getStatusCode(), code);
            return true;

        } catch (RestClientException e) {
            log.warn("ZIBO unavailable for result code validation (code={}), proceeding optimistically: {}",
                    code, e.getMessage());
            return true;
        }
    }

    /**
     * Look up a code mapping from one system to another.
     *
     * @param sourceSystem the source coding system (e.g. "LIMS", "DHIS2")
     * @param sourceCode   the source code
     * @param targetSystem the target coding system (e.g. "LOINC", "SNOMED")
     * @return the mapped code, or empty if no mapping found or ZIBO is unavailable
     */
    @SuppressWarnings("unchecked")
    public Optional<String> lookupMapping(String sourceSystem, String sourceCode, String targetSystem) {
        try {
            String url = baseUrl + "/v1/mappings/translate"
                    + "?sourceSystem=" + sourceSystem
                    + "&sourceCode=" + sourceCode
                    + "&targetSystem=" + targetSystem;

            HttpHeaders headers = buildTrustHeaders();
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url, org.springframework.http.HttpMethod.GET, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String targetCode = (String) response.getBody().get("targetCode");
                if (targetCode != null && !targetCode.isBlank()) {
                    log.debug("ZIBO mapping found: {}:{} -> {}:{}", sourceSystem, sourceCode, targetSystem, targetCode);
                    return Optional.of(targetCode);
                }
            }

            log.debug("ZIBO mapping not found: {}:{} -> {}", sourceSystem, sourceCode, targetSystem);
            return Optional.empty();

        } catch (RestClientException e) {
            log.warn("ZIBO unavailable for mapping lookup ({}:{} -> {}): {}",
                    sourceSystem, sourceCode, targetSystem, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Build HTTP headers with trust context for inter-service communication.
     */
    private HttpHeaders buildTrustHeaders() {
        HttpHeaders headers = new HttpHeaders();
        try {
            TrustContext ctx = TrustContextHolder.require();
            headers.set(TrustContext.H_TENANT_ID, ctx.tenantId().toString());
            headers.set(TrustContext.H_ACTOR_ID, ctx.actorId());
            headers.set(TrustContext.H_CORRELATION_ID, ctx.correlationId().toString());
            if (ctx.facilityId() != null) {
                headers.set(TrustContext.H_FACILITY_ID, ctx.facilityId().toString());
            }
        } catch (IllegalStateException e) {
            log.debug("No trust context available for ZIBO call headers");
        }
        return headers;
    }
}
