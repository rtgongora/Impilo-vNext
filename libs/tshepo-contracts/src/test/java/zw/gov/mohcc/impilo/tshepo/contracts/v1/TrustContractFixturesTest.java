package zw.gov.mohcc.impilo.tshepo.contracts.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Java side of the shared tshepo.trust.v1 fixture conformance suite.
 *
 * <p>The same fixtures are validated against TypeScript runtime validation
 * ({@code ui/one-ui-shell/src/lib/trust/__tests__/trust-decision-contracts.test.ts}),
 * a real JSON Schema validator, and OpenAPI-compatible models
 * ({@code scripts/guard/validate-trust-decision-fixtures.py}).</p>
 */
class TrustContractFixturesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final Instant FIXED_NOW = Instant.parse("2026-08-01T12:00:00Z");

    private static final Map<String, Class<?>> TYPES = Map.of(
            "TrustChallengeOutcome", TrustChallengeOutcome.class,
            "TrustDecisionInput", TrustDecisionInput.class,
            "AuthenticationAssurance", AuthenticationAssurance.class,
            "AsyncExecutionReference", AsyncExecutionReference.class,
            "DelegationChain", DelegationChain.class,
            "DelegatedHumanContext", DelegatedHumanContext.class,
            "LawfulBasisDecision", LawfulBasisDecision.class,
            "TrustAuditEvent", TrustAuditEvent.class);

    private static JsonNode fixtures;

    @BeforeAll
    static void loadFixtures() throws Exception {
        Path file = Path.of("..", "..", "contracts", "trust-decision", "fixtures",
                "trust-decision-v1.fixtures.json");
        assertThat(Files.exists(file))
                .as("shared fixture manifest must exist at %s", file.toAbsolutePath().normalize())
                .isTrue();
        fixtures = MAPPER.readTree(Files.readString(file));
    }

    @Test
    @DisplayName("every valid shared fixture deserializes into its canonical Java record")
    void validFixturesConstruct() throws Exception {
        List<String> failed = new ArrayList<>();
        int checked = 0;
        for (JsonNode fixture : fixtures.get("valid")) {
            String name = fixture.get("name").asText();
            Class<?> type = TYPES.get(fixture.get("type").asText());
            assertThat(type).as("fixture %s has known type", name).isNotNull();
            checked++;
            try {
                Object value = MAPPER.treeToValue(fixture.get("data"), type);
                assertThat(value).isNotNull();
            } catch (Exception e) {
                failed.add(name + ": " + e.getMessage());
            }
        }
        assertThat(checked).isGreaterThanOrEqualTo(27);
        assertThat(failed).as("valid fixtures rejected by Java").isEmpty();
    }

    @Test
    @DisplayName("every invalid fixture tagged 'java' is rejected by Jackson or the record constructor")
    void invalidFixturesRejected() {
        List<String> notRejected = new ArrayList<>();
        int checked = 0;
        for (JsonNode fixture : fixtures.get("invalid")) {
            String name = fixture.get("name").asText();
            boolean javaMustReject = false;
            for (JsonNode layer : fixture.get("rejects")) {
                if ("java".equals(layer.asText())) {
                    javaMustReject = true;
                }
            }
            assertThat(javaMustReject)
                    .as("all invalid fixtures must at least be rejected by Java (%s)", name)
                    .isTrue();
            Class<?> type = TYPES.get(fixture.get("type").asText());
            checked++;
            Throwable thrown = catchThrowable(() -> MAPPER.treeToValue(fixture.get("data"), type));
            if (thrown == null) {
                notRejected.add(name);
            }
        }
        assertThat(checked).isGreaterThanOrEqualTo(17);
        assertThat(notRejected).as("invalid fixtures Java failed to reject").isEmpty();
    }

    @Test
    @DisplayName("all 12 TrustChallengeOutcome decisions are covered by valid fixtures")
    void everyChallengeDecisionCovered() throws Exception {
        List<TrustChallengeDecision> seen = new ArrayList<>();
        for (JsonNode fixture : fixtures.get("valid")) {
            if (!"TrustChallengeOutcome".equals(fixture.get("type").asText())) {
                continue;
            }
            TrustChallengeOutcome outcome =
                    MAPPER.treeToValue(fixture.get("data"), TrustChallengeOutcome.class);
            seen.add(outcome.decision());
        }
        assertThat(seen).containsExactlyInAnyOrder(TrustChallengeDecision.values());
    }

    @Test
    @DisplayName("expired async delegation fixture is structurally valid but not executable")
    void expiredAsyncDelegationNotExecutable() throws Exception {
        AsyncExecutionReference expired = MAPPER.treeToValue(
                fixtureByName("valid", "async-delegation-expired").get("data"),
                AsyncExecutionReference.class);
        assertThat(expired.isExecutable(FIXED_NOW)).isFalse();

        AsyncExecutionReference live = MAPPER.treeToValue(
                fixtureByName("valid", "async-delegation-executable").get("data"),
                AsyncExecutionReference.class);
        assertThat(live.isExecutable(FIXED_NOW)).isTrue();
    }

    @Test
    @DisplayName("expired delegation hop fixture reports isExpired at the fixed evaluation instant")
    void expiredDelegationHop() throws Exception {
        DelegatedHumanContext hop = MAPPER.treeToValue(
                fixtureByName("valid", "expired-delegation-hop").get("data"),
                DelegatedHumanContext.class);
        assertThat(hop.isExpired(FIXED_NOW)).isTrue();
    }

    @Test
    @DisplayName("constrained recovery fixture never grants ordinary workforce AAL2")
    void constrainedRecoveryFixtureSemantics() throws Exception {
        AuthenticationAssurance recovery = MAPPER.treeToValue(
                fixtureByName("valid", "assurance-constrained-recovery").get("data"),
                AuthenticationAssurance.class);
        assertThat(recovery.aal()).isEqualTo(2);
        assertThat(recovery.grantsOrdinaryWorkforceAal2()).isFalse();
        assertThat(recovery.isConstrainedRecovery()).isTrue();
    }

    private static JsonNode fixtureByName(String bucket, String name) {
        for (JsonNode fixture : fixtures.get(bucket)) {
            if (name.equals(fixture.get("name").asText())) {
                return fixture;
            }
        }
        throw new AssertionError("fixture not found: " + bucket + "/" + name);
    }
}
