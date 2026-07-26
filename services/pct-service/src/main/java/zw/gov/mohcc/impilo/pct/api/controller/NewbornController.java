package zw.gov.mohcc.impilo.pct.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.pct.core.clinical.NewbornEpisodeService;
import zw.gov.mohcc.impilo.pct.persistence.entity.NewbornBirthRecordEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Newborn birth record API — the child-anchored view of a delivery.
 *
 * <p>Recording is idempotent on the baby's CPID, so a delivery captured here after the
 * theatre event already opened the episode enriches the existing record rather than
 * creating a second one.</p>
 */
@RestController
@RequestMapping("/v1/newborn-records")
public class NewbornController {

    private final NewbornEpisodeService newbornEpisodeService;

    public NewbornController(NewbornEpisodeService newbornEpisodeService) {
        this.newbornEpisodeService = newbornEpisodeService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> get(
            @RequestParam(name = "patient_id", required = false) String patientId,
            @RequestParam(name = "subject_cpid", required = false) String subjectCpid) {
        String subject = subjectCpid != null && !subjectCpid.isBlank() ? subjectCpid : patientId;
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("patient_id is required");
        }
        NewbornBirthRecordEntity record = newbornEpisodeService.findBySubject(subject)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No birth record for subject"));
        return ResponseEntity.ok(ApiResponse.ok(toMap(record), correlationId()));
    }

    /** Children linked to a mother — siblings of a multiple birth, and earlier deliveries. */
    @GetMapping("/by-mother")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> byMother(
            @RequestParam(name = "mother_cpid") String motherCpid) {
        if (motherCpid == null || motherCpid.isBlank()) {
            throw new IllegalArgumentException("mother_cpid is required");
        }
        List<Map<String, Object>> out = newbornEpisodeService.findByMother(motherCpid)
                .stream().map(NewbornController::toMap).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(out, correlationId()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> record(@RequestBody Map<String, Object> body) {
        Map<String, Object> payload = new LinkedHashMap<>(body);
        payload.putIfAbsent("delivery_source", NewbornBirthRecordEntity.SOURCE_MATERNITY_FORM);
        NewbornBirthRecordEntity record = newbornEpisodeService.ensureNewbornEpisode(payload);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(toMap(record), correlationId()));
    }

    private static Map<String, Object> toMap(NewbornBirthRecordEntity r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("birth_record_id", r.getBirthRecordId().toString());
        m.put("patient_id", r.getSubjectCpid());
        m.put("subject_cpid", r.getSubjectCpid());
        m.put("mother_cpid", r.getMotherCpid());
        m.put("journey_id", r.getJourneyId());
        m.put("encounter_id", r.getEncounterId());

        m.put("born_at", r.getBornAt() != null ? r.getBornAt().toString() : null);
        m.put("sex_at_birth", r.getSexAtBirth());
        m.put("gestational_age_weeks", r.getGestationalAgeWeeks());
        m.put("gestational_age_basis", r.getGestationalAgeBasis());
        m.put("preterm", r.isPreterm());
        m.put("birth_weight_grams", r.getBirthWeightGrams());
        m.put("low_birth_weight", r.isLowBirthWeight());
        m.put("birth_length_cm", r.getBirthLengthCm());
        m.put("head_circumference_cm", r.getHeadCircumferenceCm());
        m.put("birth_order", r.getBirthOrder());
        m.put("multiple_birth", r.isMultipleBirth());

        m.put("delivery_mode", r.getDeliveryMode());
        m.put("place_of_birth", r.getPlaceOfBirth());
        m.put("birth_attendant", r.getBirthAttendant());
        m.put("birth_outcome", r.getBirthOutcome());

        m.put("apgar_1", r.getApgarOneMinute());
        m.put("apgar_5", r.getApgarFiveMinute());
        m.put("apgar_10", r.getApgarTenMinute());
        m.put("resuscitation", r.getResuscitation());
        m.put("resuscitation_response", r.getResuscitationResponse());

        m.put("skin_to_skin", r.getSkinToSkin());
        m.put("minutes_to_first_breastfeed", r.getMinutesToFirstBreastfeed());
        m.put("vitamin_k_given", r.getVitaminKGiven());
        m.put("eye_prophylaxis_given", r.getEyeProphylaxisGiven());
        m.put("cord_care", r.getCordCare());
        m.put("temperature_celsius", r.getTemperatureCelsius());

        m.put("hiv_exposure", r.getHivExposure());
        m.put("maternal_risk_factors", r.getMaternalRiskFactors());
        m.put("newborn_examination", r.getNewbornExamination());
        m.put("congenital_anomalies", r.getCongenitalAnomalies());
        m.put("birth_trauma", r.getBirthTrauma());
        m.put("danger_signs", r.getDangerSigns());

        m.put("admission_destination", r.getAdmissionDestination());
        m.put("delivery_source", r.getDeliverySource());
        m.put("delivery_source_ref", r.getDeliverySourceRef());
        m.put("ubomi_notification_ref", r.getUbomiNotificationRef());

        m.put("recorded_by", r.getRecordedBy());
        m.put("created_at", r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
        return m;
    }

    private static String correlationId() {
        return TrustContextHolder.require().correlationId().toString();
    }
}
