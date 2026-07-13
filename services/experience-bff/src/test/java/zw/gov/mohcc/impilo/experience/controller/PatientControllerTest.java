package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PatientControllerTest {

    @Test
    void listPatients_failsCleanWhenVitoUnavailable_neverSeeds() {
        // VITO is the registry of record. With it down, the BFF must NOT serve a
        // fabricated seeded directory — it fails clean with 503 VITO_UNAVAILABLE.
        PatientController controller = new PatientController(new UnavailableVitoClient());

        ResponseEntity<Map<String, Object>> response =
                controller.listPatients("req-1", "corr-1", 0, 20, null, null);

        assertEquals(503, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("VITO_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
        assertEquals("req-1", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    @Test
    void listPatients_returnsRealVitoResultsWhenAvailable() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode paged = mapper.createObjectNode();
        paged.put("totalElements", 1);
        ObjectNode item = paged.putArray("items").addObject();
        item.put("healthId", "33333333-3333-3333-3333-333333333333");
        item.put("displayName", "Tatenda Moyo");
        item.put("lifecycleStatus", "ACTIVE");
        PatientController controller = new PatientController(new ListReturningVitoClient(paged));

        ResponseEntity<Map<String, Object>> response =
                controller.listPatients("req-2", "corr-2", 0, 20, null, null);

        assertEquals(200, response.getStatusCode().value());
        List<?> data = (List<?>) response.getBody().get("data");
        assertEquals(1, data.size());
        Map<?, ?> row = (Map<?, ?>) data.get(0);
        assertEquals("33333333-3333-3333-3333-333333333333", row.get("id"));
        Map<?, ?> attrs = (Map<?, ?>) row.get("attributes");
        assertEquals("Tatenda Moyo", attrs.get("displayName"));
    }

    @Test
    void getPatient_failsCleanWhenVitoUnavailable_neverSeeds() {
        PatientController controller = new PatientController(new UnavailableVitoClient());

        ResponseEntity<Map<String, Object>> response =
                controller.getPatient("pat-001", "req-3", "corr-3");

        assertEquals(503, response.getStatusCode().value());
        assertEquals("VITO_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void toClientRegistryRegistration_mapsExtendedDemographics() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("given_name", "Tendai");
        body.put("middle_name", "Ruvarashe");
        body.put("family_name", "Moyo");
        body.put("date_of_birth", "1992-04-07");
        body.put("sex", "male");
        body.put("phone", "+263771234567");
        body.put("email", "tendai.moyo@example.zw");
        body.put("passport_reference", "P12345678");
        body.put("address_line", "12 Samora Machel Ave");
        body.put("city", "Harare");
        body.put("preferred_language", "en-ZW");
        body.put("marital_status", "MARRIED");
        body.put("emergency_contact_name", "Rudo Moyo");
        body.put("emergency_contact_phone", "+263772345678");
        body.put("registration_mode", "HEALTH_WORKER_INITIATED");
        body.put("source_workflow", "EXPERIENCE_VITO_WIZARD");

        Map<String, Object> reg = PatientController.toClientRegistryRegistration(body);

        assertEquals("Tendai", reg.get("firstName"));
        assertEquals("Ruvarashe", reg.get("middleName"));
        assertEquals("Moyo", reg.get("lastName"));
        assertEquals("tendai.moyo@example.zw", reg.get("email"));
        assertEquals("P12345678", reg.get("passportReference"));
        assertEquals("12 Samora Machel Ave", reg.get("addressLine1"));
        assertEquals("Harare", reg.get("city"));
        assertEquals("en-ZW", reg.get("preferredLanguage"));
        assertEquals("MARRIED", reg.get("maritalStatus"));
        assertEquals("Rudo Moyo", reg.get("emergencyContactName"));
        assertEquals("+263772345678", reg.get("emergencyContactPhone"));
        assertEquals("FACILITY_REGISTRATION", reg.get("registrationType"));
    }

    @Test
    void toClientRegistryRegistration_mapsMedicalAidNumberFromCoverage() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("given_name", "Tendai");
        body.put("family_name", "Moyo");
        Map<String, Object> coverage = new LinkedHashMap<>();
        coverage.put("membership_number", "MA-99881");
        body.put("coverage", coverage);

        Map<String, Object> reg = PatientController.toClientRegistryRegistration(body);

        // The medical-aid number is threaded to VITO so it is tied to the Health ID (optional).
        assertEquals("MA-99881", reg.get("medicalAidNumber"));
    }

    @Test
    void toClientRegistryRegistration_omitsMedicalAidNumberWhenAbsent() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("given_name", "Tendai");
        body.put("family_name", "Moyo");

        Map<String, Object> reg = PatientController.toClientRegistryRegistration(body);

        assertFalse(reg.containsKey("medicalAidNumber"));
    }

    @Test
    void createPatient_returnsExtendedDemographicsFromRegistryProfile() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode profile = mapper.createObjectNode();
        ObjectNode master = profile.putObject("master");
        master.put("healthId", "11111111-1111-1111-1111-111111111111");
        master.put("firstName", "Tendai");
        master.put("middleName", "Ruvarashe");
        master.put("lastName", "Moyo");
        master.put("dateOfBirth", "1992-04-07");
        master.put("sex", "male");
        master.put("lifecycleStatus", "PROVISIONAL");
        master.putObject("contacts")
                .put("email", "tendai.moyo@example.zw")
                .put("phone", "+263771234567")
                .put("emergencyContactName", "Rudo Moyo")
                .put("emergencyContactPhone", "+263772345678");
        master.putObject("address")
                .put("addressLine1", "12 Samora Machel Ave")
                .put("city", "Harare");
        master.putObject("demographics")
                .put("preferredLanguage", "en-ZW")
                .put("maritalStatus", "MARRIED");

        PatientController controller = new PatientController(new RegistryCapturingVitoClient(profile));

        ResponseEntity<Map<String, Object>> response = controller.createPatient(
                "tenant-1",
                "req-ext",
                "corr-ext",
                null,
                Map.of(
                        "given_name", "Tendai",
                        "middle_name", "Ruvarashe",
                        "family_name", "Moyo",
                        "date_of_birth", "1992-04-07",
                        "sex", "male",
                        "email", "tendai.moyo@example.zw",
                        "preferred_language", "en-ZW"
                )
        );

        assertEquals(201, response.getStatusCode().value());
        Map<?, ?> attrs = (Map<?, ?>) ((Map<?, ?>) response.getBody().get("data")).get("attributes");
        assertEquals("Ruvarashe", attrs.get("middleName"));
        assertEquals("tendai.moyo@example.zw", attrs.get("email"));
        assertEquals("en-ZW", attrs.get("preferredLanguage"));
        assertEquals("MARRIED", attrs.get("maritalStatus"));
        assertEquals("Harare", attrs.get("city"));
        assertEquals(true, attrs.get("registryDelegation"));
    }

    @Test
    void getPatient_mapsPassportReferenceFromProfileIdentifiers() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode profile = mapper.createObjectNode();
        ObjectNode master = profile.putObject("master");
        master.put("healthId", "11111111-1111-1111-1111-111111111111");
        master.put("firstName", "Tendai");
        master.put("lastName", "Moyo");
        master.put("dateOfBirth", "1992-04-07");
        master.put("sex", "male");
        master.put("lifecycleStatus", "PROVISIONAL");
        var identifiers = profile.putArray("identifiers");
        ObjectNode passport = identifiers.addObject();
        passport.put("identifierType", "PASSPORT_REFERENCE");
        passport.put("identifierValue", "P12345678");

        PatientController controller = new PatientController(new ProfileReturningVitoClient(profile));

        ResponseEntity<Map<String, Object>> response = controller.getPatient(
                "11111111-1111-1111-1111-111111111111",
                "req-passport-get",
                "corr-passport-get");

        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> attrs = (Map<?, ?>) ((Map<?, ?>) response.getBody().get("data")).get("attributes");
        assertEquals("P12345678", attrs.get("passportReference"));
    }

    @Test
    void getPatient_mapsMedicalAidNumberFromProfileIdentifiers() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode profile = mapper.createObjectNode();
        ObjectNode master = profile.putObject("master");
        master.put("healthId", "11111111-1111-1111-1111-111111111111");
        master.put("firstName", "Tendai");
        master.put("lastName", "Moyo");
        master.put("dateOfBirth", "1992-04-07");
        master.put("sex", "male");
        master.put("lifecycleStatus", "PROVISIONAL");
        var identifiers = profile.putArray("identifiers");
        ObjectNode ma = identifiers.addObject();
        ma.put("identifierType", "MEDICAL_AID_NUMBER");
        ma.put("identifierValue", "MA-99881");

        PatientController controller = new PatientController(new ProfileReturningVitoClient(profile));

        ResponseEntity<Map<String, Object>> response = controller.getPatient(
                "11111111-1111-1111-1111-111111111111", "req-ma-get", "corr-ma-get");

        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> attrs = (Map<?, ?>) ((Map<?, ?>) response.getBody().get("data")).get("attributes");
        assertEquals("MA-99881", attrs.get("medicalAidNumber"));
    }

    @Test
    void getPatient_omitsPassportReferenceWhenIdentifiersAbsent() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode profile = mapper.createObjectNode();
        ObjectNode master = profile.putObject("master");
        master.put("healthId", "22222222-2222-2222-2222-222222222222");
        master.put("firstName", "Tendai");
        master.put("lastName", "Moyo");
        master.put("dateOfBirth", "1992-04-07");
        master.put("sex", "male");
        master.put("lifecycleStatus", "PROVISIONAL");

        PatientController controller = new PatientController(new ProfileReturningVitoClient(profile));

        ResponseEntity<Map<String, Object>> response = controller.getPatient(
                "22222222-2222-2222-2222-222222222222",
                "req-minimal-get",
                "corr-minimal-get");

        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> attrs = (Map<?, ?>) ((Map<?, ?>) response.getBody().get("data")).get("attributes");
        assertFalse(attrs.containsKey("passportReference"));
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static final class RegistryCapturingVitoClient extends VitoServiceClient {
        private final JsonNode profile;

        RegistryCapturingVitoClient(JsonNode profile) {
            super(new RestTemplate(), endpoints());
            this.profile = profile;
        }

        @Override
        public JsonNode createClientRegistration(Map<String, Object> body) {
            return profile;
        }
    }

    private static final class ProfileReturningVitoClient extends VitoServiceClient {
        private final JsonNode profile;

        ProfileReturningVitoClient(JsonNode profile) {
            super(new RestTemplate(), endpoints());
            this.profile = profile;
        }

        @Override
        public JsonNode getClientRegistryProfile(String id) {
            return profile;
        }
    }

    private static final class ListReturningVitoClient extends VitoServiceClient {
        private final JsonNode paged;

        ListReturningVitoClient(JsonNode paged) {
            super(new RestTemplate(), endpoints());
            this.paged = paged;
        }

        @Override
        public JsonNode listClientRegistryClients(String search, String status, String verificationState, int page, int size) {
            return paged;
        }
    }

    private static final class UnavailableVitoClient extends VitoServiceClient {
        UnavailableVitoClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode listClientRegistryClients(String search, String status, String verificationState, int page, int size) {
            throw new RuntimeException("vito unavailable");
        }

        @Override
        public JsonNode getClientRegistryProfile(String id) {
            throw new RuntimeException("vito unavailable");
        }

        @Override
        public JsonNode createClientRegistration(Map<String, Object> body) {
            throw new RuntimeException("vito unavailable");
        }

        @Override
        public JsonNode getPatient(String id) {
            throw new RuntimeException("vito unavailable");
        }
    }
}
