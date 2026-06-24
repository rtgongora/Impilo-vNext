package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.HrPayrollServiceClient;
import zw.gov.mohcc.impilo.experience.client.VashandiServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErpHrBffControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    @Test
    void employeesFailClosesWithTypedErrorWhenHrServiceUnavailable() {
        ErpHrBffController controller = new ErpHrBffController(new FailingHrPayrollClient(), new StubVashandiClient());

        ResponseEntity<?> response = controller.employees("req-hr-1", "corr-hr-1");

        assertEquals(502, response.getStatusCode().value());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("HR_PAYROLL_UNAVAILABLE", ((Map<?, ?>) body.get("error")).get("code"));
    }

    @Test
    void attendanceReadsVashandiByProvider() throws Exception {
        ErpHrBffController controller = new ErpHrBffController(new StubHrPayrollClient(), new StubVashandiClient());

        ResponseEntity<?> response = controller.attendance("PRV-7", 2026, 3, "req-1", "corr-1");

        assertEquals(200, response.getStatusCode().value());
        JsonNode body = (JsonNode) response.getBody();
        assertNotNull(body);
        assertTrue(body.isArray());
        assertEquals("check_in", body.get(0).get("eventType").asText());
    }

    @Test
    void leaveReadsVashandiByProvider() {
        ErpHrBffController controller = new ErpHrBffController(new StubHrPayrollClient(), new StubVashandiClient());

        ResponseEntity<?> response = controller.leaveRequests("PRV-7", "req-1", "corr-1");

        assertEquals(200, response.getStatusCode().value());
        JsonNode body = (JsonNode) response.getBody();
        assertEquals("ANNUAL", body.get(0).get("leaveType").asText());
    }

    @Test
    void leaveTypesReadVashandi() {
        ErpHrBffController controller = new ErpHrBffController(new StubHrPayrollClient(), new StubVashandiClient());

        ResponseEntity<?> response = controller.leaveTypes("req-1", "corr-1");

        assertEquals(200, response.getStatusCode().value());
        JsonNode body = (JsonNode) response.getBody();
        assertEquals("ANNUAL", body.get(0).get("name").asText());
    }

    @Test
    void attendanceFailClosesWhenVashandiUnavailable() {
        ErpHrBffController controller = new ErpHrBffController(new StubHrPayrollClient(), new FailingVashandiClient());

        ResponseEntity<?> response = controller.attendance("PRV-7", 2026, 3, "req-1", "corr-1");

        assertEquals(502, response.getStatusCode().value());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("VASHANDI_UNAVAILABLE", ((Map<?, ?>) body.get("error")).get("code"));
    }

    // ── stubs ──

    private static class StubHrPayrollClient extends HrPayrollServiceClient {
        StubHrPayrollClient() { super(new RestTemplate(), endpoints()); }
    }

    private static final class FailingHrPayrollClient extends HrPayrollServiceClient {
        FailingHrPayrollClient() { super(new RestTemplate(), endpoints()); }
        @Override public JsonNode getEmployees() { throw new RuntimeException("hr-payroll unavailable"); }
    }

    private static class StubVashandiClient extends VashandiServiceClient {
        StubVashandiClient() { super(new RestTemplate(), endpoints(), MAPPER); }
        @Override public JsonNode getAttendanceEventsByProvider(String p, int y, int m) {
            try { return MAPPER.readTree("[{\"eventType\":\"check_in\",\"eventTime\":\"2026-03-02T08:00:00Z\"}]"); }
            catch (Exception e) { throw new RuntimeException(e); }
        }
        @Override public JsonNode getLeaveByProvider(String p) {
            try { return MAPPER.readTree("[{\"leaveType\":\"ANNUAL\",\"startDate\":\"2026-03-01\",\"endDate\":\"2026-03-03\",\"status\":\"pending\"}]"); }
            catch (Exception e) { throw new RuntimeException(e); }
        }
        @Override public JsonNode getLeaveTypes() {
            try { return MAPPER.readTree("[{\"name\":\"ANNUAL\",\"annualEntitlement\":21}]"); }
            catch (Exception e) { throw new RuntimeException(e); }
        }
    }

    private static final class FailingVashandiClient extends VashandiServiceClient {
        FailingVashandiClient() { super(new RestTemplate(), endpoints(), MAPPER); }
        @Override public JsonNode getAttendanceEventsByProvider(String p, int y, int m) {
            throw new RuntimeException("vashandi unavailable");
        }
        @Override public JsonNode getLeaveByProvider(String p) {
            throw new RuntimeException("vashandi unavailable");
        }
    }
}
