package zw.gov.mohcc.impilo.costa.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/** Transparent period arithmetic shared by variance and forecast. No hidden state. */
final class BudgetMath {

    private BudgetMath() {}

    static LocalDate periodStart(LocalDate explicit, int periodYear) {
        return explicit != null ? explicit : LocalDate.of(periodYear, 1, 1);
    }

    static LocalDate periodEnd(LocalDate explicit, int periodYear) {
        return explicit != null ? explicit : LocalDate.of(periodYear, 12, 31);
    }

    static long daysInPeriod(LocalDate start, LocalDate end) {
        return Math.max(1, end.toEpochDay() - start.toEpochDay() + 1);
    }

    static long daysElapsed(LocalDate start, LocalDate end, LocalDate asOf) {
        long total = daysInPeriod(start, end);
        if (asOf.isBefore(start)) return 0;
        if (!asOf.isBefore(end)) return total;
        return Math.min(total, asOf.toEpochDay() - start.toEpochDay() + 1);
    }

    /** Fraction of the period elapsed, in [0,1], scale 6. */
    static BigDecimal elapsedFraction(LocalDate start, LocalDate end, LocalDate asOf) {
        long total = daysInPeriod(start, end);
        long elapsed = daysElapsed(start, end, asOf);
        return BigDecimal.valueOf(elapsed).divide(BigDecimal.valueOf(total), 6, RoundingMode.HALF_UP);
    }
}
