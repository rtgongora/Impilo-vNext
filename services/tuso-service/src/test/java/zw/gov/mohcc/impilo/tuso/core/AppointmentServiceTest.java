package zw.gov.mohcc.impilo.tuso.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.api.dto.AppointmentResponse;
import zw.gov.mohcc.impilo.tuso.persistence.entity.AppointmentEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.AppointmentRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private FacilityRepository facilityRepository;
    @Mock
    private EventOutboxRepository outboxRepository;

    private AppointmentService service;

    @BeforeEach
    void setUp() {
        service = new AppointmentService(appointmentRepository, facilityRepository, outboxRepository);
        TrustContextHolder.set(new TrustContext(
                TENANT_ID,
                "actor-citizen-1",
                "CITIZEN",
                "TREATMENT",
                "device-test",
                UUID.randomUUID(),
                null,
                null,
                null,
                AccessMode.INTERNAL));
    }

    @Test
    void createCitizen_persistsRequestedAppointmentAndEmitsOutbox() {
        FacilityEntity facility = new FacilityEntity();
        facility.setId(42L);
        facility.setTenantId(TENANT_ID);
        facility.setName("Harare Central");
        when(facilityRepository.findById(42L)).thenReturn(Optional.of(facility));

        AppointmentEntity saved = new AppointmentEntity();
        saved.setId(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"));
        saved.setTenantId(TENANT_ID);
        saved.setFacilityId(42L);
        saved.setPatientCpid("CPID-ZW-00001");
        saved.setPatientId("actor-citizen-1");
        saved.setAppointmentType("GENERAL");
        saved.setStatus("REQUESTED");
        saved.setScheduledAt(Instant.parse("2026-06-15T09:00:00Z"));
        saved.setEndAt(Instant.parse("2026-06-15T09:30:00Z"));
        when(appointmentRepository.save(any(AppointmentEntity.class))).thenReturn(saved);
        when(facilityRepository.findById(42L)).thenReturn(Optional.of(facility));

        AppointmentResponse response = service.createCitizen("CPID-ZW-00001",
                new AppointmentService.CreateCitizenAppointmentRequest(
                        "42",
                        "GENERAL",
                        "2026-06-15T09:00:00Z",
                        "Annual check-up"));

        assertThat(response.status()).isEqualTo("REQUESTED");
        assertThat(response.facilityName()).isEqualTo("Harare Central");

        ArgumentCaptor<AppointmentEntity> captor = ArgumentCaptor.forClass(AppointmentEntity.class);
        verify(appointmentRepository).save(captor.capture());
        assertThat(captor.getValue().getPatientCpid()).isEqualTo("CPID-ZW-00001");
        assertThat(captor.getValue().getFacilityId()).isEqualTo(42L);

        ArgumentCaptor<EventOutboxEntity> outboxCaptor = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("appointment.booked");
    }
}
