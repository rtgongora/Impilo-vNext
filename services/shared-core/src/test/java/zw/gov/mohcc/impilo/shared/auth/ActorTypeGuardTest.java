package zw.gov.mohcc.impilo.shared.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.ActorType;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActorTypeGuardTest {

    private static TrustContext ctx(String actorType) {
        return new TrustContext(UUID.randomUUID(), "actor-1", actorType, "PAYMENT",
                "dev", UUID.randomUUID(), UUID.randomUUID(), null, null, AccessMode.INTERNAL);
    }

    /**
     * The guard against a seventh vocabulary. Every token in every constant set must be a value some
     * client actually emits — otherwise the set reads as if it enforces a distinction it cannot,
     * which is exactly the defect this class was extracted to stop. Break this by adding
     * {@code "REGISTRY_ADMIN"} to either constant and the test goes red.
     */
    @Test
    void everyConstantNamesOnlyAnEmittableActorType() {
        Set<String> emittable = Arrays.stream(ActorType.values())
                .map(Enum::name)
                .collect(Collectors.toSet());

        assertThat(emittable)
                .as("positive control — the enum must actually hold the values the sets are built from")
                .contains("SYSTEM", "SERVICE", "OPERATOR");

        assertThat(ActorTypeGuard.BACK_OFFICE_WRITERS).isSubsetOf(emittable);
        assertThat(ActorTypeGuard.MACHINE_ONLY).isSubsetOf(emittable);
    }

    @Test
    void backOfficeWritersExcludesEveryPersonActingForThemselvesOrAPatient() {
        assertThat(ActorTypeGuard.BACK_OFFICE_WRITERS)
                .doesNotContain("CITIZEN", "PROVIDER", "CAREGIVER");
    }

    @ParameterizedTest
    @ValueSource(strings = {"SYSTEM", "SERVICE", "OPERATOR"})
    void backOfficeWriterIsAdmitted(String actorType) {
        assertThatCode(() -> ActorTypeGuard.require(
                ctx(actorType), ActorTypeGuard.BACK_OFFICE_WRITERS, "posting a journal"))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"CITIZEN", "PROVIDER", "CAREGIVER", "REGISTRY_ADMIN", ""})
    void nonBackOfficeActorIsRefused(String actorType) {
        assertThatThrownBy(() -> ActorTypeGuard.require(
                ctx(actorType), ActorTypeGuard.BACK_OFFICE_WRITERS, "posting a journal"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void refusalNamesWhatIsRequiredAndWhatWasPresented() {
        assertThatThrownBy(() -> ActorTypeGuard.require(
                ctx("CITIZEN"), ActorTypeGuard.BACK_OFFICE_WRITERS, "granting a fee waiver"))
                .hasMessageContaining("Granting a fee waiver")
                .hasMessageContaining("OPERATOR")
                .hasMessageContaining("SERVICE")
                .hasMessageContaining("SYSTEM")
                .hasMessageContaining("'CITIZEN'");
    }

    @Test
    void absentActorTypeIsNamedAsAbsentRatherThanAsAnEmptyString() {
        assertThatThrownBy(() -> ActorTypeGuard.require(
                ctx(null), ActorTypeGuard.BACK_OFFICE_WRITERS, "granting a fee waiver"))
                .hasMessageContaining("no actor type");
    }

    @Test
    void nullContextIsRefusedRatherThanWavedThrough() {
        assertThatThrownBy(() -> ActorTypeGuard.require(
                (TrustContext) null, ActorTypeGuard.BACK_OFFICE_WRITERS, "granting a fee waiver"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void actorTypeIsMatchedCaseAndWhitespaceInsensitively() {
        assertThatCode(() -> ActorTypeGuard.require(
                ctx("  operator  "), ActorTypeGuard.BACK_OFFICE_WRITERS, "posting a journal"))
                .doesNotThrowAnyException();
    }

    @Test
    void machineOnlyRefusesAnOperator() {
        assertThat(ActorTypeGuard.permits(ActorTypeGuard.MACHINE_ONLY, "OPERATOR")).isFalse();
        assertThat(ActorTypeGuard.permits(ActorTypeGuard.MACHINE_ONLY, "SYSTEM")).isTrue();
    }
}
