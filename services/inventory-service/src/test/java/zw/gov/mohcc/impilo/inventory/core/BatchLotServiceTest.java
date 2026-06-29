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
import zw.gov.mohcc.impilo.inventory.persistence.repository.BatchLotRepository;
import zw.gov.mohcc.impilo.inventory.persistence.repository.ItemRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BatchLotServiceTest {

    @Mock private BatchLotRepository batchLotRepository;
    @Mock private ItemRepository itemRepository;
    @InjectMocks private BatchLotServiceImpl service;

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID ITEM_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TrustContextHolder.set(new TrustContext(
                TENANT, "actor-1", "STAFF", "LOGISTICS", null,
                UUID.randomUUID(), null, null, null, null));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private ItemEntity item() {
        ItemEntity i = new ItemEntity();
        i.setItemId(ITEM_ID);
        i.setTenantId(TENANT);
        i.setItemCode("BCG-VAC");
        return i;
    }

    @Test
    @DisplayName("registerBatch creates a new ACTIVE batch when none exists")
    void registerBatch_createsNew() {
        when(itemRepository.findByTenantIdAndItemCode(TENANT, "BCG-VAC")).thenReturn(Optional.of(item()));
        when(batchLotRepository.findByTenantIdAndItemIdAndBatchNumber(TENANT, ITEM_ID, "B-001"))
                .thenReturn(Optional.empty());
        when(batchLotRepository.save(any(BatchLotEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        BatchLotEntity b = service.registerBatch("BCG-VAC", "B-001", "L-9",
                LocalDate.of(2027, 1, 1), null, "MfgCo", "SupplierCo", null, "received");

        assertThat(b.getTenantId()).isEqualTo(TENANT);
        assertThat(b.getItemId()).isEqualTo(ITEM_ID);
        assertThat(b.getBatchNumber()).isEqualTo("B-001");
        assertThat(b.getStatus()).isEqualTo(BatchLotStatus.ACTIVE);
        assertThat(b.getExpiryDate()).isEqualTo(LocalDate.of(2027, 1, 1));
    }

    @Test
    @DisplayName("registerBatch upserts an existing batch")
    void registerBatch_upserts() {
        BatchLotEntity existing = new BatchLotEntity();
        existing.setTenantId(TENANT);
        existing.setItemId(ITEM_ID);
        existing.setItemCode("BCG-VAC");
        existing.setBatchNumber("B-001");
        existing.setStatus(BatchLotStatus.ACTIVE);
        when(itemRepository.findByTenantIdAndItemCode(TENANT, "BCG-VAC")).thenReturn(Optional.of(item()));
        when(batchLotRepository.findByTenantIdAndItemIdAndBatchNumber(TENANT, ITEM_ID, "B-001"))
                .thenReturn(Optional.of(existing));
        when(batchLotRepository.save(any(BatchLotEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        BatchLotEntity b = service.registerBatch("BCG-VAC", "B-001", null,
                LocalDate.of(2028, 6, 30), null, null, null, null, null);

        assertThat(b).isSameAs(existing);
        assertThat(b.getExpiryDate()).isEqualTo(LocalDate.of(2028, 6, 30));
    }

    @Test
    @DisplayName("registerBatch rejects unknown commodity")
    void registerBatch_unknownItem() {
        when(itemRepository.findByTenantIdAndItemCode(TENANT, "NOPE")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.registerBatch("NOPE", "B-1", null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("setStatus quarantines a batch and records the reason")
    void setStatus_quarantine() {
        UUID id = UUID.randomUUID();
        BatchLotEntity batch = new BatchLotEntity();
        batch.setBatchLotId(id);
        batch.setTenantId(TENANT);
        batch.setStatus(BatchLotStatus.ACTIVE);
        when(batchLotRepository.findById(id)).thenReturn(Optional.of(batch));
        when(batchLotRepository.save(any(BatchLotEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        BatchLotEntity result = service.setStatus(id, BatchLotStatus.QUARANTINED, "cold-chain excursion");

        assertThat(result.getStatus()).isEqualTo(BatchLotStatus.QUARANTINED);
        assertThat(result.getNotes()).isEqualTo("cold-chain excursion");
    }

    @Test
    @DisplayName("setStatus rejects a batch from another tenant")
    void setStatus_wrongTenant() {
        UUID id = UUID.randomUUID();
        BatchLotEntity batch = new BatchLotEntity();
        batch.setBatchLotId(id);
        batch.setTenantId(UUID.randomUUID()); // different tenant
        when(batchLotRepository.findById(id)).thenReturn(Optional.of(batch));

        assertThatThrownBy(() -> service.setStatus(id, BatchLotStatus.RECALLED, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenant");
    }

    @Test
    @DisplayName("isUsable is true for ACTIVE non-expired, false otherwise")
    void isUsable() {
        UUID id = UUID.randomUUID();
        BatchLotEntity batch = new BatchLotEntity();
        batch.setBatchLotId(id);
        batch.setTenantId(TENANT);
        batch.setStatus(BatchLotStatus.ACTIVE);
        batch.setExpiryDate(LocalDate.now().plusDays(10));
        when(batchLotRepository.findById(id)).thenReturn(Optional.of(batch));
        assertThat(service.isUsable(id)).isTrue();

        batch.setExpiryDate(LocalDate.now().minusDays(1));
        assertThat(service.isUsable(id)).isFalse();

        batch.setExpiryDate(LocalDate.now().plusDays(10));
        batch.setStatus(BatchLotStatus.QUARANTINED);
        assertThat(service.isUsable(id)).isFalse();
    }
}
