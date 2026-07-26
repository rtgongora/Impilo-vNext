package zw.gov.mohcc.impilo.ndila.core.location;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.ndila.core.facility.NdilaFacilityMasterSyncService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HAR W4 guard tests — the naming split must not re-open.
 *
 * <p>Three writers put facility rows into {@code ndila_locations} and the V003 seed migration
 * disagreed with the other two, writing {@code location_type='FACILITY'}. That is invisible in a
 * code review and fatal at runtime: {@link NdilaSpatialSearchService} normalises a caller's
 * {@code FACILITY} <em>towards</em> {@code HEALTH_FACILITY} before querying, so the seeded rows —
 * the ones that actually have coordinates — could never match a proximity search.</p>
 */
class NdilaFacilityVocabularyTest {

    // The "canonical constant == what search normalises towards" invariant lives beside the alias
    // map itself, in NdilaSpatialSearchServiceAliasTest — normalizeLocationType is package-private
    // and widening it for a test would be the wrong trade.

    /** Both runtime writers must agree, and must agree with the shared constants. */
    @Test
    void theMasterSyncWriterUsesTheCanonicalVocabulary() {
        assertThat(NdilaFacilityMasterSyncService.OWNER_SERVICE)
                .isEqualTo(NdilaLocationVocabulary.OWNER_SERVICE_TUSO);
        assertThat(NdilaFacilityMasterSyncService.OWNER_ENTITY_TYPE)
                .isEqualTo(NdilaLocationVocabulary.OWNER_ENTITY_TYPE_FACILITY);
        assertThat(NdilaFacilityMasterSyncService.LOCATION_TYPE)
                .isEqualTo(NdilaLocationVocabulary.LOCATION_TYPE_HEALTH_FACILITY);
    }

    /**
     * The HPA importer must not carry a hand-typed owner_service or location_type literal — that is
     * how the split happened the first time.
     */
    @Test
    void theHpaImporterCarriesNoHandTypedVocabularyLiteral() throws IOException {
        String source = readSource("core/location/HpaLocationImportService.java");
        assertThat(source)
                .as("owner_service must come from the shared constant, not a literal")
                .doesNotContain("'TUSO'");
        assertThat(source)
                .as("location_type must come from the shared constant, not a literal")
                .doesNotContain("'HEALTH_FACILITY'");
    }

    /** V005 must normalise the seeded rows onto the canonical spelling, in that direction. */
    @Test
    void theMigrationNormalisesTowardsTheCanonicalSpelling() throws IOException {
        String sql = Files.readString(
                Path.of("src/main/resources/db/migration/V005__canonicalise_facility_location_vocabulary.sql"),
                StandardCharsets.UTF_8);
        assertThat(sql).contains("SET owner_service = 'TUSO'");
        assertThat(sql).contains("location_type = 'HEALTH_FACILITY'");
        assertThat(sql)
                .as("a proposal must never be written onto ndila_locations by this migration")
                .doesNotContain("INSERT INTO ndila_locations");
    }

    private static String readSource(String relative) throws IOException {
        return Files.readString(
                Path.of("src/main/java/zw/gov/mohcc/impilo/ndila/").resolve(relative),
                StandardCharsets.UTF_8);
    }
}
