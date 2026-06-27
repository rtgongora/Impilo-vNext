package zw.gov.mohcc.impilo.oros.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to link a fulfilled exam's PACS/DICOM study to an imaging order (criterion F).
 *
 * @param studyUid         DICOM Study Instance UID (required)
 * @param viewerUrl        optional deep-link to launch the study in the DICOM viewer
 * @param accessionNumber  optional accession to backfill if one was not reserved at submit
 */
public record LinkStudyRequest(
        @NotBlank String studyUid,
        String viewerUrl,
        String accessionNumber
) {}
