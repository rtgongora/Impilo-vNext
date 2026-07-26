package zw.gov.mohcc.impilo.reproductive.contraception;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ContraceptiveCoverageCalculatorTest {

    private final ContraceptiveMethodCatalog catalog = ContraceptiveMethodCatalog.engineeringSeed();

    private ContraceptiveMethodCatalog.ContraceptiveMethodProfile profile(ContraceptiveMethod m) {
        return catalog.profile(m);
    }

    @Test
    void anImplantWithNoInsertionDateIsUnknown_neverCovered() {
        // THE rule. A woman shown as protected by a method nobody can date is a confident answer
        // produced from an absence, and she acts on it.
        assertEquals(CoverageStatus.UNKNOWN, ContraceptiveCoverageCalculator.coverageOn(
                profile(ContraceptiveMethod.IMPLANT_LEVONORGESTREL_2ROD),
                null, null, LocalDate.of(2026, 6, 1)));
    }

    @Test
    void anInjectionWithNoRecordedDoseIsUnknown_evenWhenTheMethodWasStartedLongAgo() {
        // Falling back to the start date would report a woman four months past her injection as
        // covered because she once began the method.
        assertEquals(CoverageStatus.UNKNOWN, ContraceptiveCoverageCalculator.coverageOn(
                profile(ContraceptiveMethod.INJECTABLE_DMPA_IM),
                LocalDate.of(2025, 1, 1), null, LocalDate.of(2026, 6, 1)));
    }

    @Test
    void aMethodTheCatalogueDoesNotDescribeIsUnknown() {
        assertEquals(CoverageStatus.UNKNOWN, ContraceptiveCoverageCalculator.coverageOn(
                null, LocalDate.of(2026, 1, 1), null, LocalDate.of(2026, 6, 1)));
    }

    @Test
    void anImplantExpiresOnItsClinicalLifeNotItsShelfLife() {
        LocalDate inserted = LocalDate.of(2022, 3, 1);
        var jadelle = profile(ContraceptiveMethod.IMPLANT_LEVONORGESTREL_2ROD);

        assertEquals(LocalDate.of(2027, 3, 1),
                ContraceptiveCoverageCalculator.expiresOn(jadelle, inserted));
        assertEquals(CoverageStatus.COVERED, ContraceptiveCoverageCalculator.coverageOn(
                jadelle, inserted, null, LocalDate.of(2026, 6, 1)));
        assertEquals(CoverageStatus.EXPIRED, ContraceptiveCoverageCalculator.coverageOn(
                jadelle, inserted, null, LocalDate.of(2027, 4, 1)));
    }

    @Test
    void aLateInjectionInsideTheGraceWindowIsStillProtecting() {
        // The late window is not administrative tolerance — it is the period in which the method is
        // still working, which is why a woman inside it needs no backup and a woman past it does.
        var dmpa = profile(ContraceptiveMethod.INJECTABLE_DMPA_IM);
        LocalDate lastDose = LocalDate.of(2026, 1, 1);
        var window = ContraceptiveCoverageCalculator.doseWindow(dmpa, lastDose);

        assertEquals(LocalDate.of(2026, 4, 2), window.dueOn());
        assertEquals(LocalDate.of(2026, 4, 30), window.closesOn());

        assertEquals(CoverageStatus.COVERED,
                ContraceptiveCoverageCalculator.coverageOn(dmpa, null, lastDose, LocalDate.of(2026, 3, 1)));
        assertEquals(CoverageStatus.DUE,
                ContraceptiveCoverageCalculator.coverageOn(dmpa, null, lastDose, LocalDate.of(2026, 4, 2)));
        assertEquals(CoverageStatus.LATE_WITHIN_GRACE,
                ContraceptiveCoverageCalculator.coverageOn(dmpa, null, lastDose, LocalDate.of(2026, 4, 20)));
        assertEquals(CoverageStatus.LAPSED,
                ContraceptiveCoverageCalculator.coverageOn(dmpa, null, lastDose, LocalDate.of(2026, 5, 1)));
    }

    @Test
    void aCondomIsNotReportedAsContinuouslyCovering() {
        // Asserting coverage from a recorded method would be a claim about behaviour the record
        // cannot see. A condom protects when it is used.
        assertEquals(CoverageStatus.NOT_APPLICABLE, ContraceptiveCoverageCalculator.coverageOn(
                profile(ContraceptiveMethod.MALE_CONDOM),
                LocalDate.of(2026, 1, 1), null, LocalDate.of(2026, 6, 1)));
    }

    @Test
    void daysRemainingGoesNegativeRatherThanClampingToZero() {
        var jadelle = profile(ContraceptiveMethod.IMPLANT_LEVONORGESTREL_2ROD);
        LocalDate inserted = LocalDate.of(2022, 3, 1);
        assertEquals(-31, ContraceptiveCoverageCalculator.daysOfProtectionRemaining(
                jadelle, inserted, null, LocalDate.of(2027, 4, 1)));
    }

    @Test
    void everyNamedMethodHasAProfile_andOtherDeliberatelyDoesNot() {
        // A method the catalogue does not describe reports UNKNOWN forever, so a gap here is a
        // method that can be recorded and never assessed. This test found exactly that on its first
        // run: OTHER had no profile.
        //
        // OTHER keeps none, on purpose. It means "a method not on this list", and nothing can be
        // said about how long an unnamed method protects for. Giving it a no-expiry profile would
        // resolve to NOT_APPLICABLE, which claims there is nothing to compute — when the truth is
        // that we do not know what she is using. UNKNOWN is the honest answer and the one that
        // prompts someone to ask.
        for (ContraceptiveMethod method : ContraceptiveMethod.values()) {
            if (method == ContraceptiveMethod.OTHER) {
                continue;
            }
            assertNotNull(catalog.profile(method), "no catalogue profile for " + method);
        }
        assertNull(catalog.profile(ContraceptiveMethod.OTHER));
        assertEquals(CoverageStatus.UNKNOWN, ContraceptiveCoverageCalculator.coverageOn(
                catalog.profile(ContraceptiveMethod.OTHER),
                LocalDate.of(2026, 1, 1), null, LocalDate.of(2026, 6, 1)));
    }

    @Test
    void theCatalogueDeclaresItselfUnratified() {
        assertEquals("PENDING_MOHCC_RATIFICATION", catalog.approvalStatus());
        assertTrue(catalog.contentVersion().contains("engineering-seed"));
    }

    @Test
    void everyLarcMethodRequiresRemovalAndEveryInjectableIsScheduled() {
        // Structural, and load-bearing: a method requiring removal that is never removed leaves a
        // woman believing she is protected by something that expired years ago.
        assertTrue(ContraceptiveMethod.IMPLANT_ETONOGESTREL_1ROD.requiresRemoval());
        assertTrue(ContraceptiveMethod.IUD_COPPER_T380A.requiresRemoval());
        assertTrue(ContraceptiveMethod.INJECTABLE_DMPA_IM.scheduled());
        assertFalse(ContraceptiveMethod.MALE_CONDOM.requiresRemoval());
        assertTrue(ContraceptiveMethod.MALE_CONDOM.protectsAgainstSti());
        assertFalse(ContraceptiveMethod.IMPLANT_ETONOGESTREL_1ROD.protectsAgainstSti());
    }
}
