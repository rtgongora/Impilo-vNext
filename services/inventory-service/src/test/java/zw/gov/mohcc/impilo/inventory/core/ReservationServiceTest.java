package zw.gov.mohcc.impilo.inventory.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import zw.gov.mohcc.impilo.inventory.domain.ReservationStatus;
import zw.gov.mohcc.impilo.inventory.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.OnHandEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.StockReservationEntity;
import zw.gov.mohcc.impilo.inventory.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.inventory.persistence.repository.StockReservationRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock private StockReservationRepository reservationRepository;
    @Mock private OnHandService onHandService;
    @Mock private EventOutboxRepository outboxRepository;

    private ReservationServiceImpl service;

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID FACILITY = UUID.randomUUID();
    private static final UUID STORE = UUID.randomUUID();
    private static final String ITEM = "PARA-500";

    @BeforeEach
    void setUp() {
        service = new ReservationServiceImpl(reservationRepository, onHandService,
                outboxRepository, new ObjectMapper());
        TrustContextHolder.set(new TrustContext(
                TENANT, "nurse-7", "STAFF", "TREATMENT", null,
                UUID.randomUUID(), FACILITY, null, null, null));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private void stubOnHand(int qty) {
        OnHandEntity oh = new OnHandEntity();
        oh.setQtyOnHand(qty);
        Page<OnHandEntity> page = new PageImpl<>(List.of(oh));
        when(onHandService.getOnHand(eq(FACILITY), eq(STORE), eq(null), eq(ITEM), any(Pageable.class)))
                .thenReturn(page);
    }

    private List<String> capturedEventTypes() {
        ArgumentCaptor<EventOutboxEntity> captor = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository, atLeastOnce()).save(captor.capture());
        return captor.getAllValues().stream().map(EventOutboxEntity::getEventType).toList();
    }

    @Test
    @DisplayName("availableQuantity = on-hand minus active reservations")
    void availableQuantity() {
        stubOnHand(100);
        when(reservationRepository.sumActiveReserved(TENANT, FACILITY, STORE, ITEM)).thenReturn(30);

        assertThat(service.availableQuantity(FACILITY, STORE, ITEM)).isEqualTo(70);
    }

    @Test
    @DisplayName("reserve succeeds when available is sufficient")
    void reserve_success() {
        stubOnHand(100);
        when(reservationRepository.sumActiveReserved(TENANT, FACILITY, STORE, ITEM)).thenReturn(0);
        when(reservationRepository.save(any(StockReservationEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        StockReservationEntity r = service.reserve(FACILITY, STORE, ITEM, null, 40, "TAB",
                "DISPENSE", "rx-1", "prescription", null);

        assertThat(r.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(r.getQty()).isEqualTo(40);
        assertThat(r.getTenantId()).isEqualTo(TENANT);
        assertThat(r.getReservedBy()).isEqualTo("nurse-7");
        assertThat(r.getRefType()).isEqualTo("DISPENSE");
    }

    @Test
    @DisplayName("reserve emits typed created event plus legacy status_changed shape (OF-B11)")
    void reserve_emitsCreatedEvents() {
        stubOnHand(100);
        when(reservationRepository.sumActiveReserved(TENANT, FACILITY, STORE, ITEM)).thenReturn(0);
        when(reservationRepository.save(any(StockReservationEntity.class))).thenAnswer(inv -> {
            StockReservationEntity e = inv.getArgument(0);
            if (e.getReservationId() == null) {
                e.setReservationId(UUID.randomUUID());
            }
            return e;
        });

        service.reserve(FACILITY, STORE, ITEM, null, 5, "TAB",
                "MARKETPLACE_SELECTION", "sel-1", null, OffsetDateTime.now().plusHours(4));

        assertThat(capturedEventTypes())
                .containsExactly("RESERVATION_CREATED", "RESERVATION_STATUS_CHANGED");
    }

    @Test
    @DisplayName("reserve fails when available is insufficient")
    void reserve_insufficient() {
        stubOnHand(10);
        when(reservationRepository.sumActiveReserved(TENANT, FACILITY, STORE, ITEM)).thenReturn(5);

        assertThatThrownBy(() -> service.reserve(FACILITY, STORE, ITEM, null, 20, "TAB",
                "DISPENSE", "rx-2", null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Insufficient");
        verify(reservationRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    @DisplayName("reserve rejects non-positive quantity")
    void reserve_nonPositive() {
        assertThatThrownBy(() -> service.reserve(FACILITY, STORE, ITEM, null, 0, "TAB", null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    @DisplayName("release transitions an ACTIVE reservation to RELEASED and emits events")
    void release_success() {
        UUID id = UUID.randomUUID();
        StockReservationEntity r = new StockReservationEntity();
        r.setReservationId(id);
        r.setTenantId(TENANT);
        r.setStatus(ReservationStatus.ACTIVE);
        when(reservationRepository.findById(id)).thenReturn(Optional.of(r));
        when(reservationRepository.save(any(StockReservationEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        StockReservationEntity result = service.release(id);
        assertThat(result.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(capturedEventTypes())
                .containsExactly("RESERVATION_RELEASED", "RESERVATION_STATUS_CHANGED");
    }

    @Test
    @DisplayName("consume emits consumed + legacy events")
    void consume_emitsEvents() {
        UUID id = UUID.randomUUID();
        StockReservationEntity r = new StockReservationEntity();
        r.setReservationId(id);
        r.setTenantId(TENANT);
        r.setStatus(ReservationStatus.ACTIVE);
        when(reservationRepository.findById(id)).thenReturn(Optional.of(r));
        when(reservationRepository.save(any(StockReservationEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.consume(id);
        assertThat(capturedEventTypes())
                .containsExactly("RESERVATION_CONSUMED", "RESERVATION_STATUS_CHANGED");
    }

    @Test
    @DisplayName("consume rejects a non-ACTIVE reservation")
    void consume_notActive() {
        UUID id = UUID.randomUUID();
        StockReservationEntity r = new StockReservationEntity();
        r.setReservationId(id);
        r.setTenantId(TENANT);
        r.setStatus(ReservationStatus.RELEASED);
        when(reservationRepository.findById(id)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.consume(id))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not ACTIVE");
    }

    @Test
    @DisplayName("legacy status_changed payload carries {reservationRef,status} for the msika-flow projection")
    void legacyEventPayloadShape() throws Exception {
        UUID id = UUID.randomUUID();
        StockReservationEntity r = new StockReservationEntity();
        r.setReservationId(id);
        r.setTenantId(TENANT);
        r.setStatus(ReservationStatus.ACTIVE);
        when(reservationRepository.findById(id)).thenReturn(Optional.of(r));
        when(reservationRepository.save(any(StockReservationEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.release(id);

        ArgumentCaptor<EventOutboxEntity> captor = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository, times(2)).save(captor.capture());
        EventOutboxEntity legacy = captor.getAllValues().stream()
                .filter(e -> "RESERVATION_STATUS_CHANGED".equals(e.getEventType()))
                .findFirst().orElseThrow();
        var payload = new ObjectMapper().readTree(legacy.getPayload());
        assertThat(payload.path("reservationRef").asText()).isEqualTo(id.toString());
        assertThat(payload.path("status").asText()).isEqualTo("RELEASED");
    }

    // ── expiry sweep (OF-B11 §8.9.3 — no zombie holds) ───────────────────

    @Test
    @DisplayName("expireLapsed flips lapsed ACTIVE holds to EXPIRED and emits expiry events")
    void expireLapsed_flipsAndEvents() {
        StockReservationEntity lapsed = new StockReservationEntity();
        lapsed.setReservationId(UUID.randomUUID());
        lapsed.setTenantId(TENANT);
        lapsed.setStatus(ReservationStatus.ACTIVE);
        lapsed.setItemCode(ITEM);
        lapsed.setQty(3);
        lapsed.setExpiresAt(OffsetDateTime.now().minusMinutes(10));
        when(reservationRepository.findByStatusAndExpiresAtIsNotNullAndExpiresAtBefore(
                eq(ReservationStatus.ACTIVE), any())).thenReturn(List.of(lapsed));
        when(reservationRepository.save(any(StockReservationEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        int expired = service.expireLapsed(OffsetDateTime.now());

        assertThat(expired).isEqualTo(1);
        assertThat(lapsed.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(capturedEventTypes())
                .containsExactly("RESERVATION_EXPIRED", "RESERVATION_STATUS_CHANGED");
    }

    @Test
    @DisplayName("expireLapsed runs without a trust context (system sweep)")
    void expireLapsed_noTrustContextRequired() {
        TrustContextHolder.clear();
        when(reservationRepository.findByStatusAndExpiresAtIsNotNullAndExpiresAtBefore(
                eq(ReservationStatus.ACTIVE), any())).thenReturn(List.of());

        int expired = service.expireLapsed(OffsetDateTime.now());
        assertThat(expired).isZero();
    }

}
