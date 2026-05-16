package zw.gov.mohcc.impilo.docstore.core.ocr;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Default OCR provider for environments without OCR engine integration.
 */
@Component
public class NoopOcrProvider implements OcrProvider {

    @Override
    public String providerType() {
        return "NOOP";
    }

    @Override
    public OcrResult extract(UUID objectId, byte[] content, String mimeType) {
        if (mimeType != null && mimeType.startsWith("text/")) {
            String text = new String(content, StandardCharsets.UTF_8);
            return new OcrResult(truncate(text), 0.95d);
        }
        String placeholder = "OCR placeholder: provider=NOOP, extraction unavailable for mimeType="
                + (mimeType == null ? "unknown" : mimeType);
        return new OcrResult(placeholder, 0.0d);
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 8000 ? value : value.substring(0, 8000);
    }
}
