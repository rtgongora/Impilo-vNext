package zw.gov.mohcc.impilo.vito.core.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/**
 * JPA converter that transparently encrypts the Impilo ID column at rest
 * (Identity Contract §6). The Java field stays plaintext in memory (VITO is the
 * issuer, permitted to hold it transiently); the database column holds the
 * AES-256-GCM envelope from {@link ImpiloIdCipher}.
 *
 * <p>Applied explicitly via {@code @Convert} on the {@code impiloId} /
 * {@code impiloIdIssued} fields. Because GCM ciphertext is randomised per write,
 * columns carrying this converter can no longer be queried by value — value
 * lookups route through the alias vault instead (see
 * {@code IdentityService.findByImpiloId}).</p>
 *
 * <p>Registered as a Spring bean so Hibernate injects the cipher; requires
 * {@code hibernate.jpa.compliance...} default bean-container behaviour, which
 * Spring Boot 3 provides.</p>
 */
@Component
@Converter
public class ImpiloIdEncryptionConverter implements AttributeConverter<String, String> {

    private final ImpiloIdCipher cipher;

    public ImpiloIdEncryptionConverter(ImpiloIdCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return cipher.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return cipher.decrypt(dbData);
    }
}
