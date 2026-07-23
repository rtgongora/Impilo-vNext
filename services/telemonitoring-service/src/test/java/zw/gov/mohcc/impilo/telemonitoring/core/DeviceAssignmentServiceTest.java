package zw.gov.mohcc.impilo.telemonitoring.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.telemonitoring.core.DeviceAssignmentService.AssignCommand;
import zw.gov.mohcc.impilo.telemonitoring.core.DeviceAssignmentService.ResolvedAssignment;
import zw.gov.mohcc.impilo.telemonitoring.domain.CalibrationPosture;
import zw.gov.mohcc.impilo.telemonitoring.domain.DeviceAssignmentStatus;
import zw.gov.mohcc.impilo.telemonitoring.integration.AssetRegistryClient;
import zw.gov.mohcc.impilo.telemonitoring.integration.AssetRegistryClient.EquipmentCalibration;
import zw.gov.mohcc.impilo.telemonitoring.integration.IotDeviceRegistryClient;
import zw.gov.mohcc.impilo.telemonitoring.integration.IotDeviceRegistryClient.DeviceVerification;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.DeviceAssignmentEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.MonitoringPlanEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.MonitoringProgrammeEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.DeviceAssignmentRepository;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.MonitoringProgrammeRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * OF-B24 — assignment machine, one-device-one-patient race guard, fail-closed
 * DEVICE_UNVERIFIED, calibration posture matrix and the resolve-gate shape.
 * The two read-only cross-service legs (iot-ingestion digital identity,
 * asset-registry physical/calibration truth) are mocked at the client seam —
 * exactly the boundary the §6.2 three-way split draws.
 */
@SpringBootTest
@ActiveProfiles("test")
class DeviceAssignmentServiceTest {

    @Autowired private DeviceAssignmentService assignmentService;
    @Autowired private MonitoringPlanService planService;
    @Autowired private MonitoringProgrammeRepository programmeRepository;
    @Autowired private DeviceAssignmentRepository assignmentRepository;
    @Autowired private EventOutboxRepository outboxRepository;

    @MockBean private IotDeviceRegistryClient iotDeviceRegistryClient;
    @MockBean private AssetRegistryClient assetRegistryClient;

    private final UUID tenant = UUID.randomUUID();

    @BeforeEach
    void seedProgrammeAndDefaultMocks() {
        if (programmeRepository.findByCode("HYPERTENSION").isEmpty()) {
            MonitoringProgrammeEntity p = new MonitoringProgrammeEntity();
            p.setCode("HYPERTENSION");
            p.setName("Hypertension");
            p.setDefaultThresholds("{\"systolic_bp\":{\"greenMax\":140}}");
            programmeRepository.save(p);
        }
        when(iotDeviceRegistryClient.verify(any(), any(), any()))
                .thenReturn(DeviceVerification.verified("CERTIFIED"));
        when(assetRegistryClient.calibrationState(any(), any(), any()))
                .thenReturn(EquipmentCalibration.found("ACTIVE", LocalDate.now().plusMonths(6)));
    }

    private MonitoringPlanEntity activePlan() {
        MonitoringPlanEntity plan = planService.createDraft(new MonitoringPlanService.CreatePlanCommand(
                tenant, "CPID-" + UUID.randomUUID(), "HYPERTENSION", null,
                "Essential hypertension", "HOME_SELF_MONITORING", "WEEKLY", null, null,
                null, null, "clinician-a"), true);
        return planService.approve(plan.getId(), "clinician-b");
    }

    private AssignCommand cmd(MonitoringPlanEntity plan, String deviceRef, String assetRef) {
        return new AssignCommand(tenant, plan.getId(), deviceRef, assetRef,
                "ISSUED_TO_PATIENT", "nurse-1", null);
    }

    private String newDevice() {
        return UUID.randomUUID().toString();
    }

    // ── Assignment + fail-closed device verification ──

    @Test
    void assignBindsDeviceToPlanPatientAndEmitsAssignedEvent() {
        MonitoringPlanEntity plan = activePlan();
        String device = newDevice();
        DeviceAssignmentEntity assignment = assignmentService.assign(cmd(plan, device, null), null);

        assertThat(assignment.getStatus()).isEqualTo(DeviceAssignmentStatus.ASSIGNED);
        assertThat(assignment.getPatientCpid()).isEqualTo(plan.getPatientCpid());
        assertThat(assignment.getPlanId()).isEqualTo(plan.getId());
        assertThat(assignment.getDeviceActiveGuard()).isEqualTo(device);
        assertThat(events(assignment)).containsExactly("telemonitoring.device.assigned.v1");
    }

    @Test
    void assignFailsClosedWhenDeviceRegistryUnreachable() {
        MonitoringPlanEntity plan = activePlan();
        String device = newDevice();
        when(iotDeviceRegistryClient.verify(eq(device), any(), any()))
                .thenReturn(DeviceVerification.unreachable());

        assertThatThrownBy(() -> assignmentService.assign(cmd(plan, device, null), null))
                .isInstanceOf(TelemonitoringDomainException.class)
                .hasFieldOrPropertyWithValue("code", "DEVICE_UNVERIFIED")
                .hasMessageContaining("unreachable");
        assertThat(assignmentRepository.findByDeviceActiveGuard(device)).isEmpty();
    }

    @Test
    void assignFailsClosedWhenDeviceUnknownToRegistry() {
        MonitoringPlanEntity plan = activePlan();
        String device = newDevice();
        when(iotDeviceRegistryClient.verify(eq(device), any(), any()))
                .thenReturn(DeviceVerification.notFound());

        assertThatThrownBy(() -> assignmentService.assign(cmd(plan, device, null), null))
                .isInstanceOf(TelemonitoringDomainException.class)
                .hasFieldOrPropertyWithValue("code", "DEVICE_UNVERIFIED");
    }

    @Test
    void assignRefusesRevokedDeviceIdentity() {
        MonitoringPlanEntity plan = activePlan();
        String device = newDevice();
        when(iotDeviceRegistryClient.verify(eq(device), any(), any()))
                .thenReturn(DeviceVerification.verified("REVOKED"));

        assertThatThrownBy(() -> assignmentService.assign(cmd(plan, device, null), null))
                .isInstanceOf(TelemonitoringDomainException.class)
                .hasFieldOrPropertyWithValue("code", "DEVICE_UNVERIFIED")
                .hasMessageContaining("REVOKED");
    }

    @Test
    void assignRequiresAnActivePlan() {
        MonitoringPlanEntity draft = planService.createDraft(new MonitoringPlanService.CreatePlanCommand(
                tenant, "CPID-DRAFT", "HYPERTENSION", null, null, null, null, null, null,
                null, null, "clinician-a"), true);

        assertThatThrownBy(() -> assignmentService.assign(cmd(draft, newDevice(), null), null))
                .isInstanceOf(TelemonitoringDomainException.class)
                .hasFieldOrPropertyWithValue("code", "TM_PLAN_NOT_ACTIVE");
    }

    @Test
    void assignLapsedCalibrationBlocksAtAssignmentTime() {
        MonitoringPlanEntity plan = activePlan();
        String device = newDevice();
        when(assetRegistryClient.calibrationState(eq("EQ-LAPSED"), any(), any()))
                .thenReturn(EquipmentCalibration.found("ACTIVE", LocalDate.now().minusDays(10)));

        assertThatThrownBy(() -> assignmentService.assign(cmd(plan, device, "EQ-LAPSED"), null))
                .isInstanceOf(TelemonitoringDomainException.class)
                .hasFieldOrPropertyWithValue("code", "TM_CALIBRATION_EXPIRED");
    }

    @Test
    void assignProceedsWhenAssetRegistryUnreachableButStampsUnverifiedAtResolve() {
        MonitoringPlanEntity plan = activePlan();
        String device = newDevice();
        when(assetRegistryClient.calibrationState(eq("EQ-DOWN"), any(), any()))
                .thenReturn(EquipmentCalibration.unreachable());

        DeviceAssignmentEntity assignment = assignmentService.assign(cmd(plan, device, "EQ-DOWN"), null);
        assertThat(assignment.getStatus()).isEqualTo(DeviceAssignmentStatus.ASSIGNED);

        ResolvedAssignment resolved = assignmentService.resolveAssignment(device, null);
        assertThat(resolved.calibrationPosture()).isEqualTo(CalibrationPosture.UNVERIFIED);
    }

    // ── One device, one patient ──

    @Test
    void secondLiveAssignmentOfTheSameDeviceIsRefused() {
        MonitoringPlanEntity planA = activePlan();
        MonitoringPlanEntity planB = activePlan();
        String device = newDevice();
        assignmentService.assign(cmd(planA, device, null), null);

        assertThatThrownBy(() -> assignmentService.assign(cmd(planB, device, null), null))
                .isInstanceOf(TelemonitoringDomainException.class)
                .hasFieldOrPropertyWithValue("code", "TM_DEVICE_ALREADY_ASSIGNED");
    }

    @Test
    void uniqueGuardHoldsUnderARaceThatBypassesThePreflightCheck() {
        MonitoringPlanEntity plan = activePlan();
        String device = newDevice();
        assignmentService.assign(cmd(plan, device, null), null);

        // Simulate the concurrent writer that raced past the service pre-check: a direct
        // insert with the same live guard must be a DB integrity violation.
        DeviceAssignmentEntity racer = new DeviceAssignmentEntity();
        racer.setTenantId(tenant);
        racer.setPlanId(plan.getId());
        racer.setPatientCpid("CPID-RACER");
        racer.setDeviceRef(device);
        racer.setAssignedBy("racer");
        racer.setDeviceActiveGuard(device);
        assertThatThrownBy(() -> assignmentRepository.saveAndFlush(racer))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void returnedDeviceCanServeAnotherPatient() {
        MonitoringPlanEntity planA = activePlan();
        MonitoringPlanEntity planB = activePlan();
        String device = newDevice();
        DeviceAssignmentEntity first = assignmentService.assign(cmd(planA, device, null), null);
        assignmentService.markReturned(first.getId(), "Programme completed", "nurse-1");

        DeviceAssignmentEntity second = assignmentService.assign(cmd(planB, device, null), null);
        assertThat(second.getPatientCpid()).isEqualTo(planB.getPatientCpid());
        // History retained: both rows exist, only the live one carries the guard.
        assertThat(assignmentRepository.findByDeviceRefOrderByCreatedAtDesc(device)).hasSize(2);
        assertThat(assignmentRepository.findByDeviceActiveGuard(device).orElseThrow().getId())
                .isEqualTo(second.getId());
    }

    // ── Lifecycle machine ──

    @Test
    void activationRecordsTrainingAndIsRefusedWithoutIt() {
        MonitoringPlanEntity plan = activePlan();
        DeviceAssignmentEntity assignment = assignmentService.assign(cmd(plan, newDevice(), null), null);

        assertThatThrownBy(() -> assignmentService.activate(assignment.getId(), "nurse-1", false, null))
                .isInstanceOf(TelemonitoringDomainException.class)
                .hasFieldOrPropertyWithValue("code", "TM_TRAINING_NOT_CONFIRMED");

        DeviceAssignmentEntity active = assignmentService.activate(assignment.getId(), "nurse-1", true, null);
        assertThat(active.getStatus()).isEqualTo(DeviceAssignmentStatus.ACTIVE);
        assertThat(active.getActivatedAt()).isNotNull();
        assertThat(active.getActivatedBy()).isEqualTo("nurse-1");
        assertThat(active.isTrainingConfirmed()).isTrue();
        assertThat(events(assignment)).containsExactly(
                "telemonitoring.device.assigned.v1", "telemonitoring.device.activated.v1");
    }

    @Test
    void lifecycleIsReasonBoundAndEmitsTheContractEvents() {
        MonitoringPlanEntity plan = activePlan();
        DeviceAssignmentEntity assignment = assignmentService.assign(cmd(plan, newDevice(), null), null);
        assignmentService.activate(assignment.getId(), "nurse-1", true, null);

        assertThatThrownBy(() -> assignmentService.suspend(assignment.getId(), " ", "nurse-1"))
                .isInstanceOf(TelemonitoringDomainException.class);
        assignmentService.suspend(assignment.getId(), "Device reported faulty", "nurse-1");
        assertThat(assignmentService.getAssignment(assignment.getId()).getStatus())
                .isEqualTo(DeviceAssignmentStatus.SUSPENDED);

        assignmentService.resume(assignment.getId(), "Fault cleared after inspection", "nurse-1");
        assertThat(assignmentService.getAssignment(assignment.getId()).getStatus())
                .isEqualTo(DeviceAssignmentStatus.ACTIVE);

        assertThatThrownBy(() -> assignmentService.markReturned(assignment.getId(), null, "nurse-1"))
                .isInstanceOf(TelemonitoringDomainException.class);
        DeviceAssignmentEntity returned =
                assignmentService.markReturned(assignment.getId(), "Plan completed", "nurse-1");
        assertThat(returned.getStatus()).isEqualTo(DeviceAssignmentStatus.RETURNED);
        assertThat(returned.getReturnedAt()).isNotNull();
        assertThat(returned.getDeviceActiveGuard()).isNull();

        // Terminal: nothing further.
        assertThatThrownBy(() -> assignmentService.activate(assignment.getId(), "nurse-1", true, null))
                .isInstanceOf(TelemonitoringDomainException.class)
                .hasFieldOrPropertyWithValue("code", "TM_INVALID_TRANSITION");

        assertThat(events(assignment)).containsExactly(
                "telemonitoring.device.assigned.v1",
                "telemonitoring.device.activated.v1",
                "telemonitoring.device.activated.v1", // resume re-emits activated (resumed:true)
                "telemonitoring.device.returned.v1");
    }

    @Test
    void lostIsTerminalReasonBoundAndFreesTheDevice() {
        MonitoringPlanEntity plan = activePlan();
        String device = newDevice();
        DeviceAssignmentEntity assignment = assignmentService.assign(cmd(plan, device, null), null);

        assertThatThrownBy(() -> assignmentService.markLost(assignment.getId(), "", "nurse-1"))
                .isInstanceOf(TelemonitoringDomainException.class);
        DeviceAssignmentEntity lost = assignmentService.markLost(assignment.getId(), "Patient relocated, device untraceable", "nurse-1");
        assertThat(lost.getStatus()).isEqualTo(DeviceAssignmentStatus.LOST);
        assertThat(lost.getDeviceActiveGuard()).isNull();
        assertThat(assignmentRepository.findByDeviceActiveGuard(device)).isEmpty();
    }

    // ── The assignment gate: calibration posture matrix + shape ──

    @Test
    void resolveGateReturnsTheAttributionShape() {
        MonitoringPlanEntity plan = activePlan();
        String device = newDevice();
        DeviceAssignmentEntity assignment = assignmentService.assign(cmd(plan, device, null), null);
        assignmentService.activate(assignment.getId(), "nurse-1", true, null);

        ResolvedAssignment resolved = assignmentService.resolveAssignment(device, null);
        assertThat(resolved.assignmentId()).isEqualTo(assignment.getId());
        assertThat(resolved.tenantId()).isEqualTo(tenant);
        assertThat(resolved.planId()).isEqualTo(plan.getId());
        assertThat(resolved.patientCpid()).isEqualTo(plan.getPatientCpid());
        assertThat(resolved.deviceRef()).isEqualTo(device);
        assertThat(resolved.status()).isEqualTo(DeviceAssignmentStatus.ACTIVE);
        assertThat(resolved.activatedAt()).isNotNull();
        // No asset_ref: no physical-truth leg to project.
        assertThat(resolved.calibrationPosture()).isEqualTo(CalibrationPosture.UNTRACKED);
    }

    @Test
    void calibrationPostureMatrixOkLapsedUnknownUnreachable() {
        MonitoringPlanEntity plan = activePlan();
        String device = newDevice();
        assignmentService.assign(cmd(plan, device, "EQ-1"), null);

        // OK — calibration current.
        when(assetRegistryClient.calibrationState(eq("EQ-1"), any(), any()))
                .thenReturn(EquipmentCalibration.found("ACTIVE", LocalDate.now().plusMonths(3)));
        assertThat(assignmentService.resolveAssignment(device, null).calibrationPosture())
                .isEqualTo(CalibrationPosture.OK);

        // OK — asset carries no calibration schedule at all.
        when(assetRegistryClient.calibrationState(eq("EQ-1"), any(), any()))
                .thenReturn(EquipmentCalibration.found("ACTIVE", null));
        assertThat(assignmentService.resolveAssignment(device, null).calibrationPosture())
                .isEqualTo(CalibrationPosture.OK);

        // DEGRADED — lapsed calibration: stamped, lookup still answers (never blocked).
        when(assetRegistryClient.calibrationState(eq("EQ-1"), any(), any()))
                .thenReturn(EquipmentCalibration.found("ACTIVE", LocalDate.now().minusDays(1)));
        ResolvedAssignment degraded = assignmentService.resolveAssignment(device, null);
        assertThat(degraded.calibrationPosture()).isEqualTo(CalibrationPosture.DEGRADED);
        assertThat(degraded.calibrationPostureReason()).contains("CALIBRATION_LAPSED");
        assertThat(degraded.patientCpid()).isNotBlank(); // attribution still delivered

        // DEGRADED — equipment status itself flags calibration.
        when(assetRegistryClient.calibrationState(eq("EQ-1"), any(), any()))
                .thenReturn(EquipmentCalibration.found("CALIBRATION_EXPIRED", LocalDate.now().plusMonths(1)));
        assertThat(assignmentService.resolveAssignment(device, null).calibrationPosture())
                .isEqualTo(CalibrationPosture.DEGRADED);

        // UNVERIFIED — asset unknown to the registry.
        when(assetRegistryClient.calibrationState(eq("EQ-1"), any(), any()))
                .thenReturn(EquipmentCalibration.notFound());
        assertThat(assignmentService.resolveAssignment(device, null).calibrationPosture())
                .isEqualTo(CalibrationPosture.UNVERIFIED);

        // UNVERIFIED — asset-registry unreachable: stamped, never blocking the lookup.
        when(assetRegistryClient.calibrationState(eq("EQ-1"), any(), any()))
                .thenReturn(EquipmentCalibration.unreachable());
        ResolvedAssignment unverified = assignmentService.resolveAssignment(device, null);
        assertThat(unverified.calibrationPosture()).isEqualTo(CalibrationPosture.UNVERIFIED);
        assertThat(unverified.patientCpid()).isEqualTo(
                assignmentService.getAssignment(unverified.assignmentId()).getPatientCpid());
    }

    @Test
    void resolveGateRefusesUnassignedAndClosedDevices() {
        assertThatThrownBy(() -> assignmentService.resolveAssignment(UUID.randomUUID().toString(), null))
                .isInstanceOf(TelemonitoringDomainException.class)
                .hasFieldOrPropertyWithValue("code", "TM_ASSIGNMENT_NOT_FOUND");

        MonitoringPlanEntity plan = activePlan();
        String device = newDevice();
        DeviceAssignmentEntity assignment = assignmentService.assign(cmd(plan, device, null), null);
        assignmentService.markReturned(assignment.getId(), "Returned to stock", "nurse-1");
        assertThatThrownBy(() -> assignmentService.resolveAssignment(device, null))
                .isInstanceOf(TelemonitoringDomainException.class)
                .hasFieldOrPropertyWithValue("code", "TM_ASSIGNMENT_NOT_FOUND");
    }

    private List<String> events(DeviceAssignmentEntity assignment) {
        return outboxRepository.findByAggregateIdOrderByIdAsc(assignment.getId().toString()).stream()
                .map(EventOutboxEntity::getEventType)
                .toList();
    }
}
