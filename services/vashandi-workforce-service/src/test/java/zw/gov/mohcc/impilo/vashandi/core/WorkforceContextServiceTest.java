package zw.gov.mohcc.impilo.vashandi.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.vashandi.api.VashandiDtos;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.AttendanceEventEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.WorkforceAssignmentEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.WorkforceProfileEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.AttendanceEventRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.LeaveAvailabilityRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.ShiftRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.WorkforceAssignmentRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.WorkforceMembershipRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.WorkforceProfileRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkforceContextServiceTest {

    @Mock
    WorkforceProfileRepository profileRepository;
    @Mock
    WorkforceAssignmentRepository assignmentRepository;
    @Mock
    WorkforceMembershipRepository membershipRepository;
    @Mock
    AttendanceEventRepository attendanceRepository;
    @Mock
    ShiftRepository shiftRepository;
    @Mock
    LeaveAvailabilityRepository leaveAvailabilityRepository;

    WorkforceContextService service;

    @BeforeEach
    void setUp() {
        service = new WorkforceContextService(
                profileRepository, assignmentRepository, membershipRepository, attendanceRepository,
                shiftRepository, leaveAvailabilityRepository);
    }

    @Test
    void resolveByHealthId_unknownActor_returnsUnresolvedEmptyContext() {
        UUID tenant = UUID.randomUUID();
        when(profileRepository.findByTenantIdAndHealthId(tenant, "HID-X")).thenReturn(Optional.empty());

        VashandiDtos.WorkforceContextResponse response = service.resolveByHealthId(tenant, "HID-X");

        assertThat(response.resolved()).isFalse();
        assertThat(response.activeAssignments()).isEmpty();
        assertThat(response.checkIn().state()).isEqualTo("CHECKED_OUT");
        assertThat(response.requiresContextChooser()).isFalse();
    }

    @Test
    void resolveByHealthId_blankHealthId_returnsUnresolved() {
        VashandiDtos.WorkforceContextResponse response = service.resolveByHealthId(UUID.randomUUID(), "  ");
        assertThat(response.resolved()).isFalse();
    }

    @Test
    void resolveByHealthId_singleAssignment_noChooser_deriveScope() {
        UUID tenant = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        WorkforceProfileEntity profile = new WorkforceProfileEntity();
        profile.setId(profileId);
        profile.setTenantId(tenant);
        profile.setHealthId("HID-1");

        WorkforceAssignmentEntity a = new WorkforceAssignmentEntity();
        a.setId(UUID.randomUUID());
        a.setTenantId(tenant);
        a.setWorkforceProfileId(profileId);
        a.setStatus("active");
        a.setFacilityId(UUID.randomUUID());
        a.setDepartmentId("OPD");
        a.setEligibilityStatus("allowed");

        when(profileRepository.findByTenantIdAndHealthId(tenant, "HID-1")).thenReturn(Optional.of(profile));
        when(assignmentRepository.findByTenantIdAndWorkforceProfileIdAndStatusIn(eq(tenant), eq(profileId), any()))
                .thenReturn(List.of(a));
        when(membershipRepository.findByTenantIdAndWorkforceProfileIdAndStatusOrderByEffectiveDateDesc(
                tenant, profileId, "active")).thenReturn(List.of());
        when(attendanceRepository.findByTenantIdAndWorkforceProfileIdOrderByEventTimeDesc(tenant, profileId))
                .thenReturn(List.of());

        VashandiDtos.WorkforceContextResponse response = service.resolveByHealthId(tenant, "HID-1");

        assertThat(response.resolved()).isTrue();
        assertThat(response.workforceProfileId()).isEqualTo(profileId);
        assertThat(response.activeAssignments()).hasSize(1);
        assertThat(response.activeAssignments().get(0).status()).isEqualTo("ACTIVE");
        assertThat(response.activeAssignments().get(0).scope()).isEqualTo("DEPARTMENT");
        assertThat(response.requiresContextChooser()).isFalse();
        assertThat(response.checkIn().state()).isEqualTo("CHECKED_OUT");
    }

    @Test
    void resolveByHealthId_multipleAssignments_requiresChooser_andCheckInState() {
        UUID tenant = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        WorkforceProfileEntity profile = new WorkforceProfileEntity();
        profile.setId(profileId);
        profile.setTenantId(tenant);
        profile.setHealthId("HID-2");

        WorkforceAssignmentEntity a1 = new WorkforceAssignmentEntity();
        a1.setId(UUID.randomUUID());
        a1.setStatus("active");
        a1.setUnitId("WARD-3");
        WorkforceAssignmentEntity a2 = new WorkforceAssignmentEntity();
        a2.setId(UUID.randomUUID());
        a2.setStatus("approved");
        a2.setFacilityId(UUID.randomUUID());

        AttendanceEventEntity latest = new AttendanceEventEntity();
        latest.setEventType("check_in");
        latest.setEventTime(OffsetDateTime.now());
        latest.setFacilityId(UUID.randomUUID());

        when(profileRepository.findByTenantIdAndHealthId(tenant, "HID-2")).thenReturn(Optional.of(profile));
        when(assignmentRepository.findByTenantIdAndWorkforceProfileIdAndStatusIn(eq(tenant), eq(profileId), any()))
                .thenReturn(List.of(a1, a2));
        when(membershipRepository.findByTenantIdAndWorkforceProfileIdAndStatusOrderByEffectiveDateDesc(
                tenant, profileId, "active")).thenReturn(List.of());
        when(attendanceRepository.findByTenantIdAndWorkforceProfileIdOrderByEventTimeDesc(tenant, profileId))
                .thenReturn(List.of(latest));

        VashandiDtos.WorkforceContextResponse response = service.resolveByHealthId(tenant, "HID-2");

        assertThat(response.requiresContextChooser()).isTrue();
        assertThat(response.activeAssignments()).hasSize(2);
        assertThat(response.activeAssignments().get(0).scope()).isEqualTo("WARD");
        assertThat(response.checkIn().state()).isEqualTo("CHECKED_IN");
        assertThat(response.checkIn().supervisorConfirmed()).isFalse();
    }

    @Test
    void resolveByProfileId_supervisorConfirmedCheckIn_reportsConfirmed() {
        UUID tenant = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        WorkforceProfileEntity profile = new WorkforceProfileEntity();
        profile.setId(profileId);
        profile.setTenantId(tenant);
        profile.setHealthId("HID-3");

        AttendanceEventEntity latest = new AttendanceEventEntity();
        latest.setEventType("supervisor_confirmed_check_in");
        latest.setEventTime(OffsetDateTime.now());

        when(profileRepository.findByTenantIdAndId(tenant, profileId)).thenReturn(Optional.of(profile));
        lenient().when(assignmentRepository.findByTenantIdAndWorkforceProfileIdAndStatusIn(
                eq(tenant), eq(profileId), any())).thenReturn(List.of());
        when(membershipRepository.findByTenantIdAndWorkforceProfileIdAndStatusOrderByEffectiveDateDesc(
                tenant, profileId, "active")).thenReturn(List.of());
        when(attendanceRepository.findByTenantIdAndWorkforceProfileIdOrderByEventTimeDesc(tenant, profileId))
                .thenReturn(List.of(latest));

        VashandiDtos.WorkforceContextResponse response = service.resolveByProfileId(tenant, profileId);

        assertThat(response.checkIn().state()).isEqualTo("CHECKED_IN");
        assertThat(response.checkIn().supervisorConfirmed()).isTrue();
    }
}
