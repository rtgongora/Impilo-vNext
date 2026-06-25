package zw.gov.mohcc.impilo.cardprint.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

/**
 * Personalises a physical SMART card by generating its secure-element keypair.
 *
 * <p>On real hardware the EC keypair is generated on the card's secure element and the
 * private key never leaves it; only the public key is exported for provisioning. This
 * service models that personalisation step for the print agent: it generates an
 * NIST P-256 (secp256r1) keypair and returns the public key as a PEM-encoded
 * {@code SubjectPublicKeyInfo} string.
 *
 * <p>The PEM is what gets provisioned into VITO at print time (G032). VITO fails closed
 * on activation unless a real public key is present, so this must produce a genuine key —
 * never a {@code DEV-PLACEHOLDER-} fabrication.
 */
@Service
public class SecureElementKeyService {

    private static final Logger log = LoggerFactory.getLogger(SecureElementKeyService.class);

    private static final String CURVE = "secp256r1";
    private static final String PEM_HEADER = "-----BEGIN PUBLIC KEY-----";
    private static final String PEM_FOOTER = "-----END PUBLIC KEY-----";

    /**
     * Generates a fresh secure-element keypair for a card and returns its public key as PEM.
     *
     * @param cardId the VITO card id being personalised (for logging/traceability only)
     * @return PEM-encoded public key ({@code -----BEGIN PUBLIC KEY----- ...})
     */
    public String generatePublicKeyPem(long cardId) {
        KeyPair keyPair = generateKeyPair();
        String pem = toPem(keyPair.getPublic());
        log.info("Personalised card {}: generated secure-element keypair (curve={}, publicKey {} bytes PEM)",
                cardId, CURVE, pem.length());
        return pem;
    }

    private KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec(CURVE));
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException | java.security.InvalidAlgorithmParameterException e) {
            throw new IllegalStateException("Unable to generate secure-element keypair", e);
        }
    }

    private String toPem(PublicKey publicKey) {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes())
                .encodeToString(publicKey.getEncoded());
        return PEM_HEADER + "\n" + base64 + "\n" + PEM_FOOTER + "\n";
    }
}
