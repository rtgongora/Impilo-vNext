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
    private final zw.gov.mohcc.impilo.madi.integration.DaidzaiEpisodeClient daidzaiEpisodes;

    public MadiBloodOrderController(BloodOrderService bloodOrderService,
                                    zw.gov.mohcc.impilo.madi.integration.DaidzaiEpisodeClient daidzaiEpisodes) {
        this.bloodOrderService = bloodOrderService;
        this.daidzaiEpisodes = daidzaiEpisodes;
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
            @RequestHeader(value = CompanionHeaders.TRAUMA_EPISODE_ID, required = false) String traumaEpisodeId,
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
        // Canonical trauma spine: a trauma blood order inherits the DAIDZAI-minted episode id from
        // X-Trauma-Episode-ID (or the body). MADI keeps its own SoR row and stamps the shared id.
        UUID episodeId = traumaEpisodeId != null && !traumaEpisodeId.isBlank()
                ? UUID.fromString(traumaEpisodeId) : uuid(body, "trauma_episode_id");
        order.setTraumaEpisodeId(episodeId);
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
        BloodOrderEntity saved = bloodOrderService.createOrder(order, items);
        if (episodeId != null) {
            daidzaiEpisodes.registerPhase(tenantId, episodeId, "BLOOD", saved.getOrderId().toString(),
                    saved.getStatus() != null ? saved.getStatus() : "DRAFT", "madi.blood.ordered");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
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

    /**
     * Emergency O-neg / uncrossmatched blood release under break-glass (G1.14). Requires an
     * EMERGENCY/BREAK_GLASS purpose-of-use; the shared EmergencyAccessGuard DENIES (403) otherwise —
     * an emergency override is audited, never a silent bypass.
     */
    @PostMapping("/{orderId}/emergency-release")
    public ResponseEntity<BloodOrderEntity> emergencyRelease(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @RequestHeader(value = "X-Escalation-Grant-Id", required = false) String escalationGrantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @PathVariable UUID orderId,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            Map<String, Object> b = body != null ? body : Map.of();
            BloodOrderEntity order = bloodOrderService.emergencyRelease(tenantId, orderId, purposeOfUse,
                    actorId, str(b, "reason"), str(b, "bloodUnitId"), escalationGrantId);
            return ResponseEntity.ok(order);
        } catch (zw.gov.mohcc.impilo.sharedkernel.security.EmergencyAccessGuard.EmergencyAccessDeniedException e) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        }
    }

    /** Delivery-side completion (e.g. Nhume drop-off sign-off): ISSUED -> COMPLETED. */
    @PostMapping("/{orderId}/complete")
    public ResponseEntity<BloodOrderEntity> complete(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID orderId,
            @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(bloodOrderService.complete(
                tenantId, orderId,
                body != null ? str(body, "completed_by") : null,
                body != null ? str(body, "delivery_ref") : null));
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
