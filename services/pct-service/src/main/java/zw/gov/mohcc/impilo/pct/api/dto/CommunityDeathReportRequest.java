package zw.gov.mohcc.impilo.pct.api.dto;

import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

/**
 * Report of a community / field death by a NON-confirming actor (CHW, family, surveillance team,
 * community leader) — an unverified notification, distinct from a provider confirmation. The case is
 * opened in status {@code REPORTED} with {@code cause_of_death_basis = UNKNOWN_UNCERTIFIED}; it does
 * NOT create mortuary custody and does NOT assert a cause. A red-flag manner still routes the case to
 * the medico-legal pathway (basis becomes MEDICO_LEGAL_PENDING). The body may never come to a facility.
 *
 * @param deathDatetime          date/time (best estimate) of death
 * @param sourceContext          origin: COMMUNITY_HOME|COMMUNITY_FIELD|OUTREACH_FIELD_OPS|DISASTER_SITE|
 *                               ISOLATION_FIELD_SITE|CUSTODY|UNKNOWN_LOCATION (defaults COMMUNITY_FIELD)
 * @param placeOfDeathContext    free-form place context (e.g. COMMUNITY_HOME)
 * @param placeOfDeathLocation   free-text location (village / site)
 * @param deceasedCpid           deceased identity (CPID) when known
 * @param deceasedIdentityStatus KNOWN|UNKNOWN|TEMPORARY
 * @param suspectedManner        NATURAL|ACCIDENT|HOMICIDE|SUICIDE|UNDETERMINED|PENDING (drives red-flag screen)
 * @param inCustody              whether the deceased was in custody / detention
 * @param bodyDispositionContext where the body is (defaults NOT_BROUGHT_TO_FACILITY)
 * @param reporterName           name of the reporting party
 * @param reporterRole           CHW|FAMILY|SURVEILLANCE|COMMUNITY_LEADER|POLICE|OTHER
 * @param reporterContact        reporter contact detail
 * @param facilityId             catchment / receiving facility (defaults to trust-context facility)
 */
public record CommunityDeathReportRequest(
        @NotNull OffsetDateTime deathDatetime,
        String sourceContext,
        String placeOfDeathContext,
        String placeOfDeathLocation,
        String deceasedCpid,
        String deceasedIdentityStatus,
        String suspectedManner,
        Boolean inCustody,
        String bodyDispositionContext,
        String reporterName,
        String reporterRole,
        String reporterContact,
        String facilityId
) {}
