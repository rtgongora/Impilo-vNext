package zw.gov.mohcc.impilo.madi.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.madi.core.BloodOrderService;
import zw.gov.mohcc.impilo.madi.domain.CrossmatchResultStatus;
import zw.gov.mohcc.impilo.madi.persistence.entity.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/madi/orders")
public class MadiBloodOrderController {

    private final BloodOrderService bloodOrderService;

    public MadiBloodOrderController(BloodOrderService bloodOrderService) {
        this.bloodOrderService = bloodOrderService;
    }

    @GetMapping
    public List<BloodOrderEntity> list(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) UUID facilityId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "patient_cpid", required = false) String patientCpid) {
        return bloodOrderService.listOrders(tenantId, facilityId, status, patientCpid);
    }

    @PostMapping
    public ResponseEntity<BloodOrderEntity> create(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) UUID facilityId,
            @RequestBody Map<String, Object> body) {
        BloodOrderEntity order = new BloodOrderEntity();
        order.setTenantId(tenantId);
        order.setOrosOrderRef(str(body, "oros_order_ref"));
        order.setPatientCpid(required(body, "patient_cpid"));
        order.setBloodGroup(required(body, "blood_group"));
        order.setComponentType(required(body, "component_type"));
        order.setUnitsRequested(Integer.parseInt(body.getOrDefault("units_requested", "1").toString()));
        order.setFacilityId(facilityId);
        order.setOrderingProvider(str(body, "ordering_provider"));
        List<BloodOrderItemEntity> items = new ArrayList<>();
        if (body.get("items") instanceof List<?> rawItems) {
            for (Object raw : rawItems) {
                if (raw instanceof Map<?, ?> itemMap) {
                    BloodOrderItemEntity item = new BloodOrderItemEntity();
                    item.setComponentType(String.valueOf(itemMap.get("component_type")));
                    item.setBloodGroup(String.valueOf(itemMap.get("blood_group")));
                    Object qty = itemMap.get("quantity");
                    item.setQuantity(Integer.parseInt(qty != null ? qty.toString() : "1"));
                    items.add(item);
                }
            }
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(bloodOrderService.createOrder(order, items));
    }

    @GetMapping("/{orderId}")
    public Map<String, Object> getOrder(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID orderId) {
        return bloodOrderService.getOrderDetail(tenantId, orderId);
    }

    @PostMapping("/{orderId}/submit")
    public BloodOrderEntity submit(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID orderId) {
        return bloodOrderService.submit(tenantId, orderId);
    }

    @PostMapping("/{orderId}/samples")
    public ResponseEntity<BloodSampleEntity> sample(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) UUID facilityId,
            @PathVariable UUID orderId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bloodOrderService.collectSample(
                tenantId, orderId, required(body, "sample_number"),
                str(body, "collected_by"), facilityId));
    }

    @PostMapping("/{orderId}/crossmatch")
    public ResponseEntity<CrossmatchResultEntity> crossmatch(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) UUID facilityId,
            @PathVariable UUID orderId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bloodOrderService.crossmatch(
                tenantId, orderId,
                UUID.fromString(required(body, "sample_id")),
                UUID.fromString(required(body, "unit_id")),
                CrossmatchResultStatus.valueOf(required(body, "result")),
                str(body, "tested_by"), facilityId));
    }

    @PostMapping("/{orderId}/reserve")
    public ResponseEntity<BloodReservationEntity> reserve(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) UUID facilityId,
            @PathVariable UUID orderId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bloodOrderService.reserve(
                tenantId, orderId, UUID.fromString(required(body, "unit_id")),
                str(body, "reserved_by"), facilityId));
    }

    @PostMapping("/{orderId}/issue")
    public ResponseEntity<BloodIssueEntity> issue(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) UUID facilityId,
            @PathVariable UUID orderId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bloodOrderService.issue(
                tenantId, orderId, UUID.fromString(required(body, "unit_id")),
                uuid(body, "reservation_id"), str(body, "issued_by"), facilityId));
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
