package zw.gov.mohcc.impilo.medicine.common;

/**
 * Why a calculator in this library declined to produce a number.
 *
 * <p>Every calculator here returns a result type that carries either a value or one of these
 * reasons. There is no third option: no magic sentinel, no null standing in for an answer, no
 * silent default. That constraint is the whole point of the vocabulary. A missing serum
 * creatinine that produces "eGFR 90" reads to a clinician as a normal kidney, and they act on
 * it; "cannot compute — serum creatinine was not recorded" reads as a task.</p>
 *
 * <p>The reasons are deliberately coarse and machine-readable. A surface decides what to render
 * from the code; the free-text explanation beside it is for a human and is not parsed.</p>
 *
 * <p><strong>None of these are policy.</strong> Refusing to extrapolate a chart beyond its
 * published age range is a statement about the instrument, not about what a ministry wants done
 * with the result.</p>
 */
public enum RefusalReason {

    /**
     * A required input was not supplied. The distinction from a zero value is the safety
     * property: an unrecorded medicine count is not a count of zero.
     */
    INPUT_MISSING,

    /**
     * An input was supplied but lies outside the range a living adult can occupy, or outside the
     * range in which a data-entry error is more likely than a true value. Refused rather than
     * extrapolated, because every equation here is a curve fitted inside a range and none of them
     * degrade gracefully outside it.
     */
    INPUT_OUT_OF_PHYSIOLOGICAL_RANGE,

    /**
     * The inputs are valid but fall outside the range the published instrument covers — a 32-year-old
     * against a chart drawn from age 40, a frailty score of 12 on a nine-point scale. The instrument
     * has nothing to say here and this library will not invent it.
     */
    OUTSIDE_INSTRUMENT_RANGE,

    /**
     * The instrument's own scope statement excludes this person. The WHO cardiovascular risk charts
     * estimate risk for people <em>without</em> established cardiovascular disease; someone who has
     * already had the event is not at 10-year risk of a first one.
     */
    INSTRUMENT_NOT_APPLICABLE,

    /**
     * The inputs contradict each other in a way that makes the answer unsafe to guess at — symptoms
     * reported at rest but denied on ordinary exertion, threshold cut-points supplied out of order.
     * Picking one of the two readings would be a coin toss recorded as a finding.
     */
    INPUTS_INCONSISTENT,

    /**
     * The conversion is real but is not a single multiplication, so no linear factor exists to
     * apply. Methadone is the case this reason was written for: its ratio to morphine changes with
     * the dose being converted from, and a fixed factor produces overdoses at the top of the range.
     */
    REQUIRES_SPECIALIST_CALCULATION,

    /**
     * The published reference this calculation needs has not been vendored into the repository, so
     * the authoritative values are not available to compute against.
     */
    REFERENCE_NOT_AVAILABLE
}
