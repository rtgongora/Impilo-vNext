package zw.gov.mohcc.impilo.docstore.core.signature;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Provider-neutral signature contract.
 */
public interface SignatureProvider {

    String providerType();

    SignatureResult sign(SignatureRequest request);

    record SignatureRequest(UUID objectId, String signerId, String requestedBy) {
    }

    record SignatureResult(String signatureRef, String verificationUrl, OffsetDateTime signedAt) {
    }
}
