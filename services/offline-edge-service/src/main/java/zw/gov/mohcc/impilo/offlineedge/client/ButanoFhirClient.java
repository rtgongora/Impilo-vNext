package zw.gov.mohcc.impilo.offlineedge.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Client for replaying offline vitals to BUTANO (HAPI FHIR) as Observations.
 *
 * <p>During offline replay, captured vitals are converted to FHIR Observation
 * resources and POST'd to BUTANO's FHIR endpoint. BUTANO stores them using
 * CPID (no PII) as required by the architecture.</p>
 *
 * <p>Conflict detection: if BUTANO returns 409 (duplicate Observation by
 * business key), the action is marked as CONFLICT for manual review.</p>
 */
@Component
public class ButanoFhirClient {

    private static final Logger log = LoggerFactory.getLogger(ButanoFhirClient.class);

    private final RestTemplate restTemplate;
    private final String butanoBaseUrl;
    private final ObjectMapper objectMapper;

    public ButanoFhirClient(ObjectMapper objectMapper,
                             @Value("${impilo.offline.butano-base-url:http://localhost:8090}") String butanoBaseUrl) {
        this.restTemplate = new RestTemplate();
        this.butanoBaseUrl = butanoBaseUrl.endsWith("/")
                ? butanoBaseUrl.substring(0, butanoBaseUrl.length() - 1)
                : butanoBaseUrl;
        this.objectMapper = objectMapper;
    }

    /**
     * Post a FHIR Observation to BUTANO for a captured vital sign.
     *
     * @param patientCpid    the CPID (Clinical Patient ID) — no PII
     * @param observationPayload the vital sign data (code, value, unit, effectiveDateTime)
     * @param tenantId       tenant context
     * @param correlationId  correlation ID for tracing
     * @return the result of the FHIR POST
     */
    public FhirReplayResult postObservation(String patientCpid, Map<String, Object> observationPayload,
                                             String tenantId, String correlationId) {
        try {
            // Build a FHIR Observation resource
            Map<String, Object> observation = buildFhirObservation(patientCpid, observationPayload);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Tenant-ID", tenantId);
            headers.set("X-Correlation-ID", correlationId);

            String body = objectMapper.writeValueAsString(observation);
            HttpEntity<String> request = new HttpEntity<>(body, headers);

            String url = butanoBaseUrl + "/fhir/Observation";
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("BUTANO FHIR Observation created for CPID={}, correlation={}", patientCpid, correlationId);
                return FhirReplayResult.success(response.getBody());
            } else {
                log.warn("BUTANO returned unexpected status {} for CPID={}", response.getStatusCode(), patientCpid);
                return FhirReplayResult.failure("Unexpected status: " + response.getStatusCode());
            }

        } catch (HttpClientErrorException.Conflict e) {
            log.warn("BUTANO FHIR Observation conflict for CPID={}: {}", patientCpid, e.getMessage());
            return FhirReplayResult.conflict("Duplicate observation detected: " + e.getMessage());
        } catch (Exception e) {
            log.error("Failed to post Observation to BUTANO for CPID={}: {}", patientCpid, e.getMessage(), e);
            return FhirReplayResult.failure("BUTANO call failed: " + e.getMessage());
        }
    }

    private Map<String, Object> buildFhirObservation(String patientCpid, Map<String, Object> payload) {
        return Map.of(
                "resourceType", "Observation",
                "status", "final",
                "subject", Map.of("reference", "Patient/" + patientCpid),
                "code", Map.of(
                        "coding", java.util.List.of(Map.of(
                                "system", payload.getOrDefault("code_system", "http://loinc.org"),
                                "code", payload.getOrDefault("code", ""),
                                "display", payload.getOrDefault("display", "")
                        ))
                ),
                "valueQuantity", Map.of(
                        "value", payload.getOrDefault("value", 0),
                        "unit", payload.getOrDefault("unit", ""),
                        "system", "http://unitsofmeasure.org"
                ),
                "effectiveDateTime", payload.getOrDefault("effective_date_time", ""),
                "meta", Map.of(
                        "tag", java.util.List.of(Map.of(
                                "system", "http://impilo.health/tags",
                                "code", "offline-captured",
                                "display", "Captured during offline session"
                        ))
                )
        );
    }

    /**
     * Result of a FHIR replay attempt.
     */
    public record FhirReplayResult(Status status, String detail) {
        public enum Status { SUCCESS, CONFLICT, FAILURE }

        public static FhirReplayResult success(String detail) { return new FhirReplayResult(Status.SUCCESS, detail); }
        public static FhirReplayResult conflict(String detail) { return new FhirReplayResult(Status.CONFLICT, detail); }
        public static FhirReplayResult failure(String detail) { return new FhirReplayResult(Status.FAILURE, detail); }

        public boolean isSuccess() { return status == Status.SUCCESS; }
        public boolean isConflict() { return status == Status.CONFLICT; }
    }
}
