package zw.gov.mohcc.impilo.reproductive.dating;

/**
 * How a pregnancy was dated, ordered by how much a clinician should trust it.
 *
 * <p>Precedence is the clinical content of this enum, not an implementation detail. An early
 * ultrasound measures the fetus; a last menstrual period measures the woman's recall of a date and
 * an assumption about when she ovulated. Both are legitimate, and where they disagree by more than a
 * tolerance the more precise one wins — but only within the window where it is more precise. After
 * about 22 weeks a scan estimates size, and size stops being a reliable proxy for age exactly when
 * growth restriction becomes the thing you are looking for. Redating a small third-trimester fetus
 * onto its measurements makes it a normal younger one, and the problem disappears from the chart.
 *
 * <p>Lower {@link #precedence()} is more authoritative. {@link #UNKNOWN} never wins.
 */
public enum DatingMethod {

    /** Conception or transfer date known exactly. Nothing is more precise than this. */
    ASSISTED_CONCEPTION(10),

    /** Crown-rump length before 14+0 — accurate to about ±5 days. */
    ULTRASOUND_CRL_FIRST_TRIMESTER(20),

    /** Biometry from 14+0 to 21+6 — accurate to about ±10 days. */
    ULTRASOUND_EARLY_SECOND_TRIMESTER(30),

    /** A remembered, regular, certain last menstrual period. */
    LMP_CERTAIN(40),

    /**
     * Biometry at or after 22+0. Deliberately ranked BELOW a certain LMP: at this gestation a scan
     * measures size, and using it to redate converts a growth-restricted fetus into a younger
     * normal one.
     */
    ULTRASOUND_LATE_SECOND_OR_THIRD(50),

    /** An LMP the woman is unsure of, or cycles that were irregular. */
    LMP_UNCERTAIN(60),

    /** Symphysis-fundal height. A screening measure pressed into service as a dating one. */
    SYMPHYSIS_FUNDAL_HEIGHT(70),

    /** A clinician's overall estimate where nothing better exists. */
    CLINICAL_ESTIMATE(80),

    /** Not dated. Never wins, never supersedes, and never silently becomes term. */
    UNKNOWN(Integer.MAX_VALUE);

    private final int precedence;

    DatingMethod(int precedence) {
        this.precedence = precedence;
    }

    /** Lower is more authoritative. */
    public int precedence() {
        return precedence;
    }

    public boolean derivesFromUltrasound() {
        return this == ULTRASOUND_CRL_FIRST_TRIMESTER
                || this == ULTRASOUND_EARLY_SECOND_TRIMESTER
                || this == ULTRASOUND_LATE_SECOND_OR_THIRD;
    }

    public boolean derivesFromMenstrualHistory() {
        return this == LMP_CERTAIN || this == LMP_UNCERTAIN;
    }

    /** True when this method can date a pregnancy at all. */
    public boolean usable() {
        return this != UNKNOWN;
    }
}
