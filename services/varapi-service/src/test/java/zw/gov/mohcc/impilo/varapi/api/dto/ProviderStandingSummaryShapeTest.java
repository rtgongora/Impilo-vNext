package zw.gov.mohcc.impilo.varapi.api.dto;

import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave-2: the dead {@code professional_standing_status} axis was removed end-to-end.
 * The provider standing summary (the cross-service legitimacy surface) must no longer
 * expose professional standing, while keeping the axes that consumers actually rely on.
 */
class ProviderStandingSummaryShapeTest {

    @Test
    void standingSummaryNoLongerExposesProfessionalStanding() {
        var componentNames = Arrays.stream(ProviderStandingSummary.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(componentNames).doesNotContain("professionalStandingStatus");
        // The canonical + derived axes consumers depend on are still present.
        assertThat(componentNames).contains("status", "lifecycleStatus", "licenceStatus", "active");
    }
}
