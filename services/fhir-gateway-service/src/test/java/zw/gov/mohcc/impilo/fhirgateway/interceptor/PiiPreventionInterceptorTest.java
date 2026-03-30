package zw.gov.mohcc.impilo.fhirgateway.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.fhirgateway.config.FhirGatewayProperties;

import static org.junit.jupiter.api.Assertions.*;

class PiiPreventionInterceptorTest {

    private PiiPreventionInterceptor interceptor;

    @BeforeEach
    void setUp() {
        FhirGatewayProperties properties = new FhirGatewayProperties();
        properties.getPii().setEnforce(true);
        interceptor = new PiiPreventionInterceptor(new ObjectMapper(), properties);
    }

    @Test
    @DisplayName("reject Patient with name — names are PII stored in VITO")
    void testRejectPatientWithName() {
        String json = """
                {
                  "resourceType": "Patient",
                  "identifier": [{"system": "%s", "value": "CPID-001"}],
                  "name": [{"family": "Smith", "given": ["John"]}]
                }
                """.formatted(PiiPreventionInterceptor.CPID_SYSTEM);

        PiiViolationException ex = assertThrows(
                PiiViolationException.class,
                () -> interceptor.validatePayload(json)
        );

        assertTrue(ex.getMessage().contains("Patient.name"),
                "Error must mention Patient.name");
        assertTrue(ex.getMessage().contains("PII"),
                "Error must reference PII violation");
    }

    @Test
    @DisplayName("reject Patient with telecom — contact info is PII stored in VITO")
    void testRejectPatientWithTelecom() {
        String json = """
                {
                  "resourceType": "Patient",
                  "identifier": [{"system": "%s", "value": "CPID-002"}],
                  "telecom": [{"system": "phone", "value": "+263771234567"}]
                }
                """.formatted(PiiPreventionInterceptor.CPID_SYSTEM);

        PiiViolationException ex = assertThrows(
                PiiViolationException.class,
                () -> interceptor.validatePayload(json)
        );

        assertTrue(ex.getMessage().contains("Patient.telecom"),
                "Error must mention Patient.telecom");
    }

    @Test
    @DisplayName("reject Patient with address — addresses are PII stored in VITO")
    void testRejectPatientWithAddress() {
        String json = """
                {
                  "resourceType": "Patient",
                  "identifier": [{"system": "%s", "value": "CPID-003"}],
                  "address": [{"city": "Harare", "country": "ZW"}]
                }
                """.formatted(PiiPreventionInterceptor.CPID_SYSTEM);

        PiiViolationException ex = assertThrows(
                PiiViolationException.class,
                () -> interceptor.validatePayload(json)
        );

        assertTrue(ex.getMessage().contains("Patient.address"),
                "Error must mention Patient.address");
    }

    @Test
    @DisplayName("reject Patient with non-CPID identifier system")
    void testRejectPatientWithNonCpidIdentifier() {
        String json = """
                {
                  "resourceType": "Patient",
                  "identifier": [{"system": "http://other.system/id", "value": "12345"}]
                }
                """;

        PiiViolationException ex = assertThrows(
                PiiViolationException.class,
                () -> interceptor.validatePayload(json)
        );

        assertTrue(ex.getMessage().contains("Patient.identifier"),
                "Error must mention Patient.identifier");
        assertTrue(ex.getMessage().contains(PiiPreventionInterceptor.CPID_SYSTEM),
                "Error must reference CPID system");
    }

    @Test
    @DisplayName("allow Patient with CPID identifier only — minimal valid Patient")
    void testAllowPatientWithCpidOnly() {
        String json = """
                {
                  "resourceType": "Patient",
                  "identifier": [{"system": "%s", "value": "CPID-004"}],
                  "active": true
                }
                """.formatted(PiiPreventionInterceptor.CPID_SYSTEM);

        assertDoesNotThrow(() -> interceptor.validatePayload(json),
                "Patient with only CPID identifier should be accepted");
    }

    @Test
    @DisplayName("allow Patient with gender and birthDate — permitted clinical demographics")
    void testAllowPatientWithGenderAndBirthDate() {
        String json = """
                {
                  "resourceType": "Patient",
                  "identifier": [{"system": "%s", "value": "CPID-005"}],
                  "gender": "female",
                  "birthDate": "1990-05-15",
                  "active": true
                }
                """.formatted(PiiPreventionInterceptor.CPID_SYSTEM);

        assertDoesNotThrow(() -> interceptor.validatePayload(json),
                "Patient with gender and birthDate (but no PII) should be accepted");
    }

    @Test
    @DisplayName("allow non-Patient FHIR resource without any checks")
    void testAllowNonPatientResource() {
        String json = """
                {
                  "resourceType": "Encounter",
                  "status": "finished",
                  "subject": {"reference": "Patient/CPID-006"}
                }
                """;

        assertDoesNotThrow(() -> interceptor.validatePayload(json),
                "Non-Patient resources should pass through without PII checks");
    }

    @Test
    @DisplayName("allow null or blank body — no-op")
    void testAllowNullOrBlankBody() {
        assertDoesNotThrow(() -> interceptor.validatePayload(null),
                "Null body should not throw");
        assertDoesNotThrow(() -> interceptor.validatePayload(""),
                "Empty body should not throw");
        assertDoesNotThrow(() -> interceptor.validatePayload("   "),
                "Blank body should not throw");
    }

    @Test
    @DisplayName("allow non-JSON body — graceful skip")
    void testAllowNonJsonBody() {
        assertDoesNotThrow(() -> interceptor.validatePayload("not valid json"),
                "Non-JSON body should not throw — gracefully skipped");
    }

    @Test
    @DisplayName("skip PII check when enforce is disabled")
    void testSkipCheckWhenEnforceDisabled() {
        FhirGatewayProperties properties = new FhirGatewayProperties();
        properties.getPii().setEnforce(false);
        PiiPreventionInterceptor disabledInterceptor =
                new PiiPreventionInterceptor(new ObjectMapper(), properties);

        String json = """
                {
                  "resourceType": "Patient",
                  "name": [{"family": "Smith"}]
                }
                """;

        assertDoesNotThrow(() -> disabledInterceptor.validatePayload(json),
                "PII check should be skipped when enforce=false");
    }
}
