package zw.gov.mohcc.impilo.docstore.core.ocr;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Fail-closed default OCR provider. When no real OCR engine is configured
 * (DOCUMENT_OCR_PROVIDER unset, defaulting to NOOP) — or an unknown provider type is configured
 * and the router falls back here — extraction must NOT fabricate output. Previously this returned
 * a literal "OCR placeholder" string with 0.0 confidence and the job completed, so callers saw a
 * COMPLETED OCR job with no real extraction. It now refuses; the OCR job is recorded FAILED until
 * a real provider is configured.
 */
@Component
public class NoopOcrProvider implements OcrProvider {

    @Override
    public String providerType() {
        return "NOOP";
    }

    @Override
    public OcrResult extract(UUID objectId, byte[] content, String mimeType) {
        throw new IllegalStateException(
                "No OCR provider configured: refusing to fabricate extracted text. "
                + "Configure DOCUMENT_OCR_PROVIDER (e.g. EXTERNAL_STUB) with a real OCR engine.");
    }
}
