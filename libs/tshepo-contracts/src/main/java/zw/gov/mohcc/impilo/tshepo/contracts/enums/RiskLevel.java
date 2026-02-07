package zw.gov.mohcc.impilo.tshepo.contracts.enums;

/**
 * Risk score bands used by TSHEPO for step-up and deny decisions.
 *
 * <pre>
 *   0–30  LOW      — proceed normally
 *  31–60  MEDIUM   — elevated logging
 *  61–80  HIGH     — step-up required for sensitive operations
 *  81–100 BLOCKED  — deny outright, flag for security review
 * </pre>
 */
public enum RiskLevel {
    LOW(0, 30),
    MEDIUM(31, 60),
    HIGH(61, 80),
    BLOCKED(81, 100);

    private final int min;
    private final int max;

    RiskLevel(int min, int max) {
        this.min = min;
        this.max = max;
    }

    public static RiskLevel fromScore(int score) {
        if (score <= 30) return LOW;
        if (score <= 60) return MEDIUM;
        if (score <= 80) return HIGH;
        return BLOCKED;
    }

    public int getMin() { return min; }
    public int getMax() { return max; }
}
