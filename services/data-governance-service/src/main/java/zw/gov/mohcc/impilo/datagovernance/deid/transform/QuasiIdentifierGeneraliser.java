package zw.gov.mohcc.impilo.datagovernance.deid.transform;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Quasi-identifier generaliser (design principle 3).
 *
 * <p>Rules (see {@link GeneralisationRules}):</p>
 * <ul>
 *   <li>{@code AGE_BAND_5} — DOB collapsed to a 5-year age band; the column is
 *       renamed to {@code age_band} so no date-of-birth-shaped column survives.</li>
 *   <li>{@code MONTH} — date coarsened to {@code yyyy-MM}.</li>
 *   <li>{@code DATE_SHIFT} — date shifted deterministically per subject
 *       (derived from the row's {@code subject_token}) within
 *       ±{@code maxDateShiftDays}, preserving intervals per subject without a
 *       recoverable global offset.</li>
 *   <li>{@code DISTRICT} — geo reduced to district level (maps keep only the
 *       {@code district} entry; {@code province/district/facility} paths keep
 *       the district segment).</li>
 *   <li>{@code RARE_TO_OTHER} — categorical values occurring fewer than
 *       {@code rareThreshold} times across the batch become {@code OTHER}.</li>
 * </ul>
 */
@Component
public class QuasiIdentifierGeneraliser {

    public static final String AGE_BAND_COLUMN = "age_band";
    public static final String OTHER_VALUE = "OTHER";
    public static final String SUBJECT_TOKEN_COLUMN = "subject_token";

    /** Apply the configured rules to a batch of rows; input rows are not modified. */
    public List<Map<String, Object>> generalise(List<Map<String, Object>> rows,
                                                GeneralisationRules rules,
                                                LocalDate asOf) {
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            out.add(generaliseRow(row, rules, asOf));
        }
        applyRareToOther(out, rules);
        return out;
    }

    private Map<String, Object> generaliseRow(Map<String, Object> row,
                                              GeneralisationRules rules,
                                              LocalDate asOf) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String column = entry.getKey();
            Object value = entry.getValue();
            String rule = rules.columns().get(column);
            if (rule == null || value == null) {
                out.put(column, value);
                continue;
            }
            switch (rule) {
                case GeneralisationRules.RULE_AGE_BAND_5 ->
                        out.put(AGE_BAND_COLUMN, ageBand(parseDate(value), asOf));
                case GeneralisationRules.RULE_MONTH ->
                        out.put(column, toMonth(parseDate(value)));
                case GeneralisationRules.RULE_DATE_SHIFT -> {
                    String salt = String.valueOf(row.getOrDefault(SUBJECT_TOKEN_COLUMN, column));
                    out.put(column, dateShift(parseDate(value), salt, rules.maxDateShiftDays()).toString());
                }
                case GeneralisationRules.RULE_DISTRICT ->
                        out.put(column, districtOf(value));
                case GeneralisationRules.RULE_RARE_TO_OTHER ->
                        out.put(column, value);   // handled batch-wide below
                default ->
                        throw new IllegalArgumentException("Unknown generalisation rule: " + rule);
            }
        }
        return out;
    }

    /** DOB → 5-year age band, e.g. {@code "35-39"}. Ages are floored at 0. */
    public String ageBand(LocalDate dob, LocalDate asOf) {
        int age = Math.max(0, Period.between(dob, asOf).getYears());
        int lower = (age / 5) * 5;
        return lower + "-" + (lower + 4);
    }

    /** Date → {@code yyyy-MM}. */
    public String toMonth(LocalDate date) {
        return String.format("%04d-%02d", date.getYear(), date.getMonthValue());
    }

    /**
     * Deterministic per-subject date shift within ±{@code maxShiftDays}.
     * The shift is derived from the salt (normally the subject token), so all
     * dates of one subject in one dataset shift together, while no global
     * offset exists to subtract back out.
     */
    public LocalDate dateShift(LocalDate date, String salt, int maxShiftDays) {
        if (maxShiftDays <= 0) {
            return date;
        }
        int window = maxShiftDays * 2 + 1;
        int shift = Math.floorMod(salt.hashCode(), window) - maxShiftDays;
        return date.plusDays(shift);
    }

    /**
     * Geo → district level. Maps keep only their {@code district} entry;
     * {@code province/district/facility} path strings keep the district
     * segment; plain strings are assumed to already be district-level.
     */
    public String districtOf(Object geo) {
        if (geo instanceof Map<?, ?> map) {
            Object district = map.get("district");
            return district != null ? district.toString() : OTHER_VALUE;
        }
        String value = String.valueOf(geo);
        String[] segments = value.split("/");
        if (segments.length >= 2) {
            return segments[1].trim();
        }
        return value.trim();
    }

    private void applyRareToOther(List<Map<String, Object>> rows, GeneralisationRules rules) {
        for (Map.Entry<String, String> entry : rules.columns().entrySet()) {
            if (!GeneralisationRules.RULE_RARE_TO_OTHER.equals(entry.getValue())) {
                continue;
            }
            String column = entry.getKey();
            Map<Object, Integer> counts = new HashMap<>();
            for (Map<String, Object> row : rows) {
                Object value = row.get(column);
                if (value != null) {
                    counts.merge(value, 1, Integer::sum);
                }
            }
            for (Map<String, Object> row : rows) {
                Object value = row.get(column);
                if (value != null && counts.get(value) < rules.rareThreshold()) {
                    row.put(column, OTHER_VALUE);
                }
            }
        }
    }

    private static LocalDate parseDate(Object value) {
        if (value instanceof LocalDate date) {
            return date;
        }
        String text = String.valueOf(value).trim();
        // Tolerate ISO date-time inputs by keeping the date part.
        if (text.length() > 10) {
            text = text.substring(0, 10);
        }
        return LocalDate.parse(text);
    }
}
