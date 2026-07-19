package zw.gov.mohcc.impilo.tuso.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.tuso.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityAdminAppointmentEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityAdminAppointmentRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V030/D-L4: expiry is ENFORCED — an ACTIVE appointment whose valid_to has
 * passed flips to EXPIRED and emits an event; open-ended appointments are
 * untouched.
 */
@ExtendWith(MockitoExtension.class)
class FacilityAdminAppointmentExpirySweepTest {

    @Mock private FacilityAdminAppointmentRepository appointmentRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private FacilityRepository facilityRepository;

    private FacilityAdminAppointmentExpirySweep sweep;

    private final UUID facilityUuid = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        sweep = new FacilityAdminAppointmentExpirySweep(
                appointmentRepository, outboxRepository, facilityRepository);
        FacilityEntity facility = new FacilityEntity();
        facility.setFacilityUuid(facilityUuid);
        facility.setTenantId(tenantId);
        lenient().when(facilityRepository.findByFacilityUuid(facilityUuid))
                .thenReturn(Optional.of(facility));
        lenient().when(appointmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void lapsedActiveAppointmentExpiresAndEmitsEvent() {
        FacilityAdminAppointmentEntity lapsed = new FacilityAdminAppointmentEntity();
        lapsed.setId(5L);
        lapsed.setFacilityUuid(facilityUuid);
        lapsed.setPersonHealthId("HID-1");
        lapsed.setRole(FacilityAdminAppointmentEntity.ROLE_FACILITY_ADMINISTRATOR);
        lapsed.setApprovalState(FacilityAdminAppointmentEntity.STATE_ACTIVE);
        lapsed.setValidTo(LocalDate.of(2026, 6, 30));
        when(appointmentRepository.findByApprovalStateAndValidToBefore(
                FacilityAdminAppointmentEntity.STATE_ACTIVE, LocalDate.of(2026, 7, 19)))
                .thenReturn(List.of(lapsed));

        int expired = sweep.expireLapsed(LocalDate.of(2026, 7, 19));

        assertThat(expired).isEqualTo(1);
        assertThat(lapsed.getApprovalState()).isEqualTo(FacilityAdminAppointmentEntity.STATE_EXPIRED);

        ArgumentCaptor<EventOutboxEntity> outbox = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(outbox.capture());
        assertThat(outbox.getValue().getEventType())
                .isEqualTo(FacilityAdminAppointmentExpirySweep.EVENT_EXPIRED);
        assertThat(outbox.getValue().getTenantId()).isEqualTo(tenantId.toString());
    }

    @Test
    void nothingLapsedMeansNoWrites() {
        when(appointmentRepository.findByApprovalStateAndValidToBefore(any(), any()))
                .thenReturn(List.of());

        assertThat(sweep.expireLapsed(LocalDate.now())).isZero();
        verify(outboxRepository, never()).save(any());
    }
}
