package zw.gov.mohcc.impilo.pct.core.clinical;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.reproductive.confidentiality.ConfidentialCarePolicy;
import zw.gov.mohcc.impilo.reproductive.confidentiality.ConfidentialityCategory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The confidentiality decision table, and the two asymmetries that keep it safe.
 */
class ConfidentialityStamperTest {

    private static final ConfidentialityCategory SRH = ConfidentialityCategory.SEXUAL_REPRODUCTIVE_HEALTH;

    /** A governed policy in force with the given age. */
    private static ConfidentialCarePolicy policyWithAge(int age, boolean ratified) {
        return new ConfidentialCarePolicy() {
            public Optional<Integer> confidentialFromGuardianAgeYears(ConfidentialityCategory c) {
                return Optional.of(age);
            }
            public Optional<Boolean> nonTerminationLossConfidentialFromGuardian() { return Optional.of(true); }
            public String contentVersion() { return "engineering-seed-1.0.0"; }
            public boolean ratified() { return ratified; }
        };
    }

    /** CKP reachable, but no parameter in force for this category. */
    private static ConfidentialCarePolicy policyWithNoAge() {
        return new ConfidentialCarePolicy() {
            public Optional<Integer> confidentialFromGuardianAgeYears(ConfidentialityCategory c) {
                return Optional.empty();
            }
            public Optional<Boolean> nonTerminationLossConfidentialFromGuardian() { return Optional.empty(); }
            public String contentVersion() { return "none"; }
            public boolean ratified() { return false; }
        };
    }

    @Test
    @DisplayName("a termination is confidential at any age — the guardian question is not what makes it sensitive")
    void terminationIsAlwaysConfidential() {
        var stamp = ConfidentialityStamper.alwaysConfidential(SRH, policyWithAge(18, false));
        assertThat(stamp.category()).isEqualTo("SEXUAL_REPRODUCTIVE_HEALTH");
        assertThat(stamp.sensitivityClass()).isEqualTo(ConfidentialityStamper.CLASS_SPECIALLY_PROTECTED);
        assertThat(stamp.basis()).isEqualTo(ConfidentialityStamper.BASIS_RECORD_TYPE_ALWAYS);
    }

    @Test
    @DisplayName("AGE IS TAKEN AT THE ACT, NOT TODAY — 17 then, 19 now, still protected")
    void ageIsTakenAtTheActNotToday() {
        // Born 2007; the loss occurred in 2024 when she was 17. Today she is 19.
        var subject = new ConfidentialityStamper.SubjectContext(LocalDate.of(2007, 6, 1), false);
        var atAct = ConfidentialityStamper.ageGated(SRH, subject, LocalDate.of(2024, 7, 1), policyWithAge(18, false));

        assertThat(atAct.sensitivityClass())
                .as("the record was created under a promise of confidentiality; it does not lapse on a birthday")
                .isEqualTo(ConfidentialityStamper.CLASS_SPECIALLY_PROTECTED);
        assertThat(atAct.basis()).isEqualTo(ConfidentialityStamper.BASIS_SUBJECT_UNDER_POLICY_AGE);

        // The same subject, an act today, is over the threshold — proving the test above is not
        // passing for some age-independent reason.
        var now = ConfidentialityStamper.ageGated(SRH, subject, LocalDate.of(2026, 7, 28), policyWithAge(18, false));
        assertThat(now.sensitivityClass()).isEqualTo(ConfidentialityStamper.CLASS_FULL_CLINICAL);
    }

    @Test
    @DisplayName("capacity ADDS eligibility and never removes it")
    void capacityOnlyAdds() {
        var adultWithCapacity = new ConfidentialityStamper.SubjectContext(LocalDate.of(2000, 1, 1), true);
        assertThat(ConfidentialityStamper.ageGated(SRH, adultWithCapacity, LocalDate.of(2026, 1, 1),
                policyWithAge(18, false)).sensitivityClass())
                .isEqualTo(ConfidentialityStamper.CLASS_SPECIALLY_PROTECTED);

        // An under-age subject WITHOUT a capacity determination is still protected by the age rule —
        // capacity is not a precondition for protection.
        var minorNoCapacity = new ConfidentialityStamper.SubjectContext(LocalDate.of(2012, 1, 1), false);
        assertThat(ConfidentialityStamper.ageGated(SRH, minorNoCapacity, LocalDate.of(2026, 1, 1),
                policyWithAge(18, false)).sensitivityClass())
                .isEqualTo(ConfidentialityStamper.CLASS_SPECIALLY_PROTECTED);
    }

    @Test
    @DisplayName("STAMPING FAILS OPEN: an unresolved date of birth still records the category")
    void unresolvedAgeFailsOpen() {
        var stamp = ConfidentialityStamper.ageGated(SRH, null, LocalDate.of(2026, 1, 1), policyWithAge(18, false));
        assertThat(stamp.category()).isEqualTo("SEXUAL_REPRODUCTIVE_HEALTH");
        assertThat(stamp.sensitivityClass()).isEqualTo(ConfidentialityStamper.CLASS_FULL_CLINICAL);
        assertThat(stamp.basis()).isEqualTo(ConfidentialityStamper.BASIS_AGE_UNRESOLVED);
    }

    @Test
    @DisplayName("no governed parameter means NO age — never a fallback number")
    void noPolicyMeansNoAgeBasedStamp() {
        var minor = new ConfidentialityStamper.SubjectContext(LocalDate.of(2012, 1, 1), false);

        var unreachable = ConfidentialityStamper.ageGated(SRH, minor, LocalDate.of(2026, 1, 1), null);
        assertThat(unreachable.basis()).isEqualTo(ConfidentialityStamper.BASIS_POLICY_UNAVAILABLE);
        assertThat(unreachable.sensitivityClass()).isEqualTo(ConfidentialityStamper.CLASS_FULL_CLINICAL);

        var noParameter = ConfidentialityStamper.ageGated(SRH, minor, LocalDate.of(2026, 1, 1), policyWithNoAge());
        assertThat(noParameter.basis()).isEqualTo(ConfidentialityStamper.BASIS_POLICY_UNAVAILABLE);
        assertThat(noParameter.sensitivityClass()).isEqualTo(ConfidentialityStamper.CLASS_FULL_CLINICAL);
    }

    @Test
    @DisplayName("the flip gate downgrades the class but preserves category and basis")
    void flipGatePreservesTheDecision() {
        var decided = ConfidentialityStamper.alwaysConfidential(SRH, policyWithAge(18, false));

        var shadow = ConfidentialityStamper.gated(decided, false);
        assertThat(shadow.sensitivityClass()).isEqualTo(ConfidentialityStamper.CLASS_FULL_CLINICAL);
        // The decision survives, so the record still says what it IS and the shadow-withhold signal
        // can report what WOULD have been withheld.
        assertThat(shadow.category()).isEqualTo("SEXUAL_REPRODUCTIVE_HEALTH");
        assertThat(shadow.basis()).isEqualTo(ConfidentialityStamper.BASIS_RECORD_TYPE_ALWAYS);

        var live = ConfidentialityStamper.gated(decided, true);
        assertThat(live.sensitivityClass()).isEqualTo(ConfidentialityStamper.CLASS_SPECIALLY_PROTECTED);
    }

    @Test
    @DisplayName("no compiled-in age: the stamper source contains no bare age literal")
    void noCompiledInAge() throws Exception {
        Path src = Path.of("src/main/java/zw/gov/mohcc/impilo/pct/core/clinical/ConfidentialityStamper.java");
        String body = Files.readString(src);
        // Strip comments and javadoc, where "at 32 as much as at 15" legitimately appears as prose.
        String code = body.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
        assertThat(code)
                .as("an age threshold must come from governed policy, never from a literal in this class")
                .doesNotContain(" 18", "=18", "(18", " 16", "=16", "(16");
    }
}
