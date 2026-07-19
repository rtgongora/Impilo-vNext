package zw.gov.mohcc.impilo.vashandi.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.vashandi.api.VashandiDtos;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.AttendanceEventEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.WorkforceAssignmentEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.AttendanceEventRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.ShiftRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.WorkforceAssignmentRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceAdhocTest {

    @Mock
    AttendanceEventRepository attendanceRepository;
    @Mock
    ShiftRepository shiftRepository;
    @Mock
    WorkforceAssignmentRepository assignmentRepository;
    @Mock
    VashandiOutboxWriter outboxWriter;
    @Mock
    zw.gov.mohcc.impilo.shared.biometric.BiometricVerificationClient biometricVerification;

    AttendanceService service;

    @BeforeEach
    void setUp() {
        service = new AttendanceService(attendanceRepository, shiftRepository, assignmentRepository, outboxWriter,
                biometricVerification);
    }

    @Test
    void adhocCheckIn_deniedWhenNoProfile() throws Exception {
        VashandiDtos.AdhocCheckInRequest req = new VashandiDtos.AdhocCheckInRequest(
                null, null, UUID.randomUUID(), null, null, null, null, null, null, null, null);
        VashandiDtos.AttendanceActionResponse response = service.adhocCheckIn(UUID.randomUUID(), req);
        assertThat(response.status()).isEqualTo("denied");
    }

    @Test
    void adhocCheckIn_deniedWhenNoAssignmentAndNoFacility() throws Exception {
        VashandiDtos.AdhocCheckInRequest req = new VashandiDtos.AdhocCheckInRequest(
                UUID.randomUUID(), null, null, null, null, null, null, null, null, null, null);
        VashandiDtos.AttendanceActionResponse response = service.adhocCheckIn(UUID.randomUUID(), req);
        assertThat(response.status()).isEqualTo("denied");
        assertThat(response.message()).contains("assignment or facility context required");
    }

    @Test
    void adhocCheckIn_deniedWhenAssignmentBelongsToAnotherWorker() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        WorkforceAssignmentEntity assignment = new WorkforceAssignmentEntity();
        assignment.setId(assignmentId);
        assignment.setWorkforceProfileId(UUID.randomUUID()); // different worker
        assignment.setStatus("active");
        when(assignmentRepository.findByTenantIdAndId(tenant, assignmentId)).thenReturn(Optional.of(assignment));

        VashandiDtos.AdhocCheckInRequest req = new VashandiDtos.AdhocCheckInRequest(
                UUID.randomUUID(), assignmentId, null, null, null, null, null, null, null, null, null);
        VashandiDtos.AttendanceActionResponse response = service.adhocCheckIn(tenant, req);
        assertThat(response.status()).isEqualTo("denied");
        assertThat(response.message()).contains("assignment not found for worker");
    }

    @Test
    void adhocCheckIn_deniedWhenAssignmentNotActive() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        WorkforceAssignmentEntity assignment = new WorkforceAssignmentEntity();
        assignment.setId(assignmentId);
        assignment.setWorkforceProfileId(profileId);
        assignment.setStatus("draft");
        when(assignmentRepository.findByTenantIdAndId(tenant, assignmentId)).thenReturn(Optional.of(assignment));

        VashandiDtos.AdhocCheckInRequest req = new VashandiDtos.AdhocCheckInRequest(
                profileId, assignmentId, null, null, null, null, null, null, null, null, null);
        VashandiDtos.AttendanceActionResponse response = service.adhocCheckIn(tenant, req);
        assertThat(response.status()).isEqualTo("denied");
        assertThat(response.message()).contains("not active");
    }

    @Test
    void adhocCheckIn_completesAndInheritsAssignmentContext() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID facilityId = UUID.randomUUID();
        WorkforceAssignmentEntity assignment = new WorkforceAssignmentEntity();
        assignment.setId(assignmentId);
        assignment.setWorkforceProfileId(profileId);
        assignment.setStatus("active");
        assignment.setFacilityId(facilityId);
        assignment.setDepartmentId("OPD");
        when(assignmentRepository.findByTenantIdAndId(tenant, assignmentId)).thenReturn(Optional.of(assignment));
        when(attendanceRepository.save(any())).thenAnswer(inv -> {
            AttendanceEventEntity e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(UUID.randomUUID());
            }
            return e;
        });

        VashandiDtos.AdhocCheckInRequest req = new VashandiDtos.AdhocCheckInRequest(
                profileId, assignmentId, null, null, null, null, null, null, null, null, null);
        VashandiDtos.AttendanceActionResponse response = service.adhocCheckIn(tenant, req);

        assertThat(response.status()).isEqualTo("completed");
        assertThat(response.message()).contains("ad-hoc");
        assertThat(response.eventId()).isNotNull();
    }

    @Test
    void adhocCheckIn_completesWithFacilityOnlyContext() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID facilityId = UUID.randomUUID();
        when(attendanceRepository.save(any())).thenAnswer(inv -> {
            AttendanceEventEntity e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        VashandiDtos.AdhocCheckInRequest req = new VashandiDtos.AdhocCheckInRequest(
                profileId, null, facilityId, "ER", null, null, "FACILITY", null, null, null, null);
        VashandiDtos.AttendanceActionResponse response = service.adhocCheckIn(tenant, req);

        assertThat(response.status()).isEqualTo("completed");
    }
}
