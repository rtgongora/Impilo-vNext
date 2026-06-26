package zw.gov.mohcc.impilo.patientsafety.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

/** Add an implicated drug/vaccine to a report. */
public record AddProductRequest(
        String productRole,        // SUSPECT | CONCOMITANT (default SUSPECT)
        String productKind,        // DRUG | VACCINE (default DRUG)
        String productId,          // product-registry ref (optional)
        @NotBlank String productName,
        String manufacturer,
        String batchNumber,
        String lotNumber,
        LocalDate expiryDate,
        String dose,
        String doseUnit,
        String route,
        String frequency,
        String indication,
        LocalDate therapyStartDate,
        LocalDate therapyEndDate,
        String dechallenge,
        String rechallenge,
        String doseNumber,
        LocalDate vaccinationDate,
        String vaccinationSite
) {}
