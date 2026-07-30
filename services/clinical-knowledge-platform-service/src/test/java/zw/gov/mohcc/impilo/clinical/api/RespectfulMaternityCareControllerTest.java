package zw.gov.mohcc.impilo.clinical.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import zw.gov.mohcc.impilo.clinical.maternal.RespectfulMaternityCareService;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The RMC surface: snake_case out, and every governance fact a consumer needs carried on the payload. */
class RespectfulMaternityCareControllerTest {

    private final RespectfulMaternityCareController controller =
            new RespectfulMaternityCareController(
                    new RespectfulMaternityCareService(new ObjectMapper()));

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(ResponseEntity<Map<String, Object>> r) {
        return (Map<String, Object>) r.getBody().get("data");
    }

    @Test
    @DisplayName("the instrument is served with its prompts, scale and reverse-score flags")
    void instrumentIsServed() {
        var r = controller.instrument();

        assertThat(r.getStatusCode().value()).isEqualTo(200);
        assertThat(data(r).get("instrument_id")).isEqualTo("RESPECTFUL_MATERNITY_CARE");
        assertThat(data(r).get("rating_domain")).isEqualTo("CLIENT_EXPERIENCE");

        List<?> measures = (List<?>) data(r).get("measures");
        assertThat(measures).hasSize(10);
        // The flag is exposed so a renderer can show a negatively-worded item in its own voice, and so
        // a client can verify the normalisation it was given rather than trusting it.
        assertThat(measures.toString()).contains("reverse_scored");
        assertThat(measures.toString()).contains("mistreatment_category");
    }

    @Test
    @DisplayName("the approval status and the firewall are on the payload, not only in a document")
    void governanceTravelsOnThePayload() {
        var r = controller.instrument();

        // An unratified seed read as national policy is the specific confusion this prevents.
        assertThat(data(r).get("approval_status")).isEqualTo("ENGINEERING_SEED");
        assertThat(String.valueOf(data(r).get("regulation_firewall"))).contains("NEVER mutates a licence");
        assertThat(String.valueOf(data(r).get("anonymous_by_default"))).contains("DEFAULT");
    }

    @Test
    @DisplayName("normalise returns both the stored and the raw value, so the flip is checkable")
    void normaliseCarriesBothValues() {
        Map<String, BigDecimal> answers = new LinkedHashMap<>();
        answers.put("never_left_alone", BigDecimal.valueOf(5));

        var r = controller.normalise(new RespectfulMaternityCareController.NormaliseRequest(answers));

        List<?> scores = (List<?>) data(r).get("scores");
        assertThat(scores).hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> row = (Map<String, Object>) scores.get(0);
        // She was always left alone: the worst answer, stored at the bottom of the scale.
        assertThat((BigDecimal) row.get("score")).isEqualByComparingTo(BigDecimal.ONE);
        assertThat((BigDecimal) row.get("raw_score")).isEqualByComparingTo(BigDecimal.valueOf(5));
        assertThat(row.get("reverse_scored")).isEqualTo(true);
        assertThat(row.get("domain")).isEqualTo("CLIENT_EXPERIENCE");
    }

    @Test
    @DisplayName("unasked measures are listed by name on the response")
    void notAskedIsNamed() {
        Map<String, BigDecimal> answers = new LinkedHashMap<>();
        answers.put("respect_dignity", BigDecimal.valueOf(4));

        var r = controller.normalise(new RespectfulMaternityCareController.NormaliseRequest(answers));

        assertThat((List<?>) data(r).get("not_asked")).hasSize(9)
                .anyMatch("free_to_leave"::equals);
        assertThat(data(r).get("answered_count")).isEqualTo(1);
        assertThat(String.valueOf(data(r).get("note"))).contains("excluded rather than imputed");
    }

    @Test
    @DisplayName("an absent body normalises to nothing rather than throwing")
    void absentBodyIsSafe() {
        var r = controller.normalise(null);

        assertThat(r.getStatusCode().value()).isEqualTo(200);
        assertThat((List<?>) data(r).get("scores")).isEmpty();
        assertThat(String.valueOf(data(r).get("note"))).contains("absence of evidence");
    }
}
