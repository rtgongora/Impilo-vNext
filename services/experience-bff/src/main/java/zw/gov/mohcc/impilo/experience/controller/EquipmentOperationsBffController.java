package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.AssetRegistryServiceClient;
import zw.gov.mohcc.impilo.experience.client.GuidanceServiceClient;
import zw.gov.mohcc.impilo.experience.client.KhulumaServiceClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * BFF composition surface for the WS#5 equipment-operations lifecycle.
 *
 * <p>Proxies {@code asset-registry-service} (sovereign owner) for asset/equipment/IoT
 * lifecycle, and composes two cross-domain helpers without owning their truth:
 * Khuluma ({@code /notify}) to <em>request</em> an operational alert, and
 * guidance-service ({@code domain=assets}) for Nompilo readiness/locked guidance.
 * Stateless — no datasource. Trust headers flow through the shared RestTemplate.</p>
 */
@RestController
@RequestMapping("/internal/v1/equipment")
public class EquipmentOperationsBffController {

    private final AssetRegistryServiceClient asset;
    private final KhulumaServiceClient khuluma;
    private final GuidanceServiceClient guidance;

    public EquipmentOperationsBffController(AssetRegistryServiceClient asset,
                                            KhulumaServiceClient khuluma,
                                            GuidanceServiceClient guidance) {
        this.asset = asset;
        this.khuluma = khuluma;
        this.guidance = guidance;
    }

    // -- Registry --

    @GetMapping
    public ResponseEntity<JsonNode> list(@RequestParam(name = "facility_id", required = false) String facilityId,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(asset.listEquipment(facilityId, page, size));
    }

    @PostMapping
    public ResponseEntity<JsonNode> register(@RequestBody JsonNode body) {
        return ResponseEntity.ok(asset.registerEquipment(body));
    }

    @GetMapping("/{equipmentId}")
    public ResponseEntity<JsonNode> get(@PathVariable UUID equipmentId) {
        return ResponseEntity.ok(asset.getEquipment(equipmentId));
    }

    @GetMapping("/{equipmentId}/detail")
    public ResponseEntity<JsonNode> detail(@PathVariable UUID equipmentId) {
        return ResponseEntity.ok(asset.getEquipmentDetail(equipmentId));
    }

    @PostMapping("/{equipmentId}/metadata")
    public ResponseEntity<JsonNode> metadata(@PathVariable UUID equipmentId, @RequestBody JsonNode body) {
        return ResponseEntity.ok(asset.updateEquipmentMetadata(equipmentId, body));
    }

    @PostMapping("/{equipmentId}/status")
    public ResponseEntity<JsonNode> status(@PathVariable UUID equipmentId, @RequestBody JsonNode body) {
        return ResponseEntity.ok(asset.changeEquipmentStatus(equipmentId, body));
    }

    // -- Transfer / custody --

    @PostMapping("/{equipmentId}/transfers")
    public ResponseEntity<JsonNode> transfer(@PathVariable UUID equipmentId, @RequestBody JsonNode body) {
        return ResponseEntity.ok(asset.initiateTransfer(equipmentId, body));
    }

    @PostMapping("/transfers/{transferId}/approve")
    public ResponseEntity<JsonNode> approveTransfer(@PathVariable UUID transferId) {
        return ResponseEntity.ok(asset.approveTransfer(transferId));
    }

    @PostMapping("/transfers/{transferId}/receive")
    public ResponseEntity<JsonNode> receiveTransfer(@PathVariable UUID transferId) {
        return ResponseEntity.ok(asset.confirmReceipt(transferId));
    }

    // -- Maintenance --

    @PostMapping("/{equipmentId}/maintenance")
    public ResponseEntity<JsonNode> openMaintenance(@PathVariable UUID equipmentId, @RequestBody JsonNode body) {
        return ResponseEntity.ok(asset.openMaintenance(equipmentId, body));
    }

    @PostMapping("/maintenance/{taskId}")
    public ResponseEntity<JsonNode> updateMaintenance(@PathVariable UUID taskId, @RequestBody JsonNode body) {
        return ResponseEntity.ok(asset.updateMaintenance(taskId, body));
    }

    @PostMapping("/maintenance/{taskId}/complete")
    public ResponseEntity<JsonNode> completeMaintenance(@PathVariable UUID taskId, @RequestBody JsonNode body) {
        return ResponseEntity.ok(asset.completeMaintenance(taskId, body));
    }

    @GetMapping("/maintenance/due")
    public ResponseEntity<JsonNode> maintenanceDue() {
        return ResponseEntity.ok(asset.maintenanceDue());
    }

    @GetMapping("/maintenance/open")
    public ResponseEntity<JsonNode> openMaintenanceList() {
        return ResponseEntity.ok(asset.openMaintenanceList());
    }

    // -- Calibration --

    @PostMapping("/{equipmentId}/calibration")
    public ResponseEntity<JsonNode> calibration(@PathVariable UUID equipmentId, @RequestBody JsonNode body) {
        return ResponseEntity.ok(asset.recordCalibration(equipmentId, body));
    }

    @GetMapping("/calibration/due")
    public ResponseEntity<JsonNode> calibrationDue() {
        return ResponseEntity.ok(asset.calibrationDue());
    }

    // -- Fault reporting (safety -> Rito via service) --

    @PostMapping("/{equipmentId}/faults")
    public ResponseEntity<JsonNode> reportFault(@PathVariable UUID equipmentId, @RequestBody JsonNode body) {
        return ResponseEntity.ok(asset.reportFault(equipmentId, body));
    }

    @PostMapping("/faults/{faultId}/link-rito")
    public ResponseEntity<JsonNode> linkRito(@PathVariable UUID faultId, @RequestBody JsonNode body) {
        return ResponseEntity.ok(asset.linkRito(faultId, body));
    }

    // -- IoT alerts --

    @PostMapping("/alerts")
    public ResponseEntity<JsonNode> raiseAlert(@RequestBody JsonNode body) {
        return ResponseEntity.ok(asset.raiseAlert(body));
    }

    @PostMapping("/alerts/{alertId}/resolve")
    public ResponseEntity<JsonNode> resolveAlert(@PathVariable UUID alertId) {
        return ResponseEntity.ok(asset.resolveAlert(alertId));
    }

    @GetMapping("/alerts/active")
    public ResponseEntity<JsonNode> activeAlerts() {
        return ResponseEntity.ok(asset.activeAlerts());
    }

    /**
     * Request a Khuluma operational notification for an alert (Khuluma owns delivery;
     * we only request). Marks the alert khuluma_requested on the sovereign service.
     */
    @PostMapping("/alerts/{alertId}/notify")
    public ResponseEntity<Map<String, Object>> notifyAlert(
            @PathVariable UUID alertId,
            @RequestBody(required = false) JsonNode body,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId) {
        Map<String, Object> notice = new LinkedHashMap<>();
        notice.put("channel", "OPERATIONS");
        notice.put("category", "ASSET_IOT_ALERT");
        notice.put("alert_id", alertId.toString());
        if (body != null && body.hasNonNull("message")) notice.put("message", body.get("message").asText());
        if (body != null && body.hasNonNull("audience")) notice.put("audience", body.get("audience").asText());
        khuluma.dispatch(tenantId, notice);
        asset.markAlertKhuluma(alertId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("alert_id", alertId.toString());
        out.put("khuluma_requested", true);
        return ResponseEntity.ok(out);
    }

    // -- Readiness (+ Nompilo guidance composition) --

    @PostMapping("/readiness/profiles")
    public ResponseEntity<JsonNode> createProfile(@RequestBody JsonNode body) {
        return ResponseEntity.ok(asset.createReadinessProfile(body));
    }

    @GetMapping("/readiness")
    public ResponseEntity<JsonNode> readiness(@RequestParam(name = "facility_ref") String facilityRef) {
        return ResponseEntity.ok(asset.readiness(facilityRef));
    }

    /** Nompilo guidance for the readiness/locked surface — config-driven domain='assets' catalogue. */
    @GetMapping("/readiness/guidance")
    public ResponseEntity<JsonNode> readinessGuidance(@RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "5") int size) {
        return ResponseEntity.ok(guidance.getEducation("assets", page, size));
    }

    // -- Deployment kits --

    @PostMapping("/deployment-kits")
    public ResponseEntity<JsonNode> createKit(@RequestBody JsonNode body) {
        return ResponseEntity.ok(asset.createKit(body));
    }

    @PostMapping("/deployment-kits/{kitId}/deploy")
    public ResponseEntity<JsonNode> deployKit(@PathVariable UUID kitId) {
        return ResponseEntity.ok(asset.deployKit(kitId));
    }

    @PostMapping("/deployment-kits/{kitId}/return")
    public ResponseEntity<JsonNode> returnKit(@PathVariable UUID kitId, @RequestBody JsonNode body) {
        return ResponseEntity.ok(asset.returnKit(kitId, body));
    }

    @GetMapping("/deployment-kits")
    public ResponseEntity<JsonNode> kits() {
        return ResponseEntity.ok(asset.kits());
    }

    // -- Asset audit --

    @PostMapping("/audits")
    public ResponseEntity<JsonNode> openAudit(@RequestBody JsonNode body) {
        return ResponseEntity.ok(asset.openAudit(body));
    }

    @PostMapping("/audits/items/{itemId}")
    public ResponseEntity<JsonNode> confirmAuditItem(@PathVariable UUID itemId, @RequestBody JsonNode body) {
        return ResponseEntity.ok(asset.confirmAuditItem(itemId, body));
    }

    @PostMapping("/audits/{auditId}/complete")
    public ResponseEntity<JsonNode> completeAudit(@PathVariable UUID auditId) {
        return ResponseEntity.ok(asset.completeAudit(auditId));
    }

    @GetMapping("/audits")
    public ResponseEntity<JsonNode> audits(@RequestParam(name = "facility_ref") String facilityRef) {
        return ResponseEntity.ok(asset.audits(facilityRef));
    }

    @GetMapping("/audits/{auditId}/items")
    public ResponseEntity<JsonNode> auditItems(@PathVariable UUID auditId) {
        return ResponseEntity.ok(asset.auditItems(auditId));
    }
}
