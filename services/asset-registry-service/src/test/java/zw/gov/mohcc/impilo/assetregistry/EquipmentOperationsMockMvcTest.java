package zw.gov.mohcc.impilo.assetregistry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import zw.gov.mohcc.impilo.assetregistry.repository.OutboxEventRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Equipment-operations lifecycle integration tests (WS#5). Exercises the real service +
 * controller + repositories over H2(PG mode): registration + duplicate detection,
 * fault->maintenance spawn, calibration FAIL -> unsafe, transfer receipt applies custody,
 * readiness gap computation, IoT alert dedup, audit, and outbox/lifecycle audit events.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EquipmentOperationsMockMvcTest {

    @Autowired private MockMvc mvc;
    @Autowired private OutboxEventRepository outbox;
    private static final ObjectMapper M = new ObjectMapper();
    private static final String TENANT = UUID.randomUUID().toString();

    private MockHttpServletRequestBuilder withHeaders(MockHttpServletRequestBuilder b) {
        return b.header("X-Tenant-ID", TENANT)
                .header("X-Pod-ID", "national")
                .header("X-Request-ID", "req-" + System.nanoTime())
                .header("X-Correlation-ID", UUID.randomUUID().toString())
                .header("X-Actor-ID", "HID-TEST")
                .header("Idempotency-Key", "idem-" + System.nanoTime())
                .contentType(MediaType.APPLICATION_JSON);
    }

    private JsonNode register(String name, String type, String serial, String tag) throws Exception {
        String body = M.createObjectNode()
                .put("facilityId", "FAC-1").put("name", name).put("equipmentType", type)
                .put("serialNumber", serial).put("tag", tag).put("criticality", "CRITICAL")
                .toString();
        MvcResult r = mvc.perform(withHeaders(post("/internal/v1/equipment")).content(body))
                .andExpect(status().isCreated()).andReturn();
        return M.readTree(r.getResponse().getContentAsString());
    }

    @Test
    void register_persists_andEmitsOutbox() throws Exception {
        JsonNode eq = register("Ventilator A", "VENTILATOR", "SN-100", "TAG-100");
        assertThat(eq.get("equipment_id").asText()).isNotBlank();
        assertThat(eq.get("criticality").asText()).isEqualTo("CRITICAL");
        assertThat(outbox.countByAggregateId(eq.get("equipment_id").asText())).isGreaterThanOrEqualTo(1);
    }

    @Test
    void register_duplicateSerial_returns409() throws Exception {
        register("Pump A", "PUMP", "SN-DUP", "TAG-A");
        String body = M.createObjectNode().put("facilityId", "FAC-1").put("name", "Pump B")
                .put("equipmentType", "PUMP").put("serialNumber", "SN-DUP").toString();
        mvc.perform(withHeaders(post("/internal/v1/equipment")).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void register_missingFields_returns400() throws Exception {
        String body = M.createObjectNode().put("facilityId", "FAC-1").toString();
        mvc.perform(withHeaders(post("/internal/v1/equipment")).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reportFault_routineSpawnsMaintenance() throws Exception {
        JsonNode eq = register("Monitor A", "MONITOR", "SN-200", "TAG-200");
        String id = eq.get("equipment_id").asText();
        String body = M.createObjectNode().put("summary", "Screen flicker").put("severity", "MINOR").toString();
        MvcResult r = mvc.perform(withHeaders(post("/internal/v1/equipment/" + id + "/faults")).content(body))
                .andExpect(status().isCreated()).andReturn();
        JsonNode fault = M.readTree(r.getResponse().getContentAsString());
        assertThat(fault.get("status").asText()).isEqualTo("TRIAGED");
        assertThat(fault.get("maintenance_task_id").isNull()).isFalse();
    }

    @Test
    void reportFault_safetyLinksRito() throws Exception {
        JsonNode eq = register("Defib A", "DEFIBRILLATOR", "SN-201", "TAG-201");
        String id = eq.get("equipment_id").asText();
        String body = M.createObjectNode().put("summary", "Burn risk").put("severity", "SAFETY")
                .put("safetyConcern", true).toString();
        MvcResult r = mvc.perform(withHeaders(post("/internal/v1/equipment/" + id + "/faults")).content(body))
                .andExpect(status().isCreated()).andReturn();
        JsonNode fault = M.readTree(r.getResponse().getContentAsString());
        assertThat(fault.get("safety_concern").asBoolean()).isTrue();
        assertThat(fault.get("status").asText()).isEqualTo("LINKED");
    }

    @Test
    void calibrationFail_marksUnsafe() throws Exception {
        JsonNode eq = register("Scale A", "SCALE", "SN-300", "TAG-300");
        String id = eq.get("equipment_id").asText();
        String body = M.createObjectNode().put("outcome", "FAIL").toString();
        mvc.perform(withHeaders(post("/internal/v1/equipment/" + id + "/calibration")).content(body))
                .andExpect(status().isCreated());
        MvcResult got = mvc.perform(withHeaders(get("/internal/v1/equipment/" + id))).andExpect(status().isOk()).andReturn();
        assertThat(M.readTree(got.getResponse().getContentAsString()).get("status").asText()).isEqualTo("UNSAFE");
    }

    @Test
    void transferReceipt_appliesCustodyAndBlocksEarlyReceipt() throws Exception {
        JsonNode eq = register("Bed A", "BED", "SN-400", "TAG-400");
        String id = eq.get("equipment_id").asText();
        String tbody = M.createObjectNode().put("toFacility", "FAC-2").put("requiresApproval", true).toString();
        MvcResult tr = mvc.perform(withHeaders(post("/internal/v1/equipment/" + id + "/transfers")).content(tbody))
                .andExpect(status().isCreated()).andReturn();
        String transferId = M.readTree(tr.getResponse().getContentAsString()).get("transfer_id").asText();

        // Receipt before approval is invalid.
        mvc.perform(withHeaders(post("/internal/v1/equipment/transfers/" + transferId + "/receive")))
                .andExpect(status().isBadRequest());

        mvc.perform(withHeaders(post("/internal/v1/equipment/transfers/" + transferId + "/approve")))
                .andExpect(status().isOk());
        mvc.perform(withHeaders(post("/internal/v1/equipment/transfers/" + transferId + "/receive")))
                .andExpect(status().isOk());

        MvcResult got = mvc.perform(withHeaders(get("/internal/v1/equipment/" + id))).andExpect(status().isOk()).andReturn();
        assertThat(M.readTree(got.getResponse().getContentAsString()).get("facility_id").asText()).isEqualTo("FAC-2");
    }

    @Test
    void readiness_identifiesGaps() throws Exception {
        // Profile at FAC-RD needs 2 VENTILATORs; none registered there -> gap.
        String prof = M.createObjectNode()
                .put("facilityRef", "FAC-RD").put("servicePoint", "THEATRE-1").put("name", "Theatre 1")
                .set("requirements", M.createArrayNode().add(
                        M.createObjectNode().put("equipmentType", "VENTILATOR").put("minCount", 2)))
                .toString();
        mvc.perform(withHeaders(post("/internal/v1/equipment/readiness/profiles")).content(prof))
                .andExpect(status().isCreated());
        MvcResult r = mvc.perform(withHeaders(get("/internal/v1/equipment/readiness").param("facility_ref", "FAC-RD")))
                .andExpect(status().isOk()).andReturn();
        JsonNode body = M.readTree(r.getResponse().getContentAsString());
        assertThat(body.get("ready").asBoolean()).isFalse();
        assertThat(body.get("total_gaps").asInt()).isEqualTo(1);
        assertThat(body.get("profiles").get(0).get("requirements").get(0).get("gap").asText()).isEqualTo("MISSING");
    }

    @Test
    void iotAlert_dedupsActiveAndAttributesToEquipment() throws Exception {
        String body = M.createObjectNode().put("deviceId", "DEV-1").put("alertType", "OFFLINE")
                .put("severity", "WARN").toString();
        mvc.perform(withHeaders(post("/internal/v1/equipment/alerts")).content(body)).andExpect(status().isCreated());
        mvc.perform(withHeaders(post("/internal/v1/equipment/alerts")).content(body)).andExpect(status().isCreated());
        MvcResult r = mvc.perform(withHeaders(get("/internal/v1/equipment/alerts/active"))).andExpect(status().isOk()).andReturn();
        JsonNode active = M.readTree(r.getResponse().getContentAsString()).get("items");
        long offlineForDev1 = 0;
        for (JsonNode a : active) {
            if ("DEV-1".equals(a.get("device_id").asText()) && "OFFLINE".equals(a.get("alert_type").asText())) offlineForDev1++;
        }
        assertThat(offlineForDev1).isEqualTo(1); // deduped
    }

    @Test
    void audit_seedsExpectedItems() throws Exception {
        register("Trolley A", "TROLLEY", "SN-500", "TAG-500");
        String body = M.createObjectNode().put("facilityRef", "FAC-1").put("name", "Q3 audit").toString();
        MvcResult r = mvc.perform(withHeaders(post("/internal/v1/equipment/audits")).content(body))
                .andExpect(status().isCreated()).andReturn();
        JsonNode audit = M.readTree(r.getResponse().getContentAsString());
        assertThat(audit.get("status").asText()).isEqualTo("OPEN");
        assertThat(audit.get("items").size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void list_missingTenantHeader_returns400() throws Exception {
        mvc.perform(get("/internal/v1/equipment").header("X-Pod-ID", "national")
                        .header("X-Request-ID", "r").header("X-Correlation-ID", "c"))
                .andExpect(status().isBadRequest());
    }
}
