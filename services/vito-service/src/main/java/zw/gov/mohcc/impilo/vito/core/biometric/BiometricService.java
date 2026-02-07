package zw.gov.mohcc.impilo.vito.core.biometric;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.vito.core.BiometricModality;
import zw.gov.mohcc.impilo.vito.persistence.entity.BiometricTemplateEntity;
import zw.gov.mohcc.impilo.vito.persistence.repository.BiometricTemplateRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Biometric Identity Service per ITU-T X.1081.
 *
 * Design constraints:
 *   - Templates stored as FHIR Binary resources (opaque blobs)
 *   - Strictly decoupled from demographic data
 *   - Linked to client via health_id only (no PII in biometric tables)
 *   - Template integrity verified via SHA-256 hash
 *   - Quality scoring (NFIQ2 or equivalent, 0-100)
 *
 * Supported modalities: FINGERPRINT, IRIS, FACE
 */
@Service
public class BiometricService {

    private final BiometricTemplateRepository templateRepository;

    public BiometricService(BiometricTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    /**
     * Enroll a biometric template. Supersedes any existing active template
     * for the same (healthId, modality, position) combination.
     */
    @Transactional
    public BiometricTemplateEntity enroll(UUID tenantId, UUID healthId,
                                           BiometricModality modality, String position,
                                           byte[] templateData, Integer qualityScore,
                                           String captureDevice, String capturedBy) {
        // Supersede existing active template at this position
        Optional<BiometricTemplateEntity> existing = templateRepository
                .findByTenantIdAndHealthIdAndModalityAndPositionAndStatus(
                        tenantId, healthId, modality, position, "ACTIVE");

        existing.ifPresent(t -> {
            t.setStatus("SUPERSEDED");
            t.setSupersededAt(OffsetDateTime.now());
            templateRepository.save(t);
        });

        BiometricTemplateEntity template = new BiometricTemplateEntity();
        template.setTenantId(tenantId);
        template.setHealthId(healthId);
        template.setModality(modality);
        template.setPosition(position);
        template.setTemplateData(templateData);
        template.setTemplateHash(sha256Hex(templateData));
        template.setQualityScore(qualityScore);
        template.setCaptureDevice(captureDevice);
        template.setCapturedBy(capturedBy);
        template.setStatus("ACTIVE");

        return templateRepository.save(template);
    }

    /**
     * Get all active biometric templates for a client.
     */
    @Transactional(readOnly = true)
    public List<BiometricTemplateEntity> getActiveTemplates(UUID tenantId, UUID healthId) {
        return templateRepository.findByTenantIdAndHealthIdAndStatus(tenantId, healthId, "ACTIVE");
    }

    /**
     * Verify template integrity using SHA-256 hash.
     */
    public boolean verifyIntegrity(BiometricTemplateEntity template) {
        String computed = sha256Hex(template.getTemplateData());
        return computed.equals(template.getTemplateHash());
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 not available", e);
        }
    }
}
