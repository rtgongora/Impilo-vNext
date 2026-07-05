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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
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

    @Test
    @SuppressWarnings("unchecked")
    void checkIn_startsJourneyByJourneyIdKeyAndEnqueues() {
        UUID appointmentId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        UUID queueId = UUID.fromString("bbbbbbbb-cccc-dddd-eeee-ffffffffffff");
        UUID queueItemId = UUID.fromString("cccccccc-dddd-eeee-ffff-000000000000");

        AppointmentEntity entity = new AppointmentEntity();
        entity.setId(appointmentId);
        entity.setTenantId(TENANT_ID);
        entity.setPatientCpid("CPID-ZW-00001");
        entity.setFacilityId(42L);
        entity.setStatus(AppointmentStatus.CONFIRMED);
        // checkIn publishes appointment.booked with scheduledAt = startTime;
        // a fixture without times NPEs inside publishBookedEvent
        entity.setStartTime(Instant.parse("2026-06-15T09:00:00Z"));
        entity.setEndTime(Instant.parse("2026-06-15T09:30:00Z"));
        when(appointmentRepository.findByIdAndTenantId(appointmentId, TENANT_ID))
                .thenReturn(Optional.of(entity));
        when(appointmentRepository.save(any(AppointmentEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // PCT journeys are keyed by journeyId (ULID), never "id" — the old code
        // read "id" and silently skipped the enqueue on every check-in.
        when(pctClient.startJourney(any(), anyMap()))
                .thenReturn(Map.of("journeyId", "01JZZZZZZZZZZZZZZZZZZZZZZZ"));
        when(pctClient.enqueue(any(), eq(queueId), anyMap()))
                .thenReturn(Map.of("id", queueItemId.toString()));

        AppointmentResponse response = service.checkIn(appointmentId, queueId);

        assertThat(response.status()).isEqualTo("CHECKED_IN");
        assertThat(entity.getCheckInStatus()).isEqualTo("CHECKED_IN");
        assertThat(entity.getQueueTokenId()).isEqualTo(queueItemId);

        ArgumentCaptor<Map<String, Object>> journeyPayload = ArgumentCaptor.forClass(Map.class);
        verify(pctClient).startJourney(any(), journeyPayload.capture());
        assertThat(journeyPayload.getValue())
                .containsEntry("patientCpid", "CPID-ZW-00001")
                .containsEntry("appointmentId", appointmentId.toString())
                // TUSO numeric facility id must not be sent; PCT resolves the
                // facility from the X-Facility-ID trust header
                .doesNotContainKey("facilityId");

        ArgumentCaptor<Map<String, Object>> enqueuePayload = ArgumentCaptor.forClass(Map.class);
        verify(pctClient).enqueue(any(), eq(queueId), enqueuePayload.capture());
        assertThat(enqueuePayload.getValue())
                .containsEntry("journeyId", "01JZZZZZZZZZZZZZZZZZZZZZZZ")
                .containsEntry("priority", 3);

        // Check-in ends at queue entry: the encounter starts when the provider
        // begins service, never at the front desk.
        verify(pctClient, never()).startEncounter(any(), any(), anyMap());
    }

    @Test
    void checkIn_marksNoQueueHonestlyWhenJourneyUnavailable() {
        UUID appointmentId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

        AppointmentEntity entity = new AppointmentEntity();
        entity.setId(appointmentId);
        entity.setTenantId(TENANT_ID);
        entity.setPatientCpid("CPID-ZW-00001");
        entity.setStatus(AppointmentStatus.CONFIRMED);
        entity.setStartTime(Instant.parse("2026-06-15T09:00:00Z"));
        entity.setEndTime(Instant.parse("2026-06-15T09:30:00Z"));
        when(appointmentRepository.findByIdAndTenantId(appointmentId, TENANT_ID))
                .thenReturn(Optional.of(entity));
        when(appointmentRepository.save(any(AppointmentEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        // PCT unreachable → PctClient returns an empty map, never throws
        when(pctClient.startJourney(any(), anyMap())).thenReturn(Map.of());

        AppointmentResponse response = service.checkIn(appointmentId,
                UUID.fromString("bbbbbbbb-cccc-dddd-eeee-ffffffffffff"));

        assertThat(response.status()).isEqualTo("CHECKED_IN");
        assertThat(entity.getCheckInStatus()).isEqualTo("CHECKED_IN_NO_QUEUE");
        assertThat(entity.getQueueTokenId()).isNull();
        verify(pctClient, never()).enqueue(any(), any(), anyMap());
    }
}
