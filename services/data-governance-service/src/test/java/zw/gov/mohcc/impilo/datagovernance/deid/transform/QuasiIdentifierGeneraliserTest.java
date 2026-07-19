package zw.gov.mohcc.impilo.datagovernance.deid.transform;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("QuasiIdentifierGeneraliser — DOB→band, date→month/shift, geo→district, rare→OTHER")
class QuasiIdentifierGeneraliserTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final LocalDate AS_OF = LocalDate.of(2026, 7, 19);

    private final QuasiIdentifierGeneraliser generaliser = new QuasiIdentifierGeneraliser();

    @Test
    @DisplayName("DOB collapses to a 5-year age band")
    void ageBand5Years() {
        assertThat(generaliser.ageBand(LocalDate.of(1990, 6, 15), AS_OF)).isEqualTo("35-39");
        assertThat(generaliser.ageBand(LocalDate.of(2026, 1, 1), AS_OF)).isEqualTo("0-4");
        assertThat(generaliser.ageBand(LocalDate.of(1950, 12, 31), AS_OF)).isEqualTo("75-79");
    }

    @Test
    @DisplayName("date coarsens to yyyy-MM")
    void dateToMonth() {
        assertThat(generaliser.toMonth(LocalDate.of(2026, 7, 3))).isEqualTo("2026-07");
    }

    @Test
    @DisplayName("date shift is deterministic per salt and bounded by maxShiftDays")
    void dateShiftDeterministicAndBounded() {
        LocalDate date = LocalDate.of(2026, 7, 3);
        LocalDate first = generaliser.dateShift(date, "subject-token-1", 7);
        LocalDate second = generaliser.dateShift(date, "subject-token-1", 7);
        assertThat(first).isEqualTo(second);
        assertThat(Math.abs(java.time.temporal.ChronoUnit.DAYS.between(date, first)))
                .isLessThanOrEqualTo(7);
        assertThat(generaliser.dateShift(date, "any", 0)).isEqualTo(date);
    }

    @Test
    @DisplayName("geo reduces to district level")
    void geoToDistrict() {
        assertThat(generaliser.districtOf("Harare Province/Harare/Mbare Clinic")).isEqualTo("Harare");
        assertThat(generaliser.districtOf(Map.of(
                "province", "Manicaland", "district", "Mutare", "facility", "Sakubva Clinic")))
                .isEqualTo("Mutare");
        assertThat(generaliser.districtOf("Gweru")).isEqualTo("Gweru");
    }

    @Test
    @DisplayName("generalise renames dob to age_band so no DOB-shaped column survives")
    void generaliseRenamesDob() {
        GeneralisationRules rules = rules("{\"columns\":{\"dob\":\"AGE_BAND_5\"}}");
        List<Map<String, Object>> out = generaliser.generalise(
                rows(Map.of("dob", "1990-06-15", "gender", "F")), rules, AS_OF);
        assertThat(out.get(0)).doesNotContainKey("dob");
        assertThat(out.get(0)).containsEntry("age_band", "35-39");
        assertThat(out.get(0)).containsEntry("gender", "F");
    }

    @Test
    @DisplayName("MONTH rule coarsens configured date columns in place")
    void generaliseMonthRule() {
        GeneralisationRules rules = rules("{\"columns\":{\"visit_date\":\"MONTH\"}}");
        List<Map<String, Object>> out = generaliser.generalise(
                rows(Map.of("visit_date", "2026-07-03")), rules, AS_OF);
        assertThat(out.get(0)).containsEntry("visit_date", "2026-07");
    }

    @Test
    @DisplayName("rare categorical values (< threshold occurrences) become OTHER")
    void rareCategoricalsBecomeOther() {
        GeneralisationRules rules = rules(
                "{\"columns\":{\"diagnosis\":\"RARE_TO_OTHER\"},\"rareThreshold\":2}");
        List<Map<String, Object>> batch = new ArrayList<>();
        batch.addAll(rows(Map.of("diagnosis", "TB"), Map.of("diagnosis", "TB"),
                Map.of("diagnosis", "TB"), Map.of("diagnosis", "RARE-SYNDROME")));
        List<Map<String, Object>> out = generaliser.generalise(batch, rules, AS_OF);
        assertThat(out).extracting(r -> r.get("diagnosis"))
                .containsExactly("TB", "TB", "TB", "OTHER");
    }

    @Test
    @DisplayName("unknown generalisation rule is rejected")
    void unknownRuleRejected() {
        GeneralisationRules rules = rules("{\"columns\":{\"dob\":\"NO_SUCH_RULE\"}}");
        assertThatThrownBy(() -> generaliser.generalise(
                rows(Map.of("dob", "1990-06-15")), rules, AS_OF))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rules JSON parses with defaults")
    void rulesParseWithDefaults() {
        GeneralisationRules empty = GeneralisationRules.fromJson(MAPPER, null);
        assertThat(empty.columns()).isEmpty();
        assertThat(empty.rareThreshold()).isEqualTo(GeneralisationRules.DEFAULT_RARE_THRESHOLD);
        assertThat(empty.maxDateShiftDays()).isEqualTo(GeneralisationRules.DEFAULT_MAX_DATE_SHIFT_DAYS);

        GeneralisationRules parsed = rules(
                "{\"columns\":{\"dob\":\"AGE_BAND_5\"},\"quasiColumns\":[\"age_band\"],"
                        + "\"rareThreshold\":3,\"maxDateShiftDays\":10}");
        assertThat(parsed.columns()).containsEntry("dob", "AGE_BAND_5");
        assertThat(parsed.quasiColumns()).containsExactly("age_band");
        assertThat(parsed.rareThreshold()).isEqualTo(3);
        assertThat(parsed.maxDateShiftDays()).isEqualTo(10);
    }

    private static GeneralisationRules rules(String json) {
        return GeneralisationRules.fromJson(MAPPER, json);
    }

    @SafeVarargs
    private static List<Map<String, Object>> rows(Map<String, Object>... rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            out.add(new LinkedHashMap<>(row));
        }
        return out;
    }
}
