package zw.gov.mohcc.impilo.reproductive.dating;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PregnancyDatingResolverTest {

    private final RedatingPolicy policy = RedatingPolicy.engineeringSeed();

    @Test
    void naegelesRuleMatchesTheWhoSchedulingLibrary() {
        // Transcribed from WHO SMART ANC ANCS01: EDD = LMP + 280 days. This is the one number every
        // maternal rule depends on, so it is pinned rather than trusted.
        LocalDate lmp = LocalDate.of(2026, 1, 1);
        assertEquals(LocalDate.of(2026, 10, 8), EddCalculator.fromLastMenstrualPeriod(lmp));
        assertEquals(280, EddCalculator.NAEGELE_LMP_TO_EDD_DAYS);
    }

    @Test
    void gestationalAgeIsWholeDaysSinceTheNotionalStart() {
        LocalDate edd = LocalDate.of(2026, 10, 8);
        assertEquals(0, EddCalculator.gestationalAgeDays(edd, LocalDate.of(2026, 1, 1)));
        assertEquals(228, EddCalculator.gestationalAgeDays(edd, LocalDate.of(2026, 8, 17)));
        assertEquals("32+4", EddCalculator.gestationalAge(edd, LocalDate.of(2026, 8, 17)).display());
    }

    @Test
    void anUndatedPregnancyIsNullRatherThanTerm() {
        // The failure this whole class is built to prevent. A null EDD must propagate as null; a
        // pregnancy of unknown gestation quietly rendering as term changes neonatal preparation,
        // steroid decisions and whether anyone worries about a small fetus.
        assertNull(EddCalculator.fromLastMenstrualPeriod(null));
        assertNull(EddCalculator.gestationalAgeDays(null, LocalDate.of(2026, 8, 17)));
        assertNull(GestationalAge.ofDays(null));
        assertNull(PregnancyDatingResolver.resolve(List.of(), LocalDate.of(2026, 8, 17), policy));
    }

    @Test
    void aDateBeforeThePregnancyBeganIsNotANegativeGestation() {
        LocalDate edd = LocalDate.of(2026, 10, 8);
        assertNull(EddCalculator.gestationalAgeDays(edd, LocalDate.of(2025, 12, 1)));
    }

    @Test
    void anEarlyScanSupersedesACertainLmpWhenTheyDisagreeBeyondAWeek() {
        LocalDate lmp = LocalDate.of(2026, 1, 1);
        DatingBasis byLmp = DatingBasis.fromLmp(lmp, true);
        // Scanned at 10 weeks on 1 April; that implies an LMP about 21 January.
        DatingBasis byScan = DatingBasis.fromUltrasound(LocalDate.of(2026, 4, 1), 70, "USS-1");

        PregnancyDating initial = PregnancyDatingResolver.resolve(List.of(byLmp), lmp, policy);
        PregnancyDating revised = PregnancyDatingResolver.revise(initial, byScan, LocalDate.of(2026, 4, 1), policy);

        assertTrue(revised.redatingApplied());
        assertEquals(DatingMethod.ULTRASOUND_CRL_FIRST_TRIMESTER, revised.method());
        assertEquals(initial.estimatedDeliveryDate(), revised.supersededBasis().estimatedDeliveryDate());
        assertTrue(revised.discrepancyDays() > 7);
        assertTrue(revised.rationale().contains("supersedes"));
    }

    @Test
    void aScanThatAgreesWithinToleranceDoesNotChurnTheDate() {
        // Two methods agreeing within a week are not in conflict. Rewriting the EDD to move it three
        // days would recompute every antenatal contact for no clinical gain.
        LocalDate lmp = LocalDate.of(2026, 1, 1);
        PregnancyDating initial = PregnancyDatingResolver.resolve(
                List.of(DatingBasis.fromLmp(lmp, true)), lmp, policy);

        // Scanned at 12+3 on 26 March — within a few days of the LMP dating.
        DatingBasis byScan = DatingBasis.fromUltrasound(LocalDate.of(2026, 3, 26), 87, "USS-1");
        PregnancyDating revised = PregnancyDatingResolver.revise(initial, byScan, LocalDate.of(2026, 3, 26), policy);

        assertFalse(revised.redatingApplied());
        assertEquals(initial.estimatedDeliveryDate(), revised.estimatedDeliveryDate());
        assertTrue(revised.rationale().contains("within the"));
    }

    @Test
    void aLateScanNeverRedates_evenWhenItDisagreesWildly() {
        // THE clinically load-bearing rule. After about 22 weeks a scan measures size, not age.
        // Redating a growth-restricted fetus onto its own measurements makes it a normal younger
        // one and the finding disappears from the chart at the moment it starts to matter.
        LocalDate lmp = LocalDate.of(2026, 1, 1);
        PregnancyDating initial = PregnancyDatingResolver.resolve(
                List.of(DatingBasis.fromLmp(lmp, true)), lmp, policy);

        // Scanned on 1 September; LMP dating says 34+4, the scan measures only 30+0 — a small fetus.
        DatingBasis lateScan = DatingBasis.fromUltrasound(LocalDate.of(2026, 9, 1), 210, "USS-3");

        assertEquals(DatingMethod.ULTRASOUND_LATE_SECOND_OR_THIRD, lateScan.method());
        assertFalse(PregnancyDatingResolver.redatingIndicated(initial, lateScan, policy));

        PregnancyDating revised = PregnancyDatingResolver.revise(initial, lateScan, LocalDate.of(2026, 9, 1), policy);
        assertFalse(revised.redatingApplied());
        assertEquals(initial.estimatedDeliveryDate(), revised.estimatedDeliveryDate());
        assertTrue(revised.rationale().contains("measures size"));
    }

    @Test
    void aRejectedBasisIsStillRecorded() {
        // "We saw the scan and kept the LMP dating" is clinical information. Without it the next
        // clinician sees a scan that appears to have been ignored.
        LocalDate lmp = LocalDate.of(2026, 1, 1);
        PregnancyDating initial = PregnancyDatingResolver.resolve(
                List.of(DatingBasis.fromLmp(lmp, true)), lmp, policy);
        DatingBasis lateScan = DatingBasis.fromUltrasound(LocalDate.of(2026, 9, 1), 210, "USS-3");

        PregnancyDating revised = PregnancyDatingResolver.revise(initial, lateScan, LocalDate.of(2026, 9, 1), policy);

        assertNotNull(revised.supersededBasis());
        assertEquals("USS-3", revised.supersededBasis().sourceRef());
        assertNotNull(revised.rationale());
        assertTrue(revised.consideredBases().size() >= 2);
    }

    @Test
    void assistedConceptionAccountsForTheEmbryosAgeAtTransfer() {
        // A day-5 blastocyst transferred today is already five days past conception. Ignoring that
        // is a five-day error in the EDD, which is the difference between 39 and 40 weeks at term.
        LocalDate transfer = LocalDate.of(2026, 1, 10);
        assertEquals(transfer.plusDays(266), EddCalculator.fromAssistedConception(transfer, 0));
        assertEquals(transfer.plusDays(261), EddCalculator.fromAssistedConception(transfer, 5));
    }

    @Test
    void assistedConceptionOutranksEverythingElse() {
        LocalDate transfer = LocalDate.of(2026, 1, 10);
        PregnancyDating dating = PregnancyDatingResolver.resolve(List.of(
                DatingBasis.fromLmp(LocalDate.of(2025, 12, 20), true),
                DatingBasis.fromAssistedConception(transfer, 5, "IVF-1")), transfer, policy);

        assertEquals(DatingMethod.ASSISTED_CONCEPTION, dating.method());
        assertEquals(PregnancyDating.DatingConfidence.EXACT, dating.confidence());
        assertEquals(0, dating.plusOrMinusDays());
    }

    @Test
    void anUncertainLmpNeverOverturnsAScan() {
        LocalDate scanDay = LocalDate.of(2026, 4, 1);
        PregnancyDating initial = PregnancyDatingResolver.resolve(
                List.of(DatingBasis.fromUltrasound(scanDay, 70, "USS-1")), scanDay, policy);

        PregnancyDating revised = PregnancyDatingResolver.revise(
                initial, DatingBasis.fromLmp(LocalDate.of(2025, 11, 1), false), scanDay, policy);

        assertFalse(revised.redatingApplied());
        assertEquals(DatingMethod.ULTRASOUND_CRL_FIRST_TRIMESTER, revised.method());
    }

    @Test
    void confidenceAndUncertaintyTrackTheMethodThatProducedTheDate() {
        assertEquals(PregnancyDating.DatingConfidence.HIGH,
                PregnancyDating.DatingConfidence.forMethod(DatingMethod.ULTRASOUND_CRL_FIRST_TRIMESTER));
        assertEquals(PregnancyDating.DatingConfidence.UNKNOWN,
                PregnancyDating.DatingConfidence.forMethod(DatingMethod.UNKNOWN));
        assertNull(PregnancyDating.DatingConfidence.plusOrMinusDays(DatingMethod.UNKNOWN));
        assertEquals(5, PregnancyDating.DatingConfidence.plusOrMinusDays(
                DatingMethod.ULTRASOUND_CRL_FIRST_TRIMESTER));
    }

    @Test
    void theDatingHierarchyIsOrderedAsClinicalContent() {
        // A late scan must rank BELOW a certain LMP. If this ordering is ever "tidied" the late-scan
        // redating protection above silently stops working.
        assertTrue(DatingMethod.ASSISTED_CONCEPTION.precedence()
                < DatingMethod.ULTRASOUND_CRL_FIRST_TRIMESTER.precedence());
        assertTrue(DatingMethod.ULTRASOUND_CRL_FIRST_TRIMESTER.precedence()
                < DatingMethod.LMP_CERTAIN.precedence());
        assertTrue(DatingMethod.LMP_CERTAIN.precedence()
                < DatingMethod.ULTRASOUND_LATE_SECOND_OR_THIRD.precedence());
        assertEquals(Integer.MAX_VALUE, DatingMethod.UNKNOWN.precedence());
    }

    @Test
    void gestationalAgeRoundTripsThroughItsDisplayForm() {
        assertEquals(228, GestationalAge.parse("32+4").totalDays());
        assertEquals("32+4", GestationalAge.ofDays(228).display());
        assertEquals(224, GestationalAge.parse("32").totalDays());
        assertNull(GestationalAge.parse("not a gestation"));
        assertNull(GestationalAge.parse(null));
    }
}
