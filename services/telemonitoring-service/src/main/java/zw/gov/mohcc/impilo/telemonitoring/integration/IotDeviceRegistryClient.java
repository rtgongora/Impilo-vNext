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

import java.util.UUID;

/**
 * READ-ONLY client to iot-ingestion's device registry — the DIGITAL-identity leg of the
 * Vol II §6.2 three-way device split. Telemonitoring never writes to the registry and
 * never talks to devices; it only verifies that a device digitally exists (and carries a
 * usable trust grade) before minting a clinical assignment (OF-B24).
 *
 * <p>Bound endpoint: {@code GET /internal/v1/devices/{deviceId}/trust} on
 * iot-ingestion-service (port 8330) — returns the {data:{deviceId, trustLevel, ...}}
 * envelope for a registered device.</p>
 *
 * <p><b>Fail-closed:</b> unlike the never-blocking Nhume dispatch idiom, this probe's
 * consumer (DeviceAssignmentService.assign) REFUSES on NOT_FOUND and UNREACHABLE alike
 * (DEVICE_UNVERIFIED): an unverifiable device must never acquire a clinical binding.</p>
 */
@Service
public class IotDeviceRegistryClient {

    private static final Logger log = LoggerFactory.getLogger(IotDeviceRegistryClient.class);

    public enum Outcome { VERIFIED, NOT_FOUND, UNREACHABLE }

    /** {@code trustLevel} is populated only when {@code outcome == VERIFIED}. */
    public record DeviceVerification(Outcome outcome, String trustLevel) {

        public static DeviceVerification verified(String trustLevel) {
            return new DeviceVerification(Outcome.VERIFIED, trustLevel);
        }

        public static DeviceVerification notFound() {
            return new DeviceVerification(Outcome.NOT_FOUND, null);
        }

        public static DeviceVerification unreachable() {
            return new DeviceVerification(Outcome.UNREACHABLE, null);
        }
    }

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public IotDeviceRegistryClient(
            ObjectMapper objectMapper,
            @Value("${telemonitoring.integration.iot-ingestion-url:http://iot-ingestion-service:8330}") String baseUrl) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl.replaceAll("/$", ""))
                .build();
    }

    /**
     * Verify a device exists in the digital registry. Never throws — outcomes are typed
     * so the caller decides policy (assignment: fail closed on anything but VERIFIED).
     */
    public DeviceVerification verify(String deviceRef, UUID tenantId, HttpServletRequest inbound) {
        try {
            ResponseEntity<String> response = restClient.get()
                    .uri("/internal/v1/devices/{deviceId}/trust", deviceRef)
                    .headers(h -> TrustHeaderPropagation.apply(inbound, h, tenantId))
                    .retrieve()
                    .toEntity(String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("iot-ingestion trust probe for device {} returned HTTP {}", deviceRef, response.getStatusCode());
                return DeviceVerification.notFound();
            }
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode data = root.hasNonNull("data") && root.get("data").isObject() ? root.get("data") : root;
            String trustLevel = data.hasNonNull("trustLevel") ? data.get("trustLevel").asText() : null;
            return DeviceVerification.verified(trustLevel);
        } catch (org.springframework.web.client.RestClientResponseException e) {
            // The registry answered but the device is not a usable identity (404/4xx/5xx body).
            log.warn("iot-ingestion rejected trust probe for device {}: HTTP {}", deviceRef, e.getStatusCode());
            return DeviceVerification.notFound();
        } catch (Exception e) {
            log.warn("iot-ingestion unreachable verifying device {}: {}", deviceRef, e.getMessage());
            return DeviceVerification.unreachable();
        }
    }
}
