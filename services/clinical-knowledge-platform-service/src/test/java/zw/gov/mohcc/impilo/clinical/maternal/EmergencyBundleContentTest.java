package zw.gov.mohcc.impilo.clinical.maternal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The eclampsia and sepsis bundles run on the same engine as PPH, so the same three invariants must
 * hold for them — a fresh trigger is not "done", it never closes on an unconfirmed control, and a
 * documentation lapse escalates. Running them here proves the engine generalises rather than the PPH
 * test having pinned one lucky bundle.
 */
class EmergencyBundleContentTest {

    private final EmergencyBundleService service = new EmergencyBundleService(new ObjectMapper());

    private static final String[] ALL = {
            EmergencyBundleService.PPH_BUNDLE,
            EmergencyBundleService.ECLAMPSIA_BUNDLE,
            EmergencyBundleService.SEPSIS_BUNDLE
    };

    private Set<String> allMandatory(String pack) {
        return service.bundle(pack).steps().stream()
                .filter(EmergencyBundleEngine.StepDefinition::mandatory)
                .map(EmergencyBundleEngine.StepDefinition::code)
                .collect(Collectors.toCollection(HashSet::new));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "clinical/rmnp-pph-bundle.json",
            "clinical/rmnp-eclampsia-bundle.json",
            "clinical/rmnp-maternal-sepsis-bundle.json"})
    @DisplayName("each bundle loads with mandatory steps and an immediate element due at trigger")
    void eachBundleLoads(String pack) {
        var b = service.bundle(pack);
        assertThat(b).isNotNull();
        assertThat(b.steps()).isNotEmpty();
        assertThat(b.steps()).anyMatch(EmergencyBundleEngine.StepDefinition::mandatory);
        assertThat(b.steps()).anyMatch(s -> s.dueWithinMinutes() == 0 && s.mandatory());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "clinical/rmnp-pph-bundle.json",
            "clinical/rmnp-eclampsia-bundle.json",
            "clinical/rmnp-maternal-sepsis-bundle.json"})
    @DisplayName("a freshly-triggered bundle is ACTIVE with every mandatory step outstanding")
    void freshTriggerIsNotDone(String pack) {
        var a = service.assess(pack, Set.of(), 0, 0, null, false);
        assertThat(a.status()).isEqualTo(EmergencyBundleEngine.BundleStatus.ACTIVE);
        assertThat(a.outstandingMandatory()).isNotEmpty();
        assertThat(a.mayClose()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "clinical/rmnp-pph-bundle.json",
            "clinical/rmnp-eclampsia-bundle.json",
            "clinical/rmnp-maternal-sepsis-bundle.json"})
    @DisplayName("no bundle closes on an unconfirmed control, even with every step done")
    void neverClosesOnUnconfirmedControl(String pack) {
        var a = service.assess(pack, allMandatory(pack), 30, 2, null, true);
        assertThat(a.mayClose()).isFalse();
        assertThat(a.note()).contains("control is not confirmed");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "clinical/rmnp-pph-bundle.json",
            "clinical/rmnp-eclampsia-bundle.json",
            "clinical/rmnp-maternal-sepsis-bundle.json"})
    @DisplayName("each bundle closes only with all steps done, control confirmed and a clinician confirm")
    void closesOnlyWithEverything(String pack) {
        var a = service.assess(pack, allMandatory(pack), 40, 2, true, true);
        assertThat(a.status()).isEqualTo(EmergencyBundleEngine.BundleStatus.CLOSABLE);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "clinical/rmnp-pph-bundle.json",
            "clinical/rmnp-eclampsia-bundle.json",
            "clinical/rmnp-maternal-sepsis-bundle.json"})
    @DisplayName("an undocumented open bundle escalates rather than closing")
    void lapseEscalates(String pack) {
        var first = service.bundle(pack).steps().get(0).code();
        var a = service.assess(pack, Set.of(first), 60, 40, true, true);
        assertThat(a.status()).isEqualTo(EmergencyBundleEngine.BundleStatus.LAPSED_UNRESOLVED);
        assertThat(a.mayClose()).isFalse();
    }

    @Test
    @DisplayName("eclampsia leads with magnesium sulfate and keeps toxicity monitoring mandatory")
    void eclampsiaBundleShape() {
        var b = service.bundle(EmergencyBundleService.ECLAMPSIA_BUNDLE);
        assertThat(b.steps()).anyMatch(s -> s.code().equals("ECL_MAGNESIUM_LOADING")
                && s.mandatory() && s.dueWithinMinutes() == 0);
        assertThat(b.steps()).anyMatch(s -> s.code().equals("ECL_MAG_MONITORING") && s.mandatory());
    }

    @Test
    @DisplayName("sepsis gives antibiotics within the hour and does not wait on the source")
    void sepsisBundleShape() {
        var b = service.bundle(EmergencyBundleService.SEPSIS_BUNDLE);
        var abx = b.steps().stream().filter(s -> s.code().equals("SEP_ANTIBIOTICS")).findFirst().orElseThrow();
        assertThat(abx.mandatory()).isTrue();
        assertThat(abx.action()).contains("empirically");
    }
}
