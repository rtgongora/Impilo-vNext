package zw.gov.mohcc.impilo.msikaflow.domain;

/**
 * Stock-truth grade for an offer line (Vol II §8E / OF-B6).
 */
public enum StockGrade {
    /** DURA-verified: available = on-hand − reserved covered the quantity at grading time. */
    VERIFIED,
    /** Vendor-attested only (or DURA unverifiable) — never silently upgraded. */
    REPORTED
}
