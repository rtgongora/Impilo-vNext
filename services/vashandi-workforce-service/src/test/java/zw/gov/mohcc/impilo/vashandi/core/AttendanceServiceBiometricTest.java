package zw.gov.mohcc.impilo.vashandi.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.shared.biometric.BiometricVerificationClient;
import zw.gov.mohcc.impilo.vashandi.api.VashandiDtos;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.AttendanceEventEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.AttendanceEventRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.ShiftRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.WorkforceAssignmentRepository;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Biometric workforce check-in through the shared verification seam: MATCH →
 * checkInMode "biometric"; NO_MATCH → denied; engine UNAVAILABLE → falls back so
 * a worker is never blocked from clocking in by a biometric outage.
 */
@ExtendWith(MockitoExtension.class)
class AttendanceServiceBiometricTest {

    @Mock AttendanceEventRepository attendanceRepository;
    @Mock ShiftRepository shiftRepository;
    @Mock WorkforceAssignmentRepository assignmentRepository;
    @Mock zw.gov.mohcc.impilo.vashandi.core.VashandiOutboxWriter outboxWriter;
    @Mock BiometricVerificationClient biometricVerification;

    AttendanceService service;

    @BeforeEach
    void setUp() {
        service = new AttendanceService(attendanceRepository, shiftRepository, assignmentRepository, outboxWriter,
                biometricVerification);
        lenient().when(attendanceRepository.save(any())).thenAnswer(i -> {
            AttendanceEventEntity e = i.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });
    }

    private VashandiDtos.CheckInRequest bioRequest() {
        return new VashandiDtos.CheckInRequest(null, UUID.randomUUID(), null, "self_check_in", "dev-1", false,
                "subj-worker-1", "FINGERPRINT", "cHJvYmU=");
    }

    @Test
    @DisplayName("MATCH → checked in as checkInMode=biometric")
    void matchCheckedInAsBiometric() throws Exception {
        when(biometricVerification.verify(eq("subj-worker-1"), eq("FINGERPRINT"), any()))
                .thenReturn(new BiometricVerificationClient.Decision("MATCH", 0.97));

        var resp = service.checkIn(UUID.randomUUID(), bioRequest());

        assertEquals("completed", resp.status());
        ArgumentCaptor<AttendanceEventEntity> captor = ArgumentCaptor.forClass(AttendanceEventEntity.class);
        verify(attendanceRepository).save(captor.capture());
        assertEquals("biometric", captor.getValue().getCheckInMode());
    }

    @Test
    @DisplayName("NO_MATCH → denied, nothing saved")
    void noMatchDenied() throws Exception {
        when(biometricVerification.verify(any(), any(), any()))
                .thenReturn(new BiometricVerificationClient.Decision("NO_MATCH", 0.1));

        var resp = service.checkIn(UUID.randomUUID(), bioRequest());

        assertEquals("denied", resp.status());
        verify(attendanceRepository, never()).save(any());
    }

    @Test
    @DisplayName("engine UNAVAILABLE → falls back (worker not blocked)")
    void unavailableFallsBack() throws Exception {
        when(biometricVerification.verify(any(), any(), any()))
                .thenReturn(BiometricVerificationClient.Decision.unavailable());

        var resp = service.checkIn(UUID.randomUUID(), bioRequest());

        assertEquals("completed", resp.status());
        ArgumentCaptor<AttendanceEventEntity> captor = ArgumentCaptor.forClass(AttendanceEventEntity.class);
        verify(attendanceRepository).save(captor.capture());
        assertEquals("self_check_in", captor.getValue().getCheckInMode(), "not marked biometric on an outage");
    }
}
