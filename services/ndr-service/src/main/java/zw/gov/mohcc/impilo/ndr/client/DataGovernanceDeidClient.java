package zw.gov.mohcc.impilo.ndr.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Client for the data-governance-service de-identification engine
 * ({@code POST /internal/v1/deid/runs}). The NDR release zone pushes CPID-keyed
 * gold rows here and receives back tokenised, stripped, generalised, k-anon,
 * policy-gated rows plus a release manifest — this is the seam that turns the
 * NDR "de-identified" claim into an enforced transform rather than a label.
 *
 * <p>Fails loud: if the de-id engine is unreachable, throws
 * {@link DeidUnavailableException}. The engine itself is deny-by-default — no
 * ACTIVE release policy ⇒ the run returns REJECTED and nothing is released.</p>
 */
@Component
public class DataGovernanceDeidClient {

    private static final Logger log = LoggerFactory.getLogger(DataGovernanceDeidClient.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String deidBaseUrl;

    public DataGovernanceDeidClient(RestTemplate restTemplate,
                                    ObjectMapper objectMapper,
                                    @Value("${ndr.deid.base-url:http://localhost:8220}") String deidBaseUrl) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.deidBaseUrl = deidBaseUrl;
    }

    public DeidRunResult requestRun(String datasetCode, String requestedBy,
                                    List<Map<String, Object>> sourceRows,
                                    String tenantId, String podId, String correlationId) {
        String url = deidBaseUrl + "/internal/v1/deid/runs";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dataset_code", datasetCode);
        body.put("requested_by", requestedBy);
        body.put("source_rows", sourceRows);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Tenant-ID", tenantId);
        headers.set("X-Pod-ID", podId != null ? podId : "national-spine");
        headers.set("X-Request-ID", UUID.randomUUID().toString());
        headers.set("X-Correlation-ID", correlationId != null ? correlationId : UUID.randomUUID().toString());
        headers.set("Idempotency-Key", "ndr-release-" + UUID.randomUUID());

        try {
            String requestBody = objectMapper.writeValueAsString(body);
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            return parse(objectMapper.readTree(response.getBody()));
        } catch (RestClientException e) {
            log.error("De-identification engine unreachable at {}: {}", url, e.getMessage());
            throw new DeidUnavailableException("De-identification engine unreachable: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to parse de-identification run response: {}", e.getMessage());
            throw new DeidUnavailableException("Failed to parse de-identification response: " + e.getMessage(), e);
        }
    }

    private DeidRunResult parse(JsonNode node) {
        UUID runId = optionalUuid(node, "run_id");
        UUID manifestId = optionalUuid(node, "manifest_id");
        List<Map<String, Object>> releasedRows = new ArrayList<>();
        JsonNode rows = node.get("released_rows");
        if (rows != null && rows.isArray()) {
            for (JsonNode row : rows) {
                releasedRows.add(objectMapper.convertValue(row, LinkedHashMap.class));
            }
        }
        return new DeidRunResult(
                text(node, "status"),
                runId,
                manifestId,
                text(node, "policy_version"),
                intVal(node, "rows_in"),
                intVal(node, "rows_out"),
                intVal(node, "rows_suppressed"),
                text(node, "rejection_reason"),
                releasedRows);
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    private static int intVal(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asInt() : 0;
    }

    private static UUID optionalUuid(JsonNode node, String field) {
        if (!node.hasNonNull(field)) return null;
        try {
            return UUID.fromString(node.get(field).asText());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Outcome of a de-identification run; {@code releasedRows} is empty unless RELEASED. */
    public record DeidRunResult(
            String status,
            UUID runId,
            UUID manifestId,
            String policyVersion,
            int rowsIn,
            int rowsOut,
            int rowsSuppressed,
            String rejectionReason,
            List<Map<String, Object>> releasedRows
    ) {
        public boolean isReleased() {
            return "RELEASED".equals(status);
        }
    }

    public static class DeidUnavailableException extends RuntimeException {
        public DeidUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
