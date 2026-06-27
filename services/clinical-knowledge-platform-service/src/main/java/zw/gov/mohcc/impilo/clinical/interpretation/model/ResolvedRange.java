package zw.gov.mohcc.impilo.clinical.interpretation.model;

/**
 * The outcome of {@code RangeResolver}: a status plus the (normalised) {@link ReferenceRange} when
 * {@link ResolutionStatus#RESOLVED}. For NO_REFERENCE_RANGE / UNIT_MISMATCH the range is null.
 */
public record ResolvedRange(ResolutionStatus status, ReferenceRange range) {

    public static ResolvedRange resolved(ReferenceRange range) {
        return new ResolvedRange(ResolutionStatus.RESOLVED, range);
    }

    public static ResolvedRange noReferenceRange() {
        return new ResolvedRange(ResolutionStatus.NO_REFERENCE_RANGE, null);
    }

    public static ResolvedRange unitMismatch() {
        return new ResolvedRange(ResolutionStatus.UNIT_MISMATCH, null);
    }

    public boolean isResolved() {
        return status == ResolutionStatus.RESOLVED && range != null;
    }
}
