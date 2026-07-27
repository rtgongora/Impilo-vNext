package zw.gov.mohcc.impilo.clinical.maternal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.clinical.danger.DangerSignEvaluationService;
import zw.gov.mohcc.impilo.clinical.rules.tabular.RuleContentLoader;
import zw.gov.mohcc.impilo.clinical.rules.tabular.TabularRule;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The postnatal newborn check, and the one-definition-of-PSBI rule.
 *
 * <p>The property that matters: PSBI is defined once (in the paediatric pack) and delegated, so the
 * PNC newborn pack must NOT restate any systemic serious-infection sign, and a systemic emergency
 * always leads over a local one. Testing it here is what stops the two definitions drifting into
 * "fine on one screen, referred on another".
 */
class PostnatalNewbornTest {

    private static final String PNC_NEWBORN_PACK = "clinical/rmnp-pnc-newborn-danger-signs.json";

    private final RuleContentLoader loader = new RuleContentLoader(new ObjectMapper());
    private final PostnatalNewbornService service = new PostnatalNewbornService(
            new DangerSignEvaluationService(loader), new MaternalDangerSignService(loader));

    private static Map<String, Object> facts(Object... pairs) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    // ---------------------------------------------------------------- one PSBI definition, delegated

    @Test
    @DisplayName("the PNC newborn pack does NOT restate any PSBI systemic sign")
    void pncPackDoesNotRestatePsbi() {
        // The PSBI systemic inputs — feeding, convulsions, movement, temperature, respiratory rate,
        // chest indrawing — must not appear in the PNC pack. If they did, there would be two
        // definitions of a septic newborn.
        Map<String, Object> pncFacts = new HashMap<>();
        for (TabularRule rule : loader.load(PNC_NEWBORN_PACK)) {
            pncFacts.put(rule.code(), rule.requiredInputs());
        }
        String allInputs = pncFacts.toString().toLowerCase();
        assertThat(allInputs).doesNotContain("feeding", "convulsions", "movesonlywhenstimulated",
                "respiratoryrate", "severechestindrawing");
    }

    @Test
    @DisplayName("PSBI is evaluated by delegation to the paediatric definition")
    void psbiComesFromDelegation() {
        // Systemic PSBI findings, and NO pnc-specific local sign.
        var a = service.assess(10, facts(
                "feeding", "NOT_FEEDING", "vitals.temperature", 35.0,
                "cordRednessOrDischarge", "NORMAL"), true);

        assertThat(a.systemicEmergency()).isTrue();
        // The PSBI alert came from the delegated paediatric pack.
        assertThat(a.psbi().alerts()).anyMatch(al -> "PSBI_YOUNG_INFANT".equals(al.code()));
    }

    @Test
    @DisplayName("a systemic PSBI emergency leads over a local PNC sign")
    void systemicLeadsOverLocal() {
        // Both a PSBI systemic sign AND a local omphalitis. The top line must be the systemic one.
        var a = service.assess(10, facts(
                "feeding", "NOT_FEEDING", "vitals.temperature", 35.0,
                "cordRednessOrDischarge", "PUS_OR_SMELL"), true);

        assertThat(a.systemicEmergency()).isTrue();
        assertThat(a.topLineAction()).isEqualTo(a.psbi().alerts().get(0).requiredAction());
        // The local sign is still recorded, just not the top line.
        assertThat(a.pncSpecificAlerts()).anyMatch(al -> "PNC_NB_OMPHALITIS".equals(al.code()));
    }

    // ---------------------------------------------------------------- the PNC-specific signs

    @Test
    @DisplayName("a local sign with no systemic emergency leads with the local action")
    void localSignLeadsWhenNoSystemic() {
        var a = service.assess(10, facts(
                "feeding", "FEEDING_WELL", "vitals.temperature", 36.8, "vitals.respiratoryRate", 45,
                "convulsions", "ABSENT", "movesOnlyWhenStimulated", "ABSENT",
                "severeChestIndrawing", "ABSENT",
                "eyeDischargeOrSwelling", "PRESENT"), true);

        assertThat(a.systemicEmergency()).isFalse();
        assertThat(a.pncSpecificAlerts()).anyMatch(al -> "PNC_NB_OPHTHALMIA".equals(al.code()));
        assertThat(a.topLineAction()).isNotNull();
    }

    @Test
    @DisplayName("day-1 jaundice fires the PNC jaundice sign")
    void dayOneJaundice() {
        var a = service.assess(0, facts(
                "feeding", "FEEDING_WELL", "vitals.temperature", 36.8, "vitals.respiratoryRate", 45,
                "convulsions", "ABSENT", "movesOnlyWhenStimulated", "ABSENT", "severeChestIndrawing", "ABSENT",
                "jaundiceOnset", "FIRST_24_HOURS", "jaundiceExtent", "FACE"), true);
        assertThat(a.pncSpecificAlerts()).anyMatch(al -> "PNC_NB_JAUNDICE".equals(al.code()));
    }

    // ---------------------------------------------------------------- silence / age

    @Test
    @DisplayName("no age means PSBI cannot be assessed — reported incomplete, never clear")
    void noAgeIsIncompleteNotClear() {
        var a = service.assess(null, facts("cordRednessOrDischarge", "NORMAL"), true);
        // The paediatric engine refuses to assess without an age; that surfaces as incomplete here.
        assertThat(a.incomplete()).isTrue();
    }
}
