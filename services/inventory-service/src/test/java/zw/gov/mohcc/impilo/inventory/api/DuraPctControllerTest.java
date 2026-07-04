package zw.gov.mohcc.impilo.inventory.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import zw.gov.mohcc.impilo.inventory.api.controller.DuraPctController;
import zw.gov.mohcc.impilo.inventory.api.dto.PctConsumeRequest;
import zw.gov.mohcc.impilo.inventory.api.dto.PctConsumeResponse;
import zw.gov.mohcc.impilo.inventory.api.dto.PctReserveRequest;
import zw.gov.mohcc.impilo.inventory.api.dto.ReservationDto;
import zw.gov.mohcc.impilo.inventory.core.ConsumptionPostingService;
import zw.gov.mohcc.impilo.inventory.core.ReservationService;
import zw.gov.mohcc.impilo.inventory.core.StockAvailabilityService;
import zw.gov.mohcc.impilo.inventory.persistence.entity.LedgerEventEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.StockReservationEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Tests the previously untested Dura↔PCT contract ({@code /v1/dura/pct/*}):
 * availability lookup, encounter reservation (with default ENCOUNTER refType),
 * and clinical consumption that posts a real ledger event and optionally
 * fulfils a reservation.
 */
@ExtendWith(MockitoExtension.class)
class DuraPctControllerTest {

    @Mock
    private StockAvailabilityService availabilityService;

    @Mock
    private ReservationService reservationService;

    @Mock
    private ConsumptionPostingService consumptionPostingService;

    private DuraPctController controller;

    private static final UUID FACILITY_ID = UUID.randomUUID();
    private static final UUID STORE_ID = UUID.randomUUID();
    private static final String ITEM_CODE = "PARA-500";

    @BeforeEach
    void setUp() {
        controller = new DuraPctController(availabilityService, reservationService, consumptionPostingService);
        TrustContextHolder.set(new TrustContext(
                UUID.randomUUID(), "provider-001", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), FACILITY_ID, null, null, null));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    @Test
    @DisplayName("availability proxies the stock-aware availability decision")
    void availability_returnsDecision() {
        StockAvailabilityService.AvailabilityResult expected =
                new StockAvailabilityService.AvailabilityResult(
                        ITEM_CODE, 0, 5, 5, true, List.of(), null,
                        List.of(new StockAvailabilityService.SubstituteOption("IBU-400", "Ibuprofen 400mg", 12)));
        when(availabilityService.availability(FACILITY_ID, STORE_ID, ITEM_CODE)).thenReturn(expected);

        ResponseEntity<ApiResponse<StockAvailabilityService.AvailabilityResult>> response =
                controller.availability(FACILITY_ID, STORE_ID, ITEM_CODE);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        StockAvailabilityService.AvailabilityResult body = response.getBody().data();
        assertThat(body.stockOut()).isTrue();
        assertThat(body.substitutes()).hasSize(1);
    }

    @Test
    @DisplayName("reserve defaults refType to ENCOUNTER and delegates to the reservation engine")
    void reserve_defaultsRefTypeToEncounter() {
        PctReserveRequest request = new PctReserveRequest(
                FACILITY_ID, STORE_ID, ITEM_CODE, null, 10, "TAB",
                "ENC-123", null, "Dispense for encounter");
        StockReservationEntity reservation = new StockReservationEntity();
        ReflectionTestUtils.setField(reservation, "reservationId", UUID.randomUUID());
        when(reservationService.reserve(
                eq(FACILITY_ID), eq(STORE_ID), eq(ITEM_CODE), isNull(),
                eq(10), eq("TAB"), eq("ENCOUNTER"), eq("ENC-123"),
                eq("Dispense for encounter"), isNull()))
                .thenReturn(reservation);

        ResponseEntity<ApiResponse<ReservationDto>> response = controller.reserve(request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().data().reservationId()).isNotNull();
    }

    @Test
    @DisplayName("consume posts a clinical ledger event and fulfils the reservation when given")
    void consume_postsLedgerEvent_andConsumesReservation() {
        UUID reservationId = UUID.randomUUID();
        UUID ledgerEventId = UUID.randomUUID();
        PctConsumeRequest request = new PctConsumeRequest(
                FACILITY_ID, STORE_ID, ITEM_CODE, 10, "ENCOUNTER", "ENC-123", reservationId);

        LedgerEventEntity event = new LedgerEventEntity();
        ReflectionTestUtils.setField(event, "eventId", ledgerEventId);
        when(consumptionPostingService.postClinicalConsumption(
                FACILITY_ID, STORE_ID, ITEM_CODE, 10, "ENCOUNTER", "ENC-123"))
                .thenReturn(event);

        ResponseEntity<ApiResponse<PctConsumeResponse>> response = controller.consume(request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().data().ledgerEventId()).isEqualTo(ledgerEventId);
        assertThat(response.getBody().data().reservationConsumed()).isTrue();
        verify(reservationService).consume(reservationId);
    }

    @Test
    @DisplayName("consume without a reservation posts the ledger event only")
    void consume_withoutReservation_postsOnly() {
        PctConsumeRequest request = new PctConsumeRequest(
                FACILITY_ID, STORE_ID, ITEM_CODE, 2, "PROCEDURE", "PROC-9", null);

        LedgerEventEntity event = new LedgerEventEntity();
        ReflectionTestUtils.setField(event, "eventId", UUID.randomUUID());
        when(consumptionPostingService.postClinicalConsumption(
                FACILITY_ID, STORE_ID, ITEM_CODE, 2, "PROCEDURE", "PROC-9"))
                .thenReturn(event);

        ResponseEntity<ApiResponse<PctConsumeResponse>> response = controller.consume(request);

        assertThat(response.getBody().data().reservationConsumed()).isFalse();
        verify(reservationService, never()).consume(org.mockito.ArgumentMatchers.any());
    }
}
