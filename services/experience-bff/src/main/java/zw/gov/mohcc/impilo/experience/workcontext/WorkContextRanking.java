package zw.gov.mohcc.impilo.experience.workcontext;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Deterministic auto-resolution scoring. Highest wins; ties break on
 * {@code contextId} lexical order so the result is stable across calls with
 * identical inputs — important because the picker and the mint-time
 * re-proof must agree on "the" recommended context.
 */
public final class WorkContextRanking {

    private WorkContextRanking() {}

    /** The margin below which the top two candidates are considered ambiguous (chooser required). */
    public static final int AUTO_RESOLVE_MARGIN = 200;

    public record Signals(
            boolean entryRouteMatch,
            boolean inShiftNow,
            boolean checkedIn,
            boolean wgvPrimary,
            boolean lastValidContext,
            boolean shiftStartingSoon,
            boolean wgvSecondary,
            boolean recentPattern,
            boolean hasRestriction,
            boolean sourceDegraded
    ) {
        public static Signals none() {
            return new Signals(false, false, false, false, false, false, false, false, false, false);
        }
    }

    public static int score(Signals s) {
        int total = 0;
        if (s.entryRouteMatch()) total += 1000;
        if (s.inShiftNow()) total += 500;
        if (s.checkedIn()) total += 400;
        if (s.wgvPrimary()) total += 250;
        if (s.lastValidContext()) total += 200;
        if (s.shiftStartingSoon()) total += 150;
        if (s.wgvSecondary()) total += 50;
        if (s.recentPattern()) total += 40;
        if (s.hasRestriction()) total -= 300;
        if (s.sourceDegraded()) total -= 100;
        return total;
    }

    /** Applies {@link #score} to every context using its already-computed rankScore as the signal total. */
    public static List<ResolvedWorkContext> sortByScoreDesc(List<ResolvedWorkContext> contexts) {
        return contexts.stream()
                .sorted(Comparator
                        .comparingInt(ResolvedWorkContext::rankScore).reversed()
                        .thenComparing(ResolvedWorkContext::contextId))
                .toList();
    }

    /**
     * requiresContextChooser is true when: more than one context AND
     * (the top-two margin is below {@link #AUTO_RESOLVE_MARGIN}, OR the top
     * two differ in contextKind — never silently auto-enter a mode family
     * the person did not ask for — OR any source is degraded with more than
     * one context resolved).
     */
    public static boolean requiresChooser(List<ResolvedWorkContext> ranked, boolean anySourceDegraded) {
        if (ranked.size() <= 1) {
            return false;
        }
        ResolvedWorkContext top = ranked.get(0);
        ResolvedWorkContext second = ranked.get(1);
        int margin = top.rankScore() - second.rankScore();
        if (margin < AUTO_RESOLVE_MARGIN) {
            return true;
        }
        if (!top.contextKind().equals(second.contextKind())) {
            return true;
        }
        return anySourceDegraded;
    }

    public static Map<String, Object> toAuditableSummary(List<ResolvedWorkContext> ranked) {
        return Map.of(
                "count", ranked.size(),
                "topContextId", ranked.isEmpty() ? null : ranked.get(0).contextId(),
                "topScore", ranked.isEmpty() ? null : ranked.get(0).rankScore());
    }
}
