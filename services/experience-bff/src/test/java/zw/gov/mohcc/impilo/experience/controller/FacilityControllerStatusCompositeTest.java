package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpServerErrorException;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;
import zw.gov.mohcc.impilo.experience.config.BffFacilitiesProperties;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for the facility legitimacy composite BFF read proxy
 * ({@code GET /internal/v1/facilities/{id}/status-composite}). Fail-closed: any TUSO failure
 * returns 502 (no stub fallback). Pure-Mockito, matching the sibling controller-test conventions.
 */
@ExtendWith(MockitoExtension.class)
class FacilityControllerStatusCompositeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String REQ = "req-1";
    private static final String CORR = "corr-1";
    private static final String FACILITY_UUID = "11111111-2222-3333-4444-555555555555";

    @Mock private TusoServiceClient tusoClient;
    @Mock private BffFacilitiesProperties facilitiesProperties;

    private FacilityController controller() {
        return new FacilityController(tusoClient, facilitiesProperties);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> error(ResponseEntity<Map<String, Object>> resp) {
        assertNotNull(resp.getBody());
        return (Map<String, Object>) resp.getBody().get("error");
    }

    @Test
    void statusComposite_passesTusoVerdictThrough() throws Exception {
        when(tusoClient.getFacilityStatusComposite(FACILITY_UUID)).thenReturn(MAPPER.readTree(
                "{\"facilityId\":\"" + FACILITY_UUID + "\",\"facilityCode\":\"ZW-HCH-001\","
                        + "\"facilityName\":\"Harare Central Hospital\",\"regulatoryStatus\":\"REGISTERED_ACTIVE\","
                        + "\"certificates\":[],\"sourceLegitimacy\":[{\"source\":\"HPA_LEGAL\","
                        + "\"status\":\"REGISTERED_CURRENT\",\"allowedOnPlatform\":true}],"
                        + "\"platformAccessAllowed\":true,\"reasons\":[]}"));

        ResponseEntity<Map<String, Object>> resp =
                controller().getFacilityStatusComposite(FACILITY_UUID, REQ, CORR);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        Object data = resp.getBody().get("data");
        assertNotNull(data, "composite passes through under data");
        assertTrue(String.valueOf(data).contains("platformAccessAllowed"));
        assertTrue(String.valueOf(data).contains("HPA_LEGAL"));
    }

    @Test
    void statusComposite_nullUpstream_is404() {
        when(tusoClient.getFacilityStatusComposite(FACILITY_UUID)).thenReturn(null);

        ResponseEntity<Map<String, Object>> resp =
                controller().getFacilityStatusComposite(FACILITY_UUID, REQ, CORR);

        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }

    @Test
    void statusComposite_upstreamFailure_failsClosedWith502() {
        when(tusoClient.getFacilityStatusComposite(FACILITY_UUID)).thenThrow(
                HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR,
                        "boom", null, null, null));

        ResponseEntity<Map<String, Object>> resp =
                controller().getFacilityStatusComposite(FACILITY_UUID, REQ, CORR);

        assertEquals(HttpStatus.BAD_GATEWAY, resp.getStatusCode());
        assertEquals("TUSO_UNAVAILABLE", error(resp).get("code"));
    }
}
