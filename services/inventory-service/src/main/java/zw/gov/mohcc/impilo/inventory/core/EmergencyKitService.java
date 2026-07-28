package zw.gov.mohcc.impilo.inventory.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.inventory.persistence.entity.EmergencyKitCheckEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.EmergencyKitEntity;
import zw.gov.mohcc.impilo.inventory.persistence.repository.EmergencyKitCheckRepository;
import zw.gov.mohcc.impilo.inventory.persistence.repository.EmergencyKitRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Emergency kit / resus trolley registry (W10) — genuinely absent before this wave. A kit is tracked
 * as a unit and periodically verified complete against its own declared manifest, distinct from
 * ordinary item-by-item stock replenishment.
 */
@Service
public class EmergencyKitService {

    private final EmergencyKitRepository kitRepository;
    private final EmergencyKitCheckRepository checkRepository;
    private final ObjectMapper objectMapper;

    public EmergencyKitService(EmergencyKitRepository kitRepository,
                               EmergencyKitCheckRepository checkRepository,
                               ObjectMapper objectMapper) {
        this.kitRepository = kitRepository;
        this.checkRepository = checkRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> registerKit(UUID tenantId, Map<String, Object> body) {
        UUID facilityId = uuidVal(body, "facilityId");
        String name = str(body, "name");
        if (facilityId == null || name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "facilityId and name are required");
        }
        EmergencyKitEntity kit = new EmergencyKitEntity();
        kit.setTenantId(tenantId);
        kit.setFacilityId(facilityId);
        kit.setStoreId(uuidVal(body, "storeId"));
        kit.setName(name);
        String kitType = str(body, "kitType");
        if (kitType != null) kit.setKitType(kitType);
        Object manifest = body.get("contentsManifest");
        if (manifest != null) kit.setContentsManifest(writeJson(manifest));
        return toMap(kitRepository.save(kit), null);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listForFacility(UUID tenantId, UUID facilityId) {
        return kitRepository.findByTenantIdAndFacilityId(tenantId, facilityId).stream()
                .map(kit -> toMap(kit, checkRepository.findFirstByTenantIdAndKitIdOrderByCheckedAtDesc(tenantId, kit.getKitId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(UUID tenantId, UUID kitId) {
        EmergencyKitEntity kit = require(tenantId, kitId);
        return toMap(kit, checkRepository.findFirstByTenantIdAndKitIdOrderByCheckedAtDesc(tenantId, kitId));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> checkHistory(UUID tenantId, UUID kitId) {
        return checkRepository.findByTenantIdAndKitIdOrderByCheckedAtDesc(tenantId, kitId).stream()
                .map(this::checkRow).toList();
    }

    /** Record a completeness check. A discrepancy check with no discrepancy detail is refused. */
    @Transactional
    public Map<String, Object> recordCheck(UUID tenantId, UUID kitId, Map<String, Object> body) {
        require(tenantId, kitId); // tenant-scoping check

        String checkedBy = str(body, "checkedBy");
        if (checkedBy == null || checkedBy.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "checkedBy is required");
        }
        boolean complete = Boolean.TRUE.equals(body.get("complete"));
        Object discrepanciesRaw = body.get("discrepancies");

        EmergencyKitCheckEntity check = new EmergencyKitCheckEntity();
        check.setTenantId(tenantId);
        check.setKitId(kitId);
        check.setCheckedBy(checkedBy);
        check.setComplete(complete);
        if (!complete) {
            if (!(discrepanciesRaw instanceof List<?> list) || list.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "discrepancies is required when complete=false — a discrepancy check with no recorded detail is not auditable");
            }
            check.setDiscrepancies(writeJson(discrepanciesRaw));
        } else if (discrepanciesRaw != null) {
            check.setDiscrepancies(writeJson(discrepanciesRaw));
        }
        check.setResealRef(str(body, "resealRef"));
        check.setNotes(str(body, "notes"));

        try {
            return checkRow(checkRepository.save(check));
        } catch (org.springframework.dao.DataIntegrityViolationException dup) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This check violates the discrepancy-required rule at the database layer");
        }
    }

    private EmergencyKitEntity require(UUID tenantId, UUID kitId) {
        return kitRepository.findByTenantIdAndKitId(tenantId, kitId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Emergency kit not found"));
    }

    private Map<String, Object> toMap(EmergencyKitEntity kit, EmergencyKitCheckEntity latestCheck) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kit_id", kit.getKitId().toString());
        m.put("facility_id", kit.getFacilityId().toString());
        m.put("store_id", kit.getStoreId() != null ? kit.getStoreId().toString() : null);
        m.put("name", kit.getName());
        m.put("kit_type", kit.getKitType());
        m.put("contents_manifest", readJson(kit.getContentsManifest()));
        m.put("status", kit.getStatus());
        if (latestCheck != null) {
            m.put("latest_check_at", latestCheck.getCheckedAt() != null ? latestCheck.getCheckedAt().toString() : null);
            m.put("latest_check_complete", latestCheck.isComplete());
        } else {
            m.put("latest_check_at", null);
            m.put("latest_check_complete", null); // never checked — distinct from "checked and complete"
        }
        return m;
    }

    private Map<String, Object> checkRow(EmergencyKitCheckEntity c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("check_id", c.getCheckId().toString());
        m.put("kit_id", c.getKitId().toString());
        m.put("checked_by", c.getCheckedBy());
        m.put("checked_at", c.getCheckedAt() != null ? c.getCheckedAt().toString() : null);
        m.put("complete", c.isComplete());
        m.put("discrepancies", readJson(c.getDiscrepancies()));
        m.put("reseal_ref", c.getResealRef());
        m.put("notes", c.getNotes());
        return m;
    }

    private String writeJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Failed to serialise JSON", e); }
    }

    private Object readJson(String json) {
        if (json == null || json.isBlank()) return null;
        try { return objectMapper.readValue(json, Object.class); }
        catch (JsonProcessingException e) { return json; }
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? null : v.toString();
    }

    private static UUID uuidVal(Map<String, Object> body, String key) {
        String s = str(body, key);
        if (s == null) return null;
        try { return UUID.fromString(s.trim()); } catch (IllegalArgumentException e) { return null; }
    }
}
