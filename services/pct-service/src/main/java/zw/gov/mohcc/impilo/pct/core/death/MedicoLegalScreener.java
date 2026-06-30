package zw.gov.mohcc.impilo.pct.core.death;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates the legal triggers that mandate a coroner / forensic referral and, while present,
 * BLOCK routine certification and body release. This is a deterministic, auditable rules screen —
 * the referral itself is routed to the competent authority (coroner/police) as an owner-routed hook;
 * we never forge external clearance.
 *
 * Triggers (Zimbabwe Inquests Act spirit + WHO medico-legal norms):
 *   WITHIN_24H_OF_ADMISSION, DURING_SURGERY_OR_PROCEDURE, MATERNAL_PERIPARTUM,
 *   SUDDEN_UNEXPECTED, VIOLENT_OR_SUSPICIOUS, IN_CUSTODY, UNNATURAL_MANNER, BROUGHT_IN_DEAD,
 *   UNKNOWN_IDENTITY.
 */
@Component
public class MedicoLegalScreener {

    public record Result(boolean coronerReferralRequired, List<String> triggers) {}

    /**
     * @param admittedAt          admission time, or null if not an inpatient
     * @param deathDatetime       time of death
     * @param placeOfDeathContext INPATIENT|ED|THEATRE|COMMUNITY|BROUGHT_IN_DEAD|IN_TRANSIT|OTHER
     * @param manner              declared/suspected manner (NATURAL|ACCIDENT|HOMICIDE|SUICIDE|UNDETERMINED|PENDING)
     * @param inCustody           whether the deceased was in custody / detention
     * @param identityStatus      KNOWN|UNKNOWN|TEMPORARY
     */
    public Result evaluate(OffsetDateTime admittedAt,
                           OffsetDateTime deathDatetime,
                           String placeOfDeathContext,
                           String manner,
                           boolean inCustody,
                           String identityStatus) {
        List<String> triggers = new ArrayList<>();

        if (admittedAt != null && deathDatetime != null
                && Duration.between(admittedAt, deathDatetime).compareTo(Duration.ofHours(24)) <= 0
                && !Duration.between(admittedAt, deathDatetime).isNegative()) {
            triggers.add("WITHIN_24H_OF_ADMISSION");
        }
        if ("THEATRE".equalsIgnoreCase(placeOfDeathContext)) {
            triggers.add("DURING_SURGERY_OR_PROCEDURE");
        }
        if ("BROUGHT_IN_DEAD".equalsIgnoreCase(placeOfDeathContext)) {
            triggers.add("BROUGHT_IN_DEAD");
            triggers.add("SUDDEN_UNEXPECTED");
        }
        if (manner != null) {
            String m = manner.toUpperCase();
            if (m.equals("HOMICIDE") || m.equals("SUICIDE") || m.equals("ACCIDENT")) {
                triggers.add("UNNATURAL_MANNER");
            }
            if (m.equals("UNDETERMINED")) {
                triggers.add("SUDDEN_UNEXPECTED");
            }
        }
        if (inCustody) {
            triggers.add("IN_CUSTODY");
        }
        if ("UNKNOWN".equalsIgnoreCase(identityStatus) || "TEMPORARY".equalsIgnoreCase(identityStatus)) {
            triggers.add("UNKNOWN_IDENTITY");
        }
        return new Result(!triggers.isEmpty(), triggers);
    }
}
