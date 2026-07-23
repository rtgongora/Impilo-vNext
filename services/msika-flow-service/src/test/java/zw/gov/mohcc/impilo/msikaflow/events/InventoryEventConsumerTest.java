package zw.gov.mohcc.impilo.msikaflow.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.msikaflow.domain.OfferStatus;
import zw.gov.mohcc.impilo.msikaflow.domain.ReservationStatus;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.FulfillmentOfferEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.FulfillmentOfferLineEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.ReservationEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.FulfillmentOfferLineRepository;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.FulfillmentOfferRepository;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.ReservationRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * OF-B11 — the no-op consumer replacement contract: DURA reservation events
 * update the {@code mf_reservations} projection by {@code reservationRef}, and
 * an expired/released hold fail-closes an offer still honouring it (§9.4).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventoryEventConsumerTest {

    @Mock private ReservationRepository reservationRepository;
    @Mock private FulfillmentOfferLineRepository offerLineRepository;
    @Mock private FulfillmentOfferRepository offerRepository;

    private InventoryEventConsumer consumer;
    private final String duraRef = UUID.randomUUID().toString();
    private ReservationEntity projection;

    @BeforeEach
    void setUp() {
        consumer = new InventoryEventConsumer(reservationRepository, offerLineRepository,
                offerRepository, new ObjectMapper());
        projection = new ReservationEntity();
        projection.setId("01RESAAAAAAAAAAAAAAAAAAAAA");
        projection.setOrderId("OROS-1");
        projection.setLineId("01LINAAAAAAAAAAAAAAAAAAAAA");
        projection.setSystem("INVENTORY");
        projection.setStatus(ReservationStatus.CONFIRMED);
        projection.setReservationRef(duraRef);
        when(reservationRepository.findFirstByReservationRef(duraRef))
                .thenReturn(Optional.of(projection));
        when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(offerLineRepository.findByDuraReservationRef(duraRef)).thenReturn(List.of());
    }

    @Test
    void statusChangedEvent_updatesProjectionRow() {
        consumer.onReservationStatusChanged(
                "{\"reservationRef\":\"" + duraRef + "\",\"status\":\"RELEASED\"}");
        assertEquals(ReservationStatus.RELEASED, projection.getStatus());
        verify(reservationRepository).save(projection);
    }

    @Test
    void consumedEvent_mapsToConsumedProjectionStatus() {
        consumer.applyStatus(duraRef, "CONSUMED");
        assertEquals(ReservationStatus.CONSUMED, projection.getStatus());
    }

    @Test
    void activeEvent_mapsToConfirmed() {
        projection.setStatus(ReservationStatus.PENDING);
        consumer.applyStatus(duraRef, "ACTIVE");
        assertEquals(ReservationStatus.CONFIRMED, projection.getStatus());
    }

    @Test
    void expiredReservation_failClosesOfferStillHonouringIt() {
        FulfillmentOfferLineEntity line = new FulfillmentOfferLineEntity();
        line.setOfferId("01OFFAAAAAAAAAAAAAAAAAAAAA");
        line.setDuraReservationRef(duraRef);
        when(offerLineRepository.findByDuraReservationRef(duraRef)).thenReturn(List.of(line));
        FulfillmentOfferEntity offer = new FulfillmentOfferEntity();
        offer.setOfferId("01OFFAAAAAAAAAAAAAAAAAAAAA");
        offer.setStatus(OfferStatus.SELECTED);
        when(offerRepository.findById("01OFFAAAAAAAAAAAAAAAAAAAAA")).thenReturn(Optional.of(offer));
        when(offerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer.applyStatus(duraRef, "EXPIRED");

        // §9.4 binding TTL semantics: the hold and the offer expire together
        assertEquals(OfferStatus.EXPIRED, offer.getStatus());
        assertEquals("RESERVATION_EXPIRED", offer.getStatusReason());
        assertEquals(ReservationStatus.EXPIRED, projection.getStatus());
    }

    @Test
    void committedOffer_isNotTouchedByLateExpiryEvent() {
        FulfillmentOfferLineEntity line = new FulfillmentOfferLineEntity();
        line.setOfferId("01OFFAAAAAAAAAAAAAAAAAAAAA");
        line.setDuraReservationRef(duraRef);
        when(offerLineRepository.findByDuraReservationRef(duraRef)).thenReturn(List.of(line));
        FulfillmentOfferEntity offer = new FulfillmentOfferEntity();
        offer.setOfferId("01OFFAAAAAAAAAAAAAAAAAAAAA");
        offer.setStatus(OfferStatus.COMMITTED);
        when(offerRepository.findById("01OFFAAAAAAAAAAAAAAAAAAAAA")).thenReturn(Optional.of(offer));

        consumer.applyStatus(duraRef, "EXPIRED");
        // post-commit expiry rides the fulfilment machine, not the offer machine
        assertEquals(OfferStatus.COMMITTED, offer.getStatus());
    }

    @Test
    void missingProjectionRow_isToleratedNotFatal() {
        when(reservationRepository.findFirstByReservationRef(duraRef)).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> consumer.applyStatus(duraRef, "RELEASED"));
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void unknownStatus_isIgnoredSafely() {
        assertDoesNotThrow(() -> consumer.applyStatus(duraRef, "SOMETHING_NEW"));
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void malformedMessage_isSwallowedWithoutCrash() {
        assertDoesNotThrow(() -> consumer.onReservationStatusChanged("not-json"));
        assertDoesNotThrow(() -> consumer.onReservationStatusChanged("{}"));
    }

    @Test
    void marketplaceTopics_routeCorrectly() {
        assertEquals("msika.flow.request.published.v1", OutboxPublisher.routeTopic("REQUEST_PUBLISHED"));
        assertEquals("msika.flow.request.offered.v1", OutboxPublisher.routeTopic("OFFER_SUBMITTED"));
        assertEquals("msika.flow.request.selected.v1", OutboxPublisher.routeTopic("SELECTION_COMMITTED"));
        assertEquals("msika.flow.request.selected.v1", OutboxPublisher.routeTopic("SELECTION_FAILED"));
        assertEquals("msika.flow.request.cancelled.v1", OutboxPublisher.routeTopic("REQUEST_CANCELLED"));
        assertEquals("msika.flow.request.expired.v1", OutboxPublisher.routeTopic("REQUEST_EXPIRED"));
        assertEquals("msika.flow.request.expired.v1", OutboxPublisher.routeTopic("REQUEST_FAILED_NO_OFFERS"));
        assertEquals("msika.flow.request.expired.v1", OutboxPublisher.routeTopic("OFFER_EXPIRED"));
    }
}
