package zw.gov.mohcc.impilo.clinical.maternal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Bishop cervical favourability score (0–13).
 *
 * <p>Pure and stateless. A blank component is not zero — if any component is missing, the result is
 * {@link Interpretation#INCOMPLETE} with a named missing list, never an invented total.
 */
public final class BishopScoreEngine {

    public static final String CONTENT_VERSION = "ENGINEERING_SEED";

    private static final Set<String> BLANK_CODES = Set.of(
            "", "UNKNOWN", "NOT_ASSESSED", "NOT_RECORDED", "NOT_DONE", "INDETERMINATE");

    public enum Interpretation {
        UNFAVOURABLE,
        INTERMEDIATE,
        FAVOURABLE,
        INCOMPLETE
    }

    public record Assessment(
            Integer score,
            Interpretation interpretation,
            Map<String, Integer> components,
            List<String> missing,
            String contentVersion) {
    }

    public record Input(
            Object dilation,
            Object effacement,
            Object station,
            Object consistency,
            Object position) {
    }

    private BishopScoreEngine() {
    }

    public static Assessment assess(Input input) {
        Map<String, Integer> components = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();

        Integer dilation = parseDilation(input == null ? null : input.dilation());
        if (dilation == null) {
            missing.add("dilation");
        } else {
            components.put("dilation", dilation);
        }

        Integer effacement = parseEffacement(input == null ? null : input.effacement());
        if (effacement == null) {
            missing.add("effacement");
        } else {
            components.put("effacement", effacement);
        }

        Integer station = parseStation(input == null ? null : input.station());
        if (station == null) {
            missing.add("station");
        } else {
            components.put("station", station);
        }

        Integer consistency = parseConsistency(input == null ? null : input.consistency());
        if (consistency == null) {
            missing.add("consistency");
        } else {
            components.put("consistency", consistency);
        }

        Integer position = parsePosition(input == null ? null : input.position());
        if (position == null) {
            missing.add("position");
        } else {
            components.put("position", position);
        }

        if (!missing.isEmpty()) {
            return new Assessment(null, Interpretation.INCOMPLETE, Map.copyOf(components), List.copyOf(missing),
                    CONTENT_VERSION);
        }

        int total = dilation + effacement + station + consistency + position;
        return new Assessment(total, interpret(total), Map.copyOf(components), List.of(), CONTENT_VERSION);
    }

    static Interpretation interpret(int score) {
        if (score <= 6) {
            return Interpretation.UNFAVOURABLE;
        }
        if (score <= 9) {
            return Interpretation.INTERMEDIATE;
        }
        return Interpretation.FAVOURABLE;
    }

    static Integer parseDilation(Object raw) {
        if (isBlank(raw)) {
            return null;
        }
        Integer direct = directScore(raw, 0, 3);
        if (direct != null) {
            return direct;
        }
        String code = normalise(raw);
        return switch (code) {
            case "CLOSED", "DIL_CLOSED", "DILATION_CLOSED", "0" -> 0;
            case "DIL_1_2", "DILATION_1_2", "1_2", "1-2" -> 1;
            case "DIL_3_4", "DILATION_3_4", "3_4", "3-4" -> 2;
            case "DIL_5_PLUS", "DILATION_5_PLUS", "5_PLUS", "5+", "GE5", "GTE5" -> 3;
            default -> scoreFromCm(parseNumber(raw), new double[][]{
                    {0, 0, 0},
                    {1, 2, 1},
                    {3, 4, 2},
                    {5, Double.MAX_VALUE, 3}
            });
        };
    }

    static Integer parseEffacement(Object raw) {
        if (isBlank(raw)) {
            return null;
        }
        Integer direct = directScore(raw, 0, 3);
        if (direct != null) {
            return direct;
        }
        String code = normalise(raw);
        return switch (code) {
            case "EFF_0_30", "EFFACEMENT_0_30", "0_30", "0-30" -> 0;
            case "EFF_40_50", "EFFACEMENT_40_50", "40_50", "40-50" -> 1;
            case "EFF_60_70", "EFFACEMENT_60_70", "60_70", "60-70" -> 2;
            case "EFF_80_PLUS", "EFFACEMENT_80_PLUS", "80_PLUS", "80+", "GE80", "GTE80" -> 3;
            default -> scoreFromPercent(parseNumber(raw));
        };
    }

    static Integer parseStation(Object raw) {
        if (isBlank(raw)) {
            return null;
        }
        Integer direct = directScore(raw, 0, 3);
        if (direct != null) {
            return direct;
        }
        String code = normalise(raw);
        return switch (code) {
            case "STATION_PLUS3", "STATION_+3", "+3", "PLUS3" -> 0;
            case "STATION_PLUS2", "STATION_+2", "+2", "PLUS2" -> 1;
            case "STATION_MID", "STATION_PLUS1_0_MINUS1", "STATION_+1_0_-1", "+1", "0", "-1", "PLUS1", "ZERO",
                    "MINUS1" -> 2;
            case "STATION_MINUS2_PLUS", "STATION_LE_MINUS2", "STATION_-2", "-2", "MINUS2", "LE_MINUS2" -> 3;
            default -> scoreFromStation(parseNumber(raw));
        };
    }

    static Integer parseConsistency(Object raw) {
        if (isBlank(raw)) {
            return null;
        }
        Integer direct = directScore(raw, 0, 2);
        if (direct != null) {
            return direct;
        }
        return switch (normalise(raw)) {
            case "FIRM" -> 0;
            case "MEDIUM", "MODERATE" -> 1;
            case "SOFT" -> 2;
            default -> null;
        };
    }

    static Integer parsePosition(Object raw) {
        if (isBlank(raw)) {
            return null;
        }
        Integer direct = directScore(raw, 0, 2);
        if (direct != null) {
            return direct;
        }
        return switch (normalise(raw)) {
            case "POSTERIOR" -> 0;
            case "MID", "MIDDLE", "CENTRAL" -> 1;
            case "ANTERIOR" -> 2;
            default -> null;
        };
    }

    private static Integer directScore(Object raw, int min, int max) {
        if (raw instanceof Number n) {
            int value = n.intValue();
            if (value >= min && value <= max) {
                return value;
            }
        }
        if (raw instanceof String s && !s.isBlank()) {
            try {
                int value = Integer.parseInt(s.trim());
                if (value >= min && value <= max) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                // fall through to coded parsing
            }
        }
        return null;
    }

    private static Integer scoreFromCm(Double cm) {
        if (cm == null) {
            return null;
        }
        if (cm <= 0) {
            return 0;
        }
        if (cm <= 2) {
            return 1;
        }
        if (cm <= 4) {
            return 2;
        }
        if (cm >= 5) {
            return 3;
        }
        return null;
    }

    private static Integer scoreFromPercent(Double percent) {
        if (percent == null) {
            return null;
        }
        if (percent <= 30) {
            return 0;
        }
        if (percent <= 50) {
            return 1;
        }
        if (percent <= 70) {
            return 2;
        }
        if (percent >= 80) {
            return 3;
        }
        return null;
    }

    private static Integer scoreFromStation(Double station) {
        if (station == null) {
            return null;
        }
        if (station >= 3) {
            return 0;
        }
        if (station >= 2) {
            return 1;
        }
        if (station >= -1) {
            return 2;
        }
        if (station <= -2) {
            return 3;
        }
        return null;
    }

    private static Integer scoreFromCm(Double value, double[][] bands) {
        if (value == null) {
            return null;
        }
        for (double[] band : bands) {
            if (value >= band[0] && value <= band[1]) {
                return (int) band[2];
            }
        }
        return null;
    }

    private static Double parseNumber(Object raw) {
        if (raw instanceof Number n) {
            return n.doubleValue();
        }
        if (raw instanceof String s && !s.isBlank()) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static boolean isBlank(Object raw) {
        if (raw == null) {
            return true;
        }
        if (raw instanceof String s) {
            String trimmed = s.trim();
            return trimmed.isEmpty() || BLANK_CODES.contains(trimmed.toUpperCase(Locale.ROOT));
        }
        return false;
    }

    private static String normalise(Object raw) {
        return raw.toString().trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
