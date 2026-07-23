package zw.gov.mohcc.impilo.msikaflow.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import zw.gov.mohcc.impilo.msikaflow.api.TrustHeaderExtractor;

import java.util.Locale;
import java.util.Optional;

/**
 * VASHANDI workforce-registry client — eligibility precondition 4 (§4.3):
 * "a licence alone is never facility authority" — the acting professional must
 * hold a current employment/affiliation binding to the fulfilment premises.
 *
 * <p>Returns {@code Optional.empty()} on transport failure so
 * {@code EligibilityService} records the precondition as UNVERIFIED and FAILS
 * CLOSED — an unreachable registry never silently passes.</p>
 */
@Service
public class VashandiClient {

    private static final Logger log = LoggerFactory.getLogger(VashandiClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public VashandiClient(ObjectMapper objectMapper,
                          @Value("${msika-flow.integration.vashandi-url:http://vashandi-workforce-service:8167}") String vashandiBaseUrl) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(vashandiBaseUrl.replaceAll("/$", ""))
                .build();
    }

    /**
     * GET /v1/internal/vashandi/assignments and look for an active assignment
     * binding {@code workforceProfileRef} to {@code facilityRef}.
     *
     * @return present true/false when the registry answered; empty on failure (UNVERIFIED).
     */
    public Optional<Boolean> hasActiveAssignment(String workforceProfileRef, String facilityRef,
                                                 HttpServletRequest inbound) {
        if (workforceProfileRef == null || workforceProfileRef.isBlank()
                || facilityRef == null || facilityRef.isBlank()) {
            return Optional.of(Boolean.FALSE);
        }
        try {
            ResponseEntity<String> response = restClient.get()
                    .uri("/v1/internal/vashandi/assignments")
                    .headers(h -> copyTrustHeaders(inbound, h))
                    .retrieve()
                    .toEntity(String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("VASHANDI assignments lookup failed: HTTP {}", response.getStatusCode());
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode items = root.isArray() ? root : root.path("data");
            if (!items.isArray()) {
                return Optional.empty();
            }
            for (JsonNode item : items) {
                String status = item.path("status").asText("").toLowerCase(Locale.ROOT);
                boolean active = status.equals("active");
                boolean profileMatch = workforceProfileRef.equalsIgnoreCase(item.path("workforceProfileId").asText(""));
                boolean facilityMatch = facilityRef.equalsIgnoreCase(item.path("facilityId").asText(""));
                if (active && profileMatch && facilityMatch) {
                    return Optional.of(Boolean.TRUE);
                }
            }
            return Optional.of(Boolean.FALSE);
        } catch (Exception e) {
            log.warn("VASHANDI assignments lookup failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static void copyTrustHeaders(HttpServletRequest inbound, HttpHeaders target) {
        if (inbound != null) {
            copyIfPresent(inbound, target, TrustHeaderExtractor.H_TENANT_ID);
            copyIfPresent(inbound, target, TrustHeaderExtractor.H_ACTOR_ID);
            copyIfPresent(inbound, target, TrustHeaderExtractor.H_ACTOR_TYPE);
            copyIfPresent(inbound, target, TrustHeaderExtractor.H_PURPOSE_OF_USE);
            copyIfPresent(inbound, target, TrustHeaderExtractor.H_CORRELATION_ID);
            copyIfPresent(inbound, target, TrustHeaderExtractor.H_FACILITY_ID);
            copyIfPresent(inbound, target, "Authorization");
        }
        String pod = inbound != null ? inbound.getHeader("X-Pod-ID") : null;
        target.add("X-Pod-ID", pod != null && !pod.isBlank() ? pod : "national-spine");
        target.add("X-Request-ID", java.util.UUID.randomUUID().toString());
        target.add("x-envoy-internal", "true");
    }

    private static void copyIfPresent(HttpServletRequest inbound, HttpHeaders target, String name) {
        String v = inbound.getHeader(name);
        if (v != null && !v.isBlank()) {
            target.add(name, v);
        }
    }
}
