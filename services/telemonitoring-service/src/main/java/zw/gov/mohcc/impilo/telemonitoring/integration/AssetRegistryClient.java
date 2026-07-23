package zw.gov.mohcc.impilo.telemonitoring.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.UUID;

/**
 * READ-ONLY client to asset-registry — the PHYSICAL-truth leg of the Vol II §6.2
 * three-way device split. Telemonitoring never edits equipment; it PROJECTS the
 * calibration state onto assignment-gate lookups (OF-B24) so the OF-B25 ingestion gate
 * can stamp readings from lapsed-calibration devices DEGRADED — never dropping them
 * (§15.5 invariant / §15.7 "nothing is silently discarded").
 *
 * <p>Bound endpoint: {@code GET /internal/v1/equipment/{equipment_id}} on
 * asset-registry-service (port 8310) — returns a bare snake_case equipment object with
 * {@code status}, {@code last_calibration} and {@code next_calibration} (asr_equipment).</p>
 */
@Service
public class AssetRegistryClient {

    private static final Logger log = LoggerFactory.getLogger(AssetRegistryClient.class);

    public enum Outcome { FOUND, NOT_FOUND, UNREACHABLE }

    /** Equipment fields are populated only when {@code outcome == FOUND}. */
    public record EquipmentCalibration(Outcome outcome, String equipmentStatus, LocalDate nextCalibration) {

        public static EquipmentCalibration found(String status, LocalDate nextCalibration) {
            return new EquipmentCalibration(Outcome.FOUND, status, nextCalibration);
        }

        public static EquipmentCalibration notFound() {
            return new EquipmentCalibration(Outcome.NOT_FOUND, null, null);
        }

        public static EquipmentCalibration unreachable() {
            return new EquipmentCalibration(Outcome.UNREACHABLE, null, null);
        }

        /** Calibration is lapsed when a due date exists and is in the past (asr next_calibration). */
        public boolean calibrationLapsed(LocalDate today) {
            return outcome == Outcome.FOUND && nextCalibration != null && nextCalibration.isBefore(today);
        }
    }

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public AssetRegistryClient(
            ObjectMapper objectMapper,
            @Value("${telemonitoring.integration.asset-registry-url:http://asset-registry-service:8310}") String baseUrl) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl.replaceAll("/$", ""))
                .build();
    }

    /**
     * Read the calibration state for an equipment ref. Never throws — the caller maps
     * NOT_FOUND/UNREACHABLE to posture UNVERIFIED (stamped, never blocking the lookup).
     */
    public EquipmentCalibration calibrationState(String equipmentId, UUID tenantId, HttpServletRequest inbound) {
        try {
            ResponseEntity<String> response = restClient.get()
                    .uri("/internal/v1/equipment/{equipmentId}", equipmentId)
                    .headers(h -> TrustHeaderPropagation.apply(inbound, h, tenantId))
                    .retrieve()
                    .toEntity(String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("asset-registry equipment probe {} returned HTTP {}", equipmentId, response.getStatusCode());
                return EquipmentCalibration.notFound();
            }
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode data = root.hasNonNull("equipment") && root.get("equipment").isObject()
                    ? root.get("equipment") : root;
            String status = data.hasNonNull("status") ? data.get("status").asText() : null;
            LocalDate nextCalibration = parseDate(data.get("next_calibration"));
            return EquipmentCalibration.found(status, nextCalibration);
        } catch (org.springframework.web.client.RestClientResponseException e) {
            log.warn("asset-registry rejected equipment probe {}: HTTP {}", equipmentId, e.getStatusCode());
            return EquipmentCalibration.notFound();
        } catch (Exception e) {
            log.warn("asset-registry unreachable reading equipment {}: {}", equipmentId, e.getMessage());
            return EquipmentCalibration.unreachable();
        }
    }

    private static LocalDate parseDate(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            if (node.isArray() && node.size() >= 3) {
                // Jackson may serialize LocalDate as [yyyy, m, d] without the JavaTimeModule shape.
                return LocalDate.of(node.get(0).asInt(), node.get(1).asInt(), node.get(2).asInt());
            }
            return LocalDate.parse(node.asText());
        } catch (Exception e) {
            return null;
        }
    }
}
