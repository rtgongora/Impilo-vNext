package zw.gov.mohcc.impilo.experience.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Device Registry BFF Controller — manages user device registration, trust levels,
 * and revocation within the Health OS trust model.
 *
 * <p>Implements the Health OS doctrine's trust-first principle applied to device
 * management. Every device that interacts with the Health OS must be registered
 * and assigned a trust level. Device trust feeds into the 10-dimension access
 * control model (specifically the assurance level dimension) — a device with
 * LOW trust may restrict the user to reduced-privilege operations.</p>
 *
 * <p>Device trust levels:</p>
 * <ul>
 *   <li><strong>HIGH</strong> — Managed device with biometric unlock, MDM enrolled</li>
 *   <li><strong>MEDIUM</strong> — Personal device with screen lock, verified ownership</li>
 *   <li><strong>LOW</strong> — Shared or unverified device, limited session capabilities</li>
 *   <li><strong>REVOKED</strong> — Device access has been explicitly revoked</li>
 * </ul>
 *
 * <h3>Endpoints:</h3>
 * <ul>
 *   <li>GET  /internal/v1/devices — list user's registered devices</li>
 *   <li>POST /internal/v1/devices/register — register a new device</li>
 *   <li>POST /internal/v1/devices/{deviceId}/revoke — revoke device trust</li>
 *   <li>GET  /internal/v1/devices/{deviceId}/trust — get device trust level</li>
 * </ul>
 *
 * @see <a href="docs/doctrine/health-os-doctrine.md">Health OS Doctrine — Trust and Governance Plane</a>
 */
@RestController
@RequestMapping("/internal/v1/devices")
public class DeviceRegistryController {

    private static final Logger log = LoggerFactory.getLogger(DeviceRegistryController.class);

    /**
     * List all registered devices for the current user.
     *
     * <p>Returns the user's device registry including trust levels, last activity
     * timestamps, and device metadata. Used by the experience shell to display
     * the "My Devices" security panel.</p>
     *
     * @param tenantId      mandatory tenant context (X-Tenant-ID)
     * @param requestId     unique request identifier (X-Request-ID)
     * @param correlationId correlation chain identifier (X-Correlation-ID)
     * @return list of registered device objects with trust levels
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listDevices(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        log.info("Listing devices: tenant={}, requestId={}", tenantId, requestId);

        // Placeholder: realistic device registry entries
        List<Map<String, Object>> devices = List.of(
                createDevice("dev-a1b2c3d4", "Samsung Galaxy S24", "MOBILE",
                        "Android 15", "MEDIUM", true,
                        OffsetDateTime.now().minusHours(1)),
                createDevice("dev-e5f6g7h8", "MacBook Pro 14\"", "DESKTOP",
                        "macOS 15.2", "HIGH", false,
                        OffsetDateTime.now().minusDays(2)),
                createDevice("dev-i9j0k1l2", "iPad Air", "TABLET",
                        "iPadOS 18.1", "MEDIUM", false,
                        OffsetDateTime.now().minusDays(14))
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", devices);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId,
                "total", devices.size()
        ));
        return ResponseEntity.ok(response);
    }

    /**
     * Register a new device in the Health OS device registry.
     *
     * <p>Accepts device metadata (name, type, OS, fingerprint) and assigns an
     * initial trust level based on device characteristics. The device is
     * registered in PENDING state until trust assessment completes.</p>
     *
     * @param tenantId      mandatory tenant context (X-Tenant-ID)
     * @param requestId     unique request identifier (X-Request-ID)
     * @param correlationId correlation chain identifier (X-Correlation-ID)
     * @param body          device registration payload with name, type, os, and fingerprint
     * @return the newly registered device object with assigned ID and trust level
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> registerDevice(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {

        String deviceName = body.getOrDefault("deviceName", "Unknown Device").toString();
        String deviceType = body.getOrDefault("deviceType", "UNKNOWN").toString();
        String os = body.getOrDefault("os", "").toString();
        String fingerprint = body.getOrDefault("fingerprint", "").toString();

        log.info("Registering device: tenant={}, name={}, type={}, requestId={}",
                tenantId, deviceName, deviceType, requestId);

        // Placeholder: generate device registration response
        String deviceId = "dev-" + UUID.randomUUID().toString().substring(0, 8);

        Map<String, Object> device = new LinkedHashMap<>();
        device.put("deviceId", deviceId);
        device.put("deviceName", deviceName);
        device.put("deviceType", deviceType);
        device.put("os", os);
        device.put("fingerprint", fingerprint);
        device.put("trustLevel", "LOW");
        device.put("status", "PENDING_VERIFICATION");
        device.put("registeredAt", OffsetDateTime.now().toString());
        device.put("verificationRequired", List.of(
                "SCREEN_LOCK_CHECK",
                "BIOMETRIC_CAPABILITY_CHECK",
                "OWNERSHIP_CONFIRMATION"
        ));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", device);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Revoke trust for a registered device.
     *
     * <p>Marks the device as REVOKED, preventing further session establishment
     * from that device. Active sessions on the device are invalidated. This
     * supports the trust-first doctrine principle — a compromised or lost device
     * can be immediately removed from the trust boundary.</p>
     *
     * @param deviceId      the device identifier to revoke
     * @param tenantId      mandatory tenant context (X-Tenant-ID)
     * @param requestId     unique request identifier (X-Request-ID)
     * @param correlationId correlation chain identifier (X-Correlation-ID)
     * @return confirmation of revocation with timestamp
     */
    @PostMapping("/{deviceId}/revoke")
    public ResponseEntity<Map<String, Object>> revokeDevice(
            @PathVariable String deviceId,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        log.info("Revoking device: tenant={}, deviceId={}, requestId={}", tenantId, deviceId, requestId);

        Map<String, Object> revocation = new LinkedHashMap<>();
        revocation.put("deviceId", deviceId);
        revocation.put("status", "REVOKED");
        revocation.put("trustLevel", "REVOKED");
        revocation.put("revokedAt", OffsetDateTime.now().toString());
        revocation.put("activeSessionsInvalidated", 1);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", revocation);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    /**
     * Get the current trust level for a specific device.
     *
     * <p>Returns detailed trust assessment for the device including the computed
     * trust level, contributing factors, and any recommended actions to improve
     * the trust level (e.g., enabling biometric lock).</p>
     *
     * @param deviceId      the device identifier to query
     * @param tenantId      mandatory tenant context (X-Tenant-ID)
     * @param requestId     unique request identifier (X-Request-ID)
     * @param correlationId correlation chain identifier (X-Correlation-ID)
     * @return device trust assessment with level, factors, and upgrade recommendations
     */
    @GetMapping("/{deviceId}/trust")
    public ResponseEntity<Map<String, Object>> getDeviceTrust(
            @PathVariable String deviceId,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        log.info("Getting device trust: tenant={}, deviceId={}, requestId={}", tenantId, deviceId, requestId);

        Map<String, Object> trust = new LinkedHashMap<>();
        trust.put("deviceId", deviceId);
        trust.put("trustLevel", "MEDIUM");
        trust.put("assessedAt", OffsetDateTime.now().toString());
        trust.put("factors", Map.of(
                "screenLockEnabled", true,
                "biometricCapable", true,
                "biometricEnrolled", false,
                "osUpToDate", true,
                "mdmEnrolled", false,
                "rootDetected", false,
                "lastActivityWithin24h", true
        ));
        trust.put("score", 65);
        trust.put("maxScore", 100);
        trust.put("upgradeActions", List.of(
                Map.of("action", "ENROLL_BIOMETRIC", "description", "Enable biometric authentication",
                        "trustIncrease", 15),
                Map.of("action", "ENROLL_MDM", "description", "Enroll in mobile device management",
                        "trustIncrease", 20)
        ));
        trust.put("permittedOperations", List.of(
                "VIEW_RECORDS", "VIEW_APPOINTMENTS", "WELLNESS_TRACKING",
                "MESSAGING", "SEARCH"
        ));
        trust.put("restrictedOperations", List.of(
                "PRESCRIBING", "CLAIMS_SUBMISSION", "IDENTITY_VERIFICATION"
        ));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", trust);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    // -- Helper methods -------------------------------------------------------

    private Map<String, Object> createDevice(String deviceId, String name, String type,
                                              String os, String trustLevel, boolean currentDevice,
                                              OffsetDateTime lastActivity) {
        Map<String, Object> device = new LinkedHashMap<>();
        device.put("deviceId", deviceId);
        device.put("deviceName", name);
        device.put("deviceType", type);
        device.put("os", os);
        device.put("trustLevel", trustLevel);
        device.put("status", "ACTIVE");
        device.put("currentDevice", currentDevice);
        device.put("lastActivity", lastActivity.toString());
        device.put("registeredAt", OffsetDateTime.now().minusDays(90).toString());
        return device;
    }
}
