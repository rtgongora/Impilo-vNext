package zw.gov.mohcc.impilo.assetregistry.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.assetregistry.domain.*;
import zw.gov.mohcc.impilo.assetregistry.repository.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Equipment-operations lifecycle service (WS#5). Owns the operational truth layer over the
 * existing asr_equipment registry: registration, classification/criticality, custody transfers
 * + handover, maintenance tasks, calibration records, fault reports, IoT alerts (derived only),
 * service readiness, deployment kits, and asset audits.
 *
 * <p>Boundary discipline: spare parts → inventory-service/Oros (reference), technicians/custodians
 * → Vashandi (reference), facilities → Tuso/Indawo (reference), clinical observation → Butano,
 * safety incidents → Rito (link). This service never owns those truths.</p>
 *
 * <p>Every meaningful mutation appends a lifecycle event (audit) and an outbox event (integration).</p>
 */
@Service
public class EquipmentOperationsService {

    private static final Logger log = LoggerFactory.getLogger(EquipmentOperationsService.class);
    private static final String AGGREGATE_TYPE = "Equipment";
    private static final String PRODUCER = "asset-registry-service";

    private final EquipmentRepository equipmentRepo;
    private final EquipmentLifecycleEventRepository eventRepo;
    private final EquipmentTransferRepository transferRepo;
    private final MaintenanceTaskRepository maintenanceRepo;
    private final CalibrationRecordRepository calibrationRepo;
    private final FaultReportRepository faultRepo;
    private final IotAlertRepository alertRepo;
    private final ReadinessProfileRepository profileRepo;
    private final ReadinessRequirementRepository requirementRepo;
    private final DeploymentKitRepository kitRepo;
    private final DeploymentKitItemRepository kitItemRepo;
    private final AssetAuditRepository auditRepo;
    private final AssetAuditItemRepository auditItemRepo;
    private final OutboxEventRepository outboxRepo;
    private final ObjectMapper mapper;

    public EquipmentOperationsService(EquipmentRepository equipmentRepo,
                                      EquipmentLifecycleEventRepository eventRepo,
                                      EquipmentTransferRepository transferRepo,
                                      MaintenanceTaskRepository maintenanceRepo,
                                      CalibrationRecordRepository calibrationRepo,
                                      FaultReportRepository faultRepo,
                                      IotAlertRepository alertRepo,
                                      ReadinessProfileRepository profileRepo,
                                      ReadinessRequirementRepository requirementRepo,
                                      DeploymentKitRepository kitRepo,
                                      DeploymentKitItemRepository kitItemRepo,
                                      AssetAuditRepository auditRepo,
                                      AssetAuditItemRepository auditItemRepo,
                                      OutboxEventRepository outboxRepo,
                                      ObjectMapper mapper) {
        this.equipmentRepo = equipmentRepo;
        this.eventRepo = eventRepo;
        this.transferRepo = transferRepo;
        this.maintenanceRepo = maintenanceRepo;
        this.calibrationRepo = calibrationRepo;
        this.faultRepo = faultRepo;
        this.alertRepo = alertRepo;
        this.profileRepo = profileRepo;
        this.requirementRepo = requirementRepo;
        this.kitRepo = kitRepo;
        this.kitItemRepo = kitItemRepo;
        this.auditRepo = auditRepo;
        this.auditItemRepo = auditItemRepo;
        this.outboxRepo = outboxRepo;
        this.mapper = mapper;
    }

    // ── Equipment registry ───────────────────────────────────────────────────

    @Transactional
    public EquipmentEntity register(UUID tenantId, String podId, String correlationId, String idemKey,
                                    String facilityId, String name, String equipmentType,
                                    String serialNumber, String tag, String manufacturer, String model,
                                    String criticality, String assetClass, boolean regulated,
                                    String custodianRef, String clinicalDeviceType, String deviceId) {
        // Duplicate serial / tag detection (tenant-scoped).
        if (serialNumber != null && !serialNumber.isBlank()
                && !equipmentRepo.findByTenantIdAndSerialNumber(tenantId, serialNumber).isEmpty()) {
            throw new DuplicateException("Equipment with serial number already exists: " + serialNumber);
        }
        if (tag != null && !tag.isBlank()
                && !equipmentRepo.findByTenantIdAndTag(tenantId, tag).isEmpty()) {
            throw new DuplicateException("Equipment with tag already exists: " + tag);
        }

        EquipmentEntity eq = new EquipmentEntity(tenantId, facilityId, name, equipmentType);
        eq.setSerialNumber(serialNumber);
        eq.setTag(tag);
        eq.setManufacturer(manufacturer);
        eq.setModel(model);
        if (criticality != null && !criticality.isBlank()) eq.setCriticality(criticality);
        if (assetClass != null && !assetClass.isBlank()) eq.setAssetClass(assetClass);
        eq.setRegulated(regulated);
        eq.setCustodianRef(custodianRef);
        eq.setClinicalDeviceType(clinicalDeviceType);
        eq.setDeviceId(deviceId);
        equipmentRepo.save(eq);

        recordEvent(tenantId, eq.getEquipmentId(), "REGISTERED", null, eq.getStatus(), custodianRef, null);
        emitOutbox("impilo.asset.equipment.registered.v1", eq.getEquipmentId(), tenantId, podId, correlationId,
                idemKey, Map.of("equipment_id", eq.getEquipmentId().toString(), "type", equipmentType,
                        "facility_id", facilityId, "criticality", eq.getCriticality()));
        log.info("Registered equipment [id={}, type={}, facility={}]", eq.getEquipmentId(), equipmentType, facilityId);
        return eq;
    }

    @Transactional(readOnly = true)
    public Optional<EquipmentEntity> get(UUID equipmentId) {
        return equipmentRepo.findById(equipmentId);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<EquipmentEntity> list(UUID tenantId, String facilityId,
                                                                       int page, int size) {
        var pageable = org.springframework.data.domain.PageRequest.of(page, size);
        if (facilityId != null && !facilityId.isBlank()) {
            return equipmentRepo.findByTenantIdAndFacilityIdOrderByNameAsc(tenantId, facilityId, pageable);
        }
        return equipmentRepo.findByTenantIdOrderByUpdatedAtDesc(tenantId, pageable);
    }

    @Transactional
    public EquipmentEntity updateMetadata(UUID equipmentId, UUID tenantId, String custodianRef,
                                          String locationDescription, String criticality) {
        EquipmentEntity eq = requireOwned(equipmentId, tenantId);
        if (custodianRef != null) eq.setCustodianRef(custodianRef);
        if (locationDescription != null) eq.setLocationDescription(locationDescription);
        if (criticality != null && !criticality.isBlank()) eq.setCriticality(criticality);
        eq.setUpdatedAt(OffsetDateTime.now());
        equipmentRepo.save(eq);
        recordEvent(tenantId, equipmentId, "STATUS_CHANGED", null, eq.getStatus(), custodianRef, "metadata edit");
        return eq;
    }

    @Transactional
    public EquipmentEntity changeStatus(UUID equipmentId, UUID tenantId, String newStatus, String actorRef, String note) {
        EquipmentEntity eq = requireOwned(equipmentId, tenantId);
        String prev = eq.getStatus();
        eq.setStatus(newStatus);
        eq.setUpdatedAt(OffsetDateTime.now());
        equipmentRepo.save(eq);
        String eventType = "UNSAFE".equalsIgnoreCase(newStatus) || "BROKEN".equalsIgnoreCase(newStatus)
                ? "MARKED_UNSAFE" : "STATUS_CHANGED";
        recordEvent(tenantId, equipmentId, eventType, prev, newStatus, actorRef, note);
        emitOutbox("impilo.asset.equipment.status.changed.v1", equipmentId, tenantId, null, null, null,
                Map.of("equipment_id", equipmentId.toString(), "from", prev, "to", newStatus));
        return eq;
    }

    // ── Custody transfer + handover ──────────────────────────────────────────

    @Transactional
    public EquipmentTransferEntity initiateTransfer(UUID equipmentId, UUID tenantId, String toFacility,
                                                    String toLocation, String toCustodianRef, String reason,
                                                    boolean requiresApproval, String actorRef) {
        EquipmentEntity eq = requireOwned(equipmentId, tenantId);
        EquipmentTransferEntity t = new EquipmentTransferEntity(tenantId, equipmentId, toFacility);
        t.setFromFacility(eq.getFacilityId());
        t.setFromLocation(eq.getLocationDescription());
        t.setToLocation(toLocation);
        t.setToCustodianRef(toCustodianRef);
        t.setReason(reason);
        t.setRequiresApproval(requiresApproval);
        t.setInitiatedBy(actorRef);
        t.setStatus(requiresApproval ? "PENDING" : "APPROVED");
        transferRepo.save(t);
        recordEvent(tenantId, equipmentId, "TRANSFERRED", eq.getFacilityId(), toFacility, actorRef, reason);
        emitOutbox("impilo.asset.equipment.transfer.initiated.v1", equipmentId, tenantId, null, null, null,
                Map.of("transfer_id", t.getTransferId().toString(), "to_facility", toFacility));
        return t;
    }

    @Transactional
    public EquipmentTransferEntity approveTransfer(UUID transferId, UUID tenantId, String approver) {
        EquipmentTransferEntity t = transferRepo.findById(transferId)
                .filter(x -> x.getTenantId().equals(tenantId))
                .orElseThrow(() -> new NotFoundException("Transfer not found: " + transferId));
        t.setStatus("APPROVED");
        t.setApprovedBy(approver);
        transferRepo.save(t);
        recordEvent(tenantId, t.getEquipmentId(), "TRANSFERRED", "PENDING", "APPROVED", approver, "approved");
        return t;
    }

    @Transactional
    public EquipmentTransferEntity confirmReceipt(UUID transferId, UUID tenantId, String receiver) {
        EquipmentTransferEntity t = transferRepo.findById(transferId)
                .filter(x -> x.getTenantId().equals(tenantId))
                .orElseThrow(() -> new NotFoundException("Transfer not found: " + transferId));
        if (!"APPROVED".equals(t.getStatus())) {
            throw new InvalidStateException("Transfer must be APPROVED before receipt: " + t.getStatus());
        }
        t.setStatus("RECEIVED");
        t.setReceivedBy(receiver);
        t.setReceivedAt(OffsetDateTime.now());
        transferRepo.save(t);
        // Apply the transfer to the equipment record.
        EquipmentEntity eq = requireOwned(t.getEquipmentId(), tenantId);
        eq.setFacilityId(t.getToFacility());
        if (t.getToLocation() != null) eq.setLocationDescription(t.getToLocation());
        if (t.getToCustodianRef() != null) eq.setCustodianRef(t.getToCustodianRef());
        eq.setUpdatedAt(OffsetDateTime.now());
        equipmentRepo.save(eq);
        recordEvent(tenantId, t.getEquipmentId(), "RECEIVED", null, t.getToFacility(), receiver, "handover confirmed");
        emitOutbox("impilo.asset.equipment.transfer.received.v1", t.getEquipmentId(), tenantId, null, null, null,
                Map.of("transfer_id", transferId.toString(), "to_facility", t.getToFacility()));
        return t;
    }

    @Transactional(readOnly = true)
    public List<EquipmentTransferEntity> transfers(UUID equipmentId) {
        return transferRepo.findByEquipmentIdOrderByInitiatedAtDesc(equipmentId);
    }

    // ── Maintenance ──────────────────────────────────────────────────────────

    @Transactional
    public MaintenanceTaskEntity openMaintenance(UUID equipmentId, UUID tenantId, String taskType, String title,
                                                 String description, String priority, LocalDate dueDate,
                                                 String technicianRef, String sparePartRequestRef,
                                                 UUID faultReportId, String actorRef) {
        requireOwned(equipmentId, tenantId);
        MaintenanceTaskEntity task = new MaintenanceTaskEntity(tenantId, equipmentId,
                taskType == null ? "CORRECTIVE" : taskType, title);
        task.setDescription(description);
        if (priority != null && !priority.isBlank()) task.setPriority(priority);
        task.setDueDate(dueDate);
        task.setTechnicianRef(technicianRef);
        task.setSparePartRequestRef(sparePartRequestRef);
        task.setFaultReportId(faultReportId);
        task.setOpenedBy(actorRef);
        if (technicianRef != null && !technicianRef.isBlank()) task.setStatus("ASSIGNED");
        maintenanceRepo.save(task);
        recordEvent(tenantId, equipmentId, "MAINTENANCE_OPENED", null, task.getStatus(), actorRef, title);
        emitOutbox("impilo.asset.maintenance.opened.v1", equipmentId, tenantId, null, null, null,
                Map.of("task_id", task.getTaskId().toString(), "type", task.getTaskType()));
        return task;
    }

    @Transactional
    public MaintenanceTaskEntity updateMaintenance(UUID taskId, UUID tenantId, String status,
                                                   String technicianRef, String serviceReport,
                                                   Integer downtimeMinutes, String actorRef) {
        MaintenanceTaskEntity task = maintenanceRepo.findById(taskId)
                .filter(x -> x.getTenantId().equals(tenantId))
                .orElseThrow(() -> new NotFoundException("Maintenance task not found: " + taskId));
        if (status != null && !status.isBlank()) task.setStatus(status);
        if (technicianRef != null) task.setTechnicianRef(technicianRef);
        if (serviceReport != null) task.setServiceReport(serviceReport);
        if (downtimeMinutes != null) task.setDowntimeMinutes(downtimeMinutes);
        maintenanceRepo.save(task);
        recordEvent(tenantId, task.getEquipmentId(), "MAINTENANCE_OPENED", null, task.getStatus(), actorRef, "update");
        return task;
    }

    /** Complete a maintenance task and (optionally) return the equipment to service. */
    @Transactional
    public MaintenanceTaskEntity completeMaintenance(UUID taskId, UUID tenantId, String serviceReport,
                                                     boolean returnToService, String approver) {
        MaintenanceTaskEntity task = maintenanceRepo.findById(taskId)
                .filter(x -> x.getTenantId().equals(tenantId))
                .orElseThrow(() -> new NotFoundException("Maintenance task not found: " + taskId));
        task.setStatus("COMPLETED");
        task.setCompletedBy(approver);
        task.setCompletedAt(OffsetDateTime.now());
        if (serviceReport != null) task.setServiceReport(serviceReport);
        maintenanceRepo.save(task);
        recordEvent(tenantId, task.getEquipmentId(), "MAINTENANCE_CLOSED", null, "COMPLETED", approver, serviceReport);

        EquipmentEntity eq = equipmentRepo.findById(task.getEquipmentId()).orElse(null);
        if (eq != null) {
            eq.setLastMaintenance(LocalDate.now());
            if (returnToService) {
                eq.setStatus("OPERATIONAL");
                recordEvent(tenantId, eq.getEquipmentId(), "RETURNED_TO_SERVICE", null, "OPERATIONAL", approver, null);
            }
            eq.setUpdatedAt(OffsetDateTime.now());
            equipmentRepo.save(eq);
        }
        emitOutbox("impilo.asset.maintenance.completed.v1", task.getEquipmentId(), tenantId, null, null, null,
                Map.of("task_id", taskId.toString(), "returned_to_service", String.valueOf(returnToService)));
        return task;
    }

    @Transactional(readOnly = true)
    public List<MaintenanceTaskEntity> maintenanceForEquipment(UUID equipmentId) {
        return maintenanceRepo.findByEquipmentIdOrderByOpenedAtDesc(equipmentId);
    }

    @Transactional(readOnly = true)
    public List<MaintenanceTaskEntity> openMaintenance(UUID tenantId) {
        return maintenanceRepo.findByTenantIdAndStatusOrderByOpenedAtDesc(tenantId, "OPEN");
    }

    @Transactional(readOnly = true)
    public List<EquipmentEntity> maintenanceDue(UUID tenantId) {
        return equipmentRepo.findMaintenanceDue(tenantId, LocalDate.now());
    }

    // ── Calibration ──────────────────────────────────────────────────────────

    @Transactional
    public CalibrationRecordEntity recordCalibration(UUID equipmentId, UUID tenantId, String outcome,
                                                     LocalDate performedOn, LocalDate nextDue,
                                                     String performedBy, String certificateRef, String notes) {
        EquipmentEntity eq = requireOwned(equipmentId, tenantId);
        CalibrationRecordEntity rec = new CalibrationRecordEntity(tenantId, equipmentId,
                outcome, performedOn != null ? performedOn : LocalDate.now());
        rec.setNextDue(nextDue);
        rec.setPerformedBy(performedBy);
        rec.setCertificateRef(certificateRef);
        rec.setNotes(notes);
        calibrationRepo.save(rec);

        eq.setLastCalibration(rec.getPerformedOn());
        eq.setNextCalibration(nextDue);
        // FAIL calibration flags the equipment unsafe → readiness gap.
        if ("FAIL".equalsIgnoreCase(outcome)) {
            eq.setStatus("UNSAFE");
            recordEvent(tenantId, equipmentId, "MARKED_UNSAFE", null, "UNSAFE", performedBy, "calibration failed");
        }
        eq.setUpdatedAt(OffsetDateTime.now());
        equipmentRepo.save(eq);
        recordEvent(tenantId, equipmentId, "CALIBRATED", null, outcome, performedBy, certificateRef);
        emitOutbox("impilo.asset.calibration.recorded.v1", equipmentId, tenantId, null, null, null,
                Map.of("calibration_id", rec.getCalibrationId().toString(), "outcome", outcome));
        return rec;
    }

    @Transactional(readOnly = true)
    public List<CalibrationRecordEntity> calibrationsForEquipment(UUID equipmentId) {
        return calibrationRepo.findByEquipmentIdOrderByPerformedOnDesc(equipmentId);
    }

    @Transactional(readOnly = true)
    public List<EquipmentEntity> calibrationDue(UUID tenantId) {
        return equipmentRepo.findCalibrationDue(tenantId, LocalDate.now());
    }

    // ── Fault reporting (safety → Rito link; else maintenance task) ──────────

    @Transactional
    public FaultReportEntity reportFault(UUID equipmentId, UUID tenantId, String severity, String summary,
                                         String detail, boolean safetyConcern, String reportedBy) {
        requireOwned(equipmentId, tenantId);
        FaultReportEntity fault = new FaultReportEntity(tenantId, equipmentId, summary);
        if (severity != null && !severity.isBlank()) fault.setSeverity(severity);
        fault.setDetail(detail);
        fault.setSafetyConcern(safetyConcern || "SAFETY".equalsIgnoreCase(severity));
        fault.setReportedBy(reportedBy);

        if (fault.isSafetyConcern()) {
            // Link to Rito (not owned here). The BFF requests a Rito case; we store the ref when known.
            fault.setStatus("LINKED");
        } else {
            // Routine fault → spawn a corrective maintenance task.
            MaintenanceTaskEntity task = new MaintenanceTaskEntity(tenantId, equipmentId, "CORRECTIVE",
                    "Corrective: " + summary);
            task.setDescription(detail);
            task.setPriority("MAJOR".equalsIgnoreCase(severity) ? "HIGH" : "NORMAL");
            task.setOpenedBy(reportedBy);
            maintenanceRepo.save(task);
            fault.setMaintenanceTaskId(task.getTaskId());
            fault.setStatus("TRIAGED");
            recordEvent(tenantId, equipmentId, "MAINTENANCE_OPENED", null, "OPEN", reportedBy, "from fault");
        }
        faultRepo.save(fault);
        recordEvent(tenantId, equipmentId, "FAULT_REPORTED", null, fault.getSeverity(), reportedBy, summary);
        emitOutbox("impilo.asset.fault.reported.v1", equipmentId, tenantId, null, null, null,
                Map.of("fault_id", fault.getFaultId().toString(), "severity", fault.getSeverity(),
                        "safety_concern", String.valueOf(fault.isSafetyConcern())));
        return fault;
    }

    @Transactional
    public FaultReportEntity linkRitoCase(UUID faultId, UUID tenantId, String ritoCaseRef) {
        FaultReportEntity fault = faultRepo.findById(faultId)
                .filter(x -> x.getTenantId().equals(tenantId))
                .orElseThrow(() -> new NotFoundException("Fault not found: " + faultId));
        fault.setRitoCaseRef(ritoCaseRef);
        fault.setStatus("LINKED");
        faultRepo.save(fault);
        recordEvent(tenantId, fault.getEquipmentId(), "FAULT_REPORTED", null, "LINKED", null, "rito:" + ritoCaseRef);
        return fault;
    }

    @Transactional(readOnly = true)
    public List<FaultReportEntity> faultsForEquipment(UUID equipmentId) {
        return faultRepo.findByEquipmentIdOrderByReportedAtDesc(equipmentId);
    }

    // ── IoT alerts (derived from REAL telemetry — never fabricated) ──────────

    /**
     * Records a derived IoT alert from a real device signal. Called by the telemetry consumer
     * or the alert-ingest API; never invents readings. Returns null when no equipment is linked
     * and the alert cannot be attributed.
     */
    @Transactional
    public IotAlertEntity raiseAlert(UUID tenantId, String deviceId, String alertType, String metricType,
                                     Double observedValue, Double thresholdValue, String severity, String detail) {
        // Suppress duplicate ACTIVE alerts of the same type for the same device.
        List<IotAlertEntity> existing = alertRepo.findByDeviceIdAndAlertTypeAndStatus(deviceId, alertType, "ACTIVE");
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        IotAlertEntity alert = new IotAlertEntity(tenantId, deviceId, alertType);
        alert.setMetricType(metricType);
        alert.setObservedValue(observedValue);
        alert.setThresholdValue(thresholdValue);
        if (severity != null && !severity.isBlank()) alert.setSeverity(severity);
        alert.setDetail(detail);
        // Attribute to equipment via device_id link.
        List<EquipmentEntity> linked = equipmentRepo.findByDeviceId(deviceId);
        if (!linked.isEmpty()) {
            EquipmentEntity eq = linked.get(0);
            alert.setEquipmentId(eq.getEquipmentId());
            if ("CRITICAL".equalsIgnoreCase(eq.getCriticality())) {
                alert.setSeverity("CRITICAL");
            }
        }
        alertRepo.save(alert);
        recordEvent(tenantId, alert.getEquipmentId(), "IOT_ALERT", null, alertType, deviceId, detail);
        emitOutbox("impilo.asset.iot.alert.raised.v1",
                alert.getEquipmentId() != null ? alert.getEquipmentId() : UUID.fromString(deterministicUuid(deviceId)),
                tenantId, null, null, null,
                Map.of("device_id", deviceId, "alert_type", alertType, "severity", alert.getSeverity()));
        log.info("IoT alert raised [device={}, type={}, severity={}]", deviceId, alertType, alert.getSeverity());
        return alert;
    }

    @Transactional
    public IotAlertEntity resolveAlert(UUID alertId, UUID tenantId, String resolvedBy) {
        IotAlertEntity alert = alertRepo.findById(alertId)
                .filter(x -> x.getTenantId().equals(tenantId))
                .orElseThrow(() -> new NotFoundException("Alert not found: " + alertId));
        alert.setStatus("RESOLVED");
        alert.setResolvedAt(OffsetDateTime.now());
        alert.setResolvedBy(resolvedBy);
        alertRepo.save(alert);
        recordEvent(tenantId, alert.getEquipmentId(), "IOT_ALERT", "ACTIVE", "RESOLVED", resolvedBy, null);
        return alert;
    }

    @Transactional
    public void markAlertKhulumaRequested(UUID alertId) {
        alertRepo.findById(alertId).ifPresent(a -> { a.setKhulumaRequested(true); alertRepo.save(a); });
    }

    @Transactional(readOnly = true)
    public List<IotAlertEntity> activeAlerts(UUID tenantId) {
        return alertRepo.findByTenantIdAndStatusOrderByObservedAtDesc(tenantId, "ACTIVE");
    }

    @Transactional(readOnly = true)
    public List<IotAlertEntity> alertsForEquipment(UUID equipmentId) {
        return alertRepo.findByEquipmentIdOrderByObservedAtDesc(equipmentId);
    }

    // ── Service readiness ────────────────────────────────────────────────────

    @Transactional
    public ReadinessProfileEntity createProfile(UUID tenantId, String facilityRef, String servicePoint,
                                                String name, List<ReadinessRequirementEntity> requirements) {
        ReadinessProfileEntity profile = new ReadinessProfileEntity(tenantId, facilityRef, servicePoint, name);
        profileRepo.save(profile);
        if (requirements != null) {
            for (ReadinessRequirementEntity r : requirements) {
                ReadinessRequirementEntity req = new ReadinessRequirementEntity(profile.getProfileId(),
                        r.getEquipmentType(), r.getMinCount());
                req.setMustBeCritical(r.isMustBeCritical());
                req.setNote(r.getNote());
                requirementRepo.save(req);
            }
        }
        return profile;
    }

    /** Compute real readiness for a facility: for each service-point profile, check each required
     *  equipment-type count vs operational assigned equipment. Returns gaps, never a decorative score. */
    @Transactional(readOnly = true)
    public Map<String, Object> computeReadiness(UUID tenantId, String facilityRef) {
        List<ReadinessProfileEntity> profiles = profileRepo.findByTenantIdAndFacilityRef(tenantId, facilityRef);
        List<Map<String, Object>> profileResults = new ArrayList<>();
        int totalGaps = 0;
        for (ReadinessProfileEntity profile : profiles) {
            List<ReadinessRequirementEntity> reqs = requirementRepo.findByProfileId(profile.getProfileId());
            List<Map<String, Object>> reqResults = new ArrayList<>();
            boolean profileReady = true;
            for (ReadinessRequirementEntity req : reqs) {
                List<EquipmentEntity> matches = equipmentRepo.findByTenantIdAndFacilityIdAndEquipmentType(
                        tenantId, facilityRef, req.getEquipmentType());
                long available = matches.stream().filter(this::isOperational).count();
                boolean met = available >= req.getMinCount();
                if (!met) { profileReady = false; totalGaps++; }
                Map<String, Object> rr = new LinkedHashMap<>();
                rr.put("equipment_type", req.getEquipmentType());
                rr.put("required", req.getMinCount());
                rr.put("available", available);
                rr.put("met", met);
                rr.put("gap", met ? "NONE" : describeGap(matches));
                reqResults.add(rr);
            }
            Map<String, Object> pr = new LinkedHashMap<>();
            pr.put("service_point", profile.getServicePoint());
            pr.put("name", profile.getName());
            pr.put("ready", profileReady);
            pr.put("requirements", reqResults);
            profileResults.add(pr);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("facility_ref", facilityRef);
        out.put("ready", totalGaps == 0 && !profiles.isEmpty());
        out.put("total_gaps", totalGaps);
        out.put("profiles", profileResults);
        return out;
    }

    private boolean isOperational(EquipmentEntity e) {
        String s = e.getStatus();
        return s != null && ("OPERATIONAL".equalsIgnoreCase(s) || "AVAILABLE".equalsIgnoreCase(s)
                || "IN_USE".equalsIgnoreCase(s));
    }

    private String describeGap(List<EquipmentEntity> matches) {
        if (matches.isEmpty()) return "MISSING";
        for (EquipmentEntity e : matches) {
            String s = e.getStatus() == null ? "" : e.getStatus().toUpperCase();
            if (s.contains("BROKEN")) return "BROKEN";
            if (s.contains("UNSAFE")) return "UNCALIBRATED";
            if (s.contains("MAINTENANCE")) return "UNDER_MAINTENANCE";
        }
        return "INSUFFICIENT";
    }

    @Transactional(readOnly = true)
    public List<ReadinessProfileEntity> profiles(UUID tenantId, String facilityRef) {
        return profileRepo.findByTenantIdAndFacilityRef(tenantId, facilityRef);
    }

    // ── Deployment kits ──────────────────────────────────────────────────────

    @Transactional
    public DeploymentKitEntity createKit(UUID tenantId, String name, String deploymentRef, String locationRef,
                                         String custodianRef, List<UUID> equipmentIds, String actorRef) {
        DeploymentKitEntity kit = new DeploymentKitEntity(tenantId, name);
        kit.setDeploymentRef(deploymentRef);
        kit.setLocationRef(locationRef);
        kit.setCustodianRef(custodianRef);
        kitRepo.save(kit);
        if (equipmentIds != null) {
            for (UUID eqId : equipmentIds) {
                requireOwned(eqId, tenantId);
                kitItemRepo.save(new DeploymentKitItemEntity(kit.getKitId(), eqId));
                recordEvent(tenantId, eqId, "DEPLOYED", null, "PREPARED", actorRef, "kit:" + kit.getKitId());
            }
        }
        emitOutbox("impilo.asset.deployment.kit.created.v1", kit.getKitId(), tenantId, null, null, null,
                Map.of("kit_id", kit.getKitId().toString(), "name", name));
        return kit;
    }

    @Transactional
    public DeploymentKitEntity deployKit(UUID kitId, UUID tenantId, String actorRef) {
        DeploymentKitEntity kit = requireKit(kitId, tenantId);
        kit.setStatus("DEPLOYED");
        kit.setDeployedAt(OffsetDateTime.now());
        kitRepo.save(kit);
        for (DeploymentKitItemEntity item : kitItemRepo.findByKitId(kitId)) {
            recordEvent(tenantId, item.getEquipmentId(), "DEPLOYED", null, "DEPLOYED", actorRef, "kit:" + kitId);
        }
        return kit;
    }

    @Transactional
    public DeploymentKitEntity returnKit(UUID kitId, UUID tenantId, Map<UUID, String> itemConditions,
                                         Map<UUID, String> itemNotes, String actorRef) {
        DeploymentKitEntity kit = requireKit(kitId, tenantId);
        kit.setStatus("RETURNED");
        kit.setReturnedAt(OffsetDateTime.now());
        kitRepo.save(kit);
        for (DeploymentKitItemEntity item : kitItemRepo.findByKitId(kitId)) {
            String cond = itemConditions != null ? itemConditions.getOrDefault(item.getEquipmentId(), "OK") : "OK";
            item.setReturnCondition(cond);
            if (itemNotes != null) item.setReturnNote(itemNotes.get(item.getEquipmentId()));
            kitItemRepo.save(item);
            // Loss/damage on return flags the equipment.
            if ("LOST".equalsIgnoreCase(cond)) {
                changeStatus(item.getEquipmentId(), tenantId, "LOST", actorRef, "lost on deployment");
            } else if ("DAMAGED".equalsIgnoreCase(cond)) {
                changeStatus(item.getEquipmentId(), tenantId, "BROKEN", actorRef, "damaged on deployment");
            }
        }
        emitOutbox("impilo.asset.deployment.kit.returned.v1", kitId, tenantId, null, null, null,
                Map.of("kit_id", kitId.toString()));
        return kit;
    }

    @Transactional(readOnly = true)
    public List<DeploymentKitEntity> kits(UUID tenantId) {
        return kitRepo.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Transactional(readOnly = true)
    public List<DeploymentKitItemEntity> kitItems(UUID kitId) {
        return kitItemRepo.findByKitId(kitId);
    }

    // ── Asset audit / verification ───────────────────────────────────────────

    @Transactional
    public AssetAuditEntity openAudit(UUID tenantId, String facilityRef, String name, String conductedBy) {
        AssetAuditEntity audit = new AssetAuditEntity(tenantId, facilityRef, name);
        audit.setConductedBy(conductedBy);
        auditRepo.save(audit);
        // Seed expected items from the current equipment list at the facility.
        for (EquipmentEntity eq : equipmentRepo.findByTenantIdAndFacilityIdOrderByNameAsc(
                tenantId, facilityRef, org.springframework.data.domain.PageRequest.of(0, 500)).getContent()) {
            auditItemRepo.save(new AssetAuditItemEntity(audit.getAuditId(), eq.getEquipmentId()));
        }
        return audit;
    }

    @Transactional
    public AssetAuditItemEntity confirmAuditItem(UUID auditItemId, UUID tenantId, boolean confirmed,
                                                 String exceptionCode, String note) {
        AssetAuditItemEntity item = auditItemRepo.findById(auditItemId)
                .orElseThrow(() -> new NotFoundException("Audit item not found: " + auditItemId));
        item.setConfirmed(confirmed);
        item.setExceptionCode(exceptionCode);
        item.setNote(note);
        auditItemRepo.save(item);
        return item;
    }

    @Transactional
    public AssetAuditEntity completeAudit(UUID auditId, UUID tenantId, String actorRef) {
        AssetAuditEntity audit = auditRepo.findById(auditId)
                .filter(x -> x.getTenantId().equals(tenantId))
                .orElseThrow(() -> new NotFoundException("Audit not found: " + auditId));
        audit.setStatus("COMPLETED");
        audit.setCompletedAt(OffsetDateTime.now());
        auditRepo.save(audit);
        long exceptions = auditItemRepo.findByAuditId(auditId).stream()
                .filter(i -> i.getExceptionCode() != null || !i.isConfirmed()).count();
        emitOutbox("impilo.asset.audit.completed.v1", auditId, tenantId, null, null, null,
                Map.of("audit_id", auditId.toString(), "exceptions", String.valueOf(exceptions)));
        return audit;
    }

    @Transactional(readOnly = true)
    public List<AssetAuditEntity> audits(UUID tenantId, String facilityRef) {
        return auditRepo.findByTenantIdAndFacilityRefOrderByOpenedAtDesc(tenantId, facilityRef);
    }

    @Transactional(readOnly = true)
    public List<AssetAuditItemEntity> auditItems(UUID auditId) {
        return auditItemRepo.findByAuditId(auditId);
    }

    @Transactional(readOnly = true)
    public List<EquipmentLifecycleEventEntity> history(UUID equipmentId) {
        return eventRepo.findByEquipmentIdOrderByOccurredAtDesc(equipmentId);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private EquipmentEntity requireOwned(UUID equipmentId, UUID tenantId) {
        return equipmentRepo.findById(equipmentId)
                .filter(e -> e.getTenantId().equals(tenantId))
                .orElseThrow(() -> new NotFoundException("Equipment not found: " + equipmentId));
    }

    private DeploymentKitEntity requireKit(UUID kitId, UUID tenantId) {
        return kitRepo.findById(kitId)
                .filter(k -> k.getTenantId().equals(tenantId))
                .orElseThrow(() -> new NotFoundException("Deployment kit not found: " + kitId));
    }

    private void recordEvent(UUID tenantId, UUID equipmentId, String eventType, String from, String to,
                             String actorRef, String note) {
        if (equipmentId == null) return;
        EquipmentLifecycleEventEntity ev = new EquipmentLifecycleEventEntity(tenantId, equipmentId, eventType);
        ev.setFromValue(from);
        ev.setToValue(to);
        ev.setActorRef(actorRef);
        ev.setNote(note);
        eventRepo.save(ev);
    }

    private void emitOutbox(String eventType, UUID aggregateId, UUID tenantId, String podId, String correlationId,
                            String idemKey, Map<String, Object> payload) {
        OutboxEventEntity ob = new OutboxEventEntity();
        ob.setAggregateType(AGGREGATE_TYPE);
        ob.setAggregateId(aggregateId.toString());
        ob.setEventType(eventType);
        ob.setSchemaVersion("1");
        ob.setCorrelationId(correlationId != null ? safeUuid(correlationId) : null);
        ob.setCausationId(correlationId != null ? safeUuid(correlationId) : null);
        ob.setIdempotencyKey(idemKey != null ? idemKey : UUID.randomUUID().toString());
        ob.setProducer(PRODUCER);
        ob.setTenantId(tenantId);
        ob.setPodId(podId != null ? podId : "national-spine");
        ob.setSubjectId(aggregateId.toString());
        ob.setSubjectType(AGGREGATE_TYPE);
        ob.setOccurredAt(OffsetDateTime.now());
        ob.setPayloadJson(toJson(payload));
        ob.setPartitionKey(aggregateId.toString());
        outboxRepo.save(ob);
    }

    private UUID safeUuid(String s) {
        try { return UUID.fromString(s); } catch (Exception e) { return null; }
    }

    private String deterministicUuid(String seed) {
        return UUID.nameUUIDFromBytes(("device:" + seed).getBytes()).toString();
    }

    private String toJson(Object o) {
        try { return mapper.writeValueAsString(o); } catch (JsonProcessingException e) { return "{}"; }
    }

    // ── exceptions ───────────────────────────────────────────────────────────

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String m) { super(m); }
    }

    public static class DuplicateException extends RuntimeException {
        public DuplicateException(String m) { super(m); }
    }

    public static class InvalidStateException extends RuntimeException {
        public InvalidStateException(String m) { super(m); }
    }
}
