package zw.gov.mohcc.impilo.assetregistry.api;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.assetregistry.api.dto.EquipmentOpsDtos.*;
import zw.gov.mohcc.impilo.assetregistry.core.EquipmentOperationsService;
import zw.gov.mohcc.impilo.assetregistry.domain.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.companion.error.ErrorEnvelope;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Equipment-operations REST surface (WS#5 — assets/devices/equipment/IoT).
 *
 * <p>Every mutation flows through {@link EquipmentOperationsService}, which appends a
 * lifecycle event (audit) + outbox event. Trust context is required via
 * {@link RequestContextHolder}; the acting actor is read from the {@code X-Actor-ID}
 * header ({@link CompanionHeaders#ACTOR_ID}). Downstream boundary owners
 * (Vashandi/Tuso/Rito/Khuluma/inventory) are referenced, never duplicated.
 * Errors return the canonical {@link ErrorEnvelope}.</p>
 */
@RestController
@RequestMapping("/internal/v1/equipment")
public class EquipmentOperationsController {

    private final EquipmentOperationsService ops;

    public EquipmentOperationsController(EquipmentOperationsService ops) {
        this.ops = ops;
    }

    // -- Registry --

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(name = "facility_id", required = false) String facilityId,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size) {
        RequestContext ctx = RequestContextHolder.require();
        Page<EquipmentEntity> p = ops.list(UUID.fromString(ctx.tenantId()), facilityId, page, size);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", p.getContent().stream().map(this::eq).toList());
        body.put("page", page);
        body.put("size", size);
        body.put("total_elements", p.getTotalElements());
        body.put("has_more", p.hasNext());
        return ResponseEntity.ok(body);
    }

    @PostMapping
    public ResponseEntity<?> register(@RequestBody RegisterEquipmentRequest req,
                                      jakarta.servlet.http.HttpServletRequest http) {
        RequestContext ctx = RequestContextHolder.require();
        String idem = http.getHeader(CompanionHeaders.IDEMPOTENCY_KEY);
        if (blank(req.facilityId()) || blank(req.name()) || blank(req.equipmentType())) {
            return bad(ctx, "VALIDATION_ERROR", "facilityId, name and equipmentType are required");
        }
        try {
            EquipmentEntity e = ops.register(UUID.fromString(ctx.tenantId()), ctx.podId(), ctx.correlationId(), idem,
                    req.facilityId(), req.name(), req.equipmentType(), req.serialNumber(), req.tag(),
                    req.manufacturer(), req.model(), req.criticality(), req.assetClass(),
                    Boolean.TRUE.equals(req.regulated()), req.custodianRef(), req.clinicalDeviceType(), req.deviceId());
            return ResponseEntity.status(HttpStatus.CREATED).body(eq(e));
        } catch (EquipmentOperationsService.DuplicateException dup) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ErrorEnvelope.of("DUPLICATE", dup.getMessage(), ctx.requestId(), ctx.correlationId()));
        }
    }

    @GetMapping("/{equipment_id}")
    public ResponseEntity<?> get(@PathVariable("equipment_id") UUID id) {
        RequestContext ctx = RequestContextHolder.require();
        return ops.get(id).<ResponseEntity<?>>map(e -> ResponseEntity.ok(eq(e)))
                .orElseGet(() -> notFound(ctx, "Equipment not found"));
    }

    @GetMapping("/{equipment_id}/detail")
    public ResponseEntity<?> detail(@PathVariable("equipment_id") UUID id) {
        RequestContext ctx = RequestContextHolder.require();
        var opt = ops.get(id);
        if (opt.isEmpty()) return notFound(ctx, "Equipment not found");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("equipment", eq(opt.get()));
        body.put("history", ops.history(id).stream().map(this::event).toList());
        body.put("maintenance", ops.maintenanceForEquipment(id).stream().map(this::task).toList());
        body.put("calibrations", ops.calibrationsForEquipment(id).stream().map(this::cal).toList());
        body.put("faults", ops.faultsForEquipment(id).stream().map(this::fault).toList());
        body.put("transfers", ops.transfers(id).stream().map(this::transfer).toList());
        body.put("alerts", ops.alertsForEquipment(id).stream().map(this::alert).toList());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/{equipment_id}/metadata")
    public ResponseEntity<?> updateMetadata(@PathVariable("equipment_id") UUID id,
                                            @RequestBody UpdateMetadataRequest req) {
        RequestContext ctx = RequestContextHolder.require();
        try {
            return ResponseEntity.ok(eq(ops.updateMetadata(id, UUID.fromString(ctx.tenantId()),
                    req.custodianRef(), req.locationDescription(), req.criticality())));
        } catch (EquipmentOperationsService.NotFoundException nf) {
            return notFound(ctx, nf.getMessage());
        }
    }

    @PostMapping("/{equipment_id}/status")
    public ResponseEntity<?> changeStatus(@PathVariable("equipment_id") UUID id,
                                          @RequestBody ChangeStatusRequest req,
                                          jakarta.servlet.http.HttpServletRequest http) {
        RequestContext ctx = RequestContextHolder.require();
        if (blank(req.status())) return bad(ctx, "VALIDATION_ERROR", "status required");
        try {
            return ResponseEntity.ok(eq(ops.changeStatus(id, UUID.fromString(ctx.tenantId()),
                    req.status(), actor(http), req.note())));
        } catch (EquipmentOperationsService.NotFoundException nf) {
            return notFound(ctx, nf.getMessage());
        }
    }

    // -- Transfer / custody --

    @PostMapping("/{equipment_id}/transfers")
    public ResponseEntity<?> initiateTransfer(@PathVariable("equipment_id") UUID id,
                                              @RequestBody TransferRequest req,
                                              jakarta.servlet.http.HttpServletRequest http) {
        RequestContext ctx = RequestContextHolder.require();
        if (blank(req.toFacility())) return bad(ctx, "VALIDATION_ERROR", "toFacility required");
        try {
            EquipmentTransferEntity t = ops.initiateTransfer(id, UUID.fromString(ctx.tenantId()), req.toFacility(),
                    req.toLocation(), req.toCustodianRef(), req.reason(),
                    Boolean.TRUE.equals(req.requiresApproval()), actor(http));
            return ResponseEntity.status(HttpStatus.CREATED).body(transfer(t));
        } catch (EquipmentOperationsService.NotFoundException nf) {
            return notFound(ctx, nf.getMessage());
        }
    }

    @PostMapping("/transfers/{transfer_id}/approve")
    public ResponseEntity<?> approveTransfer(@PathVariable("transfer_id") UUID id,
                                             jakarta.servlet.http.HttpServletRequest http) {
        RequestContext ctx = RequestContextHolder.require();
        try {
            return ResponseEntity.ok(transfer(ops.approveTransfer(id, UUID.fromString(ctx.tenantId()), actor(http))));
        } catch (EquipmentOperationsService.NotFoundException nf) {
            return notFound(ctx, nf.getMessage());
        }
    }

    @PostMapping("/transfers/{transfer_id}/receive")
    public ResponseEntity<?> confirmReceipt(@PathVariable("transfer_id") UUID id,
                                            jakarta.servlet.http.HttpServletRequest http) {
        RequestContext ctx = RequestContextHolder.require();
        try {
            return ResponseEntity.ok(transfer(ops.confirmReceipt(id, UUID.fromString(ctx.tenantId()), actor(http))));
        } catch (EquipmentOperationsService.NotFoundException nf) {
            return notFound(ctx, nf.getMessage());
        } catch (EquipmentOperationsService.InvalidStateException ise) {
            return bad(ctx, "INVALID_STATE", ise.getMessage());
        }
    }

    // -- Maintenance --

    @PostMapping("/{equipment_id}/maintenance")
    public ResponseEntity<?> openMaintenance(@PathVariable("equipment_id") UUID id,
                                             @RequestBody MaintenanceRequest req,
                                             jakarta.servlet.http.HttpServletRequest http) {
        RequestContext ctx = RequestContextHolder.require();
        if (blank(req.title())) return bad(ctx, "VALIDATION_ERROR", "title required");
        try {
            MaintenanceTaskEntity t = ops.openMaintenance(id, UUID.fromString(ctx.tenantId()), req.taskType(),
                    req.title(), req.description(), req.priority(), req.dueDate(), req.technicianRef(),
                    req.sparePartRequestRef(), req.faultReportId(), actor(http));
            return ResponseEntity.status(HttpStatus.CREATED).body(task(t));
        } catch (EquipmentOperationsService.NotFoundException nf) {
            return notFound(ctx, nf.getMessage());
        }
    }

    @PostMapping("/maintenance/{task_id}")
    public ResponseEntity<?> updateMaintenance(@PathVariable("task_id") UUID id,
                                               @RequestBody MaintenanceUpdateRequest req,
                                               jakarta.servlet.http.HttpServletRequest http) {
        RequestContext ctx = RequestContextHolder.require();
        try {
            return ResponseEntity.ok(task(ops.updateMaintenance(id, UUID.fromString(ctx.tenantId()),
                    req.status(), req.technicianRef(), req.serviceReport(), req.downtimeMinutes(), actor(http))));
        } catch (EquipmentOperationsService.NotFoundException nf) {
            return notFound(ctx, nf.getMessage());
        }
    }

    @PostMapping("/maintenance/{task_id}/complete")
    public ResponseEntity<?> completeMaintenance(@PathVariable("task_id") UUID id,
                                                 @RequestBody MaintenanceCompleteRequest req,
                                                 jakarta.servlet.http.HttpServletRequest http) {
        RequestContext ctx = RequestContextHolder.require();
        try {
            return ResponseEntity.ok(task(ops.completeMaintenance(id, UUID.fromString(ctx.tenantId()),
                    req.serviceReport(), Boolean.TRUE.equals(req.returnToService()), actor(http))));
        } catch (EquipmentOperationsService.NotFoundException nf) {
            return notFound(ctx, nf.getMessage());
        }
    }

    @GetMapping("/maintenance/due")
    public ResponseEntity<?> maintenanceDue() {
        RequestContext ctx = RequestContextHolder.require();
        return ResponseEntity.ok(Map.of("items",
                ops.maintenanceDue(UUID.fromString(ctx.tenantId())).stream().map(this::eq).toList()));
    }

    @GetMapping("/maintenance/open")
    public ResponseEntity<?> openMaintenanceList() {
        RequestContext ctx = RequestContextHolder.require();
        return ResponseEntity.ok(Map.of("items",
                ops.openMaintenance(UUID.fromString(ctx.tenantId())).stream().map(this::task).toList()));
    }

    // -- Calibration --

    @PostMapping("/{equipment_id}/calibration")
    public ResponseEntity<?> recordCalibration(@PathVariable("equipment_id") UUID id,
                                               @RequestBody CalibrationRequest req,
                                               jakarta.servlet.http.HttpServletRequest http) {
        RequestContext ctx = RequestContextHolder.require();
        if (blank(req.outcome())) return bad(ctx, "VALIDATION_ERROR", "outcome required");
        try {
            CalibrationRecordEntity rec = ops.recordCalibration(id, UUID.fromString(ctx.tenantId()), req.outcome(),
                    req.performedOn(), req.nextDue(), actor(http), req.certificateRef(), req.notes());
            return ResponseEntity.status(HttpStatus.CREATED).body(cal(rec));
        } catch (EquipmentOperationsService.NotFoundException nf) {
            return notFound(ctx, nf.getMessage());
        }
    }

    @GetMapping("/calibration/due")
    public ResponseEntity<?> calibrationDue() {
        RequestContext ctx = RequestContextHolder.require();
        return ResponseEntity.ok(Map.of("items",
                ops.calibrationDue(UUID.fromString(ctx.tenantId())).stream().map(this::eq).toList()));
    }

    // -- Fault reporting --

    @PostMapping("/{equipment_id}/faults")
    public ResponseEntity<?> reportFault(@PathVariable("equipment_id") UUID id,
                                         @RequestBody FaultRequest req,
                                         jakarta.servlet.http.HttpServletRequest http) {
        RequestContext ctx = RequestContextHolder.require();
        if (blank(req.summary())) return bad(ctx, "VALIDATION_ERROR", "summary required");
        try {
            FaultReportEntity f = ops.reportFault(id, UUID.fromString(ctx.tenantId()), req.severity(),
                    req.summary(), req.detail(), Boolean.TRUE.equals(req.safetyConcern()), actor(http));
            return ResponseEntity.status(HttpStatus.CREATED).body(fault(f));
        } catch (EquipmentOperationsService.NotFoundException nf) {
            return notFound(ctx, nf.getMessage());
        }
    }

    @PostMapping("/faults/{fault_id}/link-rito")
    public ResponseEntity<?> linkRito(@PathVariable("fault_id") UUID id, @RequestBody RitoLinkRequest req) {
        RequestContext ctx = RequestContextHolder.require();
        try {
            return ResponseEntity.ok(fault(ops.linkRitoCase(id, UUID.fromString(ctx.tenantId()), req.ritoCaseRef())));
        } catch (EquipmentOperationsService.NotFoundException nf) {
            return notFound(ctx, nf.getMessage());
        }
    }

    // -- IoT alerts (derived only) --

    @PostMapping("/alerts")
    public ResponseEntity<?> raiseAlert(@RequestBody AlertRequest req) {
        RequestContext ctx = RequestContextHolder.require();
        if (blank(req.deviceId()) || blank(req.alertType())) {
            return bad(ctx, "VALIDATION_ERROR", "deviceId and alertType required");
        }
        IotAlertEntity a = ops.raiseAlert(UUID.fromString(ctx.tenantId()), req.deviceId(), req.alertType(),
                req.metricType(), req.observedValue(), req.thresholdValue(), req.severity(), req.detail());
        return ResponseEntity.status(HttpStatus.CREATED).body(alert(a));
    }

    @PostMapping("/alerts/{alert_id}/resolve")
    public ResponseEntity<?> resolveAlert(@PathVariable("alert_id") UUID id,
                                          jakarta.servlet.http.HttpServletRequest http) {
        RequestContext ctx = RequestContextHolder.require();
        try {
            return ResponseEntity.ok(alert(ops.resolveAlert(id, UUID.fromString(ctx.tenantId()), actor(http))));
        } catch (EquipmentOperationsService.NotFoundException nf) {
            return notFound(ctx, nf.getMessage());
        }
    }

    @PostMapping("/alerts/{alert_id}/khuluma-requested")
    public ResponseEntity<?> markKhuluma(@PathVariable("alert_id") UUID id) {
        RequestContextHolder.require();
        ops.markAlertKhulumaRequested(id);
        return ResponseEntity.ok(Map.of("alert_id", id.toString(), "khuluma_requested", true));
    }

    @GetMapping("/alerts/active")
    public ResponseEntity<?> activeAlerts() {
        RequestContext ctx = RequestContextHolder.require();
        return ResponseEntity.ok(Map.of("items",
                ops.activeAlerts(UUID.fromString(ctx.tenantId())).stream().map(this::alert).toList()));
    }

    // -- Readiness --

    @PostMapping("/readiness/profiles")
    public ResponseEntity<?> createProfile(@RequestBody ReadinessProfileRequest req) {
        RequestContext ctx = RequestContextHolder.require();
        if (blank(req.facilityRef()) || blank(req.servicePoint())) {
            return bad(ctx, "VALIDATION_ERROR", "facilityRef and servicePoint required");
        }
        List<ReadinessRequirementEntity> reqs = new ArrayList<>();
        if (req.requirements() != null) {
            for (ReadinessRequirementDto d : req.requirements()) {
                ReadinessRequirementEntity r = new ReadinessRequirementEntity(null, d.equipmentType(),
                        d.minCount() == null ? 1 : d.minCount());
                r.setMustBeCritical(Boolean.TRUE.equals(d.mustBeCritical()));
                r.setNote(d.note());
                reqs.add(r);
            }
        }
        ReadinessProfileEntity p = ops.createProfile(UUID.fromString(ctx.tenantId()), req.facilityRef(),
                req.servicePoint(), blank(req.name()) ? req.servicePoint() : req.name(), reqs);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("profile_id", p.getProfileId().toString());
        body.put("facility_ref", p.getFacilityRef());
        body.put("service_point", p.getServicePoint());
        body.put("name", p.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/readiness")
    public ResponseEntity<?> readiness(@RequestParam(name = "facility_ref") String facilityRef) {
        RequestContext ctx = RequestContextHolder.require();
        return ResponseEntity.ok(ops.computeReadiness(UUID.fromString(ctx.tenantId()), facilityRef));
    }

    // -- Deployment kits --

    @PostMapping("/deployment-kits")
    public ResponseEntity<?> createKit(@RequestBody KitRequest req,
                                       jakarta.servlet.http.HttpServletRequest http) {
        RequestContext ctx = RequestContextHolder.require();
        if (blank(req.name())) return bad(ctx, "VALIDATION_ERROR", "name required");
        try {
            DeploymentKitEntity k = ops.createKit(UUID.fromString(ctx.tenantId()), req.name(), req.deploymentRef(),
                    req.locationRef(), req.custodianRef(), req.equipmentIds(), actor(http));
            return ResponseEntity.status(HttpStatus.CREATED).body(kit(k));
        } catch (EquipmentOperationsService.NotFoundException nf) {
            return notFound(ctx, nf.getMessage());
        }
    }

    @PostMapping("/deployment-kits/{kit_id}/deploy")
    public ResponseEntity<?> deployKit(@PathVariable("kit_id") UUID id,
                                       jakarta.servlet.http.HttpServletRequest http) {
        RequestContext ctx = RequestContextHolder.require();
        try {
            return ResponseEntity.ok(kit(ops.deployKit(id, UUID.fromString(ctx.tenantId()), actor(http))));
        } catch (EquipmentOperationsService.NotFoundException nf) {
            return notFound(ctx, nf.getMessage());
        }
    }

    @PostMapping("/deployment-kits/{kit_id}/return")
    public ResponseEntity<?> returnKit(@PathVariable("kit_id") UUID id, @RequestBody KitReturnRequest req,
                                       jakarta.servlet.http.HttpServletRequest http) {
        RequestContext ctx = RequestContextHolder.require();
        Map<UUID, String> conditions = new LinkedHashMap<>();
        Map<UUID, String> notes = new LinkedHashMap<>();
        if (req.items() != null) {
            for (KitReturnItem it : req.items()) {
                if (it.equipmentId() == null) continue;
                if (it.condition() != null) conditions.put(it.equipmentId(), it.condition());
                if (it.note() != null) notes.put(it.equipmentId(), it.note());
            }
        }
        try {
            return ResponseEntity.ok(kit(ops.returnKit(id, UUID.fromString(ctx.tenantId()), conditions, notes, actor(http))));
        } catch (EquipmentOperationsService.NotFoundException nf) {
            return notFound(ctx, nf.getMessage());
        }
    }

    @GetMapping("/deployment-kits")
    public ResponseEntity<?> kits() {
        RequestContext ctx = RequestContextHolder.require();
        return ResponseEntity.ok(Map.of("items",
                ops.kits(UUID.fromString(ctx.tenantId())).stream().map(this::kit).toList()));
    }

    // -- Asset audit --

    @PostMapping("/audits")
    public ResponseEntity<?> openAudit(@RequestBody AuditRequest req,
                                       jakarta.servlet.http.HttpServletRequest http) {
        RequestContext ctx = RequestContextHolder.require();
        if (blank(req.facilityRef())) return bad(ctx, "VALIDATION_ERROR", "facilityRef required");
        AssetAuditEntity a = ops.openAudit(UUID.fromString(ctx.tenantId()), req.facilityRef(),
                blank(req.name()) ? "Audit " + req.facilityRef() : req.name(), actor(http));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("audit_id", a.getAuditId().toString());
        body.put("facility_ref", a.getFacilityRef());
        body.put("status", a.getStatus());
        body.put("items", ops.auditItems(a.getAuditId()).stream().map(this::auditItem).toList());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/audits/items/{item_id}")
    public ResponseEntity<?> confirmAuditItem(@PathVariable("item_id") UUID id, @RequestBody AuditItemRequest req) {
        RequestContext ctx = RequestContextHolder.require();
        try {
            return ResponseEntity.ok(auditItem(ops.confirmAuditItem(id, UUID.fromString(ctx.tenantId()),
                    Boolean.TRUE.equals(req.confirmed()), req.exceptionCode(), req.note())));
        } catch (EquipmentOperationsService.NotFoundException nf) {
            return notFound(ctx, nf.getMessage());
        }
    }

    @PostMapping("/audits/{audit_id}/complete")
    public ResponseEntity<?> completeAudit(@PathVariable("audit_id") UUID id,
                                           jakarta.servlet.http.HttpServletRequest http) {
        RequestContext ctx = RequestContextHolder.require();
        try {
            AssetAuditEntity a = ops.completeAudit(id, UUID.fromString(ctx.tenantId()), actor(http));
            return ResponseEntity.ok(Map.of("audit_id", a.getAuditId().toString(), "status", a.getStatus()));
        } catch (EquipmentOperationsService.NotFoundException nf) {
            return notFound(ctx, nf.getMessage());
        }
    }

    @GetMapping("/audits")
    public ResponseEntity<?> audits(@RequestParam(name = "facility_ref") String facilityRef) {
        RequestContext ctx = RequestContextHolder.require();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", ops.audits(UUID.fromString(ctx.tenantId()), facilityRef).stream()
                .map(a -> Map.of("audit_id", a.getAuditId().toString(), "name", a.getName(),
                        "status", a.getStatus())).toList());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/audits/{audit_id}/items")
    public ResponseEntity<?> auditItems(@PathVariable("audit_id") UUID id) {
        RequestContextHolder.require();
        return ResponseEntity.ok(Map.of("items", ops.auditItems(id).stream().map(this::auditItem).toList()));
    }

    // -- DTO mappers --

    private Map<String, Object> eq(EquipmentEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("equipment_id", e.getEquipmentId().toString());
        m.put("facility_id", e.getFacilityId());
        m.put("name", e.getName());
        m.put("equipment_type", e.getEquipmentType());
        m.put("serial_number", e.getSerialNumber());
        m.put("tag", e.getTag());
        m.put("manufacturer", e.getManufacturer());
        m.put("model", e.getModel());
        m.put("status", e.getStatus());
        m.put("criticality", e.getCriticality());
        m.put("asset_class", e.getAssetClass());
        m.put("regulated", e.isRegulated());
        m.put("custodian_ref", e.getCustodianRef());
        m.put("clinical_device_type", e.getClinicalDeviceType());
        m.put("device_id", e.getDeviceId());
        m.put("location_description", e.getLocationDescription());
        m.put("last_calibration", e.getLastCalibration());
        m.put("next_calibration", e.getNextCalibration());
        m.put("last_maintenance", e.getLastMaintenance());
        m.put("next_maintenance", e.getNextMaintenance());
        return m;
    }

    private Map<String, Object> event(EquipmentLifecycleEventEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("event_id", e.getEventId().toString());
        m.put("event_type", e.getEventType());
        m.put("from", e.getFromValue());
        m.put("to", e.getToValue());
        m.put("actor_ref", e.getActorRef());
        m.put("note", e.getNote());
        m.put("occurred_at", e.getOccurredAt());
        return m;
    }

    private Map<String, Object> task(MaintenanceTaskEntity t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("task_id", t.getTaskId().toString());
        m.put("equipment_id", t.getEquipmentId().toString());
        m.put("task_type", t.getTaskType());
        m.put("title", t.getTitle());
        m.put("description", t.getDescription());
        m.put("priority", t.getPriority());
        m.put("status", t.getStatus());
        m.put("technician_ref", t.getTechnicianRef());
        m.put("spare_part_request_ref", t.getSparePartRequestRef());
        m.put("fault_report_id", t.getFaultReportId());
        m.put("due_date", t.getDueDate());
        m.put("downtime_minutes", t.getDowntimeMinutes());
        m.put("service_report", t.getServiceReport());
        m.put("opened_at", t.getOpenedAt());
        m.put("completed_at", t.getCompletedAt());
        return m;
    }

    private Map<String, Object> cal(CalibrationRecordEntity c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("calibration_id", c.getCalibrationId().toString());
        m.put("equipment_id", c.getEquipmentId().toString());
        m.put("outcome", c.getOutcome());
        m.put("performed_by", c.getPerformedBy());
        m.put("performed_on", c.getPerformedOn());
        m.put("next_due", c.getNextDue());
        m.put("certificate_ref", c.getCertificateRef());
        m.put("notes", c.getNotes());
        return m;
    }

    private Map<String, Object> fault(FaultReportEntity f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fault_id", f.getFaultId().toString());
        m.put("equipment_id", f.getEquipmentId().toString());
        m.put("severity", f.getSeverity());
        m.put("summary", f.getSummary());
        m.put("detail", f.getDetail());
        m.put("safety_concern", f.isSafetyConcern());
        m.put("rito_case_ref", f.getRitoCaseRef());
        m.put("maintenance_task_id", f.getMaintenanceTaskId());
        m.put("status", f.getStatus());
        m.put("reported_at", f.getReportedAt());
        return m;
    }

    private Map<String, Object> transfer(EquipmentTransferEntity t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("transfer_id", t.getTransferId().toString());
        m.put("equipment_id", t.getEquipmentId().toString());
        m.put("from_facility", t.getFromFacility());
        m.put("to_facility", t.getToFacility());
        m.put("to_custodian_ref", t.getToCustodianRef());
        m.put("status", t.getStatus());
        m.put("requires_approval", t.isRequiresApproval());
        m.put("reason", t.getReason());
        m.put("initiated_at", t.getInitiatedAt());
        m.put("received_at", t.getReceivedAt());
        return m;
    }

    private Map<String, Object> alert(IotAlertEntity a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("alert_id", a.getAlertId().toString());
        m.put("equipment_id", a.getEquipmentId());
        m.put("device_id", a.getDeviceId());
        m.put("alert_type", a.getAlertType());
        m.put("metric_type", a.getMetricType());
        m.put("observed_value", a.getObservedValue());
        m.put("threshold_value", a.getThresholdValue());
        m.put("severity", a.getSeverity());
        m.put("status", a.getStatus());
        m.put("khuluma_requested", a.isKhulumaRequested());
        m.put("detail", a.getDetail());
        m.put("observed_at", a.getObservedAt());
        return m;
    }

    private Map<String, Object> kit(DeploymentKitEntity k) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kit_id", k.getKitId().toString());
        m.put("name", k.getName());
        m.put("deployment_ref", k.getDeploymentRef());
        m.put("location_ref", k.getLocationRef());
        m.put("custodian_ref", k.getCustodianRef());
        m.put("status", k.getStatus());
        m.put("deployed_at", k.getDeployedAt());
        m.put("returned_at", k.getReturnedAt());
        m.put("items", ops.kitItems(k.getKitId()).stream().map(i -> {
            Map<String, Object> im = new LinkedHashMap<>();
            im.put("equipment_id", i.getEquipmentId().toString());
            im.put("return_condition", i.getReturnCondition());
            im.put("return_note", i.getReturnNote());
            return im;
        }).toList());
        return m;
    }

    private Map<String, Object> auditItem(AssetAuditItemEntity i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("audit_item_id", i.getAuditItemId().toString());
        m.put("equipment_id", i.getEquipmentId());
        m.put("expected", i.isExpected());
        m.put("confirmed", i.isConfirmed());
        m.put("exception_code", i.getExceptionCode());
        m.put("note", i.getNote());
        return m;
    }

    // -- helpers --

    private String actor(jakarta.servlet.http.HttpServletRequest http) {
        String a = http.getHeader(CompanionHeaders.ACTOR_ID);
        return (a == null || a.isBlank()) ? "system" : a;
    }

    private boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private ResponseEntity<?> notFound(RequestContext ctx, String msg) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorEnvelope.of("NOT_FOUND", msg, ctx.requestId(), ctx.correlationId()));
    }

    private ResponseEntity<?> bad(RequestContext ctx, String code, String msg) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorEnvelope.of(code, msg, ctx.requestId(), ctx.correlationId()));
    }
}
