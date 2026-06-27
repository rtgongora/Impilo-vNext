package zw.gov.mohcc.impilo.oros.domain;

/**
 * How a diagnostic/imaging order entered OROS.
 *
 * <p>Drives intake handling, accession reservation, and reconciliation: paper/QR/walk-in/external
 * orders preserve their provenance (scanned request, claim code, referring details) so the
 * fulfilment and result-return trail stays complete even when the order did not originate from a
 * fully electronic internal request.</p>
 */
public enum RequestSource {
    /** Created from a fully electronic internal clinical request. */
    INTERNAL,
    /** Captured from a scanned/uploaded paper request. */
    PAPER,
    /** Claimed via a QR / secure order code. */
    QR,
    /** Manual walk-in intake at the fulfilling unit. */
    WALK_IN,
    /** Received from an external electronic system or external manual referral. */
    EXTERNAL
}
