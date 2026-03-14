package zw.gov.mohcc.impilo.pacs.domain;

/**
 * Status of an imaging study within the PACS adapter lifecycle.
 */
public enum StudyStatus {
    RECEIVED,
    FORWARDING,
    FORWARDED,
    FAILED
}
