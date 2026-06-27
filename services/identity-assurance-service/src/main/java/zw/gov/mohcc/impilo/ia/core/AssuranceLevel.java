package zw.gov.mohcc.impilo.ia.core;

/**
 * Identity assurance levels (the Health OS "assurance level" access-control dimension).
 * LOA1 self-asserted → LOA4 biometric/physical-token bound. Higher-friction operations
 * (prescribing, claims) require higher levels.
 */
public enum AssuranceLevel {
    LOA1, LOA2, LOA3, LOA4;

    /** 1-based rank for comparison (LOA1 = 1 … LOA4 = 4). */
    public int rank() {
        return ordinal() + 1;
    }

    public boolean isHigherThan(AssuranceLevel other) {
        return this.rank() > other.rank();
    }
}
