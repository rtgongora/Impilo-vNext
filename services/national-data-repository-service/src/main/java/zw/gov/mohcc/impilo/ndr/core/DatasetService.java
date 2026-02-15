package zw.gov.mohcc.impilo.ndr.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.ndr.domain.DatasetStatus;
import zw.gov.mohcc.impilo.ndr.domain.VersionStatus;
import zw.gov.mohcc.impilo.ndr.persistence.entity.DatasetEntity;
import zw.gov.mohcc.impilo.ndr.persistence.entity.DatasetVersionEntity;
import zw.gov.mohcc.impilo.ndr.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.ndr.persistence.repository.DatasetRepository;
import zw.gov.mohcc.impilo.ndr.persistence.repository.DatasetVersionRepository;
import zw.gov.mohcc.impilo.ndr.persistence.repository.EventOutboxRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DatasetService {

    private static final Logger log = LoggerFactory.getLogger(DatasetService.class);

    private final DatasetRepository datasetRepository;
    private final DatasetVersionRepository versionRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public DatasetService(DatasetRepository datasetRepository,
                          DatasetVersionRepository versionRepository,
                          EventOutboxRepository outboxRepository,
                          ObjectMapper objectMapper) {
        this.datasetRepository = datasetRepository;
        this.versionRepository = versionRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DatasetEntity createDataset(UUID tenantId, String datasetKey, String name,
                                       String description, String owner) {
        datasetRepository.findByTenantIdAndDatasetKey(tenantId, datasetKey)
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Dataset key already exists: " + datasetKey);
                });

        DatasetEntity dataset = new DatasetEntity();
        dataset.setDatasetId(generateUlid());
        dataset.setTenantId(tenantId);
        dataset.setDatasetKey(datasetKey);
        dataset.setName(name);
        dataset.setDescription(description);
        dataset.setOwner(owner);
        dataset.setStatus(DatasetStatus.ACTIVE);
        dataset = datasetRepository.save(dataset);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("datasetId", dataset.getDatasetId());
        payload.put("datasetKey", dataset.getDatasetKey());
        payload.put("name", dataset.getName());
        writeOutbox("DATASET", dataset.getDatasetId(),
                "DATASET_CREATED", tenantId, toJson(payload));

        log.info("Dataset created: key={}, id={}", datasetKey, dataset.getDatasetId());
        return dataset;
    }

    @Transactional
    public DatasetVersionEntity createVersion(UUID tenantId, String datasetKey,
                                               String schemaJson, long rowCount) {
        DatasetEntity dataset = datasetRepository.findByTenantIdAndDatasetKey(tenantId, datasetKey)
                .orElseThrow(() -> new IllegalArgumentException("Dataset not found: " + datasetKey));

        int nextVersion = versionRepository.findTopByDatasetIdOrderByVersionNumberDesc(dataset.getDatasetId())
                .map(v -> v.getVersionNumber() + 1)
                .orElse(1);

        DatasetVersionEntity version = new DatasetVersionEntity();
        version.setVersionId(generateUlid());
        version.setTenantId(tenantId);
        version.setDatasetId(dataset.getDatasetId());
        version.setVersionNumber(nextVersion);
        version.setSchemaJson(schemaJson != null ? schemaJson : "{}");
        version.setRowCount(rowCount);
        version.setStatus(VersionStatus.DRAFT);
        version = versionRepository.save(version);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("datasetId", dataset.getDatasetId());
        payload.put("datasetKey", datasetKey);
        payload.put("versionId", version.getVersionId());
        payload.put("versionNumber", nextVersion);
        writeOutbox("DATASET_VERSION", version.getVersionId(),
                "DATASET_VERSIONED", tenantId, toJson(payload));

        log.info("Dataset version created: key={}, version={}", datasetKey, nextVersion);
        return version;
    }

    @Transactional(readOnly = true)
    public List<DatasetEntity> listDatasets(UUID tenantId) {
        return datasetRepository.findByTenantId(tenantId);
    }

    private void writeOutbox(String aggregateType, String aggregateId,
                             String eventType, UUID tenantId, String payloadJson) {
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType(aggregateType);
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setPayload(payloadJson);
        outbox.setTenantId(tenantId);
        outboxRepository.save(outbox);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise outbox payload: {}", e.getMessage());
            return "{}";
        }
    }

    private String generateUlid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 26);
    }
}
