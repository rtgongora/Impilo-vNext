package zw.gov.mohcc.impilo.pct.core.death;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates public-health / mortality-review flags from death context. Flags drive a death-review
 * requirement (routed to Rito for M&M / MDSR as an owner-routed link) and a public-health signal
 * (owner-routed to surveillance). Flags: MATERNAL, PERINATAL, NEONATAL, STILLBIRTH, UNDER_FIVE,
 * NOTIFIABLE, OUTBREAK.
 */
@Component
public class PublicHealthScreener {

    public record Result(List<String> flags, boolean deathReviewRequired) {}

    /**
     * @param ageYears        age in years at death (null if unknown); &lt;5 → UNDER_FIVE
     * @param ageDays         age in days at death (null if unknown); &lt;=28 → NEONATAL
     * @param maternal        maternal/peripartum death
     * @param perinatal       perinatal death
     * @param stillbirth      stillbirth
     * @param notifiableCause notifiable / outbreak-associated cause flag
     * @param outbreakLinked  linked to an active outbreak
     */
    public Result evaluate(Integer ageYears, Integer ageDays, boolean maternal, boolean perinatal,
                           boolean stillbirth, boolean notifiableCause, boolean outbreakLinked) {
        List<String> flags = new ArrayList<>();
        if (maternal) flags.add("MATERNAL");
        if (perinatal) flags.add("PERINATAL");
        if (stillbirth) flags.add("STILLBIRTH");
        if (ageDays != null && ageDays <= 28) flags.add("NEONATAL");
        if (ageYears != null && ageYears < 5) flags.add("UNDER_FIVE");
        if (notifiableCause) flags.add("NOTIFIABLE");
        if (outbreakLinked) flags.add("OUTBREAK");
        // Maternal, perinatal, neonatal, stillbirth, under-five deaths are mandatorily reviewed.
        boolean review = flags.stream().anyMatch(f ->
                f.equals("MATERNAL") || f.equals("PERINATAL") || f.equals("NEONATAL")
                        || f.equals("STILLBIRTH") || f.equals("UNDER_FIVE"));
        return new Result(flags, review);
    }
}
