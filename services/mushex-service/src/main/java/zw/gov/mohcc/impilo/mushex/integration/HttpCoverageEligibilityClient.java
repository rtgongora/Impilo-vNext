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

@Component
public class HttpCoverageEligibilityClient implements CoverageEligibilityClient {

    private static final Logger log = LoggerFactory.getLogger(HttpCoverageEligibilityClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public HttpCoverageEligibilityClient(
            RestClient.Builder restClientBuilder,
            @Value("${impilo.services.coverage-base-url:http://localhost:8140}") String baseUrl,
            ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean checkEligibility(UUID tenantId, String patientCpid, String planCode, String serviceCode) {
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
            log.info("Coverage eligibility check tenantId={} patientCpid={} planCode={} eligible={}",
                    tenantId, patientCpid, planCode, eligible);
            return eligible;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Coverage eligibility call failed tenantId={} patientCpid={}", tenantId, patientCpid, e);
            throw new IllegalStateException("Coverage eligibility service call failed: " + e.getMessage(), e);
        }
    }
}
