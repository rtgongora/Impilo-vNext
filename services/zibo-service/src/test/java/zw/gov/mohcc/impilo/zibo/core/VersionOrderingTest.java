package zw.gov.mohcc.impilo.zibo.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.stream.Stream;
import zw.gov.mohcc.impilo.zibo.domain.VersionScheme;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Version precedence must come from the publisher's version string, not from the clock.
 *
 * <p>ZIBO picked the current artifact with {@code max(comparing(publishedAt))} and
 * {@code createdAt DESC}. A 1.0.1 correction published after 1.1.0 therefore silently started
 * serving the older content — and for external terminologies, whose versions are dates, the clock
 * and the version agree only by coincidence.</p>
 *
 * <p>The corpus below is not invented. It is every version string live in the estate today, plus
 * the real formats of the publishers this programme is about to load.</p>
 */
class VersionOrderingTest {

    // ── Classification ────────────────────────────────────────────────────────────────────────

    @ParameterizedTest
    @CsvSource({
            // Live in the estate right now
            "1.0.0,        SEMVER",
            "1.1.0,        SEMVER",
            "0.1.0-draft,  SEMVER",
            "2025.1,       DATE",
            "2026.01,      DATE",
            "10-2025,      PUBLISHER_DEFINED",
            // The publishers this programme loads
            "2.82,         SEMVER",     // LOINC
            "2026-01,      DATE",       // ICD-11 MMS
            "11,           INTEGER",
    })
    void classifiesEveryVersionFormatTheEstateActuallyUses(String version, VersionScheme expected) {
        assertThat(VersionOrdering.detect(version)).isEqualTo(expected);
    }

    @Test
    void dateIsTestedBeforeNumeric_becauseBothAreDigitsDotDigits() {
        // Only the four-digit leading year separates a release date from LOINC's two-part version.
        // Numeric-first would classify every ATC and EDLIZ release as a version number.
        assertThat(VersionOrdering.detect("2026.01")).isEqualTo(VersionScheme.DATE);
        assertThat(VersionOrdering.detect("2.82")).isEqualTo(VersionScheme.SEMVER);
    }

    @Test
    void anUnorderableVersionIsAdmittedRatherThanGuessed() {
        // "10-2025" could be release 10 of 2025, or October 2025. There is no way to know.
        assertThat(VersionOrdering.detect("10-2025")).isEqualTo(VersionScheme.PUBLISHER_DEFINED);
        assertThat(VersionOrdering.sortKey("10-2025")).isEqualTo("10-2025");
    }

    // ── Ordering ──────────────────────────────────────────────────────────────────────────────

    @Test
    void semVerOrdersNumerically_notLexically() {
        // The case a naive string compare gets backwards.
        assertThat(VersionOrdering.sortKey("1.10.0"))
                .isGreaterThan(VersionOrdering.sortKey("1.9.0"));
        assertThat("1.10.0").isLessThan("1.9.0"); // ...which is why the sort key exists
    }

    @Test
    void loincTwoPartVersionsOrderCorrectly() {
        // LOINC minor versions are sequential integers: 2.79, 2.80, 2.81, 2.82.
        // As raw strings "2.9" sorts above "2.82" — backwards.
        List<String> sorted = Stream.of("2.82", "2.9", "2.80")
                .sorted((a, b) -> VersionOrdering.sortKey(a).compareTo(VersionOrdering.sortKey(b)))
                .toList();
        assertThat(sorted).containsExactly("2.9", "2.80", "2.82");
    }

    @Test
    void releaseDatesOrderChronologically() {
        assertThat(VersionOrdering.sortKey("2026.01"))
                .isGreaterThan(VersionOrdering.sortKey("2025.1"));
    }

    @Test
    void icd11AndAtcDateFormatsAreEquivalent() {
        // ICD-11 writes 2026-01, ATC writes 2026.01. Same release point, same ordering.
        assertThat(VersionOrdering.sortKey("2026-01")).isEqualTo(VersionOrdering.sortKey("2026.01"));
    }

    @Test
    void aPreReleaseSuffixDoesNotBreakOrdering() {
        assertThat(VersionOrdering.sortKey("0.1.0-draft"))
                .isLessThan(VersionOrdering.sortKey("1.0.0"));
    }

    @Test
    void aTwoPartSemVerSortsBelowItsThreePartSuccessor() {
        assertThat(VersionOrdering.sortKey("2.82")).isLessThan(VersionOrdering.sortKey("2.82.1"));
    }

    // ── Robustness ────────────────────────────────────────────────────────────────────────────

    @Test
    void survivesNullAndBlank() {
        assertThat(VersionOrdering.detect(null)).isEqualTo(VersionScheme.PUBLISHER_DEFINED);
        assertThat(VersionOrdering.detect("  ")).isEqualTo(VersionScheme.PUBLISHER_DEFINED);
        assertThat(VersionOrdering.sortKey(null)).isEmpty();
    }

    @Test
    void aVersionPartLongerThanThePaddingIsNotTruncated() {
        // Padding must never lose information — a seven-digit part is still ordered, just wider.
        assertThat(VersionOrdering.sortKey("1234567.0.0")).contains("1234567");
    }

    @Test
    void theSortKeyIsStable() {
        assertThat(VersionOrdering.sortKey("2.82")).isEqualTo(VersionOrdering.sortKey("2.82"));
    }
}
