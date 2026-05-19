package zw.gov.mohcc.impilo.docstore.core.ocr;

import java.util.UUID;

/**
 * Provider-neutral OCR extraction contract.
 */
public interface OcrProvider {

    String providerType();

    OcrResult extract(UUID objectId, byte[] content, String mimeType);

    record OcrResult(String extractedText, double confidence) {
    }
}
