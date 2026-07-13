package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class HpaRegulatoryBffControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void eligibilitySnapshot_fetchesFromVarapi_notTuso() {
        TusoServiceClient tuso = mock(TusoServiceClient.class);
        VarapiServiceClient varapi = mock(VarapiServiceClient.class);
        ObjectNode snap = MAPPER.createObjectNode();
        snap.put("profession", "MEDICAL");
        when(varapi.getEligibilitySnapshot("snap-1")).thenReturn(snap);

        var controller = new HpaRegulatoryBffController(tuso, varapi);
        var response = controller.eligibilitySnapshot("snap-1", "req", "corr");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody().get("data"));
        verify(varapi).getEligibilitySnapshot(eq("snap-1"));
        // G30: the snapshot is VARAPI's SoR — TUSO must not be consulted for it.
        verifyNoInteractions(tuso);
    }
}
