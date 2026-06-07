package zw.gov.mohcc.impilo.madi.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.madi.core.DonationDriveService;
import zw.gov.mohcc.impilo.madi.domain.ScreeningResult;
import zw.gov.mohcc.impilo.madi.persistence.entity.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/madi/drives")
public class MadiDonationDriveController {

    private final DonationDriveService driveService;

    public MadiDonationDriveController(DonationDriveService driveService) {
        this.driveService = driveService;
    }

    @PostMapping
    public ResponseEntity<DonationDriveEntity> create(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) UUID facilityId,
            @RequestBody Map<String, Object> body) {
        DonationDriveEntity drive = new DonationDriveEntity();
        drive.setTenantId(tenantId);
        drive.setTitle(required(body, "title"));
        drive.setDescription(str(body, "description"));
        drive.setDriveType(required(body, "drive_type"));
        drive.setFacilityId(facilityId);
        drive.setLocationRef(str(body, "location_ref"));
        drive.setNdilaSiteRef(str(body, "ndila_site_ref"));
        drive.setStartAt(OffsetDateTime.parse(required(body, "start_at")));
        drive.setEndAt(OffsetDateTime.parse(required(body, "end_at")));
        if (body.get("capacity") != null) {
            drive.setCapacity(Integer.parseInt(body.get("capacity").toString()));
        }
        drive.setCreatedBy(str(body, "created_by"));
        return ResponseEntity.status(HttpStatus.CREATED).body(driveService.create(drive));
    }

    @GetMapping
    public List<DonationDriveEntity> list(@RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId) {
        return driveService.list(tenantId);
    }

    @PostMapping("/{driveId}/publish")
    public DonationDriveEntity publish(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID driveId,
            @RequestBody Map<String, Object> body) {
        return driveService.publish(tenantId, driveId, str(body, "published_by"));
    }

    @PostMapping("/{driveId}/register")
    public ResponseEntity<DonationDriveAttendanceEntity> register(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) UUID facilityId,
            @PathVariable UUID driveId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(driveService.register(
                tenantId, driveId, UUID.fromString(required(body, "donor_id")),
                uuid(body, "slot_id"), facilityId));
    }

    @PostMapping("/{driveId}/screen")
    public DonorEligibilityScreeningEntity screen(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) UUID facilityId,
            @PathVariable UUID driveId,
            @RequestBody Map<String, Object> body) {
        return driveService.screenAtDrive(tenantId, driveId,
                UUID.fromString(required(body, "donor_id")),
                ScreeningResult.valueOf(required(body, "result")),
                str(body, "screened_by"), facilityId);
    }

    @PostMapping("/{driveId}/donations")
    public ResponseEntity<BloodCollectionEntity> recordDonation(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) UUID facilityId,
            @PathVariable UUID driveId,
            @RequestBody Map<String, Object> body) {
        Integer volume = body.get("volume_ml") != null ? Integer.parseInt(body.get("volume_ml").toString()) : null;
        return ResponseEntity.status(HttpStatus.CREATED).body(driveService.recordDonation(
                tenantId, driveId, UUID.fromString(required(body, "donor_id")),
                required(body, "bag_number"), volume, str(body, "collected_by"), facilityId));
    }

    @PostMapping("/{driveId}/close")
    public DonationDriveEntity close(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID driveId) {
        return driveService.close(tenantId, driveId);
    }

    private static String required(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || v.toString().isBlank()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return v.toString();
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v != null ? v.toString() : null;
    }

    private static UUID uuid(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v != null && !v.toString().isBlank() ? UUID.fromString(v.toString()) : null;
    }
}
