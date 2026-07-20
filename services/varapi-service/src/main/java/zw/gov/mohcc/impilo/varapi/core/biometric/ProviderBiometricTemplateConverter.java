package zw.gov.mohcc.impilo.varapi.core.biometric;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/**
 * JPA converter that transparently encrypts {@code provider_biometric_template.template_data} at rest
 * (PII Enforcement Wave, 0.1). The Java field holds the raw template only transiently in memory during
 * a matching op; the database column holds the AES-256-GCM envelope from {@link ProviderTemplateCrypto}.
 *
 * <p>Applied explicitly via {@code @Convert} on the {@code templateData} field. Registered as a Spring
 * bean so Hibernate injects the cipher (Spring Boot 3 bean-container default).</p>
 */
@Component
@Converter
public class ProviderBiometricTemplateConverter implements AttributeConverter<byte[], byte[]> {

    private final ProviderTemplateCrypto crypto;

    public ProviderBiometricTemplateConverter(ProviderTemplateCrypto crypto) {
        this.crypto = crypto;
    }

    @Override
    public byte[] convertToDatabaseColumn(byte[] attribute) {
        return crypto.encrypt(attribute);
    }

    @Override
    public byte[] convertToEntityAttribute(byte[] dbData) {
        return crypto.decrypt(dbData);
    }
}
