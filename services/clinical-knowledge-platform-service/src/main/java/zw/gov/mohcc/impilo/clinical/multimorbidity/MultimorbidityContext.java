package zw.gov.mohcc.impilo.clinical.multimorbidity;

import java.time.LocalDate;
import java.util.List;

/**
 * Everything the multimorbidity view reasons over, as the calling system was able to obtain it.
 *
 * <p><strong>Null means "not obtained"; an empty list means "there are none".</strong> The whole
 * value of this view is that it looks across sources, and looking across sources means some of them
 * will be unavailable. A view that renders an unavailable appointment book as "no appointment
 * burden" would report the absence of a query as the absence of a problem — which is worse than not
 * offering the view at all, because it carries the authority of having checked.</p>
 *
 * @param conditions     the active problem list, or null when it could not be read
 * @param medicationCodes current medicines as codes or names, or null when OROS could not be asked
 * @param appointments   scheduled attendances, or null when the booking system could not be read
 * @param investigations recent investigations, or null when the order history could not be read
 * @param egfr           most recent eGFR, or null when none is available
 * @param hepaticImpairment NONE/MILD/MODERATE/SEVERE, or null when no governed instrument has scored it
 * @param functionalLimitations documented functional limitations, or null when not assessed
 * @param patientPriorities what the patient said matters to them, or null when never asked
 * @param careTeam       who is responsible for what, or null when not recorded
 * @param egfrSource     where the eGFR came from, when that is worth stating — in practice, that
 *                       this platform derived it from a creatinine rather than a laboratory
 *                       reporting it. Null when a laboratory value was supplied or none exists. A
 *                       clinician reading a filtration rate is entitled to know which it is
 */
public record MultimorbidityContext(
        List<Condition> conditions,
        List<String> medicationCodes,
        List<Appointment> appointments,
        List<Investigation> investigations,
        Double egfr,
        String hepaticImpairment,
        List<String> functionalLimitations,
        List<String> patientPriorities,
        List<CareTeamMember> careTeam,
        String egfrSource) {

    /** A context whose eGFR, if any, came from a laboratory rather than from this platform. */
    public MultimorbidityContext(
            List<Condition> conditions, List<String> medicationCodes, List<Appointment> appointments,
            List<Investigation> investigations, Double egfr, String hepaticImpairment,
            List<String> functionalLimitations, List<String> patientPriorities,
            List<CareTeamMember> careTeam) {
        this(conditions, medicationCodes, appointments, investigations, egfr, hepaticImpairment,
                functionalLimitations, patientPriorities, careTeam, null);
    }

    /** One active problem. {@code longTerm} is the caller's assertion, not an inference from the code. */
    public record Condition(String code, String display, boolean longTerm) {
    }

    /** A scheduled attendance. */
    public record Appointment(LocalDate date, String clinic, String reason) {
    }

    /** A performed or ordered investigation. */
    public record Investigation(String code, LocalDate date, String orderedBy) {
    }

    /** Who is responsible for which condition. */
    public record CareTeamMember(String role, String name, String responsibleFor) {
    }

    /** An empty context — every input unobtained. Used when a caller supplies nothing. */
    public static MultimorbidityContext empty() {
        return new MultimorbidityContext(null, null, null, null, null, null, null, null, null);
    }
}
