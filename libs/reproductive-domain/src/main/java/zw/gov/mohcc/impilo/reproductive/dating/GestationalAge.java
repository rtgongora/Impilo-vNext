package zw.gov.mohcc.impilo.reproductive.dating;

/**
 * A gestational age in completed weeks plus days — "32+4", the way it is written on every chart and
 * spoken in every handover.
 *
 * <p>Kept as a type rather than a bare integer because the two representations are not
 * interchangeable in conversation and mixing them up is a real error: 32 weeks and 32 days are four
 * months apart, and "32.5 weeks" is not 32 weeks and 5 days. Arithmetic is done in days and rendered
 * in weeks-plus-days at the edge.
 */
public record GestationalAge(int weeks, int days) {

    public static final int DAYS_PER_WEEK = 7;

    public GestationalAge {
        if (weeks < 0) {
            throw new IllegalArgumentException("gestational age cannot be negative: " + weeks);
        }
        if (days < 0 || days > 6) {
            throw new IllegalArgumentException("days must be 0-6, not " + days);
        }
    }

    public int totalDays() {
        return weeks * DAYS_PER_WEEK + days;
    }

    /** "32+4". */
    public String display() {
        return weeks + "+" + days;
    }

    /**
     * Build from completed days. Returns null when the input is null or negative — a pregnancy
     * cannot be scored before it began, and a caller who has been handed a negative number has a
     * dating problem that must not be rendered as an early gestation.
     */
    public static GestationalAge ofDays(Integer totalDays) {
        if (totalDays == null || totalDays < 0) {
            return null;
        }
        return new GestationalAge(totalDays / DAYS_PER_WEEK, totalDays % DAYS_PER_WEEK);
    }

    /** Parse "32+4" or "32". Returns null when the text is not a gestational age. */
    public static GestationalAge parse(String display) {
        if (display == null || display.isBlank()) {
            return null;
        }
        String text = display.trim();
        try {
            int plus = text.indexOf('+');
            if (plus < 0) {
                return new GestationalAge(Integer.parseInt(text), 0);
            }
            return new GestationalAge(
                    Integer.parseInt(text.substring(0, plus).trim()),
                    Integer.parseInt(text.substring(plus + 1).trim()));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
