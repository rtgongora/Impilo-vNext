package zw.gov.mohcc.impilo.tshepo.authz.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.tshepo.authz.dto.DeviceProfileResponse;
import zw.gov.mohcc.impilo.tshepo.authz.service.DeviceService;

import java.util.UUID;

/**
 * Device profile management endpoints.
 *
 * <p>Used by security admins to view device reputation profiles and
 * block/unblock suspicious devices. Blocked devices are immediately
 * denied access by the PolicyEngine (risk score = 100).</p>
 */
@RestController
@RequestMapping("/v1/devices")
public class DeviceController {

    private static final Logger log = LoggerFactory.getLogger(DeviceController.class);

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    /**
     * Get a device profile by fingerprint.
     */
    @GetMapping("/{fingerprint}")
    public ResponseEntity<DeviceProfileResponse> getProfile(
            @PathVariable String fingerprint,
            @RequestHeader("x-tenant-id") UUID tenantId) {
        try {
            DeviceProfileResponse response = deviceService.getProfile(tenantId, fingerprint);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Block a device (immediate deny for all future requests from this device).
     */
    @PostMapping("/{fingerprint}/block")
    public ResponseEntity<DeviceProfileResponse> blockDevice(
            @PathVariable String fingerprint,
            @RequestHeader("x-tenant-id") UUID tenantId) {
        try {
            DeviceProfileResponse response = deviceService.blockDevice(tenantId, fingerprint);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Unblock a device (reset risk score, allow future requests).
     */
    @DeleteMapping("/{fingerprint}/block")
    public ResponseEntity<DeviceProfileResponse> unblockDevice(
            @PathVariable String fingerprint,
            @RequestHeader("x-tenant-id") UUID tenantId) {
        try {
            DeviceProfileResponse response = deviceService.unblockDevice(tenantId, fingerprint);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
