package zw.gov.mohcc.impilo.pct.core.clinical;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialHistoryVocabularyTest {

    @Test
    void governedCategoriesCoverClerkingBrief() {
        assertTrue(SocialHistoryVocabulary.GOVERNED_CATEGORIES.contains("OCCUPATION"));
        assertTrue(SocialHistoryVocabulary.GOVERNED_CATEGORIES.contains("ENVIRONMENTAL_EXPOSURE"));
        assertTrue(SocialHistoryVocabulary.GOVERNED_CATEGORIES.contains("FOOD_SECURITY"));
        assertTrue(SocialHistoryVocabulary.GOVERNED_CATEGORIES.contains("SLEEP"));
        assertEquals(11, SocialHistoryVocabulary.GOVERNED_CATEGORIES.size());
    }

    @Test
    void unknownCategoriesRemainAppendable() {
        assertFalse(SocialHistoryVocabulary.isGoverned("CUSTOM_LOCAL"));
        assertEquals("CUSTOM_LOCAL", SocialHistoryVocabulary.normaliseCategory("custom_local"));
        assertEquals("TOBACCO", SocialHistoryVocabulary.normaliseCategory("tobacco"));
    }
}
