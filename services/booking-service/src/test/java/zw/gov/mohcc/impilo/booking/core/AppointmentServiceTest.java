package zw.gov.mohcc.impilo.booking.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.booking.api.dto.AppointmentResponse;
import zw.gov.mohcc.impilo.booking.domain.AppointmentStatus;
import zw.gov.mohcc.impilo.booking.integration.PctClient;
import zw.gov.mohcc.impilo.booking.persistence.entity.AppointmentEntity;
import zw.gov.mohcc.impilo.booking.persistence.repository.AppointmentRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock private AppointmentRepository appointmentRepository;
    @Mock private BookingOutboxPublisher outboxPublisher;
    @Mock private PctClient pctClient;

    private AppointmentService service;

    @BeforeEach
    void setUp() {
        service = new AppointmentService(appointmentRepository, outboxPublisher, pctClient);
        TrustContextHolder.set(new TrustContext(
                TENANT_ID, "actor-citizen-1", "CITIZEN", "TREATMENT", "device-test",
                UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
    }

    @Test
    void createCitizen_persistsRequestedAppointmentAndEmitsOutbox() {
        AppointmentEntity saved = new AppointmentEntity();
        saved.setId(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"));
        saved.setTenantId(TENANT_ID);
        saved.setFacilityId(42L);
        saved.setPatientCpid("CPID-ZW-00001");
        saved.setPatientId("actor-citizen-1");
        saved.setAppointmentType("GENERAL");
        saved.setStatus(AppointmentStatus.REQUESTED);
        saved.setStartTime(Instant.parse("2026-06-15T09:00:00Z"));
        saved.setEndTime(Instant.parse("2026-06-15T09:30:00Z"));
        saved.setScheduledAt(saved.getStartTime());
        saved.setEndAt(saved.getEndTime());
        saved.setCreatedAt(Instant.now());
        when(appointmentRepository.save(any(AppointmentEntity.class))).thenReturn(saved);

        AppointmentResponse response = service.createCitizen("CPID-ZW-00001",
                new AppointmentService.CreateCitizenAppointmentRequest(
                        "42", "GENERAL", "2026-06-15T09:00:00Z", "Annual check-up"));

        assertThat(response.status()).isEqualTo("REQUESTED");
        assertThat(response.patientCpid()).isEqualTo("CPID-ZW-00001");

        ArgumentCaptor<AppointmentEntity> captor = ArgumentCaptor.forClass(AppointmentEntity.class);
        verify(appointmentRepository).save(captor.capture());
        assertThat(captor.getValue().getPatientCpid()).isEqualTo("CPID-ZW-00001");
        assertThat(captor.getValue().getFacilityId()).isEqualTo(42L);

        verify(outboxPublisher).append(eq("APPOINTMENT"), anyString(), eq("appointment.booked"), anyMap());
    }
}
