package zw.gov.mohcc.impilo.docstore.core;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.docstore.core.ocr.NoopOcrProvider;
import zw.gov.mohcc.impilo.docstore.core.signature.NoopSignatureProvider;
import zw.gov.mohcc.impilo.docstore.core.signature.SignatureProvider;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the F2 fix (G019/G020): the default (NOOP) signature and OCR providers fail closed
 * instead of fabricating. Previously NOOP returned a random signature ref (status SIGNED) and an
 * "OCR placeholder" (job COMPLETED), so unprocessed documents looked signed/extracted. The
 * SignatureService/OcrService record the thrown failure as a FAILED job — not a fake success.
 */
class NoopProvidersFailClosedTest {

    @Test
    void noopSignatureProvider_refusesToFabricate() {
        assertThatThrownBy(() -> new NoopSignatureProvider().sign(
                new SignatureProvider.SignatureRequest(UUID.randomUUID(), "signer-1", "requester-1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("refusing to fabricate a signature");
    }

    @Test
    void noopOcrProvider_refusesToFabricate() {
        assertThatThrownBy(() -> new NoopOcrProvider().extract(
                UUID.randomUUID(), "binary".getBytes(StandardCharsets.UTF_8), "image/png"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("refusing to fabricate extracted text");
    }
}
