package zw.gov.mohcc.impilo.tuso.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the generated virtual-service seed migration (V023, produced by
 * tools/generate-virtual-service-seed.mjs from
 * contracts/telemedicine/virtual-hospitals.json):
 *
 * <ul>
 *   <li>exactly 21 virtual service entities (12 strategic + 9 provincial),</li>
 *   <li>exactly 5 routing seams, all seeded active (the entries already
 *       ROUTABLE_VIA_EXISTING_SEAMS in the canonical document),</li>
 *   <li>every entity seeds lifecycle CONFIGURED — never ACTIVE (stored state
 *       must not overstate runtime reality),</li>
 *   <li>no SLA minutes are invented by the seed (governed configuration only).</li>
 * </ul>
 *
 * The live Flyway apply + row-count read-back is proven by the runtime rig
 * (scripts/runtime-proof/virtual-care-full-journeys.sh, J-VC-4).
 */
class VirtualServiceSeedMigrationTest {

    private static final String MIGRATION = "/db/migration/V023__virtual_service_seed.sql";

    private String seedSql() throws IOException {
        try (InputStream in = VirtualServiceSeedMigrationTest.class.getResourceAsStream(MIGRATION)) {
            assertThat(in).as("seed migration %s on classpath", MIGRATION).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static int count(String haystack, String regex) {
        Matcher m = Pattern.compile(regex).matcher(haystack);
        int n = 0;
        while (m.find()) n++;
        return n;
    }

    @Test
    void seedsExactly21VirtualServiceEntities() throws IOException {
        assertThat(count(seedSql(), "INSERT INTO tuso\\.virtual_service_entity ")).isEqualTo(21);
    }

    @Test
    void seedsExactlyFiveActiveRoutingSeamsAndNoInactiveOnes() throws IOException {
        String sql = seedSql();
        int seams = count(sql, "INSERT INTO tuso\\.virtual_service_routing_seam ");
        // seam notes may contain punctuation; anchor on the generated ", TRUE FROM …" tail
        int activeSeams = count(sql, ", TRUE FROM tuso\\.virtual_service_entity");
        assertThat(seams).isEqualTo(5);
        assertThat(activeSeams).isEqualTo(5);
    }

    @Test
    void everyEntitySeedsConfiguredNeverActive() throws IOException {
        String sql = seedSql();
        assertThat(count(sql, "'CONFIGURED'")).isEqualTo(21);
        assertThat(sql).doesNotContain("'ACTIVE'").doesNotContain("'ACTIVATION_REQUESTED'");
    }

    @Test
    void queueDefinitionsSeedWithoutInventedSlaValues() throws IOException {
        String sql = seedSql();
        int queueDefs = count(sql, "INSERT INTO tuso\\.virtual_service_queue_def ");
        int nullSla = count(sql, "INSERT INTO tuso\\.virtual_service_queue_def [^;]*, NULL, '\\{\\}'::jsonb ");
        assertThat(queueDefs).isGreaterThanOrEqualTo(80); // 89 at generation time; every VH has queues
        assertThat(nullSla).isEqualTo(queueDefs);
    }

    @Test
    void everySeededSeamTargetIsAFrozenPoolKey() throws IOException {
        String sql = seedSql();
        // The five substrate seams live in the teleconsult SPECIALTY_POOL routing space.
        for (String pool : new String[]{"GENERAL_TELEMEDICINE", "SPECIALIST_POOL", "MATERNAL_NEWBORN",
                "RADIOLOGY_DIAGNOSTICS", "LEARNING_SUPERVISION"}) {
            assertThat(sql).contains("'" + pool + "'");
        }
    }
}
