package zw.gov.mohcc.impilo.booking.api.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import zw.gov.mohcc.impilo.booking.api.dto.AppointmentResponse;
import zw.gov.mohcc.impilo.booking.api.dto.BookingResponse;
import zw.gov.mohcc.impilo.booking.core.BookingIntegrationService;
import zw.gov.mohcc.impilo.booking.core.BookingMvumoService;
import zw.gov.mohcc.impilo.booking.core.BookingService;
import zw.gov.mohcc.impilo.booking.persistence.entity.BookingEntity;
import zw.gov.mohcc.impilo.booking.persistence.entity.BookingMvumoEntity;
import zw.gov.mohcc.impilo.booking.domain.*;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingControllerContractSurfaceTest {

    @Mock private BookingService bookingService;
    @Mock private BookingIntegrationService integrationService;
    @Mock private BookingMvumoService mvumoService;

    private BookingController controller;

    @BeforeEach
    void setUp() {
        controller = new BookingController(bookingService, integrationService, mvumoService);
        TrustContextHolder.set(new TrustContext(
                UUID.randomUUID(), "actor", "CITIZEN", "TREATMENT", "device",
                UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
    }

    @Test
    void putUpdateDelegatesToService() {
        UUID id = UUID.randomUUID();
        BookingEntity entity = sampleBooking(id);
        when(bookingService.update(eq(id), any())).thenReturn(BookingResponse.from(entity));

        var result = controller.replace(id, Map.of("reason", "change"));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void convertAliasReturnsCreated() {
        UUID id = UUID.randomUUID();
        when(bookingService.convertToAppointment(id)).thenReturn(mock(AppointmentResponse.class));

        var result = controller.convert(id);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void mvumoRequestAndStatusSurfaceRealService() {
        UUID id = UUID.randomUUID();
        BookingMvumoEntity entity = new BookingMvumoEntity();
        entity.setMvumoType(MvumoType.CONSENT);
        entity.setStatus(ConsentStatus.PENDING);
        entity.setMvumoRecordId("mv-1");
        when(mvumoService.requestConsent(id, MvumoType.CONSENT)).thenReturn(entity);
        when(mvumoService.status(id, MvumoType.CONSENT)).thenReturn(ConsentStatus.PENDING);
        when(mvumoService.status(id, MvumoType.AGREEMENT)).thenReturn(ConsentStatus.NOT_REQUIRED);
        when(mvumoService.status(id, MvumoType.AUTHORISATION)).thenReturn(ConsentStatus.NOT_REQUIRED);
        when(mvumoService.status(id, MvumoType.ACKNOWLEDGEMENT)).thenReturn(ConsentStatus.NOT_REQUIRED);

        assertThat(controller.requestMvumo(id, Map.of("mvumoType", "CONSENT")).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(controller.mvumoStatus(id).getBody().data().get("consent")).isEqualTo("PENDING");
    }

    @Test
    void availabilityProxiesTusoSlots() {
        when(integrationService.checkAvailability(any(), eq(42L), eq("2026-06-07"), eq("FACILITY")))
                .thenReturn(List.of(Map.of("slot", "09:00")));

        var result = controller.availability("FACILITY", "42", "2026-06-07", 42L, null);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().data()).hasSize(1);
    }

    private static BookingEntity sampleBooking(UUID id) {
        BookingEntity entity = new BookingEntity();
        entity.setId(id);
        entity.setBookingNumber("BK-1");
        entity.setClientId("cpid-1");
        entity.setBookingType(BookingType.CONSULTATION);
        entity.setBookingStatus(BookingStatus.REQUESTED);
        entity.setTriageStatus(TriageStatus.NOT_REQUIRED);
        entity.setConsentStatus(ConsentStatus.NOT_REQUIRED);
        entity.setApprovalStatus(ApprovalStatus.NOT_REQUIRED);
        entity.setCreatedAt(java.time.Instant.now());
        return entity;
    }
}
