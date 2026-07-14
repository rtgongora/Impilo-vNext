package zw.gov.mohcc.impilo.tuso.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tuso.persistence.entity.BedEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.EquipmentAssetEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.InstrumentSetEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.BedRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.EquipmentAssetRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.InstrumentSetRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Read model over beds, equipment and instrument sets for readiness checks. */
@Service
public class ResourceAvailabilityService {

    private final BedRepository bedRepository;
    private final EquipmentAssetRepository equipmentRepository;
    private final InstrumentSetRepository instrumentSetRepository;

    public ResourceAvailabilityService(BedRepository bedRepository,
                                       EquipmentAssetRepository equipmentRepository,
                                       InstrumentSetRepository instrumentSetRepository) {
        this.bedRepository = bedRepository;
        this.equipmentRepository = equipmentRepository;
        this.instrumentSetRepository = instrumentSetRepository;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> beds(UUID tenantId, String bedClass, String status) {
        List<BedEntity> beds;
        if (bedClass != null && !bedClass.isBlank() && status != null && !status.isBlank()) {
            beds = bedRepository.findByTenantIdAndBedClassAndStatusOrderByCodeAsc(
                    tenantId, bedClass.toUpperCase(), status.toUpperCase());
        } else if (bedClass != null && !bedClass.isBlank()) {
            beds = bedRepository.findByTenantIdAndBedClassOrderByCodeAsc(tenantId, bedClass.toUpperCase());
        } else if (status != null && !status.isBlank()) {
            beds = bedRepository.findByTenantIdAndStatusOrderByCodeAsc(tenantId, status.toUpperCase());
        } else {
            beds = bedRepository.findByTenantIdOrderByCodeAsc(tenantId);
        }
        return beds.stream().map(BedEntity::toMap).toList();
    }

    @Transactional(readOnly = true)
    public long countAvailableBeds(UUID tenantId, String bedClass) {
        return bedRepository.countByTenantIdAndBedClassAndStatus(tenantId, bedClass.toUpperCase(), "AVAILABLE");
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> equipment(UUID tenantId, String state) {
        List<EquipmentAssetEntity> equipment = (state != null && !state.isBlank())
                ? equipmentRepository.findByTenantIdAndMaintenanceStateOrderByNameAsc(tenantId, state.toUpperCase())
                : equipmentRepository.findByTenantIdOrderByNameAsc(tenantId);
        return equipment.stream().map(EquipmentAssetEntity::toMap).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> instrumentSets(UUID tenantId, String state) {
        List<InstrumentSetEntity> sets = (state != null && !state.isBlank())
                ? instrumentSetRepository.findByTenantIdAndSterilisationStateOrderByNameAsc(tenantId, state.toUpperCase())
                : instrumentSetRepository.findByTenantIdOrderByNameAsc(tenantId);
        return sets.stream().map(InstrumentSetEntity::toMap).toList();
    }
}
