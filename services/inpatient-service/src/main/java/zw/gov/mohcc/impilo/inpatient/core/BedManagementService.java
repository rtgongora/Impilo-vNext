package zw.gov.mohcc.impilo.inpatient.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.EventOutboxRepository;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.BedEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.WardEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.BedRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.WardRepository;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BedManagementService {

    private final WardRepository wardRepository;
    private final BedRepository bedRepository;
    private final BedSafetyEvaluator bedSafetyEvaluator;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public BedManagementService(WardRepository wardRepository,
                                BedRepository bedRepository,
                                BedSafetyEvaluator bedSafetyEvaluator,
                                EventOutboxRepository outboxRepository,
                                ObjectMapper objectMapper) {
        this.wardRepository = wardRepository;
        this.bedRepository = bedRepository;
        this.bedSafetyEvaluator = bedSafetyEvaluator;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> listWardResources(UUID facilityId) {
        List<WardEntity> wards = wardRepository.findByFacilityIdOrderByNameAsc(facilityId);
        List<BedEntity> beds = bedRepository.findByFacilityId(facilityId);
        Map<UUID, List<BedEntity>> byWard = beds.stream().collect(Collectors.groupingBy(BedEntity::getWardId));

        List<Map<String, Object>> resources = new ArrayList<>();
        for (WardEntity ward : wards) {
            List<BedEntity> wardBeds = byWard.getOrDefault(ward.getId(), List.of());
            long occupied = wardBeds.stream().filter(b -> "OCCUPIED".equalsIgnoreCase(b.getStatus())).count();
            long maintenance = wardBeds.stream().filter(b -> "MAINTENANCE".equalsIgnoreCase(b.getStatus())).count();
            long available = wardBeds.stream().filter(b -> "AVAILABLE".equalsIgnoreCase(b.getStatus())).count();

            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("name", ward.getName());
            attributes.put("facilityId", ward.getFacilityId().toString());
            attributes.put("wardType", ward.getWardType());
            attributes.put("totalBeds", wardBeds.isEmpty() ? ward.getTotalBeds() : wardBeds.size());
            attributes.put("occupiedBeds", occupied);
            attributes.put("availableBeds", available);
            attributes.put("maintenanceBeds", maintenance);
            attributes.put("genderDesignation", ward.getGenderDesignation());
            attributes.put("ageGroup", ward.getAgeGroup());
            attributes.put("isolationCapable", ward.isIsolationCapable());
            attributes.put("oxygenAvailable", ward.isOxygenAvailable());
            attributes.put("monitoringCapable", ward.isMonitoringCapable());
            attributes.put("icuCapable", ward.isIcuCapable());

            resources.add(Map.of(
                    "id", ward.getId().toString(),
                    "type", "ward",
                    "attributes", attributes));
        }
        return resources;
    }

    public List<Map<String, Object>> listBedResources(UUID facilityId, UUID wardId, String status) {
        List<BedEntity> beds;
        if (wardId != null && status != null && !status.isBlank()) {
            beds = bedRepository.findByFacilityIdAndWardIdAndStatus(facilityId, wardId, normalizeStatus(status));
        } else if (wardId != null) {
            beds = bedRepository.findByFacilityIdAndWardId(facilityId, wardId);
        } else if (status != null && !status.isBlank()) {
            beds = bedRepository.findByFacilityIdAndStatus(facilityId, normalizeStatus(status));
        } else {
            beds = bedRepository.findByFacilityId(facilityId);
        }

        Map<UUID, WardEntity> wardsById = wardRepository.findByFacilityIdOrderByNameAsc(facilityId).stream()
                .collect(Collectors.toMap(WardEntity::getId, w -> w));

        return beds.stream()
                .sorted(Comparator.comparing(BedEntity::getBedNumber))
                .map(bed -> toBedResource(bed, wardsById.get(bed.getWardId())))
                .toList();
    }

    @Transactional
    public Map<String, Object> updateBedStatus(UUID bedId, String status) {
        BedEntity bed = requireBed(bedId);
        bed.setStatus(normalizeStatus(status));
        bedRepository.save(bed);
        WardEntity ward = wardRepository.findById(bed.getWardId()).orElse(null);
        return toBedResource(bed, ward);
    }

    @Transactional
    public Map<String, Object> assignPatient(UUID bedId, Map<String, Object> body) {
        BedEntity bed = requireBed(bedId);
        if (!"AVAILABLE".equalsIgnoreCase(bed.getStatus()) && !"CLEANING".equalsIgnoreCase(bed.getStatus())) {
            throw new IllegalStateException("Bed is not available for assignment: " + bedId);
        }

        String patientId = stringVal(body, "patientId", "patient_id", "subjectCpid", "subject_cpid", "patientMrn", "patient_mrn");
        if (patientId == null || patientId.isBlank()) {
            throw new IllegalArgumentException("patientId is required");
        }

        WardEntity ward = wardRepository.findById(bed.getWardId()).orElse(null);

        String patientGender = stringVal(body, "gender", "patientGender", "patient_gender");
        Integer patientAge = intVal(body, "age", "patientAge", "patient_age");
        boolean requiresIsolation = boolVal(body, "requiresIsolation", "requires_isolation", "isolationRequired");
        boolean requiresOxygen = boolVal(body, "requiresOxygen", "requires_oxygen", "oxygenRequired");
        boolean requiresMonitoring = boolVal(body, "requiresMonitoring", "requires_monitoring", "monitoringRequired");
        boolean requiresIcu = boolVal(body, "requiresIcu", "requires_icu", "icuRequired");

        // Safety gate: fail closed if the bed is clinically unsafe for this patient (gender bay, age
        // group, isolation, oxygen, monitoring, ICU). EMERGENCY/BREAK_GLASS is NOT silently bypassed
        // here — an unsafe placement under emergency must be an explicit clinical decision recorded
        // elsewhere; this method protects routine ward placement.
        List<String> violations = bedSafetyEvaluator.evaluate(bed, ward, new BedSafetyEvaluator.SafetyRequest(
                patientGender, patientAge, requiresIsolation, requiresOxygen, requiresMonitoring, requiresIcu));
        if (!violations.isEmpty()) {
            throw new BedSafetyViolationException(violations);
        }

        bed.setStatus("OCCUPIED");
        bed.setSubjectCpid(patientId.trim());
        bed.setPatientName(stringVal(body, "patientName", "patient_name"));
        bed.setPatientDiagnosis(stringVal(body, "diagnosis", "patientDiagnosis", "patient_diagnosis"));
        bed.setAttendingPhysician(stringVal(body, "assignedDoctor", "assigned_doctor", "attendingPhysician"));
        bed.setAcuityLevel(stringVal(body, "acuity", "acuityLevel", "acuity_level"));
        bed.setPatientGender(patientGender);
        bed.setPatientAge(patientAge);
        bed.setRequiresIsolation(requiresIsolation);
        bed.setRequiresOxygen(requiresOxygen);
        bed.setRequiresMonitoring(requiresMonitoring);
        bed.setRequiresIcu(requiresIcu);
        bed.setOccupiedAt(OffsetDateTime.now());
        bedRepository.save(bed);

        emitBedAssigned(bed);

        return toBedResource(bed, ward);
    }

    /**
     * Emit {@code inpatient.bed.assigned} for the bed board / location-event stream. The bed entity is
     * the SoR; this is a notification of the safe placement (it carries the resolved requirements that
     * were satisfied, for audit). Best-effort: a serialisation failure must not roll back the placement.
     */
    private void emitBedAssigned(BedEntity bed) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("bed_id", bed.getId().toString());
            payload.put("ward_id", bed.getWardId().toString());
            payload.put("facility_id", bed.getFacilityId().toString());
            payload.put("bed_number", bed.getBedNumber());
            payload.put("subject_cpid", bed.getSubjectCpid());
            payload.put("acuity_level", bed.getAcuityLevel());
            payload.put("requires_isolation", bed.isRequiresIsolation());
            payload.put("requires_oxygen", bed.isRequiresOxygen());
            payload.put("requires_monitoring", bed.isRequiresMonitoring());
            payload.put("requires_icu", bed.isRequiresIcu());
            EventOutboxEntity outbox = new EventOutboxEntity();
            outbox.setAggregateType("BED");
            outbox.setAggregateId(bed.getId().toString());
            outbox.setTenantId(bed.getTenantId().toString());
            outbox.setPodId(System.getenv("HOSTNAME") != null ? System.getenv("HOSTNAME") : "local");
            outbox.setCorrelationId(UUID.randomUUID().toString());
            outbox.setEventType("inpatient.bed.assigned");
            outbox.setSchemaVersion(1);
            outbox.setOccurredAt(OffsetDateTime.now());
            outbox.setPayloadJson(objectMapper.writeValueAsString(payload));
            outboxRepository.save(outbox);
        } catch (JsonProcessingException e) {
            // do not fail the placement on an event-serialisation problem
            throw new IllegalStateException("Failed to serialise bed-assigned event", e);
        }
    }

    private static boolean boolVal(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            Object value = body.get(key);
            if (value == null) continue;
            if (value instanceof Boolean b) return b;
            String sv = String.valueOf(value).trim();
            if ("true".equalsIgnoreCase(sv) || "yes".equalsIgnoreCase(sv) || "1".equals(sv)) return true;
            if ("false".equalsIgnoreCase(sv) || "no".equalsIgnoreCase(sv) || "0".equals(sv)) return false;
        }
        return false;
    }

    @Transactional
    public Map<String, Object> dischargeBed(UUID bedId) {
        BedEntity bed = requireBed(bedId);
        bed.setStatus("CLEANING");
        bed.setSubjectCpid(null);
        bed.setPatientName(null);
        bed.setPatientDiagnosis(null);
        bed.setAttendingPhysician(null);
        bed.setAcuityLevel(null);
        bed.setPatientAge(null);
        bed.setPatientGender(null);
        bed.setOccupiedAt(null);
        bedRepository.save(bed);

        WardEntity ward = wardRepository.findById(bed.getWardId()).orElse(null);
        return toBedResource(bed, ward);
    }

    private BedEntity requireBed(UUID bedId) {
        return bedRepository.findById(bedId)
                .orElseThrow(() -> new BedNotFoundException("Bed not found: " + bedId));
    }

    private static Map<String, Object> toBedResource(BedEntity bed, WardEntity ward) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("bedNumber", bed.getBedNumber());
        attributes.put("wardId", bed.getWardId().toString());
        attributes.put("wardName", ward != null ? ward.getName() : "");
        attributes.put("facilityId", bed.getFacilityId().toString());
        attributes.put("status", bed.getStatus().toLowerCase(Locale.ROOT));
        attributes.put("patientId", bed.getSubjectCpid());
        attributes.put("patientName", bed.getPatientName());
        attributes.put("patientMrn", bed.getSubjectCpid());
        attributes.put("patientDiagnosis", bed.getPatientDiagnosis());
        attributes.put("patientAttendingPhysician", bed.getAttendingPhysician());
        attributes.put("patientAcuityLevel", bed.getAcuityLevel() != null
                ? bed.getAcuityLevel().toLowerCase(Locale.ROOT) : null);
        attributes.put("patientAdmissionDate", bed.getOccupiedAt() != null ? bed.getOccupiedAt().toString() : null);
        attributes.put("patientAge", bed.getPatientAge());
        attributes.put("patientGender", bed.getPatientGender());
        attributes.put("genderDesignation", bed.getGenderDesignation());
        attributes.put("isolationCapable", bed.isIsolationCapable());
        attributes.put("oxygenAvailable", bed.isOxygenAvailable());
        attributes.put("monitoringCapable", bed.isMonitoringCapable());
        attributes.put("icuCapable", bed.isIcuCapable());
        attributes.put("requiresIsolation", bed.isRequiresIsolation());
        attributes.put("requiresMonitoring", bed.isRequiresMonitoring());
        attributes.put("requiresIcu", bed.isRequiresIcu());

        return Map.of(
                "id", bed.getId().toString(),
                "type", "bed",
                "attributes", attributes);
    }

    private static String normalizeStatus(String status) {
        return status == null ? "AVAILABLE" : status.trim().toUpperCase(Locale.ROOT);
    }

    private static String stringVal(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            Object value = body.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private static Integer intVal(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            Object value = body.get(key);
            if (value == null) continue;
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                // try next key
            }
        }
        return null;
    }
}
