package zw.gov.mohcc.impilo.inventory.api.dto;

import zw.gov.mohcc.impilo.inventory.persistence.entity.ImplantUnitEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.PatientImplantEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A traced implant: the patient/episode a physical unit was implanted in,
 * enriched with the unit's UDI/serial/lot for recall handling.
 */
public record ImplantTraceDto(
        UUID patientImplantId,
        String patientCpid,
        String episodeRef,
        String udi,
        String serialNumber,
        String lotNumber,
        String bodySite,
        String laterality,
        OffsetDateTime implantedAt,
        // §14: status is included rather than filtered out of trace/recall results — a recalled
        // unit that has already been removed is still meaningful history, not something to hide.
        String status,
        String patientFacingName,
        String patientFacingDescription,
        Integer expectedLifespanMonths,
        OffsetDateTime nextReviewDueAt,
        OffsetDateTime removedAt,
        String removedBy,
        String removalReason,
        UUID revisionOfPatientImplantId
) {
    public static ImplantTraceDto from(PatientImplantEntity link, ImplantUnitEntity unit) {
        return new ImplantTraceDto(
                link.getPatientImplantId(),
                link.getPatientCpid(),
                link.getEpisodeRef(),
                unit != null ? unit.getUdi() : null,
                unit != null ? unit.getSerialNumber() : null,
                unit != null ? unit.getLotNumber() : null,
                link.getBodySite(),
                link.getLaterality(),
                link.getImplantedAt(),
                link.getStatus(),
                link.getPatientFacingName(),
                link.getPatientFacingDescription(),
                link.getExpectedLifespanMonths(),
                link.getNextReviewDueAt(),
                link.getRemovedAt(),
                link.getRemovedBy(),
                link.getRemovalReason(),
                link.getRevisionOfPatientImplantId());
    }
}
