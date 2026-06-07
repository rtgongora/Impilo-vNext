package zw.gov.mohcc.impilo.madi.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.madi.domain.BloodUnitStatus;
import zw.gov.mohcc.impilo.madi.domain.ComponentType;
import zw.gov.mohcc.impilo.madi.events.MadiEventEmitter;
import zw.gov.mohcc.impilo.madi.persistence.entity.BloodComponentEntity;
import zw.gov.mohcc.impilo.madi.persistence.entity.BloodProcessingBatchEntity;
import zw.gov.mohcc.impilo.madi.persistence.repository.BloodComponentRepository;
import zw.gov.mohcc.impilo.madi.persistence.repository.BloodProcessingBatchRepository;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class ProcessingService {

    private final BloodProcessingBatchRepository batchRepository;
    private final BloodComponentRepository componentRepository;
    private final BloodUnitService bloodUnitService;
    private final MadiEventEmitter eventEmitter;

    public ProcessingService(BloodProcessingBatchRepository batchRepository,
                             BloodComponentRepository componentRepository,
                             BloodUnitService bloodUnitService,
                             MadiEventEmitter eventEmitter) {
        this.batchRepository = batchRepository;
        this.componentRepository = componentRepository;
        this.bloodUnitService = bloodUnitService;
        this.eventEmitter = eventEmitter;
    }

    @Transactional
    public BloodProcessingBatchEntity receive(UUID tenantId, UUID unitId, UUID facilityId) {
        bloodUnitService.transition(tenantId, unitId, BloodUnitStatus.TESTING);
        BloodProcessingBatchEntity batch = new BloodProcessingBatchEntity();
        batch.setTenantId(tenantId);
        batch.setUnitId(unitId);
        batch.setBatchStatus("RECEIVED");
        batch.setFacilityId(facilityId);
        batch.setReceivedAt(OffsetDateTime.now());
        batch.setUpdatedAt(OffsetDateTime.now());
        BloodProcessingBatchEntity saved = batchRepository.save(batch);
        eventEmitter.emit("BLOOD_UNIT", unitId.toString(), "PROCESSING_RECEIVED", "BLOOD_UNIT",
                unitId.toString(), Map.of("batchId", saved.getBatchId().toString()), tenantId);
        return saved;
    }

    @Transactional
    public BloodProcessingBatchEntity recordTest(UUID tenantId, UUID batchId, String testResultsJson, UUID facilityId) {
        BloodProcessingBatchEntity batch = requireBatch(tenantId, batchId);
        batch.setTestResultsJson(testResultsJson);
        batch.setBatchStatus("TESTED");
        batch.setUpdatedAt(OffsetDateTime.now());
        return batchRepository.save(batch);
    }

    @Transactional
    public BloodProcessingBatchEntity quarantine(UUID tenantId, UUID batchId, String reason, UUID facilityId) {
        BloodProcessingBatchEntity batch = requireBatch(tenantId, batchId);
        batch.setBatchStatus("QUARANTINE");
        batch.setQuarantineReason(reason);
        batch.setUpdatedAt(OffsetDateTime.now());
        bloodUnitService.transition(tenantId, batch.getUnitId(), BloodUnitStatus.QUARANTINE);
        BloodProcessingBatchEntity saved = batchRepository.save(batch);
        eventEmitter.emit("BLOOD_UNIT", batch.getUnitId().toString(), "PROCESSING_QUARANTINED", "BLOOD_UNIT",
                batch.getUnitId().toString(), Map.of("reason", reason), tenantId);
        return saved;
    }

    @Transactional
    public BloodProcessingBatchEntity release(UUID tenantId, UUID batchId, String releasedBy, UUID facilityId) {
        BloodProcessingBatchEntity batch = requireBatch(tenantId, batchId);
        batch.setBatchStatus("RELEASED");
        batch.setReleasedBy(releasedBy);
        batch.setReleasedAt(OffsetDateTime.now());
        batch.setUpdatedAt(OffsetDateTime.now());
        bloodUnitService.transition(tenantId, batch.getUnitId(), BloodUnitStatus.AVAILABLE);
        BloodProcessingBatchEntity saved = batchRepository.save(batch);
        eventEmitter.emit("BLOOD_UNIT", batch.getUnitId().toString(), "PROCESSING_RELEASED", "BLOOD_UNIT",
                batch.getUnitId().toString(), Map.of(), tenantId);
        return saved;
    }

    @Transactional
    public BloodComponentEntity prepareComponent(UUID tenantId, UUID unitId, ComponentType componentType,
                                                 Integer volumeMl, UUID productId, UUID facilityId) {
        bloodUnitService.get(tenantId, unitId);
        BloodComponentEntity component = new BloodComponentEntity();
        component.setTenantId(tenantId);
        component.setUnitId(unitId);
        component.setProductId(productId);
        component.setComponentType(componentType.name());
        component.setVolumeMl(volumeMl);
        component.setFacilityId(facilityId);
        component.setStatus("PREPARED");
        component.setPreparedAt(OffsetDateTime.now());
        BloodComponentEntity saved = componentRepository.save(component);
        eventEmitter.emit("BLOOD_UNIT", unitId.toString(), "COMPONENT_PREPARED", "BLOOD_COMPONENT",
                saved.getComponentId().toString(),
                Map.of("componentType", componentType.name()), tenantId);
        return saved;
    }

    private BloodProcessingBatchEntity requireBatch(UUID tenantId, UUID batchId) {
        return batchRepository.findByBatchIdAndTenantId(batchId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Processing batch not found"));
    }
}
