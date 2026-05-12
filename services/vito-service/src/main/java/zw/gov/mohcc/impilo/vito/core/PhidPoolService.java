package zw.gov.mohcc.impilo.vito.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.vito.core.id.LegacyPhidFormat;
import zw.gov.mohcc.impilo.vito.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.PhidPoolEntity;
import zw.gov.mohcc.impilo.vito.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.vito.persistence.repository.PhidPoolRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class PhidPoolService {

    private static final int MAX_COLLISION_RETRIES = 20;

    private final PhidPoolRepository phidPoolRepository;
    private final LegacyPhidFormat legacyPhidFormat;
    private final EventOutboxRepository eventOutboxRepository;
    private final ObjectMapper objectMapper;

    public PhidPoolService(PhidPoolRepository phidPoolRepository,
                           LegacyPhidFormat legacyPhidFormat,
                           EventOutboxRepository eventOutboxRepository,
                           ObjectMapper objectMapper) {
        this.phidPoolRepository = phidPoolRepository;
        this.legacyPhidFormat = legacyPhidFormat;
        this.eventOutboxRepository = eventOutboxRepository;
        this.objectMapper = objectMapper;
    }

    public List<PhidPoolEntry> allocate(String facilityId, int count) {
        if (facilityId == null || facilityId.isBlank()) {
            throw new IllegalArgumentException("facilityId must not be blank");
        }
        if (count <= 0 || count > 5000) {
            throw new IllegalArgumentException("count must be between 1 and 5000");
        }

        List<PhidPoolEntity> batch = new ArrayList<>(count);
        List<PhidPoolEntry> result = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            PhidPoolEntity entry = generateUniqueEntry(facilityId);
            batch.add(entry);
            result.add(new PhidPoolEntry(entry.getHealthId(), entry.getPhid()));
        }

        phidPoolRepository.saveAll(batch);

        publishAllocationEvent(facilityId, count);

        return result;
    }

    public PhidPoolEntity claim(UUID healthId, String mrsPatientId) {
        PhidPoolEntity entry = phidPoolRepository.findByHealthId(healthId)
                .orElseThrow(() -> new IllegalArgumentException("No pool entry found for healthId: " + healthId));

        if (!"AVAILABLE".equals(entry.getStatus())) {
            throw new AccessDeniedException("Pool entry " + healthId + " is not AVAILABLE (status=" + entry.getStatus() + ")");
        }

        entry.setStatus("ASSIGNED");
        entry.setAssignedAt(OffsetDateTime.now());
        entry.setMrsPatientId(mrsPatientId);
        return phidPoolRepository.save(entry);
    }

    public PhidPoolStats getStats(String facilityId) {
        long available = phidPoolRepository.countByFacilityIdAndStatus(facilityId, "AVAILABLE");
        long assigned = phidPoolRepository.countByFacilityIdAndStatus(facilityId, "ASSIGNED");
        long total = available + assigned;
        return new PhidPoolStats(available, assigned, total);
    }

    private PhidPoolEntity generateUniqueEntry(String facilityId) {
        for (int attempt = 0; attempt < MAX_COLLISION_RETRIES; attempt++) {
            String phid = legacyPhidFormat.generate();
            UUID healthId = UUID.randomUUID();

            if (phidPoolRepository.findByPhid(phid).isPresent()) {
                continue;
            }

            PhidPoolEntity entry = new PhidPoolEntity();
            entry.setFacilityId(facilityId);
            entry.setPhid(phid);
            entry.setHealthId(healthId);
            entry.setStatus("AVAILABLE");
            return entry;
        }
        throw new IllegalStateException("Unable to generate unique PHID after " + MAX_COLLISION_RETRIES + " attempts");
    }

    private void publishAllocationEvent(String facilityId, int count) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("facilityId", facilityId);
        payload.put("count", count);

        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType("PHID_POOL");
        event.setAggregateId(facilityId);
        event.setEventType("PHID_POOL_ALLOCATED");
        event.setPayload(toJson(payload));
        event.setSubjectType("PHID_POOL");
        event.setSubjectId(facilityId);
        eventOutboxRepository.save(event);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    public record PhidPoolEntry(UUID healthId, String phid) {}

    public record PhidPoolStats(long available, long assigned, long total) {}
}
