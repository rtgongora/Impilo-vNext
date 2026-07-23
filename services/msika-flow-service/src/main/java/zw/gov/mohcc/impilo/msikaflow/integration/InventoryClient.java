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
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * DURA (inventory-service) client — OF-B11.
 *
 * <p>DURA's {@code inv_stock_reservations} is the SOLE reservation ledger;
 * msika-flow's {@code mf_reservations} is a demoted read-projection. The
 * commitment path calls {@link #reserve} (atomic conditional reserve — fail-close:
 * no reservation, no commitment) and compensates with {@link #release}. The
 * controlled-register write (§13.4) also rides this client.</p>
 */
@Service
public class InventoryClient {

    private static final Logger log = LoggerFactory.getLogger(InventoryClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public record DuraReservation(UUID reservationId, String status, OffsetDateTime expiresAt) {}
    public record DuraAvailability(int available, int reserved, int onHand) {}

    public InventoryClient(ObjectMapper objectMapper,
                           @Value("${msika-flow.integration.inventory-url:http://inventory-service:8098}") String inventoryBaseUrl) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(inventoryBaseUrl.replaceAll("/$", ""))
                .build();
    }

    /**
     * POST /v1/dura/reservations — atomic conditional reserve
     * (available = on-hand − active reserved must cover the quantity).
     * Empty on ANY failure — the caller fails the commitment (RC-3), never fakes a hold.
     */
    public Optional<DuraReservation> reserve(UUID facilityId, UUID storeId, String itemCode,
                                             int qty, String uom, String refType, String refId,
                                             String reason, OffsetDateTime expiresAt,
                                             HttpServletRequest inbound) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("facilityId", facilityId);
            body.put("storeId", storeId);
            body.put("itemCode", itemCode);
            body.put("qty", qty);
            if (uom != null) body.put("uom", uom);
            body.put("refType", refType);
            body.put("refId", refId);
            if (reason != null) body.put("reason", reason);
            if (expiresAt != null) body.put("expiresAt", expiresAt.toString());

            ResponseEntity<String> response = restClient.post()
                    .uri("/v1/dura/reservations")
                    .headers(h -> copyTrustHeaders(inbound, h, refType + ":" + refId + ":" + itemCode))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toEntity(String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("DURA reserve failed for item {}: HTTP {}", itemCode, response.getStatusCode());
                return Optional.empty();
            }
            JsonNode data = objectMapper.readTree(response.getBody()).path("data");
            String id = data.path("reservationId").asText(null);
            if (id == null || id.isBlank()) {
                log.warn("DURA reserve response missing reservationId for item {}", itemCode);
                return Optional.empty();
            }
            OffsetDateTime resExpiry = data.hasNonNull("expiresAt")
                    ? OffsetDateTime.parse(data.get("expiresAt").asText()) : expiresAt;
            return Optional.of(new DuraReservation(UUID.fromString(id),
                    data.path("status").asText("ACTIVE"), resExpiry));
        } catch (Exception e) {
            log.warn("DURA reserve failed for item {}: {}", itemCode, e.getMessage());
            return Optional.empty();
        }
    }

    /** POST /v1/dura/reservations/{id}/release — compensation. False is logged, never swallowed as success. */
    public boolean release(UUID reservationId, HttpServletRequest inbound) {
        try {
            ResponseEntity<String> response = restClient.post()
                    .uri("/v1/dura/reservations/{id}/release", reservationId)
                    .headers(h -> copyTrustHeaders(inbound, h, "reservation-release:" + reservationId))
                    .retrieve()
                    .toEntity(String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("DURA release FAILED for reservation {} — TTL sweep is the backstop: {}",
                    reservationId, e.getMessage());
            return false;
        }
    }

    /** GET /v1/dura/reservations/availability — stock-grade check at offer time (§8E). */
    public Optional<DuraAvailability> availability(UUID facilityId, UUID storeId, String itemCode,
                                                   HttpServletRequest inbound) {
        try {
            ResponseEntity<String> response = restClient.get()
                    .uri(uri -> uri.path("/v1/dura/reservations/availability")
                            .queryParam("facilityId", facilityId)
                            .queryParam("storeId", storeId)
                            .queryParam("itemCode", itemCode)
                            .build())
                    .headers(h -> copyTrustHeaders(inbound, h, "availability:" + itemCode))
                    .retrieve()
                    .toEntity(String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return Optional.empty();
            }
            JsonNode data = objectMapper.readTree(response.getBody()).path("data");
            if (!data.has("available")) {
                return Optional.empty();
            }
            return Optional.of(new DuraAvailability(
                    data.path("available").asInt(), data.path("reserved").asInt(), data.path("onHand").asInt()));
        } catch (Exception e) {
            log.warn("DURA availability check failed for item {}: {}", itemCode, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * POST /v1/internal/controlled-drugs/record — §13.4 mandatory controlled-register
     * write on controlled marketplace commitment (action ISSUE,
     * refType MARKETPLACE_COMMITMENT, refId = selectionId). Fail-close: false fails
     * the commitment (a controlled commitment that cannot write the register cannot complete).
     */
    public boolean recordControlledIssue(UUID facilityId, UUID storeId, String itemCode,
                                         BigDecimal quantity, String unit,
                                         String administeringProvider, String selectionId,
                                         HttpServletRequest inbound) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            if (facilityId != null) body.put("facilityId", facilityId);
            if (storeId != null) body.put("storeId", storeId);
            body.put("itemCode", itemCode);
            body.put("action", "ISSUE");
            body.put("quantity", quantity);
            if (unit != null) body.put("unit", unit);
            if (administeringProvider != null) body.put("administeringProvider", administeringProvider);
            body.put("refType", "MARKETPLACE_COMMITMENT");
            body.put("refId", selectionId);
            body.put("notes", "Marketplace controlled commitment (OF-B29 half); selection " + selectionId);

            ResponseEntity<String> response = restClient.post()
                    .uri("/v1/internal/controlled-drugs/record")
                    .headers(h -> copyTrustHeaders(inbound, h, "controlled-issue:" + selectionId + ":" + itemCode))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toEntity(String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("DURA controlled-register write FAILED for selection {} item {}: {}",
                    selectionId, itemCode, e.getMessage());
            return false;
        }
    }

    private static void copyTrustHeaders(HttpServletRequest inbound, HttpHeaders target, String idempotencySeed) {
        if (inbound != null) {
            copyIfPresent(inbound, target, TrustHeaderExtractor.H_TENANT_ID);
            copyIfPresent(inbound, target, TrustHeaderExtractor.H_ACTOR_ID);
            copyIfPresent(inbound, target, TrustHeaderExtractor.H_ACTOR_TYPE);
            copyIfPresent(inbound, target, TrustHeaderExtractor.H_PURPOSE_OF_USE);
            copyIfPresent(inbound, target, TrustHeaderExtractor.H_DEVICE_FINGERPRINT);
            copyIfPresent(inbound, target, TrustHeaderExtractor.H_CORRELATION_ID);
            copyIfPresent(inbound, target, TrustHeaderExtractor.H_FACILITY_ID);
            copyIfPresent(inbound, target, TrustHeaderExtractor.H_WORKSPACE_ID);
            copyIfPresent(inbound, target, TrustHeaderExtractor.H_SHIFT_ID);
            copyIfPresent(inbound, target, "Authorization");
        }
        String pod = inbound != null ? inbound.getHeader("X-Pod-ID") : null;
        target.add("X-Pod-ID", pod != null && !pod.isBlank() ? pod : "national-spine");
        target.add("X-Request-ID", java.util.UUID.randomUUID().toString());
        target.add("X-Idempotency-Key", "msika-flow:" + idempotencySeed);
        target.add("x-envoy-internal", "true");
    }

    private static void copyIfPresent(HttpServletRequest inbound, HttpHeaders target, String name) {
        String v = inbound.getHeader(name);
        if (v != null && !v.isBlank()) {
            target.add(name, v);
        }
    }
}
