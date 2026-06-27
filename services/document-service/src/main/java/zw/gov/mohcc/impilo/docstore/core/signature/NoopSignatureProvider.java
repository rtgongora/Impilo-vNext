package zw.gov.mohcc.impilo.docstore.core.signature;

import org.springframework.stereotype.Component;

/**
 * Fail-closed default signature provider. When no real e-sign provider is configured
 * (DOCUMENT_SIGNATURE_PROVIDER unset, defaulting to NOOP) — or an unknown provider type is
 * configured and the router falls back here — signing must NOT fabricate a result. Previously
 * this returned a random signature ref + verification URL with status SIGNED, so unsigned
 * documents looked cryptographically signed. It now refuses; the signature job is recorded
 * FAILED until a real provider is configured.
 */
@Component
public class NoopSignatureProvider implements SignatureProvider {

    @Override
    public String providerType() {
        return "NOOP";
    }

    @Override
    public SignatureResult sign(SignatureRequest request) {
        throw new IllegalStateException(
                "No signature provider configured: refusing to fabricate a signature. "
                + "Configure DOCUMENT_SIGNATURE_PROVIDER (e.g. EXTERNAL_STUB) with a real e-sign integration.");
    }
}
