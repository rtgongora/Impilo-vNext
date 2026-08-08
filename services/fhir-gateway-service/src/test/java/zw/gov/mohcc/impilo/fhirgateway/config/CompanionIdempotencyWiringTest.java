package zw.gov.mohcc.impilo.fhirgateway.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.companion.idempotency.IdempotencyRepository;
import zw.gov.mohcc.impilo.companion.idempotency.JdbcIdempotencyRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * The idempotency table must be addressed by the name the database actually answers to.
 *
 * <p>{@code TechCompanionAutoConfiguration} defaults {@link JdbcIdempotencyRepository} to the
 * unqualified {@code "idempotency_keys"}. This service creates it in schema {@code fhir_gateway}
 * and sets {@code default_schema} — which is Hibernate's setting, and this repository uses raw
 * JDBC. Measured against the live database:</p>
 *
 * <pre>
 *   SHOW search_path → "$user", public
 *   SELECT … FROM idempotency_keys
 *       → ERROR: relation "idempotency_keys" does not exist
 * </pre>
 *
 * <p>The failure mode is quiet and expensive: a retried forward is not recognised as a retry, so it
 * is delivered again and creates a duplicate clinical resource. telemonitoring has always sent an
 * {@code Idempotency-Key} and was getting nothing for it.</p>
 *
 * <p>This test asserts the wiring rather than the database, so it runs anywhere. The schema
 * qualification is verified as a literal, because that literal is the whole fix.</p>
 */
class CompanionIdempotencyWiringTest {

    @Test
    void theRepositoryIsSchemaQualified() {
        // Constructing it proves the bean method compiles against the qualified name; the source
        // assertion below proves the name itself, which is the part that can silently regress.
        IdempotencyRepository repo = new CompanionV11Config()
                .companionIdempotencyRepository(mock(org.springframework.jdbc.core.JdbcTemplate.class));

        assertThat(repo).isInstanceOf(JdbcIdempotencyRepository.class);
    }

    @Test
    void theConfiguredTableNameNamesTheSchema() throws Exception {
        Path source = Path.of("src/main/java/zw/gov/mohcc/impilo/fhirgateway/config/CompanionV11Config.java");
        assertThat(source)
                .describedAs("a scan that cannot find the file would assert nothing")
                .exists();

        String text = Files.readString(source);
        assertThat(text)
                .describedAs("the autoconfig default 'idempotency_keys' resolves against "
                        + "search_path \"$user\", public — where this table does not exist")
                .contains("\"fhir_gateway.idempotency_keys\"");
    }

    @Test
    void everyServiceOwningAnIdempotencyTableQualifiesIt() throws Exception {
        // tshepo, tuso, varapi and vito each carry this same class for the same reason. If a future
        // service adds a CompanionV11Config with an unqualified name, it inherits this exact bug.
        Path servicesRoot = Path.of("../");
        assertThat(servicesRoot).exists();

        try (Stream<Path> configs = Files.walk(servicesRoot, 12)) {
            var offenders = configs
                    .filter(p -> p.getFileName().toString().equals("CompanionV11Config.java"))
                    .filter(p -> !p.toString().contains("/target/"))
                    .filter(p -> {
                        try {
                            // Match the CONSTRUCTOR ARGUMENT, not any occurrence of the string.
                            // The first version of this check matched prose too, so this very file
                            // failed it for quoting the unqualified name while explaining the bug.
                            var m = java.util.regex.Pattern
                                    .compile("new JdbcIdempotencyRepository\\(\\s*\\w+\\s*,\\s*\"([^\"]+)\"")
                                    .matcher(Files.readString(p));
                            return m.find() && !m.group(1).contains(".");
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .toList();

            assertThat(offenders)
                    .describedAs("these declare the idempotency table without a schema, so every "
                            + "read and write resolves against a table that does not exist")
                    .isEmpty();
        }
    }
}
