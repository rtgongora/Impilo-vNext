package zw.gov.mohcc.impilo.inventory.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Request to record an implanted device unit against a patient/episode.
 *
 * <p>Either a UDI or a serial number identifies the physical unit; the unit is
 * created (or reused if the UDI already exists) and linked to the patient.</p>
 */
public record RecordImplantRequest(
        String udi,
        String serialNumber,
        String lotNumber,
        LocalDate expiryDate,
        String itemCode,
        String deviceType,
        String brand,
        String model,
        UUID batchLotId,
        @NotBlank String patientCpid,
        String episodeRef,
        String bodySite,
        String laterality,
        String implantedBy,
        OffsetDateTime implantedAt,
        // Pipeline §14: what a patient carrying this device would be told about it, and when it
        // is next due a review. All optional — a unit recorded without them is still valid; the
        // recall-trace and removal/revision lifecycle do not depend on these being present.
        String patientFacingName,
        String patientFacingDescription,
        Integer expectedLifespanMonths,
        OffsetDateTime nextReviewDueAt
) {}
