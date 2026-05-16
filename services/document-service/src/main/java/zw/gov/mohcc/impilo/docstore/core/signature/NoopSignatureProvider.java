package zw.gov.mohcc.impilo.docstore.core.signature;

import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Default signature provider for environments without external e-sign integration.
 */
@Component
public class NoopSignatureProvider implements SignatureProvider {

    @Override
    public String providerType() {
        return "NOOP";
    }

    @Override
    public SignatureResult sign(SignatureRequest request) {
        String ref = "sig-" + UUID.randomUUID();
        String verify = "https://verify.impilo.local/signature/" + ref;
        return new SignatureResult(ref, verify, OffsetDateTime.now());
    }
}
