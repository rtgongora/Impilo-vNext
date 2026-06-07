package zw.gov.mohcc.impilo.madi.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.madi.core.BloodBankService;
import zw.gov.mohcc.impilo.madi.persistence.entity.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/madi/blood-banks")
public class MadiBloodBankController {

    private final BloodBankService bloodBankService;

    public MadiBloodBankController(BloodBankService bloodBankService) {
        this.bloodBankService = bloodBankService;
    }

    @PostMapping
    public ResponseEntity<BloodBankEntity> register(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestBody Map<String, Object> body) {
        BloodBankEntity bank = new BloodBankEntity();
        bank.setTenantId(tenantId);
        bank.setFacilityId(UUID.fromString(required(body, "facility_id")));
        bank.setBankName(required(body, "bank_name"));
        bank.setBankType(required(body, "bank_type"));
        return ResponseEntity.status(HttpStatus.CREATED).body(bloodBankService.registerBank(bank));
    }

    @GetMapping("/{bloodBankId}/stock")
    public List<BloodInventoryBalanceEntity> stock(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID bloodBankId) {
        return bloodBankService.stock(tenantId, bloodBankId);
    }

    @PostMapping("/{bloodBankId}/movements")
    public ResponseEntity<BloodStockMovementEntity> movement(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) UUID facilityId,
            @PathVariable UUID bloodBankId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bloodBankService.recordMovement(
                tenantId, bloodBankId, uuid(body, "unit_id"),
                required(body, "inventory_item_code"), required(body, "movement_type"),
                Integer.parseInt(body.getOrDefault("quantity", "1").toString()),
                str(body, "reference_type"), str(body, "reference_id"),
                str(body, "moved_by"), facilityId));
    }

    @PostMapping("/{bloodBankId}/reconciliations")
    public ResponseEntity<BloodStockReconciliationEntity> reconcile(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) UUID facilityId,
            @PathVariable UUID bloodBankId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bloodBankService.reconcile(
                tenantId, bloodBankId,
                Integer.parseInt(body.getOrDefault("variance_count", "0").toString()),
                str(body, "notes"), str(body, "reconciled_by"), facilityId));
    }

    @PostMapping("/{bloodBankId}/wastage")
    public ResponseEntity<BloodWastageEntity> wastage(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) UUID facilityId,
            @PathVariable UUID bloodBankId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bloodBankService.recordWastage(
                tenantId, UUID.fromString(required(body, "unit_id")), bloodBankId,
                required(body, "reason_code"), str(body, "recorded_by"), facilityId));
    }

    @PostMapping("/{bloodBankId}/returns")
    public ResponseEntity<BloodReturnEntity> returns(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) UUID facilityId,
            @PathVariable UUID bloodBankId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bloodBankService.recordReturn(
                tenantId, UUID.fromString(required(body, "unit_id")), bloodBankId,
                required(body, "reason_code"), str(body, "returned_by"), facilityId));
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
