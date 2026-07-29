package zw.gov.mohcc.impilo.costa.v11;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import zw.gov.mohcc.impilo.companion.harness.GoldenContractSuite;

/**
 * Golden Contract integration test for COSTA v1.1 compliance, against a REAL Postgres.
 *
 * <p>costa entities use PostgreSQL-specific DDL (text[] arrays, inline enum(...) types)
 * that H2 cannot create via ddl-auto, so this cannot run on the H2 unit profile — it is
 * wired into the {@code it-postgres} Maven profile (Failsafe) and runs against a Postgres
 * supplied via {@code -Dit.pg.url}; it self-skips without it. {@code @ActiveProfiles("it")}
 * loads the production application.yml (real Postgres + Flyway + PostgreSQL dialect) plus a
 * minimal {@code application-it.yml} that opens the flag-gated test security chain so the
 * v1.1 probe endpoints are reachable. Ordinary {@code mvn test}/{@code verify} never runs it.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@EnabledIfSystemProperty(named = "it.pg.url", matches = ".+")
public class CostaGoldenContractIT extends GoldenContractSuite {

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getProperty("it.pg.url"));
        registry.add("spring.datasource.username", () -> System.getProperty("it.pg.user"));
        registry.add("spring.datasource.password", () -> System.getProperty("it.pg.pass"));
    }

    @Override
    protected String getReadEndpointOverride() {
        return "/internal/v1/health";
    }

    @Override
    protected String getCommandEndpointOverride() {
        return "/internal/v1/test-command";
    }
}
