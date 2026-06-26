package zw.gov.mohcc.impilo.patientsafety.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

/** Add a reaction / adverse event to a report. */
public record AddEventRequest(
        @NotBlank String term,
        String meddraCode,
        String meddraPt,
        LocalDate onsetDate,
        LocalDate endDate,
        String severity,   // MILD | MODERATE | SEVERE
        String outcome     // RECOVERED | RECOVERING | NOT_RECOVERED | RECOVERED_WITH_SEQUELAE | FATAL | UNKNOWN
) {}
