package zw.gov.mohcc.impilo.pacs.domain;

/**
 * Status of an imaging study within the PACS adapter lifecycle.
 */
public enum StudyStatus {
    /** Placeholder row created from an OROS imaging order before DICOM study UID is known. */
    PENDING_ORDER,
    RECEIVED,
    FORWARDING,
    FORWARDED,
    FAILED
}
