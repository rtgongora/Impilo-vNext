package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WU5 — the trauma unknown-patient identity proxies forward to VITO (provisional mint +
 * MergeService merge) and wrap the response as {@code {data:...}}. The web reconcile must
 * hit the merge endpoint (which emits vito.merge.executed), not per-service repoint hooks.
 */
class TraumaIdentityControllerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void mintProvisional_delegatesAndReturns201() {
        VitoServiceClient vito = mock(VitoServiceClient.class);
        ObjectNode minted = mapper.createObjectNode();
        minted.put("health_id", "TEMP-1");
        minted.put("crid", "CRID-1");
        minted.put("status", "PROVISIONAL");
        when(vito.mintProvisionalIdentity(any())).thenReturn(minted);

        var resp = new TraumaIdentityController(vito).mintProvisional(Map.of("estimated_sex", "M", "descriptor", "unknown RTC"));

        assertEquals(201, resp.getStatusCode().value());
        assertEquals("PROVISIONAL", ((JsonNode) resp.getBody().get("data")).get("status").asText());
        verify(vito).mintProvisionalIdentity(any());
    }

    @Test
    void merge_delegatesToVitoMergeService() {
        VitoServiceClient vito = mock(VitoServiceClient.class);
        ObjectNode merged = mapper.createObjectNode();
        merged.put("survivor_crid", "CRID-SURV");
        merged.put("merged", true);
        when(vito.mergePatients(any())).thenReturn(merged);

        var body = Map.<String, Object>of("survivor_crid", "CRID-SURV", "merged_crids", List.of("CRID-1"), "reason", "IDENTIFIED");
        var resp = new TraumaIdentityController(vito).merge(body);

        assertEquals(200, resp.getStatusCode().value());
        assertEquals(true, ((JsonNode) resp.getBody().get("data")).get("merged").asBoolean());
        verify(vito).mergePatients(any());
    }
}
