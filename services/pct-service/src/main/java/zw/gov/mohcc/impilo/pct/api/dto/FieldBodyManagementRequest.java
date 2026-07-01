package zw.gov.mohcc.impilo.pct.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;

/**
 * Non-facility (field) body management for a death case — safe &amp; dignified burial, family release,
 * funeral-director direct, police handover, or unrecovered. Records the disposition without mandating
 * facility mortuary custody. WHO safe-and-dignified-burial / IFRC safe handling of bodies applies to
 * infectious-risk deaths. A non-police disposition is refused while an open medico-legal / coroner
 * referral is present.
 *
 * @param dispositionType          SAFE_AND_DIGNIFIED_BURIAL|FAMILY_RELEASE|FUNERAL_DIRECTOR|POLICE_HANDOVER|UNRECOVERED
 * @param infectiousRisk           whether the body poses an infection-control risk
 * @param infectiousHazard         EVD|MARBURG|CHOLERA|COVID19|LASSA|UNKNOWN|OTHER
 * @param safeBurialProtocolApplied whether the safe-and-dignified-burial protocol was applied
 * @param ppeUsed                  whether PPE was used by the handling team
 * @param handlingTeam             identifier of the handling / burial team
 * @param familyPresent            whether family were present / involved
 * @param religiousRitesObserved   whether religious / cultural rites were observed
 * @param releasedToName           name of the person the body was released to (when a release)
 * @param releasedToRelationship   relationship of the recipient to the deceased
 * @param location                 where the disposition took place
 * @param occurredAt               when the disposition occurred
 * @param notes                    free-text notes
 */
public record FieldBodyManagementRequest(
        @NotBlank String dispositionType,
        boolean infectiousRisk,
        String infectiousHazard,
        boolean safeBurialProtocolApplied,
        Boolean ppeUsed,
        String handlingTeam,
        Boolean familyPresent,
        Boolean religiousRitesObserved,
        String releasedToName,
        String releasedToRelationship,
        String location,
        OffsetDateTime occurredAt,
        String notes
) {}
