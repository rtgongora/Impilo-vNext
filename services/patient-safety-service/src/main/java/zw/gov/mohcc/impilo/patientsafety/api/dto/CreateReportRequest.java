package zw.gov.mohcc.impilo.patientsafety.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Create (draft) a safety report. Only {@code reportType} and {@code reporterKind} are required so a
 * provider can open a draft from clinical context and fill the rest progressively.
 */
public record CreateReportRequest(
        @NotNull @Pattern(regexp = "ADR|AEFI") String reportType,
        @NotNull @Pattern(regexp = "PROVIDER|CITIZEN|CAREGIVER") String reporterKind,
        String sourceContext,
        // reporter
        String reporterName,
        String reporterRole,
        String reporterFacilityId,
        String reporterPhone,
        String reporterEmail,
        // subject
        String subjectCpid,
        Boolean subjectIsSelf,
        String subjectInitials,
        String subjectSex,
        LocalDate subjectDob,
        Integer subjectAgeValue,
        String subjectAgeUnit,
        BigDecimal subjectWeightKg,
        // clinical linkage + narrative
        String encounterId,
        String narrative,
        LocalDate onsetDate,
        // seriousness
        Boolean serious,
        List<String> seriousnessReasons,
        String seriousnessOtherText,
        // form-pack provenance + captured data
        String formPackKey,
        String formPackVersion,
        Map<String, Object> formData,
        // optional nested children at create time
        List<AddProductRequest> products,
        List<AddEventRequest> events
) {}
