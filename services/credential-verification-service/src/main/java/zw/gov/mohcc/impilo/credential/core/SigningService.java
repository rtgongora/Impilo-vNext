package zw.gov.mohcc.impilo.credential.core;

import jakarta.annotation.PostConstruct;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.credential.config.CredentialProperties;
import zw.gov.mohcc.impilo.credential.persistence.entity.SigningKeyEntity;
import zw.gov.mohcc.impilo.credential.persistence.repository.SigningKeyRepository;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Ed25519 digital signing service.
 *
 * On startup, loads or generates an Ed25519 key pair and stores it in the
 * signing_keys table. Provides sign and verify operations using Bouncy Castle.
 */
@Service
public class SigningService {

    private static final Logger log = LoggerFactory.getLogger(SigningService.class);
    private static final String DEFAULT_KEY_ID = "primary-ed25519";

    private final SigningKeyRepository signingKeyRepository;
    private final CredentialProperties properties;

    private Ed25519PrivateKeyParameters activePrivateKey;
    private Ed25519PublicKeyParameters activePublicKey;

    public SigningService(SigningKeyRepository signingKeyRepository,
                          CredentialProperties properties) {
        this.signingKeyRepository = signingKeyRepository;
        this.properties = properties;
    }

    @PostConstruct
    @Transactional
    public void init() {
        var existingKey = signingKeyRepository.findFirstByStatusOrderByCreatedAtDesc("ACTIVE");
        if (existingKey.isPresent()) {
            loadKeyFromEntity(existingKey.get());
            log.info("Loaded existing Ed25519 signing key: {}", existingKey.get().getKeyId());
        } else {
            generateAndStoreKeyPair();
            log.info("Generated new Ed25519 signing key pair");
        }
    }

    /**
     * Signs the provided data using Ed25519.
     *
     * @param data the raw bytes to sign
     * @return hex-encoded signature
     */
    public String sign(byte[] data) {
        if (activePrivateKey == null) {
            throw new IllegalStateException("Signing key not initialized");
        }

        Ed25519Signer signer = new Ed25519Signer();
        signer.init(true, activePrivateKey);
        signer.update(data, 0, data.length);
        byte[] signature = signer.generateSignature();
        return HexFormat.of().formatHex(signature);
    }

    /**
     * Verifies an Ed25519 signature against the provided data.
     *
     * @param data         the raw bytes that were signed
     * @param signatureHex the hex-encoded signature to verify
     * @return true if the signature is valid
     */
    public boolean verify(byte[] data, String signatureHex) {
        if (activePublicKey == null) {
            throw new IllegalStateException("Signing key not initialized");
        }

        try {
            byte[] signature = HexFormat.of().parseHex(signatureHex);
            Ed25519Signer verifier = new Ed25519Signer();
            verifier.init(false, activePublicKey);
            verifier.update(data, 0, data.length);
            return verifier.verifySignature(signature);
        } catch (Exception e) {
            log.warn("Signature verification failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Generates a new Ed25519 key pair and stores it in the database.
     */
    @Transactional
    public void generateAndStoreKeyPair() {
        SecureRandom random = new SecureRandom();
        Ed25519PrivateKeyParameters privateKey = new Ed25519PrivateKeyParameters(random);
        Ed25519PublicKeyParameters publicKey = privateKey.generatePublicKey();

        String keyId = DEFAULT_KEY_ID + "-" + UUID.randomUUID().toString().substring(0, 8);

        SigningKeyEntity entity = new SigningKeyEntity();
        entity.setKeyId(keyId);
        entity.setAlgorithm(properties.getSigning().getAlgorithm());
        entity.setPublicKeyHex(HexFormat.of().formatHex(publicKey.getEncoded()));
        entity.setPrivateKeyHex(HexFormat.of().formatHex(privateKey.getEncoded()));
        entity.setStatus("ACTIVE");

        signingKeyRepository.save(entity);

        this.activePrivateKey = privateKey;
        this.activePublicKey = publicKey;
    }

    /**
     * Returns the currently active signing key entity from the database.
     */
    public SigningKeyEntity getActiveKey() {
        return signingKeyRepository.findFirstByStatusOrderByCreatedAtDesc("ACTIVE")
                .orElseThrow(() -> new IllegalStateException("No active signing key found"));
    }

    private void loadKeyFromEntity(SigningKeyEntity entity) {
        byte[] privateKeyBytes = HexFormat.of().parseHex(entity.getPrivateKeyHex());
        byte[] publicKeyBytes = HexFormat.of().parseHex(entity.getPublicKeyHex());

        this.activePrivateKey = new Ed25519PrivateKeyParameters(privateKeyBytes, 0);
        this.activePublicKey = new Ed25519PublicKeyParameters(publicKeyBytes, 0);
    }
}
