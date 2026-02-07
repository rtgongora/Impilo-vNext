package zw.gov.mohcc.impilo.tshepo.keys.core;

import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.openssl.PEMParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tshepo.keys.persistence.entity.CertificateTrustEntity;
import zw.gov.mohcc.impilo.tshepo.keys.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tshepo.keys.persistence.repository.CertificateTrustRepository;
import zw.gov.mohcc.impilo.tshepo.keys.persistence.repository.EventOutboxRepository;

import java.io.StringReader;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing the CA certificate trust store used in mTLS verification.
 *
 * <p>Supports importing X.509 certificates in PEM format, revoking trust,
 * and verifying certificates against the trust store. Certificate metadata
 * (subject DN, issuer DN, serial, SHA-256 fingerprint) is extracted and stored
 * for efficient lookups.</p>
 */
@Service
public class CertificateTrustService {

    private static final Logger log = LoggerFactory.getLogger(CertificateTrustService.class);

    private final CertificateTrustRepository certificateRepository;
    private final EventOutboxRepository eventOutboxRepository;

    public CertificateTrustService(CertificateTrustRepository certificateRepository,
                                    EventOutboxRepository eventOutboxRepository) {
        this.certificateRepository = certificateRepository;
        this.eventOutboxRepository = eventOutboxRepository;
    }

    /**
     * Import a PEM-encoded X.509 certificate into the trust store.
     *
     * @param tenantId       the tenant this certificate belongs to
     * @param certificatePem PEM-encoded X.509 certificate
     * @return the persisted certificate entity
     * @throws IllegalArgumentException if the PEM is invalid or certificate already exists
     */
    @Transactional
    public CertificateTrustEntity importCertificate(UUID tenantId, String certificatePem) {
        X509Certificate x509 = parsePemCertificate(certificatePem);

        String fingerprint = computeSha256Fingerprint(x509);

        // Check for duplicate
        if (certificateRepository.existsByFingerprintSha256(fingerprint)) {
            throw new IllegalArgumentException("Certificate with fingerprint " + fingerprint + " already exists in trust store");
        }

        CertificateTrustEntity entity = new CertificateTrustEntity();
        entity.setTenantId(tenantId);
        entity.setSubjectDn(x509.getSubjectX500Principal().getName());
        entity.setIssuerDn(x509.getIssuerX500Principal().getName());
        entity.setSerialNumber(x509.getSerialNumber().toString(16));
        entity.setFingerprintSha256(fingerprint);
        entity.setCertificatePem(certificatePem.trim());
        entity.setStatus("TRUSTED");
        entity.setExpiresAt(x509.getNotAfter().toInstant());

        entity = certificateRepository.save(entity);

        // Write outbox event
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType("CertificateTrust");
        event.setAggregateId(fingerprint);
        event.setEventType("CERTIFICATE_IMPORTED");
        event.setPayload("{\"tenantId\":\"" + tenantId + "\","
                + "\"fingerprint\":\"" + fingerprint + "\","
                + "\"subjectDn\":\"" + x509.getSubjectX500Principal().getName() + "\"}");
        eventOutboxRepository.save(event);

        log.info("Imported certificate: fingerprint={}, subject={}, tenant={}",
                fingerprint, x509.getSubjectX500Principal().getName(), tenantId);

        return entity;
    }

    /**
     * Revoke trust for a specific certificate.
     */
    @Transactional
    public void revokeCertificate(Long id, UUID tenantId) {
        CertificateTrustEntity entity = certificateRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Certificate not found: " + id));

        entity.setStatus("REVOKED");
        certificateRepository.save(entity);

        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType("CertificateTrust");
        event.setAggregateId(entity.getFingerprintSha256());
        event.setEventType("CERTIFICATE_REVOKED");
        event.setPayload("{\"tenantId\":\"" + tenantId + "\","
                + "\"fingerprint\":\"" + entity.getFingerprintSha256() + "\","
                + "\"subjectDn\":\"" + entity.getSubjectDn() + "\"}");
        eventOutboxRepository.save(event);

        log.info("Revoked certificate: id={}, fingerprint={}, tenant={}", id, entity.getFingerprintSha256(), tenantId);
    }

    /**
     * List all certificates for a tenant.
     */
    public List<CertificateTrustEntity> listCertificates(UUID tenantId) {
        return certificateRepository.findByTenantIdOrderByImportedAtDesc(tenantId);
    }

    /**
     * List only TRUSTED certificates for a tenant.
     */
    public List<CertificateTrustEntity> listTrustedCertificates(UUID tenantId) {
        return certificateRepository.findByTenantIdAndStatusOrderByImportedAtDesc(tenantId, "TRUSTED");
    }

    /**
     * Verify that a certificate (by SHA-256 fingerprint) is in the trust store and TRUSTED.
     */
    public boolean isTrusted(String fingerprintSha256) {
        return certificateRepository.findByFingerprintSha256(fingerprintSha256)
                .map(cert -> "TRUSTED".equals(cert.getStatus()))
                .orElse(false);
    }

    /**
     * Verify that a PEM certificate is trusted by checking its fingerprint against the store.
     */
    public boolean isCertificateTrusted(String certificatePem) {
        X509Certificate x509 = parsePemCertificate(certificatePem);
        String fingerprint = computeSha256Fingerprint(x509);
        return isTrusted(fingerprint);
    }

    /**
     * Parse a PEM-encoded X.509 certificate using Bouncy Castle.
     */
    private X509Certificate parsePemCertificate(String pem) {
        try (PEMParser parser = new PEMParser(new StringReader(pem))) {
            Object parsed = parser.readObject();
            if (parsed instanceof X509CertificateHolder holder) {
                return new JcaX509CertificateConverter().getCertificate(holder);
            }
            throw new IllegalArgumentException("PEM does not contain a valid X.509 certificate");
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse PEM certificate: " + e.getMessage(), e);
        }
    }

    /**
     * Compute the SHA-256 fingerprint of an X.509 certificate (hex-encoded, lowercase).
     */
    private String computeSha256Fingerprint(X509Certificate cert) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(cert.getEncoded());
            return bytesToHex(digest);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute certificate fingerprint", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
