package zw.gov.mohcc.impilo.inventory.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.inventory.domain.BatchLotStatus;
import zw.gov.mohcc.impilo.inventory.domain.RecallSeverity;
import zw.gov.mohcc.impilo.inventory.domain.RecallSource;
import zw.gov.mohcc.impilo.inventory.domain.RecallStatus;
import zw.gov.mohcc.impilo.inventory.persistence.entity.BatchLotEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.RecallEventEntity;
import zw.gov.mohcc.impilo.inventory.persistence.repository.RecallEventRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecallServiceTest {

    @Mock private RecallEventRepository recallRepository;
    @Mock private BatchLotService batchLotService;
    @InjectMocks private RecallServiceImpl service;

    private static final UUID TENANT = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TrustContextHolder.set(new TrustContext(
                TENANT, "pharmacist-3", "PROVIDER", "QUALITY", null,
                UUID.randomUUID(), null, null, null, null));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private BatchLotEntity batch(String number, BatchLotStatus status) {
        BatchLotEntity b = new BatchLotEntity();
        b.setBatchLotId(UUID.randomUUID());
        b.setItemCode("AMOX-250");
        b.setBatchNumber(number);
        b.setStatus(status);
        return b;
    }

    @Test
    @DisplayName("createRecall freezes all batches of the item when no batch specified")
    void createRecall_freezesAll() {
        BatchLotEntity b1 = batch("B-1", BatchLotStatus.ACTIVE);
        BatchLotEntity b2 = batch("B-2", BatchLotStatus.QUARANTINED);
        when(batchLotService.listBatchesForItem("AMOX-250")).thenReturn(List.of(b1, b2));
        when(recallRepository.save(any(RecallEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        RecallEventEntity recall = service.createRecall("MCAZ-2026-01", "AMOX-250", null,
                RecallSource.REGULATOR, RecallSeverity.HIGH, "contamination");

        verify(batchLotService).setStatus(eq(b1.getBatchLotId()), eq(BatchLotStatus.RECALLED), any());
        verify(batchLotService).setStatus(eq(b2.getBatchLotId()), eq(BatchLotStatus.RECALLED), any());
        assertThat(recall.getAffectedBatches()).isEqualTo(2);
        assertThat(recall.getStatus()).isEqualTo(RecallStatus.OPEN);
        assertThat(recall.getSeverity()).isEqualTo(RecallSeverity.HIGH);
        assertThat(recall.getInitiatedBy()).isEqualTo("pharmacist-3");
    }

    @Test
    @DisplayName("createRecall narrows to a single batch number")
    void createRecall_singleBatch() {
        BatchLotEntity b1 = batch("B-1", BatchLotStatus.ACTIVE);
        BatchLotEntity b2 = batch("B-2", BatchLotStatus.ACTIVE);
        when(batchLotService.listBatchesForItem("AMOX-250")).thenReturn(List.of(b1, b2));
        when(recallRepository.save(any(RecallEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        RecallEventEntity recall = service.createRecall(null, "AMOX-250", "B-2",
                RecallSource.MANUFACTURER, RecallSeverity.CRITICAL, "label error");

        verify(batchLotService).setStatus(eq(b2.getBatchLotId()), eq(BatchLotStatus.RECALLED), any());
        verify(batchLotService, never()).setStatus(eq(b1.getBatchLotId()), any(), any());
        assertThat(recall.getAffectedBatches()).isEqualTo(1);
        assertThat(recall.getBatchNumber()).isEqualTo("B-2");
    }

    @Test
    @DisplayName("createRecall does not re-transition already-RECALLED batches")
    void createRecall_skipsAlreadyRecalled() {
        BatchLotEntity b1 = batch("B-1", BatchLotStatus.RECALLED);
        when(batchLotService.listBatchesForItem("AMOX-250")).thenReturn(List.of(b1));
        when(recallRepository.save(any(RecallEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        RecallEventEntity recall = service.createRecall("R", "AMOX-250", null, null, null, null);

        verify(batchLotService, never()).setStatus(any(), any(), any());
        assertThat(recall.getAffectedBatches()).isEqualTo(1);
        assertThat(recall.getSource()).isEqualTo(RecallSource.INTERNAL);
        assertThat(recall.getSeverity()).isEqualTo(RecallSeverity.MEDIUM);
    }

    @Test
    @DisplayName("createRecall requires an item code")
    void createRecall_requiresItem() {
        assertThatThrownBy(() -> service.createRecall("R", "  ", null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("itemCode is required");
    }

    @Test
    @DisplayName("closeRecall transitions OPEN to CLOSED with note")
    void closeRecall_success() {
        UUID id = UUID.randomUUID();
        RecallEventEntity recall = new RecallEventEntity();
        recall.setRecallId(id);
        recall.setTenantId(TENANT);
        recall.setStatus(RecallStatus.OPEN);
        when(recallRepository.findById(id)).thenReturn(Optional.of(recall));
        when(recallRepository.save(any(RecallEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        RecallEventEntity result = service.closeRecall(id, "all stock returned");

        assertThat(result.getStatus()).isEqualTo(RecallStatus.CLOSED);
        assertThat(result.getClosureNote()).isEqualTo("all stock returned");
        assertThat(result.getClosedAt()).isNotNull();
    }

    @Test
    @DisplayName("closeRecall rejects an already-closed recall")
    void closeRecall_alreadyClosed() {
        UUID id = UUID.randomUUID();
        RecallEventEntity recall = new RecallEventEntity();
        recall.setRecallId(id);
        recall.setTenantId(TENANT);
        recall.setStatus(RecallStatus.CLOSED);
        when(recallRepository.findById(id)).thenReturn(Optional.of(recall));

        assertThatThrownBy(() -> service.closeRecall(id, "x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already closed");
    }
}
