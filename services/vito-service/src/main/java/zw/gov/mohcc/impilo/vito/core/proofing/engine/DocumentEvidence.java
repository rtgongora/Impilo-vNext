package zw.gov.mohcc.impilo.vito.core.proofing.engine;

import java.util.List;
import java.util.Map;

/**
 * Evidence submitted for document proofing. Carries the machine-readable zone (MRZ)
 * lines when present (passports / TD1 ID cards) plus any OCR-extracted fields and the
 * opaque document-service artifact references for the captured images.
 *
 * <p>No image bytes live here — only references — so the evidence object is safe to
 * log at debug and to keep in the proofing ledger's {@code artifactRefs}.</p>
 *
 * @param documentType   e.g. PASSPORT, NATIONAL_ID, BIRTH_CERTIFICATE
 * @param mrzLines       raw MRZ lines (2 for TD3 passports, 3 for TD1 ID cards); may be empty
 * @param extractedFields OCR fields (documentNumber, dateOfBirth, expiry, …); may be empty
 * @param artifactRefs   document-service object references for the captured images
 */
public record DocumentEvidence(
        String documentType,
        List<String> mrzLines,
        Map<String, String> extractedFields,
        List<String> artifactRefs) {

    public DocumentEvidence {
        mrzLines = mrzLines == null ? List.of() : List.copyOf(mrzLines);
        extractedFields = extractedFields == null ? Map.of() : Map.copyOf(extractedFields);
        artifactRefs = artifactRefs == null ? List.of() : List.copyOf(artifactRefs);
    }

    public boolean hasMrz() {
        return !mrzLines.isEmpty();
    }
}
