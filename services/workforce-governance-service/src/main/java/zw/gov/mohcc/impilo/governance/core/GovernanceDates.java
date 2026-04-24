package zw.gov.mohcc.impilo.governance.core;

import java.time.LocalDate;
import java.time.ZoneOffset;

public final class GovernanceDates {

    private GovernanceDates() {}

    public static LocalDate todayUtc() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    public static boolean isActiveForDate(LocalDate start, LocalDate end, LocalDate today) {
        if (start != null && today.isBefore(start)) {
            return false;
        }
        if (end != null && today.isAfter(end)) {
            return false;
        }
        return true;
    }
}
