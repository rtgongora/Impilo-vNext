package zw.gov.mohcc.impilo.ndr.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.ndr.persistence.entity.MaterializedViewEntity;
import zw.gov.mohcc.impilo.ndr.persistence.repository.MaterializedViewRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryServiceTest {

    @Mock
    private MaterializedViewRepository viewRepository;

    private QueryService queryService;

    private final UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        queryService = new QueryService(viewRepository);
    }

    @Test
    @DisplayName("query returns all rows when no filters applied")
    void queryNoFilters() {
        MaterializedViewEntity v1 = createView("row1", "{\"count\": 10}");
        MaterializedViewEntity v2 = createView("row2", "{\"count\": 20}");
        when(viewRepository.findByTenantIdAndDatasetKey(tenantId, "test_ds"))
                .thenReturn(List.of(v1, v2));

        List<String> result = queryService.query(tenantId, "test_ds", null);
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("query filters rows by matching key-value in JSON")
    void queryWithFilters() {
        MaterializedViewEntity v1 = createView("row1",
                "{\"province\": \"Harare\", \"count\": 10}");
        MaterializedViewEntity v2 = createView("row2",
                "{\"province\": \"Bulawayo\", \"count\": 20}");
        when(viewRepository.findByTenantIdAndDatasetKey(tenantId, "test_ds"))
                .thenReturn(List.of(v1, v2));

        List<String> result = queryService.query(tenantId, "test_ds",
                Map.of("province", "Harare"));
        assertThat(result).hasSize(1);
        assertThat(result.getFirst()).contains("Harare");
    }

    @Test
    @DisplayName("query returns empty list for non-matching dataset key")
    void queryEmptyDataset() {
        when(viewRepository.findByTenantIdAndDatasetKey(tenantId, "nonexistent"))
                .thenReturn(List.of());

        List<String> result = queryService.query(tenantId, "nonexistent", null);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("query with empty filters returns all rows")
    void queryEmptyFilters() {
        MaterializedViewEntity v1 = createView("row1", "{\"a\": 1}");
        when(viewRepository.findByTenantIdAndDatasetKey(tenantId, "ds"))
                .thenReturn(List.of(v1));

        List<String> result = queryService.query(tenantId, "ds", Map.of());
        assertThat(result).hasSize(1);
    }

    private MaterializedViewEntity createView(String viewId, String rowData) {
        MaterializedViewEntity v = new MaterializedViewEntity();
        v.setViewId(viewId);
        v.setTenantId(tenantId);
        v.setDatasetKey("test_ds");
        v.setViewName("Test View");
        v.setRowData(rowData);
        return v;
    }
}
