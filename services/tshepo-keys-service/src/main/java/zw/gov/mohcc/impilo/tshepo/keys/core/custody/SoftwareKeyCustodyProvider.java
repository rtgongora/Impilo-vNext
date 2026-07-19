package zw.gov.mohcc.impilo.tshepo.keys.core.custody;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator;
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.tshepo.keys.config.KeysProperties;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;

/**
 * Default software custody: Ed25519 key pairs generated in-process, private
 * keys AES-256-GCM-encrypted with the configured KEK and stored alongside the
 * key row ({@code custodyBlob} = {@code [12-byte IV | ciphertext | GCM tag]},
 * the exact at-rest format the service has always used — existing rows decrypt
 * unchanged).
 */
@Component
@ConditionalOnProperty(name = "impilo.keys.custody-provider", havingValue = "software", matchIfMissing = true)
public class SoftwareKeyCustodyProvider implements KeyCustodyProvider {

    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128; // bits

    private final SecureRandom secureRandom = new SecureRandom();
    private final byte[] kekBytes;

    public SoftwareKeyCustodyProvider(KeysProperties keysProperties) {
        this.kekBytes = hexToBytes(keysProperties.getKek());
        if (this.kekBytes.length != 32) {
            throw new IllegalStateException(
                    "KEK must be exactly 32 bytes (256 bits) for AES-256-GCM, got " + this.kekBytes.length);
        }
    }

    @Override
    public GeneratedKeyMaterial generate() {
        Ed25519KeyPairGenerator keyGen = new Ed25519KeyPairGenerator();
        keyGen.init(new Ed25519KeyGenerationParameters(secureRandom));
        AsymmetricCipherKeyPair keyPair = keyGen.generateKeyPair();

        Ed25519PrivateKeyParameters privateKey = (Ed25519PrivateKeyParameters) keyPair.getPrivate();
        Ed25519PublicKeyParameters publicKey = (Ed25519PublicKeyParameters) keyPair.getPublic();
        return new GeneratedKeyMaterial(publicKey.getEncoded(), encrypt(privateKey.getEncoded()));
    }

    @Override
    public byte[] sign(byte[] custodyBlob, byte[] payload) {
        Ed25519PrivateKeyParameters privateKey = new Ed25519PrivateKeyParameters(exportPrivate(custodyBlob), 0);
        Ed25519Signer signer = new Ed25519Signer();
        signer.init(true, privateKey);
        signer.update(payload, 0, payload.length);
        return signer.generateSignature();
    }

    @Override
    public byte[] exportPrivate(byte[] custodyBlob) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(custodyBlob);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(kekBytes, "AES"),
                    new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt private key", e);
        }
    }

    @Override
    public String custodyType() {
        return "SOFTWARE";
    }

    private byte[] encrypt(byte[] privateKeyBytes) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(kekBytes, "AES"),
                    new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] ciphertext = cipher.doFinal(privateKeyBytes);

            ByteBuffer result = ByteBuffer.allocate(GCM_IV_LENGTH + ciphertext.length);
            result.put(iv);
            result.put(ciphertext);
            return result.array();
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt private key", e);
        }
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
