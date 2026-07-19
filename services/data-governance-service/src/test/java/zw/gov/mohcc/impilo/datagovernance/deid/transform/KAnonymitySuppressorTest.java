package zw.gov.mohcc.impilo.datagovernance.deid.transform;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("KAnonymitySuppressor — equivalence classes < k are suppressed")
class KAnonymitySuppressorTest {

    private final KAnonymitySuppressor suppressor = new KAnonymitySuppressor();

    private static final List<String> QUASI = List.of("age_band", "district");

    @Test
    @DisplayName("classes smaller than k are suppressed, classes >= k retained")
    void smallClassesSuppressed() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            rows.add(row("35-39", "Harare", "TB"));
        }
        rows.add(row("75-79", "Mutare", "TB"));   // singleton class

        KAnonymitySuppressor.SuppressionResult result = suppressor.suppress(rows, QUASI, 5);

        assertThat(result.retainedRows()).hasSize(5);
        assertThat(result.suppressedRowCount()).isEqualTo(1);
        assertThat(result.equivalenceClassCount()).isEqualTo(2);
        assertThat(result.suppressedClassCount()).isEqualTo(1);
        assertThat(result.retainedRows())
                .allSatisfy(r -> assertThat(r.get("age_band")).isEqualTo("35-39"));
    }

    @Test
    @DisplayName("every retained equivalence class has count >= k")
    void retainedClassesMeetK() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < 3; i++) rows.add(row("30-34", "Harare", "TB"));
        for (int i = 0; i < 7; i++) rows.add(row("40-44", "Gweru", "HTN"));
        for (int i = 0; i < 2; i++) rows.add(row("50-54", "Mutare", "DM"));

        KAnonymitySuppressor.SuppressionResult result = suppressor.suppress(rows, QUASI, 3);

        assertThat(result.retainedRows()).hasSize(10);      // 3 + 7 retained, 2 suppressed
        assertThat(result.minRetainedClassSize()).isGreaterThanOrEqualTo(3);
        assertThat(result.suppressedRowCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("k is policy-configurable — same rows, different k, different outcome")
    void kIsConfigurable() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < 4; i++) rows.add(row("30-34", "Harare", "TB"));

        assertThat(suppressor.suppress(rows, QUASI, 4).retainedRows()).hasSize(4);
        assertThat(suppressor.suppress(rows, QUASI, 5).retainedRows()).isEmpty();
    }

    @Test
    @DisplayName("everything suppressed when all classes are rare (fail-closed)")
    void allRareAllSuppressed() {
        List<Map<String, Object>> rows = List.of(
                row("30-34", "Harare", "TB"),
                row("40-44", "Gweru", "HTN"),
                row("50-54", "Mutare", "DM"));
        KAnonymitySuppressor.SuppressionResult result = suppressor.suppress(rows, QUASI, 2);
        assertThat(result.retainedRows()).isEmpty();
        assertThat(result.suppressedRowCount()).isEqualTo(3);
        assertThat(result.suppressedClassCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("no quasi columns => single class, still suppressed when smaller than k")
    void noQuasiColumnsSingleClass() {
        List<Map<String, Object>> rows = List.of(row("30-34", "Harare", "TB"));
        assertThat(suppressor.suppress(rows, List.of(), 5).retainedRows()).isEmpty();

        List<Map<String, Object>> enough = new ArrayList<>();
        for (int i = 0; i < 5; i++) enough.add(row("30-34", "Harare", "TB"));
        assertThat(suppressor.suppress(enough, List.of(), 5).retainedRows()).hasSize(5);
    }

    @Test
    @DisplayName("non-positive k falls back to the default k=5")
    void nonPositiveKDefaults() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < 4; i++) rows.add(row("30-34", "Harare", "TB"));
        assertThat(suppressor.suppress(rows, QUASI, 0).retainedRows()).isEmpty();
    }

    @Test
    @DisplayName("small-cell suppression removes aggregate cells with count < k")
    void smallCellSuppression() {
        Map<String, Long> cells = new LinkedHashMap<>();
        cells.put("Harare|TB", 12L);
        cells.put("Gweru|TB", 4L);
        cells.put("Mutare|HTN", 5L);

        Map<String, Long> retained = suppressor.suppressSmallCells(cells, 5);

        assertThat(retained).containsOnlyKeys("Harare|TB", "Mutare|HTN");
        assertThat(retained.values()).allSatisfy(count -> assertThat(count).isGreaterThanOrEqualTo(5L));
    }

    private static Map<String, Object> row(String ageBand, String district, String diagnosis) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("age_band", ageBand);
        row.put("district", district);
        row.put("diagnosis", diagnosis);
        return row;
    }
}
