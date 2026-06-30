package zw.gov.mohcc.impilo.inventory.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.inventory.domain.BatchLotStatus;
import zw.gov.mohcc.impilo.inventory.persistence.entity.BatchLotEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.ItemEntity;
import zw.gov.mohcc.impilo.inventory.persistence.repository.ItemRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockAvailabilityServiceTest {

    @Mock private ReservationService reservationService;
    @Mock private BatchLotService batchLotService;
    @Mock private ItemRepository itemRepository;
    @InjectMocks private StockAvailabilityServiceImpl service;

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID FACILITY = UUID.randomUUID();
    private static final UUID STORE = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TrustContextHolder.set(new TrustContext(
                TENANT, "doc-1", "PROVIDER", "TREATMENT", null,
                UUID.randomUUID(), FACILITY, null, null, null));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private BatchLotEntity batch(String number, BatchLotStatus status, LocalDate expiry) {
        BatchLotEntity b = new BatchLotEntity();
        b.setBatchLotId(UUID.randomUUID());
        b.setBatchNumber(number);
        b.setStatus(status);
        b.setExpiryDate(expiry);
        return b;
    }

    @Test
    @DisplayName("availability reports stock, usable batches (FEFO), and nearest expiry")
    void availability_inStock() {
        when(reservationService.availableQuantity(FACILITY, STORE, "PARA-500")).thenReturn(70);
        when(reservationService.reservedQuantity(FACILITY, STORE, "PARA-500")).thenReturn(30);
        when(batchLotService.listBatchesForItem("PARA-500")).thenReturn(List.of(
                batch("B-EARLY", BatchLotStatus.ACTIVE, LocalDate.now().plusDays(20)),
                batch("B-LATE", BatchLotStatus.ACTIVE, LocalDate.now().plusDays(200)),
                batch("B-EXPIRED", BatchLotStatus.ACTIVE, LocalDate.now().minusDays(1)),
                batch("B-QUAR", BatchLotStatus.QUARANTINED, LocalDate.now().plusDays(50))));

        StockAvailabilityService.AvailabilityResult r = service.availability(FACILITY, STORE, "PARA-500");

        assertThat(r.available()).isEqualTo(70);
        assertThat(r.onHand()).isEqualTo(100);
        assertThat(r.reserved()).isEqualTo(30);
        assertThat(r.stockOut()).isFalse();
        assertThat(r.usableBatches()).extracting(StockAvailabilityService.UsableBatch::batchNumber)
                .containsExactly("B-EARLY", "B-LATE");
        assertThat(r.nearestExpiry()).isEqualTo(LocalDate.now().plusDays(20));
        assertThat(r.substitutes()).isEmpty();
    }

    @Test
    @DisplayName("availability surfaces approved substitutes when stocked-out")
    void availability_stockOutWithSubstitutes() {
        when(reservationService.availableQuantity(FACILITY, STORE, "AMOX-250")).thenReturn(0);
        when(reservationService.reservedQuantity(FACILITY, STORE, "AMOX-250")).thenReturn(0);
        when(batchLotService.listBatchesForItem("AMOX-250")).thenReturn(List.of());

        ItemEntity requested = new ItemEntity();
        requested.setItemCode("AMOX-250");
        requested.setSubstitutionGroup("PENICILLINS");
        ItemEntity sub = new ItemEntity();
        sub.setItemCode("AMOX-500");
        sub.setName("Amoxicillin 500mg");
        sub.setSubstitutionGroup("PENICILLINS");
        sub.setActive(true);

        when(itemRepository.findByTenantIdAndItemCode(TENANT, "AMOX-250")).thenReturn(Optional.of(requested));
        when(itemRepository.findByTenantIdAndSubstitutionGroup(TENANT, "PENICILLINS"))
                .thenReturn(List.of(requested, sub));
        when(reservationService.availableQuantity(FACILITY, STORE, "AMOX-500")).thenReturn(45);

        StockAvailabilityService.AvailabilityResult r = service.availability(FACILITY, STORE, "AMOX-250");

        assertThat(r.stockOut()).isTrue();
        assertThat(r.substitutes()).hasSize(1);
        assertThat(r.substitutes().get(0).itemCode()).isEqualTo("AMOX-500");
        assertThat(r.substitutes().get(0).available()).isEqualTo(45);
    }

    @Test
    @DisplayName("no substitutes when item has no substitution group")
    void availability_stockOutNoGroup() {
        when(reservationService.availableQuantity(FACILITY, STORE, "X")).thenReturn(0);
        when(reservationService.reservedQuantity(FACILITY, STORE, "X")).thenReturn(0);
        when(batchLotService.listBatchesForItem("X")).thenReturn(List.of());
        ItemEntity item = new ItemEntity();
        item.setItemCode("X");
        when(itemRepository.findByTenantIdAndItemCode(TENANT, "X")).thenReturn(Optional.of(item));

        StockAvailabilityService.AvailabilityResult r = service.availability(FACILITY, STORE, "X");
        assertThat(r.substitutes()).isEmpty();
    }
}
