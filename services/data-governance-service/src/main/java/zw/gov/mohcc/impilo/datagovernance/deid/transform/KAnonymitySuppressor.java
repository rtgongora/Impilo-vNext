package zw.gov.mohcc.impilo.datagovernance.deid.transform;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * k-anonymity and small-cell suppressor (design principle 4).
 *
 * <p>Rows are grouped into equivalence classes over the policy's
 * quasi-identifier columns; every class smaller than <em>k</em> (default 5,
 * policy-configurable) is suppressed entirely, so every released class has
 * count ≥ k. For aggregate outputs, {@link #suppressSmallCells} removes any
 * cell with count &lt; k.</p>
 */
@Component
public class KAnonymitySuppressor {

    public static final int DEFAULT_K = 5;

    /** Result of a row-level k-anonymity pass. */
    public record SuppressionResult(
            List<Map<String, Object>> retainedRows,
            int suppressedRowCount,
            int equivalenceClassCount,
            int suppressedClassCount,
            int minRetainedClassSize
    ) {}

    /**
     * Suppress every equivalence class (over {@code quasiColumns}) smaller
     * than {@code k}. With no quasi columns configured, all rows form a single
     * class — which is itself suppressed when smaller than k (fail closed).
     */
    public SuppressionResult suppress(List<Map<String, Object>> rows,
                                      List<String> quasiColumns, int k) {
        int effectiveK = k > 0 ? k : DEFAULT_K;
        Map<List<Object>, List<Map<String, Object>>> classes = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            List<Object> key = quasiColumns.stream()
                    .map(row::get)
                    .collect(Collectors.toList());
            classes.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
        }

        List<Map<String, Object>> retained = new ArrayList<>();
        int suppressedRows = 0;
        int suppressedClasses = 0;
        int minRetainedClassSize = 0;
        for (List<Map<String, Object>> equivalenceClass : classes.values()) {
            if (equivalenceClass.size() < effectiveK) {
                suppressedRows += equivalenceClass.size();
                suppressedClasses++;
            } else {
                retained.addAll(equivalenceClass);
                minRetainedClassSize = minRetainedClassSize == 0
                        ? equivalenceClass.size()
                        : Math.min(minRetainedClassSize, equivalenceClass.size());
            }
        }
        return new SuppressionResult(retained, suppressedRows, classes.size(),
                suppressedClasses, minRetainedClassSize);
    }

    /** Small-cell suppression for aggregate outputs: cells with count &lt; k are removed. */
    public Map<String, Long> suppressSmallCells(Map<String, Long> cells, int k) {
        int effectiveK = k > 0 ? k : DEFAULT_K;
        Map<String, Long> retained = new LinkedHashMap<>();
        for (Map.Entry<String, Long> cell : cells.entrySet()) {
            if (cell.getValue() != null && cell.getValue() >= effectiveK) {
                retained.put(cell.getKey(), cell.getValue());
            }
        }
        return retained;
    }
}
