package zw.gov.mohcc.impilo.clinical.calculator;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The catalogue's guard.
 *
 * <p>This exists because of a specific defect: a clinical-tools surface listed instruments,
 * described them as connected to this service and badged them "Validated", while computing nothing.
 * The listing was the only artefact anyone checked, and a listing is exactly the artefact that
 * cannot detect that failure.</p>
 *
 * <p>So these tests assert the properties a listing cannot: that every advertised calculator
 * actually runs, that it answers in one of the two shapes a surface knows how to render, and that
 * what it says about itself is true. They are deliberately written over the whole catalogue rather
 * than over a named list, so a calculator added tomorrow is held to the same terms without anyone
 * remembering to add it here.</p>
 */
class CalculatorRegistryTest {

    private final CalculatorRegistry registry = new CalculatorRegistry();

    @Test
    void theCatalogueIsNotEmptyAndEveryEntryIsAddressableByItsOwnId() {
        List<CalculatorDescriptor> descriptors = registry.descriptors();

        assertThat(descriptors).isNotEmpty();
        for (CalculatorDescriptor descriptor : descriptors) {
            assertThat(registry.isRegistered(descriptor.id()))
                    .as("%s is listed but not addressable", descriptor.id())
                    .isTrue();
            assertThat(registry.describe(descriptor.id()))
                    .as("%s cannot be described by the id it is listed under", descriptor.id())
                    .contains(descriptor);
        }
    }

    @Test
    void everyAdvertisedCalculatorRunsRatherThanMerelyBeingListed() {
        for (CalculatorDescriptor descriptor : registry.descriptors()) {
            var result = registry.evaluate(descriptor.id(), Map.of());

            assertThat(result)
                    .as("%s is advertised but nothing evaluates it — the exact defect this"
                            + " catalogue was built to make impossible", descriptor.id())
                    .isPresent();
            assertThat(result.get().calculatorId()).isEqualTo(descriptor.id());
        }
    }

    @Test
    void anEmptySubmissionIsRefusedByNameRatherThanScoredOnAbsentData() {
        for (CalculatorDescriptor descriptor : registry.descriptors()) {
            CalculatorResult result = registry.evaluate(descriptor.id(), Map.of()).orElseThrow();

            assertThat(result.computed())
                    .as("%s produced a number from no inputs at all", descriptor.id())
                    .isFalse();
            assertThat(result.refusalReason())
                    .as("%s refused without naming a reason", descriptor.id())
                    .isNotBlank();
            assertThat(result.refusalDetail())
                    .as("%s refused without telling the clinician why", descriptor.id())
                    .isNotBlank();
        }
    }

    @Test
    void aRefusalCarriesTheSameVersionAndCaveatsAsAResult() {
        for (CalculatorDescriptor descriptor : registry.descriptors()) {
            CalculatorResult refusal = registry.evaluate(descriptor.id(), Map.of()).orElseThrow();

            assertThat(refusal.contentVersion())
                    .as("%s refused without a content version, so a stored refusal could not be"
                            + " re-read against the arithmetic that produced it", descriptor.id())
                    .isNotBlank();
            assertThat(refusal.caveats())
                    .as("%s dropped its standing caveats on refusal; an instrument's limitations do"
                            + " not stop applying because it declined to answer", descriptor.id())
                    .isEqualTo(descriptor.caveats());
        }
    }

    @Test
    void everyDescriptorSaysWhereItsArithmeticCameFromAndWhichVersionProducedIt() {
        for (CalculatorDescriptor descriptor : registry.descriptors()) {
            assertThat(descriptor.name()).as("%s has no name", descriptor.id()).isNotBlank();
            assertThat(descriptor.category()).as("%s has no category", descriptor.id()).isNotBlank();
            assertThat(descriptor.summary()).as("%s has no summary", descriptor.id()).isNotBlank();
            assertThat(descriptor.sourceCitation())
                    .as("%s does not name a published source", descriptor.id())
                    .isNotBlank();
            assertThat(descriptor.contentVersion())
                    .as("%s carries no content version", descriptor.id())
                    .isNotBlank();
            assertThat(descriptor.inputs())
                    .as("%s asks for nothing, so it cannot be computing anything", descriptor.id())
                    .isNotEmpty();
        }
    }

    /**
     * Affirmative claims of validation or approval, as distinct from a caveat disclaiming one.
     *
     * <p>"Not validated in acute liver failure" is a warning and must stay. "Clinically validated"
     * is a claim this system has not earned, and it was the substance of the defect: a badge reading
     * "Validated" on a panel that computed nothing. The descriptor record deliberately has no
     * validation field, so the claim cannot be made structurally; this is the backstop against
     * making it in prose.</p>
     */
    private static final List<String> UNEARNED_CLAIMS = List.of(
            "clinically validated", "validated by", "validated for use", "impilo-validated",
            "moh-approved", "mohcc-approved", "clinically approved", "approved for clinical use",
            "ministry-approved");

    @Test
    void noCalculatorClaimsAValidationOrApprovalThisSystemHasNotObtained() {
        for (CalculatorDescriptor descriptor : registry.descriptors()) {
            String prose = String.join(" ",
                    descriptor.name(), descriptor.summary(), descriptor.sourceCitation(),
                    String.join(" ", descriptor.caveats())).toLowerCase(Locale.ROOT);

            for (String claim : UNEARNED_CLAIMS) {
                assertThat(prose)
                        .as("%s claims '%s'; no calculator here has been validated or approved, and"
                                + " that claim is what the clinical-tools defect consisted of",
                                descriptor.id(), claim)
                        .doesNotContain(claim);
            }
        }
    }

    @Test
    void everyCalculatorNamesWhereItsPrimaryTextIsOrIsNot() {
        for (CalculatorDescriptor descriptor : registry.descriptors()) {
            assertThat(descriptor.sourceCitation().toLowerCase(Locale.ROOT))
                    .as("%s cites a source without saying whether the text is held here. A citation"
                            + " a reader cannot follow reads as one they can", descriptor.id())
                    .containsAnyOf("not vendored", "docs/reference", "arithmetic only");
        }
    }

    @Test
    void inputKeysAreUniqueWithinACalculatorAndEnumFieldsOfferChoices() {
        for (CalculatorDescriptor descriptor : registry.descriptors()) {
            Set<String> seen = new HashSet<>();
            for (CalculatorInput input : descriptor.inputs()) {
                assertThat(seen.add(input.key()))
                        .as("%s declares '%s' twice, so one would silently overwrite the other",
                                descriptor.id(), input.key())
                        .isTrue();
                assertThat(input.label())
                        .as("%s.%s has no label to put to a clinician", descriptor.id(), input.key())
                        .isNotBlank();

                boolean enumerated = input.kind() == InputKind.ENUM || input.kind() == InputKind.ENUM_MULTI;
                assertThat(input.options().isEmpty())
                        .as("%s.%s is %s and %s options", descriptor.id(), input.key(), input.kind(),
                                enumerated ? "must offer" : "must not offer")
                        .isEqualTo(!enumerated);
            }
        }
    }

    @Test
    void outputKeysAreUniqueSoNoValueOverwritesAnother() {
        for (CalculatorDescriptor descriptor : registry.descriptors()) {
            CalculatorResult result = registry.evaluate(descriptor.id(), Map.of()).orElseThrow();
            Set<String> seen = new HashSet<>();
            for (CalculatorOutput output : result.outputs()) {
                assertThat(seen.add(output.key()))
                        .as("%s returned '%s' twice", descriptor.id(), output.key())
                        .isTrue();
            }
        }
    }

    @Test
    void anUnreadableValueIsRejectedAsAMalformedRequestRatherThanAsAClinicalRefusal() {
        int exercised = 0;
        for (CalculatorDescriptor descriptor : registry.descriptors()) {
            for (CalculatorInput input : descriptor.inputs()) {
                if (input.kind() != InputKind.DECIMAL && input.kind() != InputKind.INTEGER) {
                    continue;
                }
                Map<String, Object> submitted = new HashMap<>();
                submitted.put(input.key(), "one hundred");

                assertThatUnreadable(descriptor.id(), input.key(),
                        () -> registry.evaluate(descriptor.id(), submitted));
                exercised++;
            }
        }
        assertThat(exercised)
                .as("no numeric field was exercised, so this test proved nothing")
                .isPositive();
    }

    @Test
    void anUnknownCalculatorIsAbsentRatherThanAnEmptyResult() {
        assertThat(registry.evaluate("no-such-calculator", Map.of())).isEmpty();
        assertThat(registry.describe("no-such-calculator")).isEmpty();
        assertThat(registry.isRegistered("no-such-calculator")).isFalse();
    }

    private static void assertThatUnreadable(String calculatorId, String field, Runnable evaluation) {
        try {
            evaluation.run();
        } catch (CalculatorInputException expected) {
            assertThat(expected.key())
                    .as("%s did not say which field was unreadable, so a form cannot mark it",
                            calculatorId)
                    .isEqualTo(field);
            assertThat(expected.getMessage()).isNotBlank();
            return;
        }
        throw new AssertionError(calculatorId + "." + field + " accepted an unreadable value."
                + " A value that is present but unreadable must raise CalculatorInputException, so"
                + " it becomes a 400 rather than being laundered into a clinical refusal that reads"
                + " as a property of the patient.");
    }
}
