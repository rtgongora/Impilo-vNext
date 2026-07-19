package zw.gov.mohcc.impilo.wallet.card;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.biometric.BiometricVerificationClient;
import zw.gov.mohcc.impilo.wallet.core.WalletService;
import zw.gov.mohcc.impilo.wallet.persistence.entity.CardEntity;
import zw.gov.mohcc.impilo.wallet.persistence.entity.TransactionEntity;
import zw.gov.mohcc.impilo.wallet.persistence.repository.CardRepository;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Reconciles offline stored-value transactions signed by a card's device/SE key into the ledger
 * (unified SMART card, Phase 3). A virtual card (wallet pass on the phone) or a physical card signs a
 * canonical payload with its P-256 key; mushe verifies it against the card's cached public key,
 * enforces a monotonic counter (replay/gap rejection), and applies an idempotent ledger debit — the
 * ledger is authoritative, the on-card purse is a reconciled float. Dependency-free JDK ECDSA
 * (SHA256withECDSA over the exact payload bytes, DER signature) so it verifies identically whether the
 * key lives on a physical secure element or the phone keystore.
 */
@Service
public class OfflineTransactionService {

    private static final Logger log = LoggerFactory.getLogger(OfflineTransactionService.class);

    private final CardRepository cardRepository;
    private final WalletService walletService;
    private final ObjectMapper objectMapper;
    private final BiometricVerificationClient biometricVerification;

    public OfflineTransactionService(CardRepository cardRepository, WalletService walletService,
                                     ObjectMapper objectMapper,
                                     BiometricVerificationClient biometricVerification) {
        this.cardRepository = cardRepository;
        this.walletService = walletService;
        this.objectMapper = objectMapper;
        this.biometricVerification = biometricVerification;
    }

    /** Thrown when an offline transaction fails verification/policy — surfaced as 4xx by the controller. */
    public static class OfflineTransactionRejected extends RuntimeException {
        public OfflineTransactionRejected(String message) { super(message); }
    }

    /** User-verification method asserted in the signed payload — how the cardholder authorized the txn. */
    public static final String UV_BIOMETRIC = "BIOMETRIC";

    /**
     * Reconcile a card-signed offline transaction. No user-verification method is required
     * (backward-compatible entry point — e.g. a low-value tap).
     *
     * @param vitoCardNumber the canonical card the txn was signed on
     * @param payloadJson    canonical signed payload: {"amount","counter","nonce","currency","authMethod"}
     * @param signatureB64   Base64 DER ECDSA-SHA256 signature over the exact payloadJson bytes
     */
    @Transactional
    public TransactionEntity redeem(String vitoCardNumber, String payloadJson, String signatureB64) {
        return redeem(vitoCardNumber, payloadJson, signatureB64, null);
    }

    /**
     * Reconcile a card-signed offline transaction, optionally enforcing a required user-verification
     * method carried in the signed payload.
     *
     * <p><b>Biometric scan-to-pay</b> ({@code requiredUserVerification=BIOMETRIC}): the payload must
     * assert {@code "authMethod":"BIOMETRIC"}. This follows the WebAuthn user-verification model — the
     * device's biometric (Android BiometricPrompt / iOS Face ID / a physical card's on-card sensor)
     * gates use of the P-256 key, so a valid signature over a payload that claims BIOMETRIC proves the
     * biometric-bound key was unlocked to sign. mushe does not itself match a biometric template
     * (identity/biometrics are VITO's SoR); it enforces the signed, key-bound verification claim
     * fail-closed. Hardware key-attestation of the biometric binding is the deploy-time hardening.</p>
     *
     * @param requiredUserVerification {@code BIOMETRIC} to require biometric authorization, or null for none
     */
    @Transactional
    public TransactionEntity redeem(String vitoCardNumber, String payloadJson, String signatureB64,
                                    String requiredUserVerification) {
        return redeem(vitoCardNumber, payloadJson, signatureB64, requiredUserVerification, null, null);
    }

    /**
     * Reconcile a card-signed offline transaction with an optional <b>real server-side biometric
     * 1:1 match</b> behind the WebAuthn claim (attended POS / online reconcile). When a biometric
     * {@code subjectRef} + {@code probeBase64} are supplied, mushe additionally verifies the probe
     * against the cardholder's ABIS enrolment through the shared seam
     * (ABIS SoR → core-infra matcher-engine) — defence-in-depth over the signed device claim.
     *
     * <p>Care-first / fail-closed:</p>
     * <ul>
     *   <li>NO_MATCH → reject (a live biometric that doesn't match is authoritative);</li>
     *   <li>MATCH → proceed;</li>
     *   <li>UNAVAILABLE (biometrics off / ABIS unreachable) → fall back to the signed WebAuthn claim,
     *       already enforced below, so an outage never blocks a legitimately card-signed payment.</li>
     * </ul>
     * When no probe is supplied this behaves exactly as the WebAuthn-claim path.
     */
    @Transactional
    public TransactionEntity redeem(String vitoCardNumber, String payloadJson, String signatureB64,
                                    String requiredUserVerification, String biometricSubjectRef,
                                    String biometricProbeBase64) {
        CardEntity card = cardRepository.findByVitoCardNumber(vitoCardNumber)
                .orElseThrow(() -> new OfflineTransactionRejected("No money card linked to VITO card " + vitoCardNumber));
        if ("BLOCKED".equals(card.getStatus())) {
            throw new OfflineTransactionRejected("Card is blocked");
        }
        if (card.getVitoCardPublicKey() == null || card.getVitoCardPublicKey().isBlank()) {
            throw new OfflineTransactionRejected("Card has no provisioned public key to verify against");
        }
        if (!verify(card.getVitoCardPublicKey(), payloadJson, signatureB64)) {
            throw new OfflineTransactionRejected("Signature verification failed");
        }

        JsonNode payload;
        try {
            payload = objectMapper.readTree(payloadJson);
        } catch (Exception e) {
            throw new OfflineTransactionRejected("Malformed payload");
        }
        long counter = payload.path("counter").asLong(-1);
        String nonce = payload.path("nonce").asText(null);
        if (counter < 0 || nonce == null || nonce.isBlank()) {
            throw new OfflineTransactionRejected("Payload missing counter/nonce");
        }
        // Monotonic counter — rejects replays and stale/out-of-order offline txns.
        if (counter <= card.getLastOfflineCounter()) {
            throw new OfflineTransactionRejected("Stale offline counter " + counter
                    + " (last accepted " + card.getLastOfflineCounter() + ")");
        }
        BigDecimal amount = new BigDecimal(payload.path("amount").asText("0"));
        if (amount.signum() <= 0) {
            throw new OfflineTransactionRejected("Amount must be positive");
        }

        // The user-verification method the cardholder authorized with, carried inside the signed
        // payload (so it cannot be tampered without invalidating the signature).
        String authMethod = payload.path("authMethod").asText("NONE");
        if (requiredUserVerification != null && !requiredUserVerification.equalsIgnoreCase(authMethod)) {
            // Fail-closed: e.g. a biometric-pay request whose signed payload does not assert BIOMETRIC.
            throw new OfflineTransactionRejected("Requires " + requiredUserVerification
                    + " user verification; payload asserted " + authMethod);
        }

        // Defence-in-depth: when an attended terminal supplies a live biometric probe for a
        // BIOMETRIC-required txn, verify it 1:1 through the shared ABIS seam behind the device claim.
        if (UV_BIOMETRIC.equalsIgnoreCase(requiredUserVerification)
                && biometricSubjectRef != null && !biometricSubjectRef.isBlank()
                && biometricProbeBase64 != null && !biometricProbeBase64.isBlank()) {
            BiometricVerificationClient.Decision decision =
                    biometricVerification.verify(biometricSubjectRef, "FINGERPRINT", biometricProbeBase64);
            if ("NO_MATCH".equals(decision.result())) {
                throw new OfflineTransactionRejected("Live biometric did not match the cardholder");
            }
            // MATCH proceeds; UNAVAILABLE falls back to the signed WebAuthn claim already enforced.
            log.info("Offline biometric-pay live match for card {}: {}", vitoCardNumber, decision.result());
        }

        // Idempotent ledger debit — the nonce is the idempotency key, so a re-submitted offline txn
        // never double-debits even if the counter check is bypassed by a race.
        TransactionEntity txn = walletService.debit(
                card.getWalletId(), amount, "OFFLINE_PURSE", "CARD_OFFLINE",
                nonce, "Offline card transaction (auth=" + authMethod + ")", null, null, null, nonce);

        card.setLastOfflineCounter(counter);
        cardRepository.save(card);
        log.info("Reconciled offline txn (card {}, counter {}, nonce {}, auth {}) into the ledger",
                vitoCardNumber, counter, nonce, authMethod);
        return txn;
    }

    private static boolean verify(String publicKeyPem, String payloadJson, String signatureB64) {
        try {
            Signature sig = Signature.getInstance("SHA256withECDSA");
            sig.initVerify(parseEcPublicKey(publicKeyPem));
            sig.update(payloadJson.getBytes(StandardCharsets.UTF_8));
            return sig.verify(Base64.getDecoder().decode(signatureB64));
        } catch (Exception e) {
            log.warn("Offline-txn signature verification error: {}", e.getMessage());
            return false;
        }
    }

    private static PublicKey parseEcPublicKey(String pem) throws Exception {
        String base64 = pem
                .replaceAll("-----BEGIN[^-]*-----", "")
                .replaceAll("-----END[^-]*-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(base64);
        return KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(der));
    }
}
