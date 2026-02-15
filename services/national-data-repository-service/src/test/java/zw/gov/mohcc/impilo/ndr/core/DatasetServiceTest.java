package zw.gov.mohcc.impilo.ndr.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.ndr.domain.DatasetStatus;
import zw.gov.mohcc.impilo.ndr.persistence.entity.DatasetEntity;
import zw.gov.mohcc.impilo.ndr.persistence.entity.DatasetVersionEntity;
import zw.gov.mohcc.impilo.ndr.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.ndr.persistence.repository.DatasetRepository;
import zw.gov.mohcc.impilo.ndr.persistence.repository.DatasetVersionRepository;
import zw.gov.mohcc.impilo.ndr.persistence.repository.EventOutboxRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DatasetServiceTest {

    @Mock
    private DatasetRepository datasetRepository;

    @Mock
    private DatasetVersionRepository versionRepository;

    @Mock
    private EventOutboxRepository outboxRepository;

    private DatasetService datasetService;

    private final UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        datasetService = new DatasetService(
                datasetRepository, versionRepository, outboxRepository, new ObjectMapper());
    }

    @Test
    @DisplayName("createDataset persists entity and writes outbox event")
    void createDatasetSuccess() {
        when(datasetRepository.findByTenantIdAndDatasetKey(tenantId, "facility_summary"))
                .thenReturn(Optional.empty());
        when(datasetRepository.save(any(DatasetEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(outboxRepository.save(any(EventOutboxEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        DatasetEntity result = datasetService.createDataset(
                tenantId, "facility_summary", "Facility Summary", "Summary dataset", "admin");

        assertThat(result.getDatasetKey()).isEqualTo("facility_summary");
        assertThat(result.getName()).isEqualTo("Facility Summary");
        assertThat(result.getStatus()).isEqualTo(DatasetStatus.ACTIVE);
        assertThat(result.getTenantId()).isEqualTo(tenantId);

        ArgumentCaptor<EventOutboxEntity> outboxCaptor = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("DATASET_CREATED");
        assertThat(outboxCaptor.getValue().getAggregateType()).isEqualTo("DATASET");
    }

    @Test
    @DisplayName("createDataset rejects duplicate dataset key")
    void createDatasetDuplicateKey() {
        DatasetEntity existing = new DatasetEntity();
        existing.setDatasetKey("facility_summary");
        when(datasetRepository.findByTenantIdAndDatasetKey(tenantId, "facility_summary"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() ->
                datasetService.createDataset(tenantId, "facility_summary", "Name", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");

        verify(datasetRepository, never()).save(any());
    }

    @Test
    @DisplayName("createVersion assigns sequential version number")
    void createVersionSequential() {
        DatasetEntity dataset = new DatasetEntity();
        dataset.setDatasetId("ds-001");
        dataset.setDatasetKey("facility_summary");
        when(datasetRepository.findByTenantIdAndDatasetKey(tenantId, "facility_summary"))
                .thenReturn(Optional.of(dataset));

        DatasetVersionEntity prevVersion = new DatasetVersionEntity();
        prevVersion.setVersionNumber(3);
        when(versionRepository.findTopByDatasetIdOrderByVersionNumberDesc("ds-001"))
                .thenReturn(Optional.of(prevVersion));

        when(versionRepository.save(any(DatasetVersionEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(outboxRepository.save(any(EventOutboxEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        DatasetVersionEntity version = datasetService.createVersion(
                tenantId, "facility_summary", "{\"columns\":[]}", 100);

        assertThat(version.getVersionNumber()).isEqualTo(4);
        assertThat(version.getRowCount()).isEqualTo(100);
    }

    @Test
    @DisplayName("createVersion starts at version 1 for new dataset")
    void createVersionFirstVersion() {
        DatasetEntity dataset = new DatasetEntity();
        dataset.setDatasetId("ds-002");
        when(datasetRepository.findByTenantIdAndDatasetKey(tenantId, "new_dataset"))
                .thenReturn(Optional.of(dataset));
        when(versionRepository.findTopByDatasetIdOrderByVersionNumberDesc("ds-002"))
                .thenReturn(Optional.empty());
        when(versionRepository.save(any(DatasetVersionEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(outboxRepository.save(any(EventOutboxEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        DatasetVersionEntity version = datasetService.createVersion(
                tenantId, "new_dataset", null, 0);

        assertThat(version.getVersionNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("createVersion writes DATASET_VERSIONED outbox event")
    void createVersionOutboxEvent() {
        DatasetEntity dataset = new DatasetEntity();
        dataset.setDatasetId("ds-003");
        when(datasetRepository.findByTenantIdAndDatasetKey(tenantId, "test_ds"))
                .thenReturn(Optional.of(dataset));
        when(versionRepository.findTopByDatasetIdOrderByVersionNumberDesc("ds-003"))
                .thenReturn(Optional.empty());
        when(versionRepository.save(any(DatasetVersionEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(outboxRepository.save(any(EventOutboxEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        datasetService.createVersion(tenantId, "test_ds", "{}", 50);

        ArgumentCaptor<EventOutboxEntity> captor = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("DATASET_VERSIONED");
        assertThat(captor.getValue().getAggregateType()).isEqualTo("DATASET_VERSION");
    }

    @Test
    @DisplayName("createVersion fails for non-existent dataset")
    void createVersionNotFound() {
        when(datasetRepository.findByTenantIdAndDatasetKey(tenantId, "nonexistent"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                datasetService.createVersion(tenantId, "nonexistent", "{}", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("listDatasets returns all datasets for tenant")
    void listDatasetsReturnsAll() {
        DatasetEntity d1 = new DatasetEntity();
        d1.setDatasetKey("ds1");
        DatasetEntity d2 = new DatasetEntity();
        d2.setDatasetKey("ds2");
        when(datasetRepository.findByTenantId(tenantId)).thenReturn(List.of(d1, d2));

        List<DatasetEntity> result = datasetService.listDatasets(tenantId);
        assertThat(result).hasSize(2);
    }
}
