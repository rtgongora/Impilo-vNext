package zw.gov.mohcc.impilo.booking.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.booking.api.dto.AppointmentResponse;
import zw.gov.mohcc.impilo.booking.api.dto.BookingResponse;
import zw.gov.mohcc.impilo.booking.domain.*;
import zw.gov.mohcc.impilo.booking.persistence.entity.BookingEntity;
import zw.gov.mohcc.impilo.booking.persistence.repository.BookingRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BOOKING_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    @Mock private BookingRepository bookingRepository;
    @Mock private BookingStateMachine stateMachine;
    @Mock private BookingOutboxPublisher outboxPublisher;
    @Mock private ProviderResolutionService providerResolutionService;
    @Mock private BookingIntegrationService integrationService;
    @Mock private BookingMvumoService mvumoService;
    @Mock private AppointmentService appointmentService;

    private BookingService service;

    @BeforeEach
    void setUp() {
        service = new BookingService(
                bookingRepository, stateMachine, outboxPublisher,
                providerResolutionService, integrationService, mvumoService, appointmentService);
        TrustContextHolder.set(new TrustContext(
                TENANT_ID, "actor-1", "CITIZEN", "TREATMENT", "device",
                UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
    }

    @Test
    void create_persistsBookingWithoutAppointment() {
        when(integrationService.checkEligibility(any(), any())).thenReturn(EligibilityStatus.ELIGIBLE);
        when(bookingRepository.save(any(BookingEntity.class))).thenAnswer(inv -> {
            BookingEntity e = inv.getArgument(0);
            e.setId(BOOKING_ID);
            return e;
        });

        BookingResponse response = service.create(baseCreateRequest(false, false, true));

        assertThat(response.bookingStatus()).isEqualTo("PENDING_APPROVAL");
        assertThat(response.linkedAppointmentId()).isNull();
        assertThat(response.bookingNumber()).startsWith("BK-");
        verify(appointmentService, never()).createFromBooking(any());
        verify(outboxPublisher).append(eq("BOOKING"), anyString(), eq("booking.created"), anyMap());
    }

    @Test
    void triage_setsPendingThenTriaged() {
        BookingEntity booking = baseBooking(BookingStatus.REQUESTED);
        booking.setTriageStatus(TriageStatus.PENDING);
        when(bookingRepository.findByIdAndTenantId(BOOKING_ID, TENANT_ID)).thenReturn(Optional.of(booking));
        when(stateMachine.transition(any(), any(), eq(BookingStatus.TRIAGED), eq("TRIAGE"))).thenAnswer(inv -> {
            BookingEntity b = inv.getArgument(1);
            b.setBookingStatus(BookingStatus.TRIAGED);
            return b;
        });
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BookingResponse response = service.triage(BOOKING_ID, TriageStatus.COMPLETED);

        assertThat(response.bookingStatus()).isEqualTo("TRIAGED");
        assertThat(booking.getTriageStatus()).isEqualTo(TriageStatus.COMPLETED);
    }

    @Test
    void confirm_blocksWhenMvumoDeclined() {
        BookingEntity booking = baseBooking(BookingStatus.RESERVED);
        booking.setConsentStatus(ConsentStatus.REFUSED);
        when(bookingRepository.findByIdAndTenantId(BOOKING_ID, TENANT_ID)).thenReturn(Optional.of(booking));
        doThrow(new IllegalStateException("Mvumo consent must be GRANTED before confirmation"))
                .when(mvumoService).assertConsentGrantedForConfirmation(booking);

        assertThatThrownBy(() -> service.confirm(BOOKING_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GRANTED");
    }

    @Test
    void approve_createsAppointmentAndLinksBooking() {
        BookingEntity booking = baseBooking(BookingStatus.PENDING_APPROVAL);
        booking.setApprovalStatus(ApprovalStatus.PENDING);
        booking.setConsentStatus(ConsentStatus.NOT_REQUIRED);
        when(bookingRepository.findByIdAndTenantId(BOOKING_ID, TENANT_ID)).thenReturn(Optional.of(booking));
        when(stateMachine.transition(any(), any(), any(), anyString())).thenAnswer(inv -> {
            BookingEntity b = inv.getArgument(1);
            b.setBookingStatus(inv.getArgument(2));
            return b;
        });
        UUID appointmentId = UUID.fromString("ffffffff-1111-2222-3333-444444444444");
        when(appointmentService.createFromBooking(booking)).thenReturn(
                new AppointmentResponse(
                        appointmentId, 42L, 42L, "Harare Central", "Harare Central",
                        "CPID-1", "CPID-1", "CPID-1", "CPID-1",
                        null, null, null, null,
                        "GENERAL", "GENERAL", "SCHEDULED",
                        Instant.parse("2026-06-15T09:00:00Z"), Instant.parse("2026-06-15T09:00:00Z"),
                        Instant.parse("2026-06-15T09:30:00Z"), Instant.parse("2026-06-15T09:30:00Z"),
                        "reason", null, null, null, BOOKING_ID, BOOKING_ID,
                        null, null, null, null, null, null,
                        Instant.now(), Instant.now()));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BookingResponse response = service.approve(BOOKING_ID);

        assertThat(response.linkedAppointmentId()).isEqualTo(appointmentId);
        assertThat(response.bookingStatus()).isEqualTo("CONFIRMED");
        verify(appointmentService).createFromBooking(booking);
    }

    private BookingEntity baseBooking(BookingStatus status) {
        BookingEntity booking = new BookingEntity();
        booking.setId(BOOKING_ID);
        booking.setTenantId(TENANT_ID);
        booking.setBookingNumber("BK-TEST1234");
        booking.setClientId("CPID-1");
        booking.setRequestedByActorId("actor-1");
        booking.setRequestedByActorType(ActorType.CITIZEN);
        booking.setBookingType(BookingType.CONSULTATION);
        booking.setBookingStatus(status);
        booking.setCreatedBy("actor-1");
        return booking;
    }

    private BookingService.CreateBookingRequest baseCreateRequest(
            boolean triage, boolean consent, boolean approval) {
        return new BookingService.CreateBookingRequest(
                "CPID-1", "CITIZEN", BookingType.CONSULTATION,
                null, null, 42L, null, null, null,
                Instant.parse("2026-06-15T09:00:00Z"),
                Instant.parse("2026-06-15T09:30:00Z"),
                PreferredChannel.IN_PERSON, null, "Check-up",
                ClinicalPriority.ROUTINE, null, null,
                triage, consent, approval);
    }
}
