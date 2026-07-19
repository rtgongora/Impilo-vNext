package zw.gov.mohcc.impilo.tuso.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityCredentialEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityCredentialRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.NamedParameterSpec;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Facility QR credentials (FJ QR, D-L7). Issues a signed credential + short
 * verification code; scanning yields a policy-filtered public projection (never
 * internal ids, confidential findings or administrator PII). Ed25519 signing is
 * seed-derived and restart/cross-instance stable (the vito QrSigningService
 * house pattern, replicated locally). The LIVE facility status still comes from
 * the registry at scan time, so a revoked credential or a closed facility reads
 * honestly.
 */
@Service
public class FacilityCredentialService {

    private static final Logger log = LoggerFactory.getLogger(FacilityCredentialService.class);
    private static final String DEV_FALLBACK_SEED = "tuso-facility-credential-dev-seed-do-not-use";

    private final FacilityRepository facilityRepository;
    private final FacilityCredentialRepository credentialRepository;
    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public FacilityCredentialService(FacilityRepository facilityRepository,
                                     FacilityCredentialRepository credentialRepository,
                                     @Value("${tuso.credential.signing-key-seed:}") String configuredSeed) {
        this.facilityRepository = facilityRepository;
        this.credentialRepository = credentialRepository;
        String seed = configuredSeed == null || configuredSeed.isBlank()
                ? DEV_FALLBACK_SEED : configuredSeed;
        if (DEV_FALLBACK_SEED.equals(seed)) {
            log.warn("FACILITY CREDENTIAL SIGNING IS USING THE DEV FALLBACK SEED — set "
                    + "TUSO_CREDENTIAL_SIGNING_KEY_SEED before issuing real credentials.");
        }
        try {
            byte[] seed32 = MessageDigest.getInstance("SHA-256").digest(seed.getBytes());
            KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
            SecureRandom deterministic = SecureRandom.getInstance("SHA1PRNG");
            deterministic.setSeed(seed32);
            generator.initialize(NamedParameterSpec.ED25519, deterministic);
            KeyPair pair = generator.generateKeyPair();
            this.privateKey = pair.getPrivate();
            this.publicKey = pair.getPublic();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialise facility credential key", e);
        }
    }

    public record IssuedCredential(UUID credentialUuid, String verificationCode, String qrPayload) {}

    /** The policy-filtered public projection served on a valid scan. */
    public record CredentialVerification(String status, String facilityName, String facilityCode,
                                         String facilityType, String province, String district,
                                         String facilityStatus) {
        static final CredentialVerification NOT_RECOGNISED =
                new CredentialVerification("NOT_RECOGNISED", null, null, null, null, null, null);
    }

    @Transactional
    public IssuedCredential issue(UUID facilityUuid) {
        TrustContext ctx = TrustContextHolder.require();
        FacilityEntity facility = requireFacility(facilityUuid, ctx.tenantId());

        byte[] raw = new byte[9];
        secureRandom.nextBytes(raw);
        String code = "FVC" + java.util.HexFormat.of().formatHex(raw).toUpperCase(Locale.ROOT);

        FacilityCredentialEntity credential = new FacilityCredentialEntity();
        credential.setTenantId(ctx.tenantId());
        credential.setFacilityUuid(facility.getFacilityUuid());
        credential.setVerificationCode(code);
        credential.setIssuedBy(ctx.actorId());
        credential = credentialRepository.save(credential);

        // The QR carries the verification code only — never internal ids; the
        // scan resolves live registry facts by that code.
        String qr = sign("FVC:" + code);
        log.info("facility credential {} issued for {}", code, facility.getFacilityUuid());
        return new IssuedCredential(credential.getCredentialUuid(), code, qr);
    }

    @Transactional
    public FacilityCredentialEntity revoke(String verificationCode, String reason) {
        TrustContext ctx = TrustContextHolder.require();
        FacilityCredentialEntity credential = credentialRepository.findByVerificationCode(verificationCode.trim())
                .filter(c -> ctx.tenantId().equals(c.getTenantId()))
                .orElseThrow(() -> new IllegalArgumentException("Unknown credential"));
        if (!FacilityCredentialEntity.STATUS_ACTIVE.equals(credential.getStatus())) {
            return credential;
        }
        credential.setStatus(FacilityCredentialEntity.STATUS_REVOKED);
        credential.setRevokedAt(java.time.Instant.now());
        credential.setRevokeReason(reason);
        return credentialRepository.save(credential);
    }

    /**
     * Public scan verification. Verifies the signed QR, resolves the live
     * facility, and returns the policy-filtered projection. Every failure —
     * bad signature, unknown/revoked code, missing facility — collapses to one
     * NOT_RECOGNISED shape.
     */
    @Transactional(readOnly = true)
    public CredentialVerification verifyScan(String qrOrCode) {
        String code;
        String verifiedPayload = verify(qrOrCode);
        if (verifiedPayload != null && verifiedPayload.startsWith("FVC:")) {
            code = verifiedPayload.substring(4);
        } else if (qrOrCode != null && qrOrCode.startsWith("FVC") && !qrOrCode.contains(".")) {
            // A human-typed short code (no signature) is allowed — status still
            // comes from the DB, so it is not an oracle beyond the public page.
            code = qrOrCode.trim();
        } else {
            return CredentialVerification.NOT_RECOGNISED;
        }

        Optional<FacilityCredentialEntity> credential = credentialRepository.findByVerificationCode(code)
                .filter(c -> FacilityCredentialEntity.STATUS_ACTIVE.equals(c.getStatus()));
        if (credential.isEmpty()) {
            return CredentialVerification.NOT_RECOGNISED;
        }
        Optional<FacilityEntity> facility = facilityRepository
                .findByFacilityUuid(credential.get().getFacilityUuid());
        if (facility.isEmpty()) {
            return CredentialVerification.NOT_RECOGNISED;
        }
        FacilityEntity f = facility.get();
        return new CredentialVerification("VERIFIED", f.getName(), f.getFacilityCode(),
                f.getFacilityType(), f.getProvince(), f.getDistrict(), f.getStatus());
    }

    private FacilityEntity requireFacility(UUID facilityUuid, UUID tenantId) {
        FacilityEntity facility = facilityRepository.findByFacilityUuid(facilityUuid)
                .orElseThrow(() -> new IllegalArgumentException("Facility not found: " + facilityUuid));
        if (!facility.getTenantId().equals(tenantId)) {
            throw new SecurityException("Tenant isolation violation for facility: " + facilityUuid);
        }
        // A provisional facility cannot carry a public credential.
        if (FacilityRegistrationService.STATUS_PROVISIONAL.equalsIgnoreCase(facility.getStatus())) {
            throw new IllegalStateException("A provisional facility cannot be issued a credential.");
        }
        return facility;
    }

    private String sign(String payload) {
        try {
            Signature signature = Signature.getInstance("Ed25519");
            signature.initSign(privateKey);
            byte[] payloadBytes = payload.getBytes();
            signature.update(payloadBytes);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(payloadBytes)
                    + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
        } catch (Exception e) {
            throw new IllegalStateException("Credential signing failed", e);
        }
    }

    private String verify(String compact) {
        try {
            if (compact == null || !compact.contains(".")) {
                return null;
            }
            String[] parts = compact.split("\\.", 2);
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[0]);
            byte[] signatureBytes = Base64.getUrlDecoder().decode(parts[1]);
            Signature signature = Signature.getInstance("Ed25519");
            signature.initVerify(publicKey);
            signature.update(payloadBytes);
            return signature.verify(signatureBytes) ? new String(payloadBytes) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
