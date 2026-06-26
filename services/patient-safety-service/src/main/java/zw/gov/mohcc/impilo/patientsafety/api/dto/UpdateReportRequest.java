package zw.gov.mohcc.impilo.patientsafety.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Patch a DRAFT or NEEDS_MORE_INFORMATION report. All fields optional; nulls leave values unchanged. */
public record UpdateReportRequest(
        String reporterName,
        String reporterRole,
        String reporterFacilityId,
        String reporterPhone,
        String reporterEmail,
        String subjectCpid,
        Boolean subjectIsSelf,
        String subjectInitials,
        String subjectSex,
        LocalDate subjectDob,
        Integer subjectAgeValue,
        String subjectAgeUnit,
        BigDecimal subjectWeightKg,
        String encounterId,
        String narrative,
        LocalDate onsetDate,
        Boolean serious,
        List<String> seriousnessReasons,
        String seriousnessOtherText,
        String formPackKey,
        String formPackVersion,
        Map<String, Object> formData
) {}
