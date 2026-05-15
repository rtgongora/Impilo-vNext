package zw.gov.mohcc.impilo.ndr.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.ndr.persistence.entity.MaterializedViewEntity;
import zw.gov.mohcc.impilo.ndr.persistence.repository.MaterializedViewRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class QueryService {

    private static final Logger log = LoggerFactory.getLogger(QueryService.class);

    private final MaterializedViewRepository viewRepository;
    private final ObjectMapper objectMapper;

    public QueryService(MaterializedViewRepository viewRepository, ObjectMapper objectMapper) {
        this.viewRepository = viewRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<String> query(UUID tenantId, String datasetKey, Map<String, String> filters) {
        List<MaterializedViewEntity> views =
                viewRepository.findByTenantIdAndDatasetKey(tenantId, datasetKey);

        if (filters != null && !filters.isEmpty()) {
            views = views.stream()
                    .filter(v -> matchesFilters(v.getRowData(), filters))
                    .collect(Collectors.toList());
        }

        log.info("Query executed: datasetKey={}, results={}", datasetKey, views.size());
        return views.stream()
                .map(MaterializedViewEntity::getRowData)
                .collect(Collectors.toList());
    }

    private boolean matchesFilters(String rowDataJson, Map<String, String> filters) {
        JsonNode row;
        try {
            row = objectMapper.readTree(rowDataJson);
        } catch (Exception e) {
            log.warn("Skipping malformed materialized row during filter evaluation: {}", e.getMessage());
            return false;
        }

        for (Map.Entry<String, String> filter : filters.entrySet()) {
            JsonNode value = row.get(filter.getKey());
            if (value == null || value.isNull()) {
                return false;
            }
            if (!filter.getValue().equalsIgnoreCase(value.asText())) {
                return false;
            }
        }
        return true;
    }
}
