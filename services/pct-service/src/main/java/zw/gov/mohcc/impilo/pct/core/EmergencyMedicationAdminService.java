package zw.gov.mohcc.impilo.pct.core;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyMedicationAdminEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EmergencyEpisodeRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.EmergencyMedicationAdminRepository;

/**
 * ED / non-admitted medication administration (pct V207, W8b). See the migration header for why
 * this is a separate table from inpatient's resuscitation_medication.
 *
 * <p>Validates the two-person guard at the application layer too, for a clear 400 instead of an
 * opaque constraint-violation 500 — the database CHECKs (chk_ema_*) remain the real guarantee.
 */
@Service
public class EmergencyMedicationAdminService {

    private final EmergencyEpisodeRepository episodeRepository;
    private final EmergencyMedicationAdminRepository adminRepository;

    public EmergencyMedicationAdminService(EmergencyEpisodeRepository episodeRepository,
                                           EmergencyMedicationAdminRepository adminRepository) {
        this.episodeRepository = episodeRepository;
        this.adminRepository = adminRepository;
    }

    @Transactional
    public EmergencyMedicationAdminEntity record(UUID episodeId, UUID tenantId, Map<String, Object> body) {
        episodeRepository.findByEpisodeIdAndTenantId(episodeId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Emergency episode not found"));

        String drugName = str(body, "drugName", "drug_name", "drug");
        if (drugName == null || drugName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "drug_name is required");
        }
        String doseUnit = str(body, "doseUnit", "dose_unit");
        String route = str(body, "route");
        String administeredBy = str(body, "administeredBy", "administered_by");
        if (doseUnit == null || doseUnit.isBlank() || route == null || route.isBlank()
                || administeredBy == null || administeredBy.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "dose_unit, route and administered_by are required");
        }
        BigDecimal doseValue = decimalVal(body, "doseValue", "dose_value");
        if (doseValue == null || doseValue.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dose_value must be a positive number");
        }

        boolean highRisk = Boolean.TRUE.equals(body.get("highRisk")) || Boolean.TRUE.equals(body.get("high_risk"));
        String authorisedBy = str(body, "authorisedBy", "authorised_by");
        String witnessedBy = str(body, "witnessedBy", "witnessed_by");
        if (highRisk) {
            if (authorisedBy == null || authorisedBy.isBlank() || witnessedBy == null || witnessedBy.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "authorised_by and witnessed_by are both required for a high-risk administration");
            }
            if (authorisedBy.equalsIgnoreCase(witnessedBy)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "authorised_by and witnessed_by must be different people for a high-risk administration");
            }
            if (administeredBy.equalsIgnoreCase(witnessedBy)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "the person who administered a high-risk dose cannot also be its sole witness");
            }
        }

        EmergencyMedicationAdminEntity admin = new EmergencyMedicationAdminEntity();
        admin.setTenantId(tenantId);
        admin.setEpisodeId(episodeId);
        admin.setVisitId(uuidVal(body, "visitId", "visit_id"));
        admin.setTraumaEpisodeId(uuidVal(body, "traumaEpisodeId", "trauma_episode_id"));
        admin.setDrugName(drugName);
        admin.setDrugCode(str(body, "drugCode", "drug_code"));
        admin.setDoseValue(doseValue);
        admin.setDoseUnit(doseUnit);
        admin.setRoute(route);
        admin.setHighRisk(highRisk);
        admin.setAdministeredBy(administeredBy);
        admin.setAuthorisedBy(authorisedBy);
        admin.setWitnessedBy(witnessedBy);
        admin.setClientEventId(str(body, "clientEventId", "client_event_id"));
        admin.setNotes(str(body, "notes"));

        try {
            return adminRepository.save(admin);
        } catch (org.springframework.dao.DataIntegrityViolationException dup) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This medication administration was already recorded (duplicate client_event_id)");
        }
    }

    @Transactional(readOnly = true)
    public List<EmergencyMedicationAdminEntity> forEpisode(UUID episodeId, UUID tenantId) {
        return adminRepository.findByTenantIdAndEpisodeIdOrderByAdministeredAtDesc(tenantId, episodeId);
    }

    private static String str(Map<String, Object> body, String... keys) {
        if (body == null) return null;
        for (String k : keys) {
            Object v = body.get(k);
            if (v != null && !v.toString().isBlank()) return v.toString();
        }
        return null;
    }

    private static UUID uuidVal(Map<String, Object> body, String... keys) {
        String s = str(body, keys);
        if (s == null) return null;
        try { return UUID.fromString(s.trim()); } catch (IllegalArgumentException e) { return null; }
    }

    private static BigDecimal decimalVal(Map<String, Object> body, String... keys) {
        String s = str(body, keys);
        if (s == null) return null;
        try { return new BigDecimal(s.trim()); } catch (NumberFormatException e) { return null; }
    }
}
