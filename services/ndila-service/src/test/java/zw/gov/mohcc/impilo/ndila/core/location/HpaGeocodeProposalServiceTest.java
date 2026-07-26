package zw.gov.mohcc.impilo.ndila.core.location;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HAR W7 guard tests — a proposal must stay a proposal, must describe its own precision honestly,
 * and must key on something that actually exists in the data.
 *
 * <p>The previous implementation failed the last of those: it keyed on {@code district} and
 * {@code proposed_locality}, both null on all 6,327 live queue rows, so it proposed nothing and
 * reported success. These tests pin the replacement.</p>
 */
class HpaGeocodeProposalServiceTest {

    private static String source(String name) throws Exception {
        return Files.readString(Path.of(
                "src/main/java/zw/gov/mohcc/impilo/ndila/core/location/" + name));
    }

    /** A street address describes a building; a suburb centroid describes a neighbourhood. */
    @Test
    void addressOutranksSuburbAndNeitherIsEverHigh() {
        assertThat(HpaGeocodeProposalService.confidenceFor(
                HpaGeocodeProposalService.PRECISION_ADDRESS)).isEqualTo("MEDIUM");
        assertThat(HpaGeocodeProposalService.confidenceFor(
                HpaGeocodeProposalService.PRECISION_SUBURB)).isEqualTo("LOW");
        assertThat(HpaGeocodeProposalService.confidenceFor(null)).isNotEqualTo("HIGH");
        assertThat(HpaGeocodeProposalService.confidenceFor(
                HpaGeocodeProposalService.PRECISION_ADDRESS)).isNotEqualTo("HIGH");
    }

    /** Matching is case- and whitespace-insensitive, because the source data is neither. */
    @Test
    void placeKeysNormalise() {
        assertThat(HpaGeocodeProposalService.key("  city centre ")).isEqualTo("CITY CENTRE");
        assertThat(HpaGeocodeProposalService.key("Harare")).isEqualTo("HARARE");
        assertThat(HpaGeocodeProposalService.key(null)).isNull();
    }

    /**
     * The regression that made the previous version inert: it keyed on columns that are null on
     * every existing row. The replacement must join to ndila_locations and key on locality+province.
     */
    @Test
    void proposalsKeyOnLocalityAndProvinceViaTheLocationsJoin() throws Exception {
        String src = source("HpaGeocodeProposalService.java");
        assertThat(src)
                .as("the queue carries no locality; it must join back to ndila_locations")
                .contains("JOIN ndila_locations l")
                .contains("l.owner_entity_id = q.owner_entity_id");
        assertThat(src).contains("g.locality_key = upper(trim(l.locality))");
        assertThat(src).contains("g.province_key = upper(trim(l.province))");
        // Match the SQL/field usage, not the word — the javadoc names both columns when explaining
        // why the old keying failed, and an assertion that trips on prose tests nothing.
        assertThat(src)
                .as("district was the broken key and must not come back")
                .doesNotContain("q.district")
                .doesNotContain("q.proposed_locality")
                .doesNotContain("row.get(\"district\")")
                .doesNotContain("row.get(\"proposed_locality\")");
    }

    /** Only a human-reviewed place may be proposed from, and nothing may publish a pin. */
    @Test
    void onlyReviewedPlacesPropose_andNothingWritesALocation() throws Exception {
        String src = source("HpaGeocodeProposalService.java");
        assertThat(HpaGeocodeProposalService.GAZETTEER_REVIEWED).isEqualTo("REVIEWED");
        assertThat(src).contains("g.status = ?");
        assertThat(src)
                .as("proposals go to the review queue, never to ndila_locations")
                .doesNotContain("INSERT INTO ndila_locations")
                .doesNotContain("UPDATE ndila_locations");
    }

    /**
     * Building the gazetteer must not overwrite a coordinate a reviewer has already placed, or
     * reset their verdict — otherwise every re-run silently discards human work.
     */
    @Test
    void rebuildingTheGazetteerPreservesReviewedPlacements() throws Exception {
        String src = source("HpaGeocodeProposalService.java");
        assertThat(src).contains("ON CONFLICT (tenant_id, locality_key, province_key)");
        assertThat(src).contains("DO UPDATE SET facility_count = EXCLUDED.facility_count");
        assertThat(src)
                .as("a re-run must not touch latitude/longitude/status")
                .doesNotContain("DO UPDATE SET latitude");
    }

    /**
     * An externally geocoded point is exactly the kind that comes back plausible-looking but in the
     * wrong country, so it must pass the same validator as every other coordinate — and must still
     * land as a proposal rather than a published pin.
     */
    @Test
    void externallyGeocodedAddressesAreRevalidatedAndStayProposals() throws Exception {
        String src = source("HpaGeocodeProposalService.java");
        assertThat(src).contains("coordinateValidator.validate(point.latitude(), point.longitude()");
        assertThat(src).contains("proposal_status = 'PROPOSED'");
        assertThat(src)
                .as("an address proposal must never write a location row either")
                .doesNotContain("INSERT INTO ndila_locations");
    }

    /** A geocoding run must not be able to re-place a facility a steward has already settled. */
    @Test
    void alreadyProposedOrResolvedRowsAreNotOverwritten() throws Exception {
        String src = source("HpaGeocodeProposalService.java");
        assertThat(src).contains("AND status = 'PENDING' AND proposal_status IS NULL");
        assertThat(src)
                .as("the geocode input list must exclude rows already carrying a proposal")
                .contains("q.proposal_status IS NULL");
    }

    /** Address precision outranks a suburb centroid but is still not a surveyed coordinate. */
    @Test
    void addressPrecisionIsRecordedAsSuchOnTheQueue() throws Exception {
        String src = source("HpaGeocodeProposalService.java");
        assertThat(src).contains("proposed_precision = ?");
        assertThat(src).contains("PRECISION_ADDRESS, confidenceFor(PRECISION_ADDRESS)");
    }

    /**
     * PO ruling: the 4,028 rows with a province but no locality get NO pin. A province centroid is
     * meaningless for finding a clinic — Harare province is the whole city, Midlands is enormous —
     * so a pin there would send someone the wrong way while looking authoritative.
     *
     * <p>This holds by construction rather than by a check: the gazetteer is only built from rows
     * that HAVE a locality, so a province-only row can never match an entry and can never receive a
     * proposal. This test pins that construction — if the locality filter is ever relaxed, province
     * centroids start flowing and the ruling is silently reversed.</p>
     */
    @Test
    void provinceOnlyRowsCanNeverReceiveAPin() throws Exception {
        String src = source("HpaGeocodeProposalService.java");
        assertThat(src)
                .as("the gazetteer must only be built from places that carry a locality")
                .contains("AND nullif(trim(locality), '') IS NOT NULL");
        assertThat(src)
                .as("matching requires a locality key, so province alone can never resolve")
                .contains("g.locality_key = upper(trim(l.locality))");
    }

    /** The migration must refuse a REVIEWED row with no coordinate, and cap the precision vocabulary. */
    @Test
    void theSchemaRefusesAReviewedPlaceWithNoPoint() throws Exception {
        String sql = Files.readString(
                Path.of("src/main/resources/db/migration/V006__place_gazetteer.sql"));
        assertThat(sql).contains("CHECK (status <> 'REVIEWED' OR latitude IS NOT NULL)");
        assertThat(sql).contains("CHECK (precision_tier IN ('ADDRESS', 'SUBURB_CENTROID'))");
        assertThat(sql).contains("CHECK ((latitude IS NULL) = (longitude IS NULL))");
    }
}
