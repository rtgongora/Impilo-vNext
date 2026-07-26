package zw.gov.mohcc.impilo.ndila.core.location;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HAR W7 guard tests — the address recovery must stay a column move, and must not run twice.
 *
 * <p>Two people independently concluded the importer was discarding street addresses, because live
 * showed {@code address_line1 = 0}. It was not: the original INSERT bound its parameters
 * positionally and the address landed in {@code description}. All 2,281 were present the whole
 * time. These tests pin the recovery so a future edit cannot turn a safe move into a destructive
 * one.</p>
 */
class HpaAddressRecoveryTest {

    private static String migration() throws Exception {
        return Files.readString(Path.of(
                "src/main/resources/db/migration/V007__recover_misfiled_hpa_addresses.sql"));
    }

    /** It must move, never delete, and never touch a row outside the HPA import. */
    @Test
    void theRecoveryIsScopedToHpaImportedRows() throws Exception {
        String sql = migration();
        assertThat(sql).contains("owner_entity_id LIKE 'HPA-%'");
        assertThat(sql).contains("source = 'IMPORT'");
        assertThat(sql)
                .as("a recovery that deletes is not a recovery")
                .doesNotContain("DELETE")
                .doesNotContain("TRUNCATE")
                .doesNotContain("DROP TABLE");
    }

    /**
     * Idempotent and non-destructive: a row that already has an address — because a steward or a
     * facility claim supplied one — must not be overwritten by a stale description.
     */
    @Test
    void anExistingAddressIsNeverOverwritten() throws Exception {
        String sql = migration();
        assertThat(sql).contains("nullif(trim(coalesce(address_line1, '')), '') IS NULL");
        assertThat(sql).contains("nullif(trim(coalesce(description,   '')), '') IS NOT NULL");
    }

    /** The description is cleared, so the same text cannot be re-read as two different facts. */
    @Test
    void theSourceColumnIsClearedSoTheDataHasOneHome() throws Exception {
        assertThat(migration()).contains("description   = NULL");
    }

    /**
     * The W4 importer must already write the address to the right column, or this migration would
     * be undone by the next import.
     */
    @Test
    void theImporterAlreadyWritesTheCorrectColumn() throws Exception {
        String src = Files.readString(Path.of(
                "src/main/java/zw/gov/mohcc/impilo/ndila/core/location/HpaLocationImportService.java"));
        assertThat(src).contains("address_line1");
        assertThat(src)
                .as("description must be explicitly null in the insert, not positionally reused")
                .contains("NULL, ?,");
    }
}
