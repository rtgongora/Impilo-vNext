package zw.gov.mohcc.impilo.inpatient.core;

import org.springframework.stereotype.Service;
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

    public BedManagementService(WardRepository wardRepository, BedRepository bedRepository) {
        this.wardRepository = wardRepository;
        this.bedRepository = bedRepository;
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

        bed.setStatus("OCCUPIED");
        bed.setSubjectCpid(patientId.trim());
        bed.setPatientName(stringVal(body, "patientName", "patient_name"));
        bed.setPatientDiagnosis(stringVal(body, "diagnosis", "patientDiagnosis", "patient_diagnosis"));
        bed.setAttendingPhysician(stringVal(body, "assignedDoctor", "assigned_doctor", "attendingPhysician"));
        bed.setAcuityLevel(stringVal(body, "acuity", "acuityLevel", "acuity_level"));
        bed.setPatientGender(stringVal(body, "gender", "patientGender", "patient_gender"));
        bed.setPatientAge(intVal(body, "age", "patientAge", "patient_age"));
        bed.setOccupiedAt(OffsetDateTime.now());
        bedRepository.save(bed);

        WardEntity ward = wardRepository.findById(bed.getWardId()).orElse(null);
        return toBedResource(bed, ward);
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
