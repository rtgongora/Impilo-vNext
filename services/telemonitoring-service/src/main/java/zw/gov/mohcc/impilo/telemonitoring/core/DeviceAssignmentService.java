package zw.gov.mohcc.impilo.telemonitoring.core;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.telemonitoring.domain.CalibrationPosture;
import zw.gov.mohcc.impilo.telemonitoring.domain.DeviceAssignmentStatus;
import zw.gov.mohcc.impilo.telemonitoring.domain.PlanStatus;
import zw.gov.mohcc.impilo.telemonitoring.events.TelemonitoringEventEmitter;
import zw.gov.mohcc.impilo.telemonitoring.integration.AssetRegistryClient;
import zw.gov.mohcc.impilo.telemonitoring.integration.AssetRegistryClient.EquipmentCalibration;
import zw.gov.mohcc.impilo.telemonitoring.integration.IotDeviceRegistryClient;
import zw.gov.mohcc.impilo.telemonitoring.integration.IotDeviceRegistryClient.DeviceVerification;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.DeviceAssignmentEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.MonitoringPlanEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.DeviceAssignmentRepository;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.MonitoringPlanRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Device clinical-assignment engine (OF-B24, Vol II §15.5/§15.6) — the CLINICAL leg of
 * the §6.2 three-way device split. This service composes the other two legs at
 * decision/read time and never copies their truth:
 *
 * <ul>
 *   <li><b>Fail-closed assignment</b> — a device acquires a clinical binding only after
 *       iot-ingestion (digital-identity SoR) confirms it exists with a usable trust
 *       grade. NOT_FOUND, REVOKED and UNREACHABLE all refuse with
 *       {@code DEVICE_UNVERIFIED}: an unverifiable device must never be bound to a
 *       patient.</li>
 *   <li><b>Plan gate</b> — assignment demands an ACTIVE monitoring plan (a device is
 *       assigned under a clinician-approved plan, never free-floating).</li>
 *   <li><b>Calibration at assignment</b> — §15.6: a CONFIRMED-lapsed calibration blocks
 *       assignment ({@code TM_CALIBRATION_EXPIRED}); an unreachable/unknown asset does
 *       NOT block (asset truth is stamping-grade, not gating-grade, at this seam — the
 *       resolve gate stamps posture UNVERIFIED on every lookup instead).</li>
 *   <li><b>One device, one patient</b> — a second live assignment of the same device is
 *       refused pre-flight and, under a true race, by the DB unique guard
 *       ({@code device_active_guard} + the V003 partial-unique backstop).</li>
 *   <li><b>The assignment gate</b> — {@link #resolveAssignment}: the lookup OF-B25
 *       ingestion calls per reading to attribute a device to {patient, plan} and learn
 *       the calibration posture. Asset problems are STAMPED (DEGRADED / UNVERIFIED),
 *       never blocking the lookup (§15.5 invariant, §15.7 nothing-silently-dropped).</li>
 * </ul>
 */
@Service
public class DeviceAssignmentService {

    private static final Logger log = LoggerFactory.getLogger(DeviceAssignmentService.class);

    private final DeviceAssignmentRepository assignmentRepository;
    private final MonitoringPlanRepository planRepository;
    private final IotDeviceRegistryClient iotDeviceRegistryClient;
    private final AssetRegistryClient assetRegistryClient;
    private final TelemonitoringEventEmitter eventEmitter;

    public DeviceAssignmentService(DeviceAssignmentRepository assignmentRepository,
                                   MonitoringPlanRepository planRepository,
                                   IotDeviceRegistryClient iotDeviceRegistryClient,
                                   AssetRegistryClient assetRegistryClient,
                                   TelemonitoringEventEmitter eventEmitter) {
        this.assignmentRepository = assignmentRepository;
        this.planRepository = planRepository;
        this.iotDeviceRegistryClient = iotDeviceRegistryClient;
        this.assetRegistryClient = assetRegistryClient;
        this.eventEmitter = eventEmitter;
    }

    // ── Commands ──

    public record AssignCommand(
            UUID tenantId,
            UUID planId,
            String deviceRef,
            String assetRef,
            String assignmentType,
            String assignedBy,
            String notes) {
    }

    /** The assignment-gate answer consumed by OF-B25 ingestion. */
    public record ResolvedAssignment(
            UUID assignmentId,
            UUID tenantId,
            UUID planId,
            String patientCpid,
            String deviceRef,
            String assetRef,
            DeviceAssignmentStatus status,
            CalibrationPosture calibrationPosture,
            String calibrationPostureReason,
            OffsetDateTime activatedAt) {
    }

    // ── Assignment (§15.6) ──

    @Transactional
    public DeviceAssignmentEntity assign(AssignCommand cmd, HttpServletRequest inbound) {
        require(cmd.tenantId() != null, "tenantId is required");
        require(cmd.planId() != null, "planId is required");
        requireText(cmd.deviceRef(), "deviceRef is required");
        requireText(cmd.assignedBy(), "assignedBy (assigner identity) is required (§15.6 accountability)");

        // 1. Plan gate: the clinical binding exists only under an ACTIVE, tenant-matched plan.
        MonitoringPlanEntity plan = planRepository.findById(cmd.planId())
                .orElseThrow(() -> new TelemonitoringDomainException("TM_PLAN_NOT_FOUND", 404,
                        "Monitoring plan not found: " + cmd.planId()));
        if (!plan.getTenantId().equals(cmd.tenantId())) {
            throw new TelemonitoringDomainException("TM_PLAN_NOT_FOUND", 404,
                    "Monitoring plan not found: " + cmd.planId());
        }
        if (plan.getStatus() != PlanStatus.ACTIVE) {
            throw new TelemonitoringDomainException("TM_PLAN_NOT_ACTIVE", 409,
                    "Devices can only be assigned under an ACTIVE monitoring plan (current: "
                            + plan.getStatus() + ")");
        }

        // 2. Digital-identity gate (fail-closed): iot-ingestion must confirm the device.
        DeviceVerification verification = iotDeviceRegistryClient.verify(cmd.deviceRef(), cmd.tenantId(), inbound);
        if (verification.outcome() != IotDeviceRegistryClient.Outcome.VERIFIED
                || "REVOKED".equalsIgnoreCase(verification.trustLevel())) {
            String detail = switch (verification.outcome()) {
                case NOT_FOUND -> "the device registry does not know device " + cmd.deviceRef();
                case UNREACHABLE -> "the device registry is unreachable — refusing rather than assigning blind";
                case VERIFIED -> "device " + cmd.deviceRef() + " is REVOKED in the device registry";
            };
            throw new TelemonitoringDomainException("DEVICE_UNVERIFIED", 422,
                    "Device identity could not be verified: " + detail
                            + ". Clinical assignment is fail-closed (Vol II §6.2/§15.6).");
        }

        // 3. One device, one patient: pre-flight check (the DB unique guard backstops the race).
        assignmentRepository.findByDeviceActiveGuard(cmd.deviceRef()).ifPresent(existing -> {
            throw new TelemonitoringDomainException("TM_DEVICE_ALREADY_ASSIGNED", 409,
                    "Device " + cmd.deviceRef() + " already has a live assignment ("
                            + existing.getId() + ", status " + existing.getStatus()
                            + "). One device serves one patient at a time (§15.6); end that assignment first.");
        });

        // 4. Calibration at assignment (§15.6): a CONFIRMED-lapsed calibration blocks.
        if (cmd.assetRef() != null && !cmd.assetRef().isBlank()) {
            EquipmentCalibration calibration =
                    assetRegistryClient.calibrationState(cmd.assetRef(), cmd.tenantId(), inbound);
            if (calibration.calibrationLapsed(LocalDate.now())) {
                throw new TelemonitoringDomainException("TM_CALIBRATION_EXPIRED", 422,
                        "Asset " + cmd.assetRef() + " has lapsed calibration (next due "
                                + calibration.nextCalibration() + ") — expired calibration blocks assignment (§15.6). "
                                + "Recalibrate or assign a different device.");
            }
            if (calibration.outcome() != AssetRegistryClient.Outcome.FOUND) {
                log.warn("Asset {} could not be verified at assignment ({}); assignment proceeds — the resolve "
                        + "gate stamps posture UNVERIFIED on every lookup", cmd.assetRef(), calibration.outcome());
            }
        }

        DeviceAssignmentEntity assignment = new DeviceAssignmentEntity();
        assignment.setTenantId(cmd.tenantId());
        assignment.setPlanId(plan.getId());
        assignment.setPatientCpid(plan.getPatientCpid());
        assignment.setDeviceRef(cmd.deviceRef());
        assignment.setAssetRef(cmd.assetRef() != null && !cmd.assetRef().isBlank() ? cmd.assetRef() : null);
        assignment.setStatus(DeviceAssignmentStatus.ASSIGNED);
        assignment.setAssignmentType(cmd.assignmentType());
        assignment.setAssignedBy(cmd.assignedBy());
        assignment.setNotes(cmd.notes());
        assignment.setDeviceActiveGuard(cmd.deviceRef());
        try {
            assignment = assignmentRepository.saveAndFlush(assignment);
        } catch (DataIntegrityViolationException e) {
            // Two concurrent assigns raced past the pre-flight check; the unique guard held.
            throw new TelemonitoringDomainException("TM_DEVICE_ALREADY_ASSIGNED", 409,
                    "Device " + cmd.deviceRef() + " was concurrently assigned to another patient — "
                            + "one device serves one patient at a time (§15.6).");
        }

        Map<String, Object> payload = basePayload(assignment);
        payload.put("deviceTrustLevel", verification.trustLevel());
        eventEmitter.emitDeviceEvent("assigned", assignment.getId(), assignment.getPatientCpid(),
                payload, assignment.getTenantId());
        return assignment;
    }

    // ── Lifecycle ──

    /** Activation records training (§15.6 patient/caregiver training confirmation) — mandatory. */
    @Transactional
    public DeviceAssignmentEntity activate(UUID assignmentId, String actor, boolean trainingConfirmed, String notes) {
        DeviceAssignmentEntity assignment = load(assignmentId);
        requireText(actor, "actor is required to activate a device assignment");
        if (!trainingConfirmed) {
            throw new TelemonitoringDomainException("TM_TRAINING_NOT_CONFIRMED", 422,
                    "Activation requires patient/caregiver training confirmation (§15.6) — "
                            + "an untrained handover is a data-quality and safety defect, not a formality.");
        }
        transition(assignment, DeviceAssignmentStatus.ACTIVE, "Activated by " + actor + " (training confirmed)");
        assignment.setActivatedAt(OffsetDateTime.now());
        assignment.setActivatedBy(actor);
        assignment.setTrainingConfirmed(true);
        if (notes != null && !notes.isBlank()) {
            assignment.setNotes(notes);
        }
        assignment = assignmentRepository.save(assignment);

        Map<String, Object> payload = basePayload(assignment);
        payload.put("activatedBy", actor);
        payload.put("trainingConfirmed", true);
        eventEmitter.emitDeviceEvent("activated", assignment.getId(), assignment.getPatientCpid(),
                payload, assignment.getTenantId());
        return assignment;
    }

    @Transactional
    public DeviceAssignmentEntity suspend(UUID assignmentId, String reason, String actor) {
        DeviceAssignmentEntity assignment = load(assignmentId);
        requireText(reason, "A reason is required to suspend a device assignment");
        transition(assignment, DeviceAssignmentStatus.SUSPENDED, reason);
        return assignmentRepository.save(assignment);
    }

    /** Resume = SUSPENDED → ACTIVE; re-emits {@code activated} (no separate resumed event). */
    @Transactional
    public DeviceAssignmentEntity resume(UUID assignmentId, String reason, String actor) {
        DeviceAssignmentEntity assignment = load(assignmentId);
        requireText(reason, "A reason is required to resume a device assignment");
        if (assignment.getStatus() != DeviceAssignmentStatus.SUSPENDED) {
            throw new TelemonitoringDomainException("TM_INVALID_TRANSITION", 409,
                    "Only a SUSPENDED assignment can be resumed (current: " + assignment.getStatus() + ")");
        }
        transition(assignment, DeviceAssignmentStatus.ACTIVE, reason);
        assignment = assignmentRepository.save(assignment);
        Map<String, Object> payload = basePayload(assignment);
        payload.put("reason", reason);
        payload.put("actor", actor);
        payload.put("resumed", true);
        eventEmitter.emitDeviceEvent("activated", assignment.getId(), assignment.getPatientCpid(),
                payload, assignment.getTenantId());
        return assignment;
    }

    /**
     * Return closes the binding and frees the device (guard cleared). History is never
     * deleted — readings keep their assignment-era provenance forever (§15.6 rules).
     */
    @Transactional
    public DeviceAssignmentEntity markReturned(UUID assignmentId, String reason, String actor) {
        DeviceAssignmentEntity assignment = load(assignmentId);
        requireText(reason, "A reason is required to return a device assignment");
        transition(assignment, DeviceAssignmentStatus.RETURNED, reason);
        assignment.setReturnedAt(OffsetDateTime.now());
        assignment.setDeviceActiveGuard(null); // the device may serve another patient now
        assignment = assignmentRepository.save(assignment);
        Map<String, Object> payload = basePayload(assignment);
        payload.put("reason", reason);
        payload.put("actor", actor);
        eventEmitter.emitDeviceEvent("returned", assignment.getId(), assignment.getPatientCpid(),
                payload, assignment.getTenantId());
        return assignment;
    }

    /**
     * LOST is a stock-integrity signal for programme operations, never a silent write-off
     * (§15.6). Terminal for the clinical binding; the device stops resolving.
     */
    @Transactional
    public DeviceAssignmentEntity markLost(UUID assignmentId, String reason, String actor) {
        DeviceAssignmentEntity assignment = load(assignmentId);
        requireText(reason, "A reason is required to mark a device assignment LOST");
        transition(assignment, DeviceAssignmentStatus.LOST, reason);
        assignment.setReturnedAt(OffsetDateTime.now());
        assignment.setDeviceActiveGuard(null);
        return assignmentRepository.save(assignment);
    }

    // ── The assignment gate (consumed by OF-B25 ingestion) ──

    /**
     * Resolve a device to its live clinical binding: {patient, plan, status} plus the
     * calibration posture projected fresh from asset-registry. Asset problems are
     * STAMPED (DEGRADED / UNVERIFIED), never blocking the lookup — the caller's rules
     * engine excludes non-OK readings from value-alerting while retaining them (§15.5/§15.7).
     */
    @Transactional(readOnly = true)
    public ResolvedAssignment resolveAssignment(String deviceRef, HttpServletRequest inbound) {
        requireText(deviceRef, "deviceRef is required");
        DeviceAssignmentEntity assignment = assignmentRepository.findByDeviceActiveGuard(deviceRef)
                .orElseThrow(() -> new TelemonitoringDomainException("TM_ASSIGNMENT_NOT_FOUND", 404,
                        "No live clinical assignment for device " + deviceRef
                                + " — readings from it cannot be attributed to a patient (Vol II §6.2)."));

        CalibrationPosture posture;
        String postureReason;
        if (assignment.getAssetRef() == null) {
            posture = CalibrationPosture.UNTRACKED;
            postureReason = "No asset_ref on the assignment — no physical-truth leg to project";
        } else {
            EquipmentCalibration calibration = assetRegistryClient
                    .calibrationState(assignment.getAssetRef(), assignment.getTenantId(), inbound);
            switch (calibration.outcome()) {
                case FOUND -> {
                    if (calibration.calibrationLapsed(LocalDate.now())) {
                        posture = CalibrationPosture.DEGRADED;
                        postureReason = "CALIBRATION_LAPSED: next due " + calibration.nextCalibration()
                                + " has passed (asset " + assignment.getAssetRef() + ")";
                    } else if (calibration.equipmentStatus() != null
                            && calibration.equipmentStatus().toUpperCase().contains("CALIBRATION")) {
                        posture = CalibrationPosture.DEGRADED;
                        postureReason = "Equipment status " + calibration.equipmentStatus()
                                + " (asset " + assignment.getAssetRef() + ")";
                    } else {
                        posture = CalibrationPosture.OK;
                        postureReason = null;
                    }
                }
                case NOT_FOUND -> {
                    posture = CalibrationPosture.UNVERIFIED;
                    postureReason = "Asset " + assignment.getAssetRef() + " unknown to asset-registry";
                }
                default -> {
                    posture = CalibrationPosture.UNVERIFIED;
                    postureReason = "asset-registry unreachable — calibration state could not be projected";
                }
            }
        }

        return new ResolvedAssignment(
                assignment.getId(),
                assignment.getTenantId(),
                assignment.getPlanId(),
                assignment.getPatientCpid(),
                assignment.getDeviceRef(),
                assignment.getAssetRef(),
                assignment.getStatus(),
                posture,
                postureReason,
                assignment.getActivatedAt());
    }

    // ── Reads ──

    @Transactional(readOnly = true)
    public DeviceAssignmentEntity getAssignment(UUID assignmentId) {
        return load(assignmentId);
    }

    @Transactional(readOnly = true)
    public List<DeviceAssignmentEntity> listByPlan(UUID tenantId, UUID planId) {
        require(tenantId != null, "tenantId is required");
        require(planId != null, "planId is required");
        return assignmentRepository.findByTenantIdAndPlanIdOrderByCreatedAtDesc(tenantId, planId);
    }

    @Transactional(readOnly = true)
    public List<DeviceAssignmentEntity> listByPatient(UUID tenantId, String patientCpid) {
        require(tenantId != null, "tenantId is required");
        requireText(patientCpid, "patientCpid is required");
        return assignmentRepository.findByTenantIdAndPatientCpidOrderByCreatedAtDesc(tenantId, patientCpid);
    }

    // ── Internals ──

    private DeviceAssignmentEntity load(UUID assignmentId) {
        return assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new TelemonitoringDomainException("TM_ASSIGNMENT_NOT_FOUND", 404,
                        "Device assignment not found: " + assignmentId));
    }

    private void transition(DeviceAssignmentEntity assignment, DeviceAssignmentStatus to, String reason) {
        DeviceAssignmentStatus from = assignment.getStatus();
        if (!DeviceAssignmentStatus.canTransition(from, to)) {
            throw new TelemonitoringDomainException("TM_INVALID_TRANSITION", 409,
                    "Illegal device-assignment transition " + from + " -> " + to);
        }
        assignment.setStatus(to);
        assignment.setLifecycleReason(reason);
        log.info("Device assignment {} transitioned {} -> {} ({})", assignment.getId(), from, to, reason);
    }

    private Map<String, Object> basePayload(DeviceAssignmentEntity assignment) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("assignmentId", assignment.getId().toString());
        payload.put("planId", assignment.getPlanId().toString());
        payload.put("patientCpid", assignment.getPatientCpid());
        payload.put("deviceRef", assignment.getDeviceRef());
        payload.put("assetRef", assignment.getAssetRef());
        payload.put("status", assignment.getStatus().name());
        return payload;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new TelemonitoringDomainException("TM_INVALID_REQUEST", 400, message);
        }
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new TelemonitoringDomainException("TM_INVALID_REQUEST", 400, message);
        }
    }
}
