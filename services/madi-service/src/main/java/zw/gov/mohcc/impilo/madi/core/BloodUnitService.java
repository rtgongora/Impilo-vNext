package zw.gov.mohcc.impilo.madi.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.madi.domain.BloodUnitStatus;
import zw.gov.mohcc.impilo.madi.domain.ComponentType;
import zw.gov.mohcc.impilo.madi.events.MadiEventEmitter;
import zw.gov.mohcc.impilo.madi.persistence.entity.BloodCollectionEntity;
import zw.gov.mohcc.impilo.madi.persistence.entity.BloodUnitEntity;
import zw.gov.mohcc.impilo.madi.persistence.repository.BloodCollectionRepository;
import zw.gov.mohcc.impilo.madi.persistence.repository.BloodUnitRepository;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class BloodUnitService {

    private static final Map<BloodUnitStatus, Set<BloodUnitStatus>> TRANSITIONS = Map.of(
            BloodUnitStatus.COLLECTED, EnumSet.of(BloodUnitStatus.TESTING, BloodUnitStatus.DISCARDED),
            BloodUnitStatus.TESTING, EnumSet.of(BloodUnitStatus.QUARANTINE, BloodUnitStatus.AVAILABLE, BloodUnitStatus.DISCARDED),
            BloodUnitStatus.QUARANTINE, EnumSet.of(BloodUnitStatus.AVAILABLE, BloodUnitStatus.DISCARDED),
            BloodUnitStatus.AVAILABLE, EnumSet.of(BloodUnitStatus.RESERVED, BloodUnitStatus.ISSUED, BloodUnitStatus.EXPIRED, BloodUnitStatus.DISCARDED),
            BloodUnitStatus.RESERVED, EnumSet.of(BloodUnitStatus.AVAILABLE, BloodUnitStatus.ISSUED),
            BloodUnitStatus.ISSUED, EnumSet.of(BloodUnitStatus.DISCARDED),
            BloodUnitStatus.EXPIRED, EnumSet.of(BloodUnitStatus.DISCARDED)
    );

    private final BloodUnitRepository bloodUnitRepository;
    private final BloodCollectionRepository collectionRepository;
    private final MadiEventEmitter eventEmitter;

    public BloodUnitService(BloodUnitRepository bloodUnitRepository,
                            BloodCollectionRepository collectionRepository,
                            MadiEventEmitter eventEmitter) {
        this.bloodUnitRepository = bloodUnitRepository;
        this.collectionRepository = collectionRepository;
        this.eventEmitter = eventEmitter;
    }

    @Transactional
    public BloodUnitEntity createFromCollection(UUID tenantId, UUID collectionId, String bloodGroup,
                                                String rhFactor, UUID bloodBankId, UUID facilityId) {
        BloodCollectionEntity collection = collectionRepository.findByCollectionIdAndTenantId(collectionId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Collection not found"));
        BloodUnitEntity unit = new BloodUnitEntity();
        unit.setTenantId(tenantId);
        unit.setCollectionId(collectionId);
        unit.setBagNumber(collection.getBagNumber());
        unit.setBloodGroup(bloodGroup);
        unit.setRhFactor(rhFactor);
        unit.setComponentType(ComponentType.WHOLE_BLOOD.name());
        unit.setStatus(BloodUnitStatus.COLLECTED.name());
        unit.setBloodBankId(bloodBankId);
        unit.setFacilityId(facilityId);
        unit.setExpiryAt(OffsetDateTime.now().plusDays(35));
        unit.setUpdatedAt(OffsetDateTime.now());
        BloodUnitEntity saved = bloodUnitRepository.save(unit);
        eventEmitter.emit("BLOOD_UNIT", saved.getUnitId().toString(), "BLOOD_UNIT_CREATED", "BLOOD_UNIT",
                saved.getUnitId().toString(),
                Map.of("bagNumber", collection.getBagNumber(), "bloodGroup", bloodGroup), tenantId);
        return saved;
    }

    @Transactional
    public BloodUnitEntity transition(UUID tenantId, UUID unitId, BloodUnitStatus target) {
        BloodUnitEntity unit = bloodUnitRepository.findByUnitIdAndTenantId(unitId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Blood unit not found"));
        BloodUnitStatus current = BloodUnitStatus.valueOf(unit.getStatus());
        Set<BloodUnitStatus> allowed = TRANSITIONS.getOrDefault(current, EnumSet.noneOf(BloodUnitStatus.class));
        if (!allowed.contains(target)) {
            throw new IllegalStateException("Invalid transition from " + current + " to " + target);
        }
        unit.setStatus(target.name());
        unit.setUpdatedAt(OffsetDateTime.now());
        BloodUnitEntity saved = bloodUnitRepository.save(unit);
        eventEmitter.emit("BLOOD_UNIT", unitId.toString(), "BLOOD_UNIT_" + target.name(), "BLOOD_UNIT",
                unitId.toString(), Map.of("from", current.name(), "to", target.name()), tenantId);
        return saved;
    }

    @Transactional(readOnly = true)
    public BloodUnitEntity get(UUID tenantId, UUID unitId) {
        return bloodUnitRepository.findByUnitIdAndTenantId(unitId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Blood unit not found"));
    }
}
