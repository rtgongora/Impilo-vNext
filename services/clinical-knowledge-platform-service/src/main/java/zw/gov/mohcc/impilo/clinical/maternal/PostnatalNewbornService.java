package zw.gov.mohcc.impilo.clinical.maternal;

import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.clinical.danger.DangerSignEvaluationService;
import zw.gov.mohcc.impilo.clinical.danger.DangerSignEvaluationService.DangerSignAssessment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The postnatal newborn check — the PNC-specific local signs, with PSBI delegated to the single
 * platform definition rather than restated.
 *
 * <p><b>One definition of a sick newborn.</b> Possible serious bacterial infection (PSBI) — not
 * feeding, convulsions, movement only when stimulated, fever or hypothermia, fast breathing, severe
 * chest indrawing — is defined once, in the paediatric danger-sign pack, and evaluated here by
 * delegating to {@link DangerSignEvaluationService}. The PNC newborn pack adds only the signs a
 * sick-child visit is not built around: the cord, the eyes, jaundice, the skin. If PSBI were
 * restated here it would drift from the paediatric definition, and a mother would be told her baby
 * is fine on one screen and referred on another — three definitions of a septic newborn is exactly
 * the failure this composition prevents.
 *
 * <p>The PSBI verdict always leads: a systemic emergency is never buried beneath a local one.
 */
@Service
public class PostnatalNewbornService {

    static final String PNC_NEWBORN_PACK = "clinical/rmnp-pnc-newborn-danger-signs.json";

    private final DangerSignEvaluationService paediatricDangerSigns;
    private final MaternalDangerSignService maternalDangerSigns;

    public PostnatalNewbornService(DangerSignEvaluationService paediatricDangerSigns,
                                   MaternalDangerSignService maternalDangerSigns) {
        this.paediatricDangerSigns = paediatricDangerSigns;
        this.maternalDangerSigns = maternalDangerSigns;
    }

    /**
     * @param psbi               the delegated PSBI assessment from the single paediatric definition
     * @param pncSpecificAlerts  the PNC-specific local-infection alerts
     * @param topLineAction      the one action to lead with — the PSBI action whenever PSBI fired,
     *                           because a systemic emergency outranks any local sign
     * @param systemicEmergency  true when the delegated PSBI check found a serious bacterial infection
     * @param incomplete         true when either layer could not be completed
     */
    public record Assessment(
            DangerSignAssessment psbi,
            List<MaternalDangerSignService.Alert> pncSpecificAlerts,
            String topLineAction,
            boolean systemicEmergency,
            boolean incomplete,
            String note) {
    }

    /**
     * @param ageDays the newborn's age; required, because PSBI is age-scoped and applying the wrong
     *                set is the paediatric engine's own guarded failure
     * @param facts   the newborn findings — both the PSBI inputs and the PNC-specific ones
     * @param screeningComplete whether the caller asserts the PNC screening was finished
     */
    public Assessment assess(Integer ageDays, Map<String, Object> facts, boolean screeningComplete) {
        // Delegate PSBI to the one definition. No restating.
        DangerSignAssessment psbi = paediatricDangerSigns.evaluate(ageDays, facts);

        // The PNC-specific local signs run on the maternal danger-sign engine over the PNC newborn
        // pack (no age gate needed: the pack applies to the postnatal newborn).
        MaternalDangerSignService.Assessment pnc =
                maternalDangerSigns.evaluate(PNC_NEWBORN_PACK, facts, screeningComplete);

        boolean systemic = psbi.alerts().stream()
                .anyMatch(a -> "PSBI_YOUNG_INFANT".equals(a.code())
                        || "CRITICAL".equalsIgnoreCase(a.severity()));

        String topLine;
        if (systemic && !psbi.alerts().isEmpty()) {
            // A systemic emergency leads, always, over any local sign.
            topLine = psbi.alerts().get(0).requiredAction();
        } else if (!pnc.alerts().isEmpty()) {
            topLine = pnc.alerts().get(0).requiredAction();
        } else if (!psbi.alerts().isEmpty()) {
            topLine = psbi.alerts().get(0).requiredAction();
        } else {
            topLine = null;
        }

        boolean incomplete = psbi.incomplete() || pnc.incomplete();
        return new Assessment(psbi, pnc.alerts(), topLine, systemic, incomplete,
                note(systemic, pnc.alerts(), incomplete));
    }

    private static String note(boolean systemic, List<MaternalDangerSignService.Alert> pncAlerts,
                               boolean incomplete) {
        List<String> parts = new ArrayList<>();
        if (systemic) {
            parts.add("A systemic danger sign (PSBI) was found — it leads, before any local sign.");
        }
        if (!pncAlerts.isEmpty()) {
            parts.add(pncAlerts.size() + " PNC-specific local sign(s) also present.");
        }
        if (incomplete) {
            parts.add("The assessment is incomplete — treat gaps as unassessed, not as clear.");
        }
        if (parts.isEmpty()) {
            return "No newborn danger sign found on the recorded findings.";
        }
        return String.join(" ", parts);
    }
}
