package zw.gov.mohcc.impilo.inpatient.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Resolved current inpatient location for a patient — the active admission's
 * ward and bed with human-readable labels (the admission row itself only carries
 * ward/bed UUIDs). Backs the experience-shell patient-location badge (G053).
 */
public record PatientLocationView(
        UUID admissionRef,
        String status,
        UUID facilityId,
        UUID wardId,
        String wardName,
        UUID bedId,
        String bedNumber,
        String admissionType,
        OffsetDateTime admittedAt) {
}
