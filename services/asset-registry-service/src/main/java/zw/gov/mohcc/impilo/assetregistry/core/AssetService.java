package zw.gov.mohcc.impilo.assetregistry.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.assetregistry.api.dto.UpsertAssetRequest;
import zw.gov.mohcc.impilo.assetregistry.domain.AssetEntity;
import zw.gov.mohcc.impilo.assetregistry.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.assetregistry.repository.AssetRepository;
import zw.gov.mohcc.impilo.assetregistry.repository.OutboxEventRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class AssetService {

    private static final Logger log = LoggerFactory.getLogger(AssetService.class);
    private static final String AGGREGATE_TYPE = "Asset";
    private static final String PRODUCER = "asset-registry-service";
    private static final String SUBJECT_TYPE = "Asset";

    private final AssetRepository assetRepository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public AssetService(AssetRepository assetRepository,
                        OutboxEventRepository outboxRepository,
                        ObjectMapper objectMapper) {
        this.assetRepository = assetRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public UpsertResult upsertAsset(UUID assetId, UUID tenantId, String podId,
                                     String correlationId, String idempotencyKey,
                                     UpsertAssetRequest request) {
        Optional<AssetEntity> existing = assetRepository.findById(assetId);

        if (existing.isPresent()) {
            return updateAsset(existing.get(), tenantId, podId, correlationId, idempotencyKey, request);
        } else {
            return createAsset(assetId, tenantId, podId, correlationId, idempotencyKey, request);
        }
    }

    private UpsertResult createAsset(UUID assetId, UUID tenantId, String podId,
                                      String correlationId, String idempotencyKey,
                                      UpsertAssetRequest request) {
        AssetEntity asset = new AssetEntity(assetId, tenantId, request.facilityRef(), request.type());
        asset.setSerialNo(request.serialNo());
        asset.setMetadataJson(toJson(request.metadata()));
        if (request.status() != null && !request.status().isBlank()) {
            asset.setStatus(request.status());
        }

        assetRepository.save(asset);

        Map<String, Object> afterState = buildAssetState(asset);
        Map<String, Object> payload = buildDeltaPayload("CREATE", null, afterState,
                List.of("facility_ref", "type", "serial_no", "status", "metadata_json"));

        appendOutboxEvent(
                "impilo.asset.asset.created.v1",
                assetId.toString(), tenantId, podId, correlationId, idempotencyKey,
                payload, assetId.toString());

        log.info("Created asset [assetId={}, type={}]", assetId, request.type());
        return new UpsertResult(asset, true);
    }

    private UpsertResult updateAsset(AssetEntity asset, UUID tenantId, String podId,
                                      String correlationId, String idempotencyKey,
                                      UpsertAssetRequest request) {
        Map<String, Object> beforeState = buildAssetState(asset);
        List<String> changedFields = new java.util.ArrayList<>();

        if (!asset.getFacilityRef().equals(request.facilityRef())) {
            asset.setFacilityRef(request.facilityRef());
            changedFields.add("facility_ref");
        }
        if (!asset.getType().equals(request.type())) {
            asset.setType(request.type());
            changedFields.add("type");
        }
        if (!Objects.equals(asset.getSerialNo(), request.serialNo())) {
            asset.setSerialNo(request.serialNo());
            changedFields.add("serial_no");
        }

        String newMetadataJson = toJson(request.metadata());
        if (!Objects.equals(asset.getMetadataJson(), newMetadataJson)) {
            asset.setMetadataJson(newMetadataJson);
            changedFields.add("metadata_json");
        }

        if (request.status() != null && !request.status().isBlank()
                && !asset.getStatus().equals(request.status())) {
            asset.setStatus(request.status());
            changedFields.add("status");
        }

        asset.setVersion(asset.getVersion() + 1);
        asset.setUpdatedAt(OffsetDateTime.now());
        assetRepository.save(asset);

        Map<String, Object> afterState = buildAssetState(asset);
        Map<String, Object> payload = buildDeltaPayload("UPDATE", beforeState, afterState, changedFields);

        appendOutboxEvent(
                "impilo.asset.asset.updated.v1",
                asset.getAssetId().toString(), tenantId, podId, correlationId, idempotencyKey,
                payload, asset.getAssetId().toString());

        log.info("Updated asset [assetId={}, changedFields={}]", asset.getAssetId(), changedFields);
        return new UpsertResult(asset, false);
    }

    @Transactional
    public AssetEntity retireAsset(UUID assetId, UUID tenantId, String podId,
                                    String correlationId, String idempotencyKey) {
        AssetEntity asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new AssetNotFoundException(assetId));

        if ("RETIRED".equals(asset.getStatus())) {
            return asset; // already retired — idempotent
        }

        Map<String, Object> beforeState = buildAssetState(asset);

        asset.setStatus("RETIRED");
        asset.setVersion(asset.getVersion() + 1);
        asset.setUpdatedAt(OffsetDateTime.now());
        assetRepository.save(asset);

        Map<String, Object> afterState = buildAssetState(asset);
        Map<String, Object> payload = buildDeltaPayload("UPDATE", beforeState, afterState,
                List.of("status"));

        appendOutboxEvent(
                "impilo.asset.asset.retired.v1",
                assetId.toString(), tenantId, podId, correlationId, idempotencyKey,
                payload, assetId.toString());

        log.info("Retired asset [assetId={}]", assetId);
        return asset;
    }

    @Transactional(readOnly = true)
    public Optional<AssetEntity> getAsset(UUID assetId) {
        return assetRepository.findById(assetId);
    }

    public static class AssetNotFoundException extends RuntimeException {
        public AssetNotFoundException(UUID assetId) {
            super("Asset not found: " + assetId);
        }
    }

    @Transactional(readOnly = true)
    public Page<AssetEntity> listAssets(UUID tenantId, String facilityRef, String status,
                                         int page, int size) {
        return assetRepository.findFiltered(tenantId, facilityRef, status, PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public Page<AssetEntity> getSnapshot(OffsetDateTime asOf, int page, int size) {
        return assetRepository.findSnapshotAsOf(asOf, PageRequest.of(page, size));
    }

    private void appendOutboxEvent(String eventType, String aggregateId,
                                    UUID tenantId, String podId, String correlationId,
                                    String idempotencyKey, Map<String, Object> payload,
                                    String partitionKey) {
        OutboxEventEntity outbox = new OutboxEventEntity();
        outbox.setAggregateType(AGGREGATE_TYPE);
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setSchemaVersion("1");
        outbox.setCorrelationId(correlationId != null ? UUID.fromString(correlationId) : null);
        outbox.setCausationId(correlationId != null ? UUID.fromString(correlationId) : null);
        outbox.setIdempotencyKey(idempotencyKey);
        outbox.setProducer(PRODUCER);
        outbox.setTenantId(tenantId);
        outbox.setPodId(podId != null ? podId : "national-spine");
        outbox.setSubjectId(aggregateId);
        outbox.setSubjectType(SUBJECT_TYPE);
        outbox.setOccurredAt(OffsetDateTime.now());
        outbox.setPayloadJson(toJson(payload));
        outbox.setPartitionKey(partitionKey);
        outboxRepository.save(outbox);
    }

    private Map<String, Object> buildAssetState(AssetEntity asset) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("asset_id", asset.getAssetId().toString());
        state.put("tenant_id", asset.getTenantId().toString());
        state.put("facility_ref", asset.getFacilityRef());
        state.put("type", asset.getType());
        state.put("serial_no", asset.getSerialNo());
        state.put("status", asset.getStatus());
        state.put("metadata_json", asset.getMetadataJson());
        state.put("version", asset.getVersion());
        return state;
    }

    private Map<String, Object> buildDeltaPayload(String op, Map<String, Object> before,
                                                    Map<String, Object> after,
                                                    List<String> changedFields) {
        Map<String, Object> delta = new LinkedHashMap<>();
        delta.put("op", op);
        delta.put("before", before);
        delta.put("after", after);
        delta.put("changed_fields", changedFields);
        return delta;
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }

    public record UpsertResult(AssetEntity asset, boolean created) {}
}
