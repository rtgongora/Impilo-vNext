package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import zw.gov.mohcc.impilo.experience.telemedicine.TelemedicineOperatingModelRegistry;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemedicineOperatingModelControllerTest {

    private final TelemedicineOperatingModelController controller =
            new TelemedicineOperatingModelController(new TelemedicineOperatingModelRegistry());

    @Test
    @SuppressWarnings("unchecked")
    void listVirtualHospitals_returnsGovernedDirectoryWithHonestSubstrateStatus() {
        ResponseEntity<Map<String, Object>> response = controller.listVirtualHospitals("req-1", "corr-1");

        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        assertEquals("virtual-hospital-directory", data.get("type"));
        List<Map<String, Object>> hospitals =
                (List<Map<String, Object>>) ((Map<?, ?>) data.get("attributes")).get("virtualHospitals");
        assertTrue(hospitals.size() >= 20, "strategic + provincial institutions expected");
        for (Map<String, Object> hospital : hospitals) {
            assertNotNull(hospital.get("id"));
            assertNotNull(hospital.get("substrateStatus"), "every institution must carry an honest substrateStatus");
            assertNotNull(hospital.get("level"));
            assertNotNull(hospital.get("operatingModel"));
        }
        assertEquals("req-1", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    @Test
    void getVirtualHospital_returnsNationalInstitution() {
        ResponseEntity<Map<String, Object>> response =
                controller.getVirtualHospital("vh-national-telemedicine", "req-2", "corr-2");

        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        assertEquals("virtual-hospital", data.get("type"));
        Map<?, ?> attributes = (Map<?, ?>) data.get("attributes");
        assertEquals("NATIONAL", attributes.get("level"));
        assertEquals("POLICY_CONFIGURABLE_PENDING_DETERMINATION", attributes.get("regulatoryStatus"));
    }

    @Test
    void getVirtualHospital_returns404ForUnknownId() {
        ResponseEntity<Map<String, Object>> response =
                controller.getVirtualHospital("vh-nowhere", "req-3", "corr-3");

        assertEquals(404, response.getStatusCode().value());
        Map<?, ?> error = (Map<?, ?>) response.getBody().get("error");
        assertEquals("VIRTUAL_HOSPITAL_UNKNOWN", error.get("code"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void clinicalGroups_returnsFailClosedTaxonomy() {
        ResponseEntity<Map<String, Object>> response = controller.clinicalGroups("req-4", "corr-4");

        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        assertEquals("clinical-group-taxonomy", data.get("type"));
        Map<String, Object> attributes = (Map<String, Object>) data.get("attributes");
        assertEquals(Boolean.TRUE, attributes.get("creationBlocked"), "group creation must stay fail-closed (HO-5)");
        assertNotNull(attributes.get("creationBlockedReason"));
        List<Map<String, Object>> groupTypes = (List<Map<String, Object>>) attributes.get("groupTypes");
        assertEquals(15, groupTypes.size());
        assertFalse(((List<?>) attributes.get("governanceRequirements")).isEmpty());
        for (Map<String, Object> groupType : groupTypes) {
            assertNotNull(groupType.get("type"));
            assertNotNull(groupType.get("patientDataExposure"));
        }
    }
}
