package zw.gov.mohcc.impilo.mushex.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * HTTP integration with coverage-service for eligibility checks.
 */
@Component
public class CoverageIntegrationClient implements CoverageEligibilityClient {

    private static final Logger log = LoggerFactory.getLogger(CoverageIntegrationClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public CoverageIntegrationClient(
            RestClient.Builder restClientBuilder,
            @Value("${impilo.services.coverage-base-url:http://localhost:8140}") String baseUrl,
            ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
    }

    @Override
    public EligibilityResult evaluateEligibility(UUID tenantId, String patientCpid, String planCode, String serviceCode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("patient_cpid", patientCpid);
        body.put("plan_code", planCode);
        body.put("service_code", serviceCode != null ? serviceCode : "");

        try {
            String raw = restClient.post()
                    .uri("/internal/v1/eligibility/check")
                    .header("X-Tenant-ID", tenantId.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            if (raw == null || raw.isBlank()) {
                throw new IllegalStateException("Empty eligibility response");
            }
            JsonNode node = objectMapper.readTree(raw);
            boolean eligible = node.path("eligible").asBoolean(false);
            String reason = textOr(node, "reason", "reasonCode", "reason_code");
            if (reason == null || reason.isBlank()) {
                reason = eligible ? "ELIGIBLE" : "COVERAGE_INELIGIBLE";
            }
            String ref = null;
            if (eligible) {
                String fromBody = textOr(node, "coverage_reference", "coverageReference");
                ref = fromBody != null && !fromBody.isBlank() ? fromBody : UUID.randomUUID().toString();
            }
            log.info("Coverage eligibility tenantId={} patientCpid={} planCode={} eligible={} reason={} coverageReference={}",
                    tenantId, patientCpid, planCode, eligible, reason, ref);
            return new EligibilityResult(eligible, reason, ref);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Coverage eligibility call failed tenantId={} patientCpid={}", tenantId, patientCpid, e);
            throw new IllegalStateException("Coverage eligibility service call failed: " + e.getMessage(), e);
        }
    }

    private static String textOr(JsonNode node, String... names) {
        for (String n : names) {
            if (node.hasNonNull(n) && !node.get(n).asText().isBlank()) {
                return node.get(n).asText();
            }
        }
        return null;
    }
}
