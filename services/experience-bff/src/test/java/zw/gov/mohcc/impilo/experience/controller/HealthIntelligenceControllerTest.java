package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import zw.gov.mohcc.impilo.experience.client.DagsServiceClient;
import zw.gov.mohcc.impilo.experience.client.GuidanceServiceClient;
import zw.gov.mohcc.impilo.experience.client.LearningServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.SearchServiceClient;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;
import zw.gov.mohcc.impilo.experience.intelligence.HealthIntelligenceAuthorizationService;
import zw.gov.mohcc.impilo.experience.intelligence.HealthIntelligenceService;
import zw.gov.mohcc.impilo.experience.intelligence.IntelligenceEventPublisher;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class HealthIntelligenceControllerTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void query_invokesService() {
        HealthIntelligenceService svc = stubService();
        HealthIntelligenceAuthorizationService authz = Mockito.mock(HealthIntelligenceAuthorizationService.class);
        Mockito.doNothing().when(authz).assertIntelligencePlaneAccess(Mockito.anyString());
        ObjectMapper om = new ObjectMapper();

        HealthIntelligenceController controller = new HealthIntelligenceController(svc, authz, om);
        MockHttpServletRequest req = new MockHttpServletRequest();
        ResponseEntity<Map<String, Object>> response = controller.executeQuery(
                req,
                "tenant-1",
                "req-1",
                "corr-1",
                "actor-1",
                Map.of("queryType", "SEARCH_FUSED", "input", Map.of("text", "x")));

        Mockito.verify(authz).assertIntelligencePlaneAccess("QUERY");
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertNotNull(((Map<?, ?>) response.getBody().get("data")).get("queryId"));
    }

    @Test
    void shellSuggest_invokesService() {
        HealthIntelligenceService svc = stubService();
        HealthIntelligenceAuthorizationService authz = Mockito.mock(HealthIntelligenceAuthorizationService.class);
        Mockito.doNothing().when(authz).assertIntelligencePlaneAccess(Mockito.anyString());
        ObjectMapper om = new ObjectMapper();

        HealthIntelligenceController controller = new HealthIntelligenceController(svc, authz, om);
        MockHttpServletRequest req = new MockHttpServletRequest();
        ResponseEntity<Map<String, Object>> response =
                controller.shellSuggest(req, "tenant-2", "req-2", "corr-2", "rx", "fac-1", null);

        Mockito.verify(authz).assertIntelligencePlaneAccess("SHELL_SUGGEST");
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(((Map<?, ?>) response.getBody().get("data")).get("queryId"));
    }

    private static HealthIntelligenceService stubService() {
        GuidanceServiceClient guidance = Mockito.mock(GuidanceServiceClient.class);
        SearchServiceClient search = Mockito.mock(SearchServiceClient.class);
        PctServiceClient pct = Mockito.mock(PctServiceClient.class);
        VitoServiceClient vito = Mockito.mock(VitoServiceClient.class);
        VarapiServiceClient varapi = Mockito.mock(VarapiServiceClient.class);
        ObjectNode idx = mapper.createObjectNode();
        idx.putArray("results").addObject().put("id", "1").put("entityType", "X");
        Mockito.when(search.search(Mockito.anyString(), Mockito.isNull(), Mockito.eq(0), Mockito.eq(12), Mockito.any()))
                .thenReturn(idx);
        Mockito.when(search.search(Mockito.anyString(), Mockito.isNull(), Mockito.eq(0), Mockito.eq(6), Mockito.any()))
                .thenReturn(idx);
        ArrayNode g = mapper.createArrayNode();
        g.addObject().put("title", "G");
        Mockito.when(guidance.search(Mockito.anyString(), Mockito.anyString(), Mockito.anyInt(), Mockito.anyInt()))
                .thenReturn(g);
        LearningServiceClient learning = Mockito.mock(LearningServiceClient.class);
        Mockito.when(learning.isConfigured()).thenReturn(false);
        DagsServiceClient dags = Mockito.mock(DagsServiceClient.class);
        ObjectProvider<IntelligenceEventPublisher> publisher = Mockito.mock(ObjectProvider.class);
        Mockito.when(publisher.getIfAvailable()).thenReturn(null);
        @SuppressWarnings("unchecked")
        ObjectProvider<zw.gov.mohcc.impilo.experience.client.DataPipelineServiceClient> pipeline =
                Mockito.mock(ObjectProvider.class);
        Mockito.when(pipeline.getIfAvailable()).thenReturn(null);
        @SuppressWarnings("unchecked")
        ObjectProvider<zw.gov.mohcc.impilo.experience.client.NdrServiceClient> ndr =
                Mockito.mock(ObjectProvider.class);
        Mockito.when(ndr.getIfAvailable()).thenReturn(null);
        return new HealthIntelligenceService(
                guidance, search, pct, vito, varapi, learning, dags, mapper, publisher, pipeline, ndr);
    }
}
