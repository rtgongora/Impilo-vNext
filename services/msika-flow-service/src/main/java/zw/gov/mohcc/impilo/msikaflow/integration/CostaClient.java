package zw.gov.mohcc.impilo.msikaflow.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import zw.gov.mohcc.impilo.msikaflow.api.TrustHeaderExtractor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * COSTA charge-construction client (OF-B9, §10.4 — "COSTA supplies the charge").
 *
 * <p>Calls {@code POST /costa/v1/estimate} with the offer's coded lines and
 * returns the standard charge per line plus the total. COSTA is the ONLY
 * charge authority here — msika-flow never computes a tariff itself (money-stack
 * doctrine: Coverage decides · COSTA prices · MUSHEX pays).</p>
 *
 * <p>Degradation contract: any transport/parse failure returns
 * {@code Optional.empty()} so the caller marks the offer's liability
 * {@code UNVERIFIED} (display truth) and commitment step 7 FAILS CLOSED for
 * payer-covered flows (§10.4/§10.5).</p>
 */
@Service
public class CostaClient {

    private static final Logger log = LoggerFactory.getLogger(CostaClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    /** One coded line to price ({@code msikaCode} = the offer line's item code). */
    public record ChargeLine(String itemCode, int quantity) {}

    /** COSTA's answer: the total standard charge plus the per-line decomposition. */
    public record CostaCharge(BigDecimal totalCharge, Map<String, BigDecimal> lineCharges) {}

    public CostaClient(ObjectMapper objectMapper,
                       @Value("${msika-flow.integration.costa-url:http://localhost:8101}") String costaBaseUrl) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(costaBaseUrl.replaceAll("/$", ""))
                .build();
    }

    /**
     * POST /costa/v1/estimate — constructs the standard charge for the coded
     * lines (kind=PRODUCT, tariff cost method server-side default).
     */
    public Optional<CostaCharge> chargeForLines(List<ChargeLine> lines, HttpServletRequest inbound) {
        if (lines == null || lines.isEmpty()) {
            return Optional.empty();
        }
        try {
            List<Map<String, Object>> items = new ArrayList<>();
            for (ChargeLine line : lines) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("msikaCode", line.itemCode());
                item.put("kind", "PRODUCT");
                item.put("qty", BigDecimal.valueOf(line.quantity()));
                items.add(item);
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("items", items);

            ResponseEntity<String> response = restClient.post()
                    .uri("/costa/v1/estimate")
                    .headers(h -> copyTrustHeaders(inbound, h))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toEntity(String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("COSTA estimate failed: HTTP {}", response.getStatusCode());
                return Optional.empty();
            }
            JsonNode data = objectMapper.readTree(response.getBody()).path("data");
            JsonNode total = data.path("totalCharge");
            if (!total.isNumber() && !total.isTextual()) {
                log.warn("COSTA estimate response missing totalCharge");
                return Optional.empty();
            }
            Map<String, BigDecimal> lineCharges = new LinkedHashMap<>();
            for (JsonNode lineResult : data.path("lines")) {
                String code = lineResult.path("msikaCode").asText(null);
                JsonNode charge = lineResult.path("chargeAmount");
                if (code != null && (charge.isNumber() || charge.isTextual())) {
                    lineCharges.merge(code, new BigDecimal(charge.asText()), BigDecimal::add);
                }
            }
            return Optional.of(new CostaCharge(new BigDecimal(total.asText()), lineCharges));
        } catch (Exception e) {
            log.warn("COSTA estimate failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // M3 BUG-4 check: COSTA's /costa/v1/estimate is NOT a v1.1 /internal/v1/**
    // path, so the tech-companion IdempotencyFilter does not apply — no
    // Idempotency-Key is required on this hop (verified against
    // V11HeaderFilter.isV11Path). CoverageClient carries the fix.
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
