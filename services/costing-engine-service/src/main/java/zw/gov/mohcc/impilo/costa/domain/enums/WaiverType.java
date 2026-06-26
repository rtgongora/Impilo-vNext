package zw.gov.mohcc.impilo.costa.domain.enums;

/** Extent of a waiver. */
public enum WaiverType {
    /** The whole referenced charge/bill is waived. */
    FULL,
    /** Only {@code waivedAmount} is waived. */
    PARTIAL
}
