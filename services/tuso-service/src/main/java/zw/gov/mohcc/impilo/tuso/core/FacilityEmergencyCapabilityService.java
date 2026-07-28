package zw.gov.mohcc.impilo.tuso.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEmergencyCapabilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityEmergencyCapabilityRepository;

import java.util.Map;
import java.util.UUID;

/**
 * Hand-declared emergency capability per facility (V200, W10) — for a command view answering "can
 * this facility receive a critical trauma patient", not a national classification. See V200's own
 * migration header.
 */
@Service
public class FacilityEmergencyCapabilityService {

    private final FacilityEmergencyCapabilityRepository repository;

    public FacilityEmergencyCapabilityService(FacilityEmergencyCapabilityRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(UUID tenantId, Long facilityId) {
        return repository.findByTenantIdAndFacilityId(tenantId, facilityId)
                .map(FacilityEmergencyCapabilityEntity::toMap)
                .orElseGet(() -> Map.of("facilityId", facilityId, "declared", false));
    }

    /** Upsert — a facility declares (or re-declares) its own capability profile. */
    @Transactional
    public Map<String, Object> declare(UUID tenantId, Long facilityId, Map<String, Object> body, String declaredBy) {
        FacilityEmergencyCapabilityEntity e = repository.findByTenantIdAndFacilityId(tenantId, facilityId)
                .orElseGet(FacilityEmergencyCapabilityEntity::new);
        e.setFacilityId(facilityId);
        e.setTenantId(tenantId);
        if (body.containsKey("has24hTheatre")) e.setHas24hTheatre(boolVal(body, "has24hTheatre"));
        if (body.containsKey("hasBloodBank")) e.setHasBloodBank(boolVal(body, "hasBloodBank"));
        if (body.containsKey("hasCtScan")) e.setHasCtScan(boolVal(body, "hasCtScan"));
        if (body.containsKey("hasIcu")) e.setHasIcu(boolVal(body, "hasIcu"));
        if (body.containsKey("hasMassiveTransfusionProtocol"))
            e.setHasMassiveTransfusionProtocol(boolVal(body, "hasMassiveTransfusionProtocol"));
        if (body.get("notes") != null) e.setNotes(String.valueOf(body.get("notes")));
        e.setDeclaredBy(declaredBy);
        return repository.save(e).toMap();
    }

    private static Boolean boolVal(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? null : Boolean.parseBoolean(String.valueOf(v));
    }
}
