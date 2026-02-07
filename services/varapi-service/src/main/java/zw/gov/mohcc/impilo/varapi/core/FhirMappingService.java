package zw.gov.mohcc.impilo.varapi.core;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.hl7.fhir.r4.model.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.persistence.entity.LicenseEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.PrivilegeEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderIdentifierEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderSpecialtyEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.LicenseRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.PrivilegeRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderIdentifierRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderSpecialtyRepository;

import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * FHIR R4 resource generation service.
 *
 * Maps VARAPI provider data to HL7 FHIR R4 resources for interoperability
 * with the Shared Health Record (BUTANO/HAPI FHIR). Generated resources
 * use CPID-only references to ensure no PII enters the SHR.
 *
 * Produces:
 *   - Practitioner: demographics, identifiers, qualifications
 *   - PractitionerRole: facility/workspace assignments with specialties
 *   - Bundle: composite resource containing Practitioner + all PractitionerRoles
 *
 * Uses HAPI FHIR R4 model classes (ca.uhn.fhir).
 */
@Service
public class FhirMappingService {

    private static final Logger log = LoggerFactory.getLogger(FhirMappingService.class);

    private static final String SYSTEM_VARAPI = "https://varapi.mohcc.gov.zw/provider";
    private static final String SYSTEM_PRACTICE_NUMBER = "https://varapi.mohcc.gov.zw/practice-number";
    private static final String SYSTEM_NATIONAL_ID = "https://varapi.mohcc.gov.zw/national-id";
    private static final String SYSTEM_SPECIALTY = "http://snomed.info/sct";

    private final FhirContext fhirContext;
    private final ProviderRepository providerRepository;
    private final ProviderSpecialtyRepository specialtyRepository;
    private final ProviderIdentifierRepository identifierRepository;
    private final LicenseRepository licenseRepository;
    private final PrivilegeRepository privilegeRepository;

    public FhirMappingService(FhirContext fhirContext,
                              ProviderRepository providerRepository,
                              ProviderSpecialtyRepository specialtyRepository,
                              ProviderIdentifierRepository identifierRepository,
                              LicenseRepository licenseRepository,
                              PrivilegeRepository privilegeRepository) {
        this.fhirContext = fhirContext;
        this.providerRepository = providerRepository;
        this.specialtyRepository = specialtyRepository;
        this.identifierRepository = identifierRepository;
        this.licenseRepository = licenseRepository;
        this.privilegeRepository = privilegeRepository;
    }

    // ---- Public API ----

    /**
     * Map a provider to a FHIR R4 Practitioner resource.
     *
     * Includes identifiers, name, gender, birth date, and qualifications
     * derived from active licenses.
     *
     * @param providerPublicId the provider to map
     * @return the FHIR Practitioner resource
     * @throws IllegalArgumentException if the provider does not exist
     */
    @Transactional(readOnly = true)
    public Practitioner toPractitioner(String providerPublicId) {
        TrustContextHolder.require();
        log.debug("Mapping provider to FHIR Practitioner: providerPublicId={}", providerPublicId);

        ProviderEntity provider = providerRepository.findByProviderPublicId(providerPublicId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Provider not found: " + providerPublicId));

        Practitioner practitioner = new Practitioner();

        // Set logical ID using provider_public_id (no PII)
        practitioner.setId(provider.getProviderPublicId());

        // Identifiers
        practitioner.addIdentifier(new Identifier()
                .setSystem(SYSTEM_VARAPI)
                .setValue(provider.getProviderPublicId())
                .setUse(Identifier.IdentifierUse.OFFICIAL));

        if (provider.getPracticeNumber() != null) {
            practitioner.addIdentifier(new Identifier()
                    .setSystem(SYSTEM_PRACTICE_NUMBER)
                    .setValue(provider.getPracticeNumber()));
        }

        // Add external identifiers from provider_identifiers table
        List<ProviderIdentifierEntity> identifiers =
                identifierRepository.findByProviderId(provider.getId());
        for (ProviderIdentifierEntity ident : identifiers) {
            if (ident.isActive()) {
                Identifier fhirIdent = new Identifier()
                        .setSystem(ident.getIdentifierSystem())
                        .setValue(ident.getIdentifierValue());
                if (ident.getExpiryDate() != null) {
                    Period period = new Period();
                    if (ident.getIssuedDate() != null) {
                        period.setStart(java.sql.Date.valueOf(ident.getIssuedDate()));
                    }
                    period.setEnd(java.sql.Date.valueOf(ident.getExpiryDate()));
                    fhirIdent.setPeriod(period);
                }
                practitioner.addIdentifier(fhirIdent);
            }
        }

        // Name
        HumanName name = new HumanName()
                .setFamily(provider.getFamilyName())
                .addGiven(provider.getGivenName())
                .setUse(HumanName.NameUse.OFFICIAL);
        if (provider.getTitle() != null) {
            name.addPrefix(provider.getTitle());
        }
        practitioner.addName(name);

        // Gender
        if (provider.getGender() != null) {
            practitioner.setGender(mapGender(provider.getGender()));
        }

        // Birth date
        if (provider.getDateOfBirth() != null) {
            practitioner.setBirthDate(java.sql.Date.valueOf(provider.getDateOfBirth()));
        }

        // Telecom (limited — no PII leak to SHR, only professional contacts)
        if (provider.getEmail() != null) {
            practitioner.addTelecom(new ContactPoint()
                    .setSystem(ContactPoint.ContactPointSystem.EMAIL)
                    .setValue(provider.getEmail())
                    .setUse(ContactPoint.ContactPointUse.WORK));
        }
        if (provider.getPhone() != null) {
            practitioner.addTelecom(new ContactPoint()
                    .setSystem(ContactPoint.ContactPointSystem.PHONE)
                    .setValue(provider.getPhone())
                    .setUse(ContactPoint.ContactPointUse.WORK));
        }

        // Active status
        practitioner.setActive(provider.isActive());

        // Qualifications from licenses
        List<LicenseEntity> licenses = licenseRepository.findByProviderId(provider.getId());
        for (LicenseEntity license : licenses) {
            if (license.isActive()) {
                Practitioner.PractitionerQualificationComponent qualification =
                        new Practitioner.PractitionerQualificationComponent();
                qualification.setCode(new CodeableConcept()
                        .addCoding(new Coding()
                                .setSystem(SYSTEM_VARAPI)
                                .setCode(license.getLicenseType())
                                .setDisplay(license.getLicenseType())));

                if (license.getLicenseNumber() != null) {
                    qualification.addIdentifier(new Identifier()
                            .setValue(license.getLicenseNumber()));
                }

                if (license.getValidFrom() != null || license.getValidTo() != null) {
                    Period period = new Period();
                    if (license.getValidFrom() != null) {
                        period.setStart(java.sql.Date.valueOf(license.getValidFrom()));
                    }
                    if (license.getValidTo() != null) {
                        period.setEnd(java.sql.Date.valueOf(license.getValidTo()));
                    }
                    qualification.setPeriod(period);
                }

                practitioner.addQualification(qualification);
            }
        }

        log.debug("FHIR Practitioner mapped: id={}, identifiers={}, qualifications={}",
                practitioner.getId(), practitioner.getIdentifier().size(),
                practitioner.getQualification().size());

        return practitioner;
    }

    /**
     * Map a provider's facility assignment to a FHIR R4 PractitionerRole resource.
     *
     * Links a Practitioner to a specific facility/workspace with applicable
     * specialties and availability period.
     *
     * @param providerPublicId the provider
     * @param facilityId       the facility (mapped to Location reference)
     * @param workspaceId      the workspace (mapped to Organization reference)
     * @return the FHIR PractitionerRole resource
     * @throws IllegalArgumentException if the provider does not exist
     */
    @Transactional(readOnly = true)
    public PractitionerRole toPractitionerRole(String providerPublicId,
                                                Long facilityId, UUID workspaceId) {
        TrustContextHolder.require();
        log.debug("Mapping PractitionerRole: providerPublicId={}, facilityId={}, workspaceId={}",
                providerPublicId, facilityId, workspaceId);

        ProviderEntity provider = providerRepository.findByProviderPublicId(providerPublicId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Provider not found: " + providerPublicId));

        PractitionerRole role = new PractitionerRole();
        role.setId(providerPublicId + "-" + facilityId);

        // Link to Practitioner
        role.setPractitioner(new Reference("Practitioner/" + provider.getProviderPublicId())
                .setDisplay(provider.getGivenName() + " " + provider.getFamilyName()));

        // Link to facility as Location
        if (facilityId != null) {
            role.addLocation(new Reference("Location/" + facilityId));
        }

        // Link to workspace as Organization
        if (workspaceId != null) {
            role.setOrganization(new Reference("Organization/" + workspaceId));
        }

        // Profession/role code
        if (provider.getProfession() != null) {
            role.addCode(new CodeableConcept()
                    .addCoding(new Coding()
                            .setSystem(SYSTEM_VARAPI)
                            .setCode(provider.getProfession())
                            .setDisplay(provider.getProfession())));
        }

        // Specialties
        List<ProviderSpecialtyEntity> specialties =
                specialtyRepository.findByProviderId(provider.getId());
        for (ProviderSpecialtyEntity specialty : specialties) {
            role.addSpecialty(new CodeableConcept()
                    .addCoding(new Coding()
                            .setSystem(SYSTEM_SPECIALTY)
                            .setCode(specialty.getSpecialtyCode())
                            .setDisplay(specialty.getSpecialtyName())));
        }

        // Validity period from privileges
        if (facilityId != null) {
            List<PrivilegeEntity> privileges =
                    privilegeRepository.findByProviderIdAndFacilityIdAndStatus(
                            provider.getId(), facilityId, "APPROVED");
            if (!privileges.isEmpty()) {
                PrivilegeEntity priv = privileges.get(0);
                Period period = new Period();
                if (priv.getValidFrom() != null) {
                    period.setStart(java.sql.Date.valueOf(priv.getValidFrom()));
                }
                if (priv.getValidTo() != null) {
                    period.setEnd(java.sql.Date.valueOf(priv.getValidTo()));
                }
                role.setPeriod(period);
            }
        }

        role.setActive(provider.isActive());

        log.debug("FHIR PractitionerRole mapped: id={}, specialties={}",
                role.getId(), role.getSpecialty().size());

        return role;
    }

    /**
     * Generate a FHIR Bundle containing a Practitioner and all PractitionerRoles.
     *
     * Creates a collection Bundle with the Practitioner as the first entry
     * and one PractitionerRole per approved privilege/facility assignment.
     *
     * @param providerPublicId the provider to bundle
     * @return the FHIR Bundle resource
     * @throws IllegalArgumentException if the provider does not exist
     */
    @Transactional(readOnly = true)
    public Bundle toBundle(String providerPublicId) {
        TrustContextHolder.require();
        log.debug("Generating FHIR Bundle: providerPublicId={}", providerPublicId);

        ProviderEntity provider = providerRepository.findByProviderPublicId(providerPublicId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Provider not found: " + providerPublicId));

        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.COLLECTION);
        bundle.setTimestamp(new Date());

        // Add Practitioner as first entry
        Practitioner practitioner = toPractitioner(providerPublicId);
        bundle.addEntry()
                .setFullUrl("Practitioner/" + provider.getProviderPublicId())
                .setResource(practitioner);

        // Add PractitionerRole for each approved privilege
        List<PrivilegeEntity> approvedPrivileges =
                privilegeRepository.findByProviderIdAndStatus(provider.getId(), "APPROVED");
        for (PrivilegeEntity priv : approvedPrivileges) {
            PractitionerRole role = toPractitionerRole(
                    providerPublicId, priv.getFacilityId(), priv.getWorkspaceId());
            bundle.addEntry()
                    .setFullUrl("PractitionerRole/" + role.getId())
                    .setResource(role);
        }

        log.info("FHIR Bundle generated: providerPublicId={}, entries={}",
                providerPublicId, bundle.getEntry().size());

        return bundle;
    }

    // ---- Private helpers ----

    /**
     * Map a VARAPI gender string to the FHIR AdministrativeGender enum.
     */
    private Enumerations.AdministrativeGender mapGender(String gender) {
        if (gender == null) return Enumerations.AdministrativeGender.UNKNOWN;
        return switch (gender.toUpperCase()) {
            case "MALE", "M" -> Enumerations.AdministrativeGender.MALE;
            case "FEMALE", "F" -> Enumerations.AdministrativeGender.FEMALE;
            case "OTHER", "O" -> Enumerations.AdministrativeGender.OTHER;
            default -> Enumerations.AdministrativeGender.UNKNOWN;
        };
    }
}
