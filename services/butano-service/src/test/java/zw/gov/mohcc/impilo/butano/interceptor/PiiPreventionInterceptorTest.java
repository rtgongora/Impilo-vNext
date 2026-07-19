package zw.gov.mohcc.impilo.butano.interceptor;

import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.butano.config.ButanoProperties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link PiiPreventionInterceptor} — the core PII-free
 * enforcement mechanism for the BUTANO Shared Health Record.
 *
 * <p>Validates that Patient resources containing PII fields (name, telecom,
 * address, photo, contact) are rejected with HTTP 422, while Patient resources
 * with only permitted fields (CPID identifier, gender, birthDate) are accepted.</p>
 *
 * <p>Also validates that non-Patient resources referencing patients via
 * CPID-based references are accepted.</p>
 */
@ExtendWith(MockitoExtension.class)
class PiiPreventionInterceptorTest {

    @Mock
    private RequestDetails requestDetails;

    private PiiPreventionInterceptor interceptor;

    @BeforeEach
    void setUp() {
        ButanoProperties properties = new ButanoProperties();
        // Ensure PII enforcement is enabled for tests
        properties.getPii().setEnforce(true);
        interceptor = new PiiPreventionInterceptor(properties);
    }

    // ════════════════════════════════════════════════════════════════════
    // Patient resources that should be REJECTED (contain PII)
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("reject Patient with name — names are PII stored in VITO")
    void testRejectPatientWithName() {
        Patient patient = new Patient();
        patient.addIdentifier()
                .setSystem(PiiPreventionInterceptor.CPID_SYSTEM)
                .setValue("CPID-12345");
        patient.addName().setFamily("Smith").addGiven("John");

        UnprocessableEntityException ex = assertThrows(
                UnprocessableEntityException.class,
                () -> interceptor.onResourceCreated(patient, requestDetails),
                "Patient with name field must be rejected with 422"
        );

        assertTrue(ex.getMessage().contains("Patient.name"),
                "Error message must indicate that Patient.name is not permitted");
        assertTrue(ex.getMessage().contains("PII"),
                "Error message must reference PII violation");
    }

    @Test
    @DisplayName("reject Patient with telecom — contact info is PII stored in VITO")
    void testRejectPatientWithTelecom() {
        Patient patient = new Patient();
        patient.addIdentifier()
                .setSystem(PiiPreventionInterceptor.CPID_SYSTEM)
                .setValue("CPID-12345");
        patient.addTelecom()
                .setSystem(ContactPoint.ContactPointSystem.PHONE)
                .setValue("+263771234567");

        UnprocessableEntityException ex = assertThrows(
                UnprocessableEntityException.class,
                () -> interceptor.onResourceCreated(patient, requestDetails),
                "Patient with telecom field must be rejected with 422"
        );

        assertTrue(ex.getMessage().contains("Patient.telecom"),
                "Error message must indicate that Patient.telecom is not permitted");
    }

    @Test
    @DisplayName("reject Patient with address — addresses are PII stored in VITO")
    void testRejectPatientWithAddress() {
        Patient patient = new Patient();
        patient.addIdentifier()
                .setSystem(PiiPreventionInterceptor.CPID_SYSTEM)
                .setValue("CPID-12345");
        patient.addAddress()
                .setCity("Harare")
                .setCountry("ZW");

        UnprocessableEntityException ex = assertThrows(
                UnprocessableEntityException.class,
                () -> interceptor.onResourceCreated(patient, requestDetails),
                "Patient with address field must be rejected with 422"
        );

        assertTrue(ex.getMessage().contains("Patient.address"),
                "Error message must indicate that Patient.address is not permitted");
    }

    // ════════════════════════════════════════════════════════════════════
    // Patient resources that should be ACCEPTED (no PII)
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("allow Patient with CPID identifier only — minimal valid Patient")
    void testAllowPatientWithCpidOnly() {
        Patient patient = new Patient();
        patient.addIdentifier()
                .setSystem(PiiPreventionInterceptor.CPID_SYSTEM)
                .setValue("CPID-12345");
        patient.setActive(true);

        // Should not throw — CPID-only Patient is valid
        assertDoesNotThrow(
                () -> interceptor.onResourceCreated(patient, requestDetails),
                "Patient with only CPID identifier should be accepted"
        );
    }

    @Test
    @DisplayName("allow Patient with gender and birthDate — permitted clinical demographics")
    void testAllowPatientWithGenderAndBirthDate() {
        Patient patient = new Patient();
        patient.addIdentifier()
                .setSystem(PiiPreventionInterceptor.CPID_SYSTEM)
                .setValue("CPID-67890");
        patient.setGender(Enumerations.AdministrativeGender.FEMALE);
        patient.setBirthDateElement(new DateType("1990-05-15"));
        patient.setActive(true);

        // Should not throw — gender and birthDate are permitted
        assertDoesNotThrow(
                () -> interceptor.onResourceCreated(patient, requestDetails),
                "Patient with gender and birthDate (but no PII) should be accepted"
        );
    }

    // ════════════════════════════════════════════════════════════════════
    // Non-Patient resources with patient references
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("allow Encounter with CPID-based subject reference")
    void testAllowEncounterWithCpidSubject() {
        Encounter encounter = new Encounter();
        encounter.setStatus(Encounter.EncounterStatus.FINISHED);
        encounter.setSubject(new Reference("Patient/CPID-12345"));
        encounter.setClass_(new Coding(
                "http://terminology.hl7.org/CodeSystem/v3-ActCode",
                "AMB", "ambulatory"));

        // Should not throw — Encounter referencing Patient by ID is valid
        assertDoesNotThrow(
                () -> interceptor.onResourceCreated(encounter, requestDetails),
                "Encounter with Patient reference by ID should be accepted"
        );
    }
}
