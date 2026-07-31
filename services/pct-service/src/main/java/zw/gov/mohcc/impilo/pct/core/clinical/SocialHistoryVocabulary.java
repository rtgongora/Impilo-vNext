package zw.gov.mohcc.impilo.pct.core.clinical;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Governed categories for {@code pct_social_history} (V106).
 *
 * <p>Known codes are selectable in the UI. Unknown categories still append — the column is free
 * text by design so local programmes are not blocked — but clerking prefers these codes.</p>
 *
 * <p>Simba wellness (consumer diet/sleep/activity) is OUT_OF_PACK for clinical truth; clinician-
 * documented DIET / PHYSICAL_ACTIVITY / SLEEP here are clinical notes, not Simba feeds.</p>
 */
public final class SocialHistoryVocabulary {

    public static final List<String> GOVERNED_CATEGORIES = List.of(
            "TOBACCO",
            "ALCOHOL",
            "SUBSTANCE",
            "OCCUPATION",
            "ENVIRONMENTAL_EXPOSURE",
            "DIET",
            "PHYSICAL_ACTIVITY",
            "SLEEP",
            "SOCIAL_SUPPORT",
            "HOUSING",
            "FOOD_SECURITY"
    );

    private static final Set<String> GOVERNED = new LinkedHashSet<>(GOVERNED_CATEGORIES);

    private SocialHistoryVocabulary() {}

    public static boolean isGoverned(String category) {
        if (category == null || category.isBlank()) return false;
        return GOVERNED.contains(category.trim().toUpperCase(Locale.ROOT));
    }

    /** Normalise known codes; leave unknown categories uppercased but otherwise free. */
    public static String normaliseCategory(String category) {
        if (category == null) return null;
        String upper = category.trim().toUpperCase(Locale.ROOT);
        return GOVERNED.contains(upper) ? upper : upper;
    }
}
