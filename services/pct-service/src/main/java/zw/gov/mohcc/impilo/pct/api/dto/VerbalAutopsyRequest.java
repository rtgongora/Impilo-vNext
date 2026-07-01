package zw.gov.mohcc.impilo.pct.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Verbal-autopsy interview for a death outside a health facility (WHO VA standard). Yields a
 * <em>probable</em> cause — it is NOT a medical certification and only ever sets the case's
 * {@code cause_of_death_basis} to {@code VERBAL_AUTOPSY_PROBABLE}. If {@code redFlags} are present the
 * case is routed to the medico-legal pathway and NO probable cause is assigned.
 *
 * @param instrument              WHO_VA_2022|WHO_VA_2016|OTHER (defaults WHO_VA_2022)
 * @param intervieweeRelationship relationship of the interviewee to the deceased
 * @param interviewedAt           when the interview occurred
 * @param narrative               open-history narrative
 * @param probableImmediateCause  probable immediate cause
 * @param probableUnderlyingCause probable underlying cause (the single condition for mortality stats)
 * @param probableCauseCode       ICD-11 / VA cause-list code
 * @param causeAssignmentMethod   PHYSICIAN_REVIEW|COMPUTER_CODED_INTERVA|COMPUTER_CODED_INSILICOVA|EXPERT_PANEL
 * @param redFlags                medico-legal red flags surfaced (routes to coroner instead of probable cause)
 * @param complete                true → COMPLETED (assigns probable cause); false → DRAFT
 */
public record VerbalAutopsyRequest(
        String instrument,
        String intervieweeRelationship,
        OffsetDateTime interviewedAt,
        String narrative,
        String probableImmediateCause,
        String probableUnderlyingCause,
        String probableCauseCode,
        String causeAssignmentMethod,
        List<String> redFlags,
        boolean complete
) {}
