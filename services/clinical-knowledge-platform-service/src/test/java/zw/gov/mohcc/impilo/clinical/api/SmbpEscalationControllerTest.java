package zw.gov.mohcc.impilo.clinical.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import zw.gov.mohcc.impilo.clinical.maternal.MaternalDangerSignService;
import zw.gov.mohcc.impilo.clinical.maternal.SmbpEscalationService;
import zw.gov.mohcc.impilo.clinical.rules.tabular.RuleContentLoader;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The SMBP series verdict over HTTP, tested through the wire shape a client actually sends.
 *
 * <p>The engine's own behaviour is proven in {@code SmbpEscalationTest}. What can still break here is
 * the surface: a verdict a client can misread, a summary field dropped so a green state can be
 * constructed from an incomplete answer, or a boxed null coerced on the way in. Those are the failures
 * this class exists to catch, and each of them looks like a working endpoint.
 */
class SmbpEscalationControllerTest {

    private final ObjectMapper json = new ObjectMapper();
    private final SmbpEscalationController controller = new SmbpEscalationController(
            new SmbpEscalationService(new MaternalDangerSignService(new RuleContentLoader(new ObjectMapper()))));

    @Test
    @DisplayName("TOO FEW READINGS IS INSUFFICIENT_DATA, NEVER CONTROLLED")
    void aQuietShortSeriesIsNotReassurance() throws Exception {
        var response = controller.assess(request("""
                {"readings": [
                   {"systolic": 118, "diastolic": 74, "firstOfSession": true},
                   {"systolic": 116, "diastolic": 72}
                 ],
                 "dangerSignPresent": false}
                """));

        Map<String, Object> data = data(response);
        // A quiet week of two readings is not evidence her blood pressure is fine, and telling her it
        // is is how a woman stops monitoring right before it matters.
        assertThat(data.get("verdict")).isEqualTo("INSUFFICIENT_DATA");
        assertThat(summary(data).get("sufficient")).isEqualTo(false);
        assertThat((String) data.get("message")).contains("Not enough readings");
    }

    @Test
    @DisplayName("A SEVERE FIRST-OF-SESSION READING SURVIVES ITS OWN DISCARD FROM THE MEAN")
    void aSevereDiscardedReadingIsStillSevere() throws Exception {
        var response = controller.assess(request("""
                {"readings": [
                   {"systolic": 168, "diastolic": 114, "firstOfSession": true},
                   {"systolic": 120, "diastolic": 76}
                 ],
                 "dangerSignPresent": false}
                """));

        Map<String, Object> data = data(response);
        // WHO protocol drops the first reading of a sitting from the MEAN as a rest artefact. An
        // emergency is not a rest artefact — averaging first and checking the average is exactly how a
        // severe reading disappears into a normal week.
        assertThat(summary(data).get("anySevere")).isEqualTo(true);
        assertThat(summary(data).get("maxSystolic")).isEqualTo(168);
        assertThat(data.get("verdict")).isEqualTo("URGENT");
    }

    @Test
    @DisplayName("a null dangerSignPresent stays unasked — never coerced to a denial")
    void anUnaskedDangerSignIsNotADenial() throws Exception {
        var withNull = controller.assess(request("""
                {"readings": [{"systolic": 122, "diastolic": 78}]}
                """));
        var withFalse = controller.assess(request("""
                {"readings": [{"systolic": 122, "diastolic": 78}], "dangerSignPresent": false}
                """));

        // Asserting on the woman's behalf that she has no danger signs is the most dangerous default
        // available on this pathway. Both must reach a verdict; neither may be an alert manufactured
        // from the absence of an answer.
        assertThat(data(withNull).get("verdict")).isEqualTo("INSUFFICIENT_DATA");
        assertThat(data(withFalse).get("verdict")).isEqualTo("INSUFFICIENT_DATA");
        assertThat(data(withNull).get("alerts")).asInstanceOf(
                org.assertj.core.api.InstanceOfAssertFactories.LIST).isEmpty();
    }

    @Test
    @DisplayName("an empty series answers INSUFFICIENT_DATA with a null mean, not a mean of zero")
    void noReadingsMeansNoMean() throws Exception {
        var response = controller.assess(request("""
                {"readings": []}
                """));

        Map<String, Object> summary = summary(data(response));
        assertThat(data(response).get("verdict")).isEqualTo("INSUFFICIENT_DATA");
        // A mean of 0/0 is the most reassuring blood pressure ever recorded.
        assertThat(summary.get("meanSystolic")).isNull();
        assertThat(summary.get("meanDiastolic")).isNull();
        assertThat(summary.get("readingCount")).isEqualTo(0);
    }

    @Test
    @DisplayName("a reading missing one value is dropped, not zero-filled into the mean")
    void aHalfReadingIsNotHalfAReading() throws Exception {
        var response = controller.assess(request("""
                {"readings": [
                   {"systolic": 130, "diastolic": 84},
                   {"systolic": 128},
                   {"diastolic": 80}
                 ]}
                """));

        // A systolic of 0 would sit in the mean as the calmest reading of the week.
        assertThat(summary(data(response)).get("readingCount")).isEqualTo(1);
        assertThat(summary(data(response)).get("meanSystolic")).isEqualTo(130);
    }

    @Test
    @DisplayName("the whole summary is carried, so a client cannot build a green state from a partial read")
    void theSummaryIsCompleteAndProvenanceTravels() throws Exception {
        var response = controller.assess(request("""
                {"readings": [{"systolic": 120, "diastolic": 78}]}
                """));

        Map<String, Object> data = data(response);
        assertThat(summary(data)).containsKeys("readingCount", "meanCount", "meanSystolic",
                "meanDiastolic", "maxSystolic", "maxDiastolic", "anySevere", "elevatedMean",
                "sufficient");
        // A consumer reading only the verdict would treat an unratified engineering seed as national
        // clinical policy — the same reason PolicyParameterController carries these.
        assertThat(data).containsKeys("contentVersion", "approvalStatus");
    }

    @Test
    @DisplayName("overriding one threshold does not silently reset the others to the seed")
    void aPartialThresholdOverrideKeepsTheRest() throws Exception {
        // Only the minimum-readings count is overridden. If the record fell back wholesale, the severe
        // cut-offs would silently become whatever this version happens to seed.
        var response = controller.assess(request("""
                {"readings": [
                   {"systolic": 165, "diastolic": 112},
                   {"systolic": 120, "diastolic": 78}
                 ],
                 "thresholds": {"minimumReadingsToConclude": 2}}
                """));

        assertThat(summary(data(response)).get("anySevere"))
                .as("the seeded 160/110 severe cut-off must survive an unrelated override")
                .isEqualTo(true);
    }

    private SmbpEscalationController.AssessRequest request(String body) throws Exception {
        return json.readValue(body, SmbpEscalationController.AssessRequest.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(ResponseEntity<Map<String, Object>> response) {
        return (Map<String, Object>) response.getBody().get("data");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> summary(Map<String, Object> data) {
        return (Map<String, Object>) data.get("summary");
    }
}
