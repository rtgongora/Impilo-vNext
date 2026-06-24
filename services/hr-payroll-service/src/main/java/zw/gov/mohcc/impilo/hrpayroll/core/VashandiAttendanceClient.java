package zw.gov.mohcc.impilo.hrpayroll.core;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads derived worked-hours/overtime from Vashandi — the system-of-record for workforce
 * attendance (real check-in/out events). Payroll consumes this instead of a separately-
 * entered attendance table (kills the double-entry). The worker is identified by
 * provider-worker-id (the Employee.providerId ↔ WorkforceProfile.providerWorkerId bridge).
 */
@Component
public class VashandiAttendanceClient {

    private static final Logger log = LoggerFactory.getLogger(VashandiAttendanceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public VashandiAttendanceClient(@Value("${vashandi.base-url:http://localhost:8167}") String baseUrl) {
        this.baseUrl = baseUrl;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(3).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(5).toMillis());
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * Overtime hours for the worker in the given month, or empty when the worker is not
     * mapped to a Vashandi profile or Vashandi is unavailable — the caller decides the
     * fail-policy (it must not silently treat "unknown" as a confirmed zero).
     */
    public Optional<BigDecimal> overtimeHours(UUID tenantId, String providerWorkerId, int year, int month) {
        if (providerWorkerId == null || providerWorkerId.isBlank()) {
            return Optional.empty();
        }
        String url = baseUrl + "/v1/internal/vashandi/attendance/hours-summary"
                + "?provider_worker_id={pwid}&year={year}&month={month}";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-ID", tenantId.toString());
        headers.set("X-Request-ID", UUID.randomUUID().toString());
        headers.set("X-Correlation-ID", UUID.randomUUID().toString());
        headers.set("X-Access-Mode", "INTERNAL");
        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class,
                    providerWorkerId, year, month);
            JsonNode body = response.getBody();
            if (body != null && body.hasNonNull("overtimeHours")) {
                return Optional.of(new BigDecimal(body.get("overtimeHours").asText()));
            }
            return Optional.of(BigDecimal.ZERO);
        } catch (HttpClientErrorException.NotFound e) {
            // No Vashandi profile maps this worker — unmapped, not a confirmed zero.
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Vashandi attendance unavailable for provider={} period={}-{}: {}",
                    providerWorkerId, year, month, e.getMessage());
            return Optional.empty();
        }
    }
}
