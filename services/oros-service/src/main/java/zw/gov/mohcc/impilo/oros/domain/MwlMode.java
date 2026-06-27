package zw.gov.mohcc.impilo.oros.domain;

/**
 * How DICOM Modality Worklist (MWL) items are published for imaging orders (spec §7):
 * {@code OFF} (NOT_LIVE seam), {@code REST} (DICOMweb/UPS-RS to an archive such as dcm4chee-arc or
 * Orthanc), or {@code DIMSE} (UPS N-CREATE via dcm4che to an archive/SCP).
 */
public enum MwlMode {
    OFF,
    REST,
    DIMSE
}
