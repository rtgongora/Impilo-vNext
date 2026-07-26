package zw.gov.mohcc.impilo.ndila.core.location;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HAR W4 guard tests — a geocode proposal must stay a proposal, and must describe itself honestly.
 */
class HpaGeocodeProposalServiceTest {

    /**
     * A district centroid is where to start looking, not where the clinic is. Nothing in this
     * service may ever grade itself HIGH — that word belongs to a surveyed coordinate.
     */
    @Test
    void confidenceIsNeverHigh() {
        var tight = new HpaGeocodeProposalService.DistrictCentroid(
                "Buhera", null, null, 40, HpaGeocodeProposalService.TIGHT_SPREAD_KM - 1);
        var wide = new HpaGeocodeProposalService.DistrictCentroid(
                "Binga", null, null, 40, HpaGeocodeProposalService.TIGHT_SPREAD_KM + 50);

        assertThat(tight.confidence()).isEqualTo("MEDIUM");
        assertThat(wide.confidence()).isEqualTo("LOW");
        assertThat(tight.confidence()).isNotEqualTo("HIGH");
        assertThat(wide.confidence()).isNotEqualTo("HIGH");
    }

    /** The source label has to say what the number actually is, so a reviewer is not misled. */
    @Test
    void theSourceLabelNamesTheDerivation() {
        assertThat(HpaGeocodeProposalService.PROPOSED_SOURCE)
                .contains("CENTROID")
                .contains("SURVEYED");
    }

    /** A wider district must never grade better than a tighter one. */
    @Test
    void spreadGradesMonotonically() {
        double narrow = HpaGeocodeProposalService.boundingBoxDiagonalKm(-19.5, -19.4, 31.7, 31.8);
        double broad = HpaGeocodeProposalService.boundingBoxDiagonalKm(-19.9, -18.9, 31.2, 32.4);
        assertThat(narrow).isLessThan(broad);
        assertThat(narrow).isGreaterThan(0.0);
    }

    /**
     * The gazetteer must be built from surveyed rows only. If it ever read GEOCODED rows, each run
     * would average its own guesses and the estate would drift towards a single point.
     */
    @Test
    void theGazetteerExcludesItsOwnOutput() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/zw/gov/mohcc/impilo/ndila/core/location/HpaGeocodeProposalService.java"));
        assertThat(source).contains("source <> 'GEOCODED'");
        assertThat(source)
                .as("proposals are written to the review queue, never to ndila_locations")
                .doesNotContain("UPDATE ndila_locations")
                .doesNotContain("INSERT INTO ndila_locations");
    }
}
