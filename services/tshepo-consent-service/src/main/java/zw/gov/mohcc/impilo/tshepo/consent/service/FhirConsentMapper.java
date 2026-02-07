package zw.gov.mohcc.impilo.tshepo.consent.service;

import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.Period;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.tshepo.consent.dto.ConsentDirectiveDto;
import zw.gov.mohcc.impilo.tshepo.consent.dto.CreateConsentRequest;
import zw.gov.mohcc.impilo.tshepo.consent.exception.InvalidFhirConsentException;
import zw.gov.mohcc.impilo.tshepo.consent.persistence.ConsentDirectiveEntity;

import java.time.Instant;

/**
 * Converts between FHIR R4 Consent resources and {@link ConsentDirectiveEntity}.
 *
 * <p>Uses HAPI FHIR {@link IParser} for serialization/deserialization. Every inbound
 * FHIR Consent JSON is validated during parsing — invalid resources are rejected.</p>
 */
@Component
public class FhirConsentMapper {

    private static final Logger log = LoggerFactory.getLogger(FhirConsentMapper.class);

    private final IParser fhirJsonParser;

    public FhirConsentMapper(IParser fhirJsonParser) {
        this.fhirJsonParser = fhirJsonParser;
    }

    /**
     * Validates and parses a FHIR R4 Consent JSON string.
     *
     * @param fhirJson the FHIR Consent resource as JSON
     * @return the parsed HAPI FHIR Consent resource
     * @throws InvalidFhirConsentException if the JSON is malformed or not a valid Consent resource
     */
    public Consent parseFhirConsent(String fhirJson) {
        if (fhirJson == null || fhirJson.isBlank()) {
            throw new InvalidFhirConsentException("FHIR Consent JSON must not be null or blank");
        }
        try {
            Consent consent = fhirJsonParser.parseResource(Consent.class, fhirJson);
            validateConsentResource(consent);
            return consent;
        } catch (DataFormatException e) {
            throw new InvalidFhirConsentException("Invalid FHIR Consent JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Serializes a HAPI FHIR Consent resource to JSON.
     */
    public String serializeFhirConsent(Consent consent) {
        return fhirJsonParser.encodeResourceToString(consent);
    }

    /**
     * Validates the parsed Consent resource has the minimum required structure.
     */
    private void validateConsentResource(Consent consent) {
        if (consent.getStatus() == null) {
            throw new InvalidFhirConsentException("FHIR Consent must have a status");
        }
        if (consent.getScope() == null || consent.getScope().isEmpty()) {
            throw new InvalidFhirConsentException("FHIR Consent must have a scope");
        }
        if (consent.getCategory().isEmpty()) {
            throw new InvalidFhirConsentException("FHIR Consent must have at least one category");
        }
    }

    /**
     * Creates a new {@link ConsentDirectiveEntity} from a create request and parsed FHIR Consent.
     * Extracts period information from the FHIR resource when available.
     */
    public ConsentDirectiveEntity toEntity(CreateConsentRequest request, Consent fhirConsent) {
        ConsentDirectiveEntity entity = new ConsentDirectiveEntity();
        entity.setTenantId(request.tenantId());
        entity.setPatientRef(request.patientRef());
        entity.setGrantorRef(request.grantorRef());
        entity.setGranteeRef(request.granteeRef());
        entity.setScope(request.scope());
        entity.setPurpose(request.purpose());
        entity.setProvision(request.provision() != null ? request.provision() : "permit");
        entity.setStatus("ACTIVE");
        entity.setFhirConsentJson(serializeFhirConsent(fhirConsent));

        // Extract period from FHIR Consent provision if available
        if (fhirConsent.getProvision() != null && fhirConsent.getProvision().getPeriod() != null) {
            Period period = fhirConsent.getProvision().getPeriod();
            if (period.getStart() != null) {
                entity.setPeriodStart(period.getStart().toInstant());
            }
            if (period.getEnd() != null) {
                entity.setPeriodEnd(period.getEnd().toInstant());
            }
        }

        // Fallback: if no period start, default to now
        if (entity.getPeriodStart() == null) {
            entity.setPeriodStart(Instant.now());
        }

        return entity;
    }

    /**
     * Converts a {@link ConsentDirectiveEntity} to a {@link ConsentDirectiveDto}.
     */
    public ConsentDirectiveDto toDto(ConsentDirectiveEntity entity) {
        return new ConsentDirectiveDto(
                entity.getId(),
                entity.getTenantId(),
                entity.getPatientRef(),
                entity.getGrantorRef(),
                entity.getGranteeRef(),
                entity.getStatus(),
                entity.getScope(),
                entity.getPurpose(),
                entity.getProvision(),
                entity.getPeriodStart(),
                entity.getPeriodEnd(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getRevokedAt(),
                entity.getRevocationReason()
        );
    }
}
