package zw.gov.mohcc.impilo.shared.visibility;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.VisibilityProfile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AggregateVisibilityGuardTest {

    @Test
    void blocksWhenAggregateOnlyFlag() {
        VisibilityProfile p = new VisibilityProfile(
                "IDENTIFIED_OPERATIONAL_ONLY", "FULL", "NONE",
                true, null, null, null, null, null, null, true);
        assertTrue(AggregateVisibilityGuard.blocksRowLevelDetail(p));
    }

    @Test
    void blocksWhenTierAggregateOnly() {
        VisibilityProfile p = new VisibilityProfile(
                "AGGREGATE_ONLY", "NONE", "NONE",
                false, null, null, null, null, null, null, false);
        assertTrue(AggregateVisibilityGuard.blocksRowLevelDetail(p));
    }

    @Test
    void allowsWhenNullProfile() {
        assertFalse(AggregateVisibilityGuard.blocksRowLevelDetail(null));
    }
}
