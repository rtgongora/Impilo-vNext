package zw.gov.mohcc.impilo.pct.api.dto;

import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

/**
 * Request to confirm a death and open / update a death case (WS#8). Captures the
 * confirmation, place, context, resuscitation, presence at death, and identity status.
 * The confirming clinician's role is taken from the trust context; an unauthorised role
 * is rejected by the workflow.
 *
 * @param journeyId            journey the death occurred in (may be null for community / brought-in-dead)
 * @param deathDatetime        date/time of death
 * @param placeOfDeathContext  INPATIENT|ED|THEATRE|COMMUNITY|BROUGHT_IN_DEAD|IN_TRANSIT|OTHER
 * @param placeOfDeathLocation free-text location (ward/site)
 * @param resuscitationAttempted whether resuscitation was attempted
 * @param presentAtDeath       who was present at death
 * @param deceasedCpid         deceased identity (CPID) when known
 * @param deceasedIdentityStatus KNOWN|UNKNOWN|TEMPORARY
 * @param admittedAt           admission time (for the within-24h medico-legal trigger), nullable
 * @param suspectedManner      NATURAL|ACCIDENT|HOMICIDE|SUICIDE|UNDETERMINED|PENDING, nullable
 * @param inCustody            whether the deceased was in custody / detention
 * @param facilityId           facility id (defaults to trust-context facility when null)
 * @param sourceContext        FACILITY|BROUGHT_IN_DEAD|COMMUNITY_HOME|COMMUNITY_FIELD|OUTREACH_FIELD_OPS|
 *                             DISASTER_SITE|ISOLATION_FIELD_SITE|CUSTODY|UNKNOWN_LOCATION|
 *                             INPATIENT_DISCHARGE|EMERGENCY_INCIDENT
 * @param sourceRef            originating reference (discharge case / emergency incident id)
 * @param bodyDispositionContext where the body goes; a case no longer requires facility mortuary custody:
 *                             BROUGHT_TO_FACILITY|NOT_BROUGHT_TO_FACILITY|FIELD_SAFE_BURIAL|COMMUNITY_RELEASE|
 *                             POLICE_CUSTODY|FUNERAL_DIRECTOR_DIRECT|TRANSFERRED_TO_MORTUARY|TRANSFERRED_TO_POST_MORTEM_SITE
 */
public record ConfirmDeathRequest(
        String journeyId,
        @NotNull OffsetDateTime deathDatetime,
        String placeOfDeathContext,
        String placeOfDeathLocation,
        Boolean resuscitationAttempted,
        String presentAtDeath,
        String deceasedCpid,
        String deceasedIdentityStatus,
        OffsetDateTime admittedAt,
        String suspectedManner,
        Boolean inCustody,
        String facilityId,
        String sourceContext,
        String sourceRef,
        String bodyDispositionContext
) {}
