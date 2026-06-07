package zw.gov.mohcc.impilo.live.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventAttendanceEntity;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventEntity;
import zw.gov.mohcc.impilo.live.persistence.repository.LiveEventAttendanceRepository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID EVENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock private LiveEventService eventService;
    @Mock private LiveEventAttendanceRepository attendanceRepository;
    @Mock private LiveEventEmitter emitter;

    private AttendanceService attendanceService;

    @BeforeEach
    void setUp() {
        attendanceService = new AttendanceService(eventService, attendanceRepository, emitter);
    }

    @Test
    void evaluateEligibility_grantsCpdWhenThresholdMet() {
        LiveEventEntity event = cpdEvent(45);
        LiveEventAttendanceEntity attendance = attendanceWithMinutes(50, 0);

        attendanceService.evaluateEligibility(attendance, event);

        assertThat(attendance.isEligibleForCpd()).isTrue();
        assertThat(attendance.isEligibleForCertificate()).isTrue();
        assertThat(attendance.getCompletionStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void evaluateEligibility_deniesCpdWhenBelowThreshold() {
        LiveEventEntity event = cpdEvent(45);
        LiveEventAttendanceEntity attendance = attendanceWithMinutes(20, 0);

        attendanceService.evaluateEligibility(attendance, event);

        assertThat(attendance.isEligibleForCpd()).isFalse();
        assertThat(attendance.isEligibleForCertificate()).isFalse();
    }

    @Test
    void evaluateEligibility_deniesCpdWhenCpdDisabled() {
        LiveEventEntity event = cpdEvent(30);
        event.setCpdEnabled(false);
        LiveEventAttendanceEntity attendance = attendanceWithMinutes(60, 0);

        attendanceService.evaluateEligibility(attendance, event);

        assertThat(attendance.isEligibleForCpd()).isFalse();
        assertThat(attendance.isEligibleForCertificate()).isTrue();
    }

    @Test
    void trackMinutes_persistsEligibility() {
        LiveEventEntity event = cpdEvent(30);
        LiveEventAttendanceEntity attendance = attendanceWithMinutes(0, 0);

        when(eventService.get(TENANT_ID, EVENT_ID)).thenReturn(event);
        when(attendanceRepository.findByEventIdAndParticipantId(EVENT_ID, "PROV-1"))
                .thenReturn(Optional.of(attendance));
        when(attendanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LiveEventAttendanceEntity result = attendanceService.trackMinutes(
                TENANT_ID, EVENT_ID, "PROV-1", 35, 0);

        assertThat(result.getLiveWatchMinutes()).isEqualTo(35);
        assertThat(result.isEligibleForCpd()).isTrue();
    }

    private static LiveEventEntity cpdEvent(int thresholdMinutes) {
        LiveEventEntity event = new LiveEventEntity();
        event.setId(EVENT_ID);
        event.setTenantId(TENANT_ID);
        event.setCpdEnabled(true);
        event.setCpdPoints(BigDecimal.valueOf(2.0));
        event.setAttendanceThresholdMinutes(thresholdMinutes);
        return event;
    }

    private static LiveEventAttendanceEntity attendanceWithMinutes(int live, int replay) {
        LiveEventAttendanceEntity attendance = new LiveEventAttendanceEntity();
        attendance.setId(UUID.randomUUID());
        attendance.setEventId(EVENT_ID);
        attendance.setParticipantId("PROV-1");
        attendance.setLiveWatchMinutes(live);
        attendance.setReplayWatchMinutes(replay);
        attendance.setTotalWatchMinutes(live + replay);
        return attendance;
    }
}
