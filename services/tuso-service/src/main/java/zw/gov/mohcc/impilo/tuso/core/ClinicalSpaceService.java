package zw.gov.mohcc.impilo.tuso.core;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.tuso.persistence.entity.ClinicalSpaceEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.ClinicalSpaceRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read model over the clinical-space registry. Availability here is REGISTRY
 * availability (is the space operational?) — booking-window overlap is owned by
 * scheduling-service; occupancy is owned by inpatient. Kept deliberately narrow.
 */
@Service
public class ClinicalSpaceService {

    private final ClinicalSpaceRepository repository;

    public ClinicalSpaceService(ClinicalSpaceRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(UUID tenantId, String spaceType) {
        List<ClinicalSpaceEntity> spaces = (spaceType != null && !spaceType.isBlank())
                ? repository.findByTenantIdAndSpaceTypeOrderByNameAsc(tenantId, spaceType.toUpperCase())
                : repository.findByTenantIdOrderByNameAsc(tenantId);
        return spaces.stream().map(ClinicalSpaceEntity::toMap).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(UUID tenantId, UUID id) {
        return require(tenantId, id).toMap();
    }

    /**
     * Registry availability for a space on a date. {@code available} is true when
     * the space is ACTIVE. Consumed by scheduling ConflictDetectionService and
     * inpatient evaluateReadiness.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> availability(UUID tenantId, UUID id, String date) {
        ClinicalSpaceEntity space = require(tenantId, id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", space.getId() != null ? space.getId().toString() : null);
        out.put("name", space.getName());
        out.put("spaceType", space.getSpaceType());
        out.put("date", date);
        out.put("status", space.getStatus());
        out.put("available", "ACTIVE".equals(space.getStatus()));
        out.put("capacity", space.getCapacity());
        out.put("capabilityTags", space.getCapabilityTags());
        return out;
    }

    private ClinicalSpaceEntity require(UUID tenantId, UUID id) {
        return repository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Clinical space not found: " + id));
    }
}
