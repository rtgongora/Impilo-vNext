package zw.gov.mohcc.impilo.pacs.domain;

/**
 * Ground-truth imaging deployment pattern of a facility. This is per-facility truth —
 * the global {@code impilo.pacs.backend.*} config is only the platform default archive,
 * never a statement about what any given facility actually has on the ground.
 */
public enum ImagingDeploymentMode {
    /** Existing PACS/VNA on site (machine → PACS → DICOMweb bridge → vNext viewer). */
    PACS_VNA,
    /** Digital machines + local Impilo imaging gateway with store-and-forward to a central/regional archive. */
    GATEWAY_STORE_FORWARD,
    /** Digital DICOM machines but no PACS and no gateway yet. */
    DIGITAL_NO_PACS,
    /** Old/limited-DICOM equipment (export / CD / USB / workstation → gateway import). */
    LEGACY_LIMITED_DICOM,
    /** Manual import from CD/USB/workstation only. */
    MANUAL_IMPORT,
    /** Analogue or non-DICOM equipment via CR/digitizer/frame-capture/scanner DICOM wrapper. */
    DIGITISATION_BRIDGE,
    /** No local acquisition; imaging orders are referred to an imaging-capable facility. */
    REFERRAL_ONLY,
    /** Offline-first / delayed-sync imaging site. */
    OFFLINE_SYNC,
    /** No imaging capability. */
    NONE
}
