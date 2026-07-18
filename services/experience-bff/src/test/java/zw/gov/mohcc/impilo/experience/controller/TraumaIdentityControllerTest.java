package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.experience.client.TshepoIdentityServiceClient;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WU5 — trauma unknown-patient identity. The provisional mint forwards to
 * tshepo-identity (Identity Contract §7.2 — the browser surface receives only
 * {cpid, status}, never a Health ID); the merge still forwards to VITO's
 * MergeService, whose event the SubjectTranslationRelay converts for clinical
 * repointing. Responses are wrapped as {@code {data:...}}.
 */
class TraumaIdentityControllerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void mintProvisional_delegatesToTrustCoreAndReturnsCpidOnly() {
        VitoServiceClient vito = mock(VitoServiceClient.class);
        TshepoIdentityServiceClient tshepoIdentity = mock(TshepoIdentityServiceClient.class);
        ObjectNode minted = mapper.createObjectNode();
        minted.put("cpid", "cccccccc-cccc-4ccc-8ccc-cccccccccccc");
        minted.put("status", "PROVISIONAL");
        when(tshepoIdentity.provisionSubject(any())).thenReturn(minted);

        var resp = new TraumaIdentityController(vito, tshepoIdentity)
                .mintProvisional(Map.of("estimated_sex", "M", "descriptor", "unknown RTC"));

        assertEquals(201, resp.getStatusCode().value());
        JsonNode data = (JsonNode) resp.getBody().get("data");
        assertEquals("PROVISIONAL", data.get("status").asText());
        assertEquals("cccccccc-cccc-4ccc-8ccc-cccccccccccc", data.get("cpid").asText());
        // The Health ID never reaches the trauma surface
        assertEquals(false, data.has("health_id"));
        verify(tshepoIdentity).provisionSubject(any());
    }

    @Test
    void merge_delegatesToVitoMergeService() {
        VitoServiceClient vito = mock(VitoServiceClient.class);
        ObjectNode merged = mapper.createObjectNode();
        merged.put("survivor_crid", "CRID-SURV");
        merged.put("merged", true);
        when(vito.mergePatients(any())).thenReturn(merged);

        var body = Map.<String, Object>of("survivor_crid", "CRID-SURV", "merged_crids", List.of("CRID-1"), "reason", "IDENTIFIED");
        var resp = new TraumaIdentityController(vito, mock(TshepoIdentityServiceClient.class)).merge(body);

        assertEquals(200, resp.getStatusCode().value());
        assertEquals(true, ((JsonNode) resp.getBody().get("data")).get("merged").asBoolean());
        verify(vito).mergePatients(any());
    }
}
