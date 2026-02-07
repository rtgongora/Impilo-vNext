package zw.gov.mohcc.impilo.tshepo.offline.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.tshepo.offline.config.OfflineProperties;
import zw.gov.mohcc.impilo.tshepo.offline.exception.OfflineActionDeniedException;
import zw.gov.mohcc.impilo.tshepo.offline.persistence.OfflineActionLogRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OfflineRulesEngine}.
 *
 * <p>Tests cover the offline action evaluation rules: always-denied actions,
 * capability-based permissions, encounter creation limits, and pharmacy-specific
 * requirements.</p>
 */
@ExtendWith(MockitoExtension.class)
class OfflineRulesEngineTest {

    private static final UUID TOKEN_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");

    @Mock
    private OfflineActionLogRepository actionLogRepo;

    private OfflineProperties offlineProperties;
    private OfflineRulesEngine rulesEngine;

    @BeforeEach
    void setUp() {
        offlineProperties = buildOfflineProperties();
        rulesEngine = new OfflineRulesEngine(offlineProperties, actionLogRepo);
    }

    // ------------------------------------------------------------------
    // evaluate — allowed actions
    // ------------------------------------------------------------------

    @Test
    @DisplayName("evaluate with READ_PATIENT action and matching capability permits the action")
    void evaluate_withAllowedAction_permits() {
        List<String> capabilities = List.of("READ_PATIENT", "CREATE_PROVISIONAL_ENCOUNTER");

        // Act + Assert: should not throw
        assertThatCode(() -> rulesEngine.evaluateAction("READ_PATIENT", capabilities, TOKEN_ID))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("evaluate with CREATE_PROVISIONAL_ENCOUNTER and matching capability permits the action")
    void evaluate_createEncounter_withCapability_permits() {
        List<String> capabilities = List.of("CREATE_PROVISIONAL_ENCOUNTER");
        when(actionLogRepo.countProvisionalEncountersByToken(TOKEN_ID)).thenReturn(0L);

        assertThatCode(() -> rulesEngine.evaluateAction("CREATE_PROVISIONAL_ENCOUNTER", capabilities, TOKEN_ID))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("evaluate with READ_ENCOUNTER action is permitted via READ_PATIENT capability")
    void evaluate_readEncounter_coveredByReadPatient_permits() {
        List<String> capabilities = List.of("READ_PATIENT");

        assertThatCode(() -> rulesEngine.evaluateAction("READ_ENCOUNTER", capabilities, TOKEN_ID))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("evaluate with READ_OBSERVATION action is permitted via READ_PATIENT capability")
    void evaluate_readObservation_coveredByReadPatient_permits() {
        List<String> capabilities = List.of("READ_PATIENT");

        assertThatCode(() -> rulesEngine.evaluateAction("READ_OBSERVATION", capabilities, TOKEN_ID))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("evaluate with READ_MEDICATION action requires READ_MEDICATION capability")
    void evaluate_readMedication_permits() {
        List<String> capabilities = List.of("READ_MEDICATION");

        assertThatCode(() -> rulesEngine.evaluateAction("READ_MEDICATION", capabilities, TOKEN_ID))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("evaluate with DISPENSE_MEDICATION action and explicit capability permits")
    void evaluate_dispenseMedication_withCapability_permits() {
        List<String> capabilities = List.of("DISPENSE_MEDICATION");

        assertThatCode(() -> rulesEngine.evaluateAction("DISPENSE_MEDICATION", capabilities, TOKEN_ID))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("evaluate with CREATE_PROVISIONAL_REGISTRATION and matching capability permits")
    void evaluate_createRegistration_withCapability_permits() {
        List<String> capabilities = List.of("CREATE_PROVISIONAL_REGISTRATION");

        assertThatCode(() -> rulesEngine.evaluateAction("CREATE_PROVISIONAL_REGISTRATION", capabilities, TOKEN_ID))
                .doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------
    // evaluate — disallowed actions
    // ------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {
            "DELETE",
            "DELETE_PATIENT",
            "DELETE_ENCOUNTER",
            "DELETE_MEDICATION",
            "UPDATE_PATIENT",
            "UPDATE_ENCOUNTER",
            "UPDATE_MEDICATION",
            "MERGE_PATIENT",
            "BULK_EXPORT",
            "ADMIN_OVERRIDE"
    })
    @DisplayName("evaluate with always-denied action denies regardless of capabilities")
    void evaluate_withDisallowedAction_denies(String actionType) {
        List<String> capabilities = List.of(
                "READ_PATIENT", "CREATE_PROVISIONAL_ENCOUNTER",
                "CREATE_PROVISIONAL_REGISTRATION", "READ_MEDICATION",
                "DISPENSE_MEDICATION", actionType); // even if the action is in capabilities

        assertThatThrownBy(() -> rulesEngine.evaluateAction(actionType, capabilities, TOKEN_ID))
                .isInstanceOf(OfflineActionDeniedException.class)
                .hasMessageContaining("never permitted in offline mode");
    }

    @Test
    @DisplayName("evaluate with action not covered by capabilities denies")
    void evaluate_withMissingCapability_denies() {
        // Only have READ_PATIENT capability, trying CREATE
        List<String> capabilities = List.of("READ_PATIENT");

        assertThatThrownBy(() -> rulesEngine.evaluateAction(
                "CREATE_PROVISIONAL_ENCOUNTER", capabilities, TOKEN_ID))
                .isInstanceOf(OfflineActionDeniedException.class)
                .hasMessageContaining("does not include permission");
    }

    @Test
    @DisplayName("evaluate with READ_MEDICATION action and only READ_PATIENT capability denies")
    void evaluate_readMedication_withOnlyReadPatient_denies() {
        List<String> capabilities = List.of("READ_PATIENT");

        assertThatThrownBy(() -> rulesEngine.evaluateAction("READ_MEDICATION", capabilities, TOKEN_ID))
                .isInstanceOf(OfflineActionDeniedException.class)
                .hasMessageContaining("does not include permission");
    }

    // ------------------------------------------------------------------
    // evaluate — expired token (encounter limit)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("evaluate denies CREATE_PROVISIONAL_ENCOUNTER when max encounters reached")
    void evaluate_withExpiredToken_denies() {
        List<String> capabilities = List.of("CREATE_PROVISIONAL_ENCOUNTER");
        // Max is 50 encounters
        when(actionLogRepo.countProvisionalEncountersByToken(TOKEN_ID)).thenReturn(50L);

        assertThatThrownBy(() -> rulesEngine.evaluateAction(
                "CREATE_PROVISIONAL_ENCOUNTER", capabilities, TOKEN_ID))
                .isInstanceOf(OfflineActionDeniedException.class)
                .hasMessageContaining("Maximum offline encounters");
    }

    @Test
    @DisplayName("evaluate permits CREATE_PROVISIONAL_ENCOUNTER when under limit")
    void evaluate_createEncounter_underLimit_permits() {
        List<String> capabilities = List.of("CREATE_PROVISIONAL_ENCOUNTER");
        when(actionLogRepo.countProvisionalEncountersByToken(TOKEN_ID)).thenReturn(49L);

        assertThatCode(() -> rulesEngine.evaluateAction(
                "CREATE_PROVISIONAL_ENCOUNTER", capabilities, TOKEN_ID))
                .doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------
    // DISPENSE_MEDICATION specific rule
    // ------------------------------------------------------------------

    @Test
    @DisplayName("evaluate denies DISPENSE_MEDICATION without explicit pharmacy capability")
    void evaluate_dispenseMedication_withoutPharmacyCapability_denies() {
        // Has other capabilities but not DISPENSE_MEDICATION
        List<String> capabilities = List.of("READ_PATIENT", "READ_MEDICATION");

        assertThatThrownBy(() -> rulesEngine.evaluateAction("DISPENSE_MEDICATION", capabilities, TOKEN_ID))
                .isInstanceOf(OfflineActionDeniedException.class)
                .hasMessageContaining("pharmacy capability");
    }

    // ------------------------------------------------------------------
    // Utility method tests
    // ------------------------------------------------------------------

    @Test
    @DisplayName("requiresOCpid returns true only for CREATE_PROVISIONAL_REGISTRATION")
    void requiresOCpid_trueForProvisionalRegistration() {
        assertThat(rulesEngine.requiresOCpid("CREATE_PROVISIONAL_REGISTRATION")).isTrue();
        assertThat(rulesEngine.requiresOCpid("CREATE_PROVISIONAL_ENCOUNTER")).isFalse();
        assertThat(rulesEngine.requiresOCpid("READ_PATIENT")).isFalse();
    }

    @Test
    @DisplayName("mayReferenceOCpid returns true for applicable action types")
    void mayReferenceOCpid_trueForApplicableActions() {
        assertThat(rulesEngine.mayReferenceOCpid("CREATE_PROVISIONAL_ENCOUNTER")).isTrue();
        assertThat(rulesEngine.mayReferenceOCpid("CREATE_PROVISIONAL_REGISTRATION")).isTrue();
        assertThat(rulesEngine.mayReferenceOCpid("DISPENSE_MEDICATION")).isTrue();
        assertThat(rulesEngine.mayReferenceOCpid("READ_PATIENT")).isFalse();
    }

    @Test
    @DisplayName("getAllowedActions returns the configured allowed offline actions")
    void getAllowedActions_returnsConfiguredActions() {
        List<String> allowed = rulesEngine.getAllowedActions();

        assertThat(allowed).containsExactlyInAnyOrder(
                "READ_PATIENT",
                "CREATE_PROVISIONAL_ENCOUNTER",
                "CREATE_PROVISIONAL_REGISTRATION",
                "READ_MEDICATION",
                "DISPENSE_MEDICATION"
        );
    }

    // ------------------------------------------------------------------
    // Test fixture helpers
    // ------------------------------------------------------------------

    private OfflineProperties buildOfflineProperties() {
        return new OfflineProperties(
                8,   // defaultCapabilityTtlHours
                72,  // maxCapabilityTtlHours
                50,  // maxOfflineEncounters
                List.of(
                        "READ_PATIENT",
                        "CREATE_PROVISIONAL_ENCOUNTER",
                        "CREATE_PROVISIONAL_REGISTRATION",
                        "READ_MEDICATION",
                        "DISPENSE_MEDICATION"
                ),
                24,  // offlinePackTtlHours
                100  // reconciliationBatchSize
        );
    }
}
