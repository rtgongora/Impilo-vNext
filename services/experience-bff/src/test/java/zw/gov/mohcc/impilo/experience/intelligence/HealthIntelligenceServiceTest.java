package zw.gov.mohcc.impilo.experience.intelligence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import zw.gov.mohcc.impilo.experience.client.DagsServiceClient;
import zw.gov.mohcc.impilo.experience.client.GuidanceServiceClient;
import zw.gov.mohcc.impilo.experience.client.LearningServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.SearchServiceClient;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthIntelligenceServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private GuidanceServiceClient guidance;
    private SearchServiceClient search;
    private PctServiceClient pct;
    private VitoServiceClient vito;
    private VarapiServiceClient varapi;
    private LearningServiceClient learning;
    private DagsServiceClient dags;
    private HealthIntelligenceService service;

    @BeforeEach
    void setUp() {
        guidance = Mockito.mock(GuidanceServiceClient.class);
        search = Mockito.mock(SearchServiceClient.class);
        pct = Mockito.mock(PctServiceClient.class);
        vito = Mockito.mock(VitoServiceClient.class);
        varapi = Mockito.mock(VarapiServiceClient.class);
        learning = Mockito.mock(LearningServiceClient.class);
        Mockito.when(learning.isConfigured()).thenReturn(false);
        dags = Mockito.mock(DagsServiceClient.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<IntelligenceEventPublisher> publisher = Mockito.mock(ObjectProvider.class);
        Mockito.when(publisher.getIfAvailable()).thenReturn(null);
        service = new HealthIntelligenceService(guidance, search, pct, vito, varapi, learning, dags, mapper, publisher);
    }

    @Test
    void executeQuery_fusedSearch_mergesSources() throws Exception {
        ObjectNode idx = mapper.createObjectNode();
        ArrayNode results = idx.putArray("results");
        results.addObject().put("id", "e1").put("entityType", "PATIENT").put("entityId", "p1");
        Mockito.when(search.search(Mockito.eq("flu"), Mockito.isNull(), Mockito.eq(0), Mockito.eq(12), Mockito.any()))
                .thenReturn(idx);

        ArrayNode gHits = mapper.createArrayNode();
        gHits.addObject().put("title", "Guidance article");
        Mockito.when(guidance.search("flu", "all", 0, 8)).thenReturn(gHits);

        Map<String, Object> body = Map.of(
                "queryType", "SEARCH_FUSED",
                "input", Map.of("text", "flu"),
                "context", Map.of("domain", "all"));
        Map<String, Object> data = service.executeQuery(body, "t1", "r1", "c1", "actor-1");
        assertNotNull(data.get("data"));
        @SuppressWarnings("unchecked")
        Map<String, Object> inner = (Map<String, Object>) data.get("data");
        assertEquals("SEARCH_FUSED", inner.get("queryType"));
        assertTrue(inner.containsKey("rankedHits"));
    }

    @Test
    void executeQuery_helpdeskAssist_hasRecommendations() {
        ArrayNode gHits = mapper.createArrayNode();
        gHits.addObject().put("title", "KB");
        Mockito.when(guidance.search("login", "all", 0, 6)).thenReturn(gHits);
        Mockito.when(guidance.getEducation("all", 0, 4)).thenReturn(mapper.createArrayNode());

        Map<String, Object> body = Map.of(
                "queryType", "HELP_DESK_ASSIST",
                "input", Map.of("text", "login failure"),
                "context", Map.of());
        Map<String, Object> data = service.executeQuery(body, "t1", "r1", "c1", null);
        @SuppressWarnings("unchecked")
        Map<String, Object> inner = (Map<String, Object>) data.get("data");
        assertNotNull(inner.get("recommendations"));
    }

    @Test
    void executeQuery_dataQualityHints_whenRequireTshepoOff_stillReturnsHints() {
        Map<String, Object> body = Map.of(
                "queryType", "DATA_QUALITY_HINTS",
                "input", Map.of("text", "possible duplicate"),
                "context", Map.of("subjectType", "CLIENT"));
        Map<String, Object> data = service.executeQuery(body, "t1", "r1", "c1", null);
        @SuppressWarnings("unchecked")
        Map<String, Object> inner = (Map<String, Object>) data.get("data");
        JsonNode structured = mapper.valueToTree(inner.get("structuredOutput"));
        assertTrue(structured.isArray());
        assertTrue(structured.size() >= 1);
    }

    @Test
    void summarizeQueues_requiresFacility() {
        Map<String, Object> body = Map.of(
                "queryType", "SUMMARY_QUEUE",
                "context", Map.of(),
                "input", Map.of());
        Map<String, Object> data = service.executeQuery(body, "t1", "r1", "c1", null);
        @SuppressWarnings("unchecked")
        Map<String, Object> inner = (Map<String, Object>) data.get("data");
        String summary = String.valueOf(inner.get("summary"));
        assertTrue(summary.contains("facilityId"));
    }

    @Test
    void summarizeQueues_withFacility_callsPct() {
        UUID fid = UUID.randomUUID();
        ArrayNode arr = mapper.createArrayNode();
        arr.addObject().put("id", "q1");
        Mockito.when(pct.listQueues(fid, null)).thenReturn(arr);

        Map<String, Object> body = Map.of(
                "queryType", "SUMMARY_QUEUE",
                "context", Map.of("facilityId", fid.toString()),
                "input", Map.of());
        Map<String, Object> data = service.executeQuery(body, "t1", "r1", "c1", null);
        @SuppressWarnings("unchecked")
        Map<String, Object> inner = (Map<String, Object>) data.get("data");
        assertTrue(String.valueOf(inner.get("summary")).contains(fid.toString()));
    }

    @Test
    void buildAssistantNotifications_emptyWorkMode_returnsEmpty() {
        List<Map<String, Object>> list = service.buildAssistantNotifications(null, "t1", null, null);
        assertTrue(list.isEmpty());
    }

    @Test
    void fuseSearch_aggregateOnly_suppressesPatientIndexHits() {
        ObjectNode idx = mapper.createObjectNode();
        ArrayNode results = idx.putArray("results");
        results.addObject().put("entityType", "PATIENT").put("entityId", "p1");
        Mockito.when(search.search(Mockito.eq("x"), Mockito.isNull(), Mockito.eq(0), Mockito.eq(12), Mockito.any()))
                .thenReturn(idx);
        ArrayNode gHits = mapper.createArrayNode();
        gHits.addObject().put("title", "Article");
        Mockito.when(guidance.search("x", "all", 0, 8)).thenReturn(gHits);

        Map<String, Object> ev = Map.of("aggregateOnly", true, "tier", "AGGREGATE");
        Map<String, Object> body = Map.of(
                "queryType", "SEARCH_FUSED",
                "input", Map.of("text", "x"),
                "context", Map.of("effectiveVisibility", ev, "domain", "all"));
        Map<String, Object> data = service.executeQuery(body, "t1", "r1", "c1", null);
        @SuppressWarnings("unchecked")
        List<?> hits = (List<?>) ((Map<?, ?>) data.get("data")).get("rankedHits");
        assertNotNull(hits);
        boolean hasPatient = hits.stream().anyMatch(h -> {
            if (!(h instanceof Map<?, ?> m)) {
                return false;
            }
            Object hit = m.get("hit");
            if (!(hit instanceof Map<?, ?> hm)) {
                return false;
            }
            return "PATIENT".equalsIgnoreCase(String.valueOf(hm.get("entityType")));
        });
        assertFalse(hasPatient);
    }

    @Test
    void helpdeskAssist_attachesDagsSummaryWhenRequested() {
        ArrayNode gHits = mapper.createArrayNode();
        gHits.addObject().put("title", "KB");
        Mockito.when(guidance.search("issue", "all", 0, 6)).thenReturn(gHits);
        Mockito.when(guidance.getEducation("all", 0, 4)).thenReturn(mapper.createArrayNode());
        ArrayNode pending = mapper.createArrayNode();
        pending.addObject().put("id", "1");
        pending.addObject().put("id", "2");
        Mockito.when(dags.listAccessRequests("PENDING", 0, 30)).thenReturn(pending);

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("text", "issue");
        input.put("attachDagsGovernanceSummary", true);
        Map<String, Object> body = Map.of("queryType", "HELP_DESK_ASSIST", "input", input, "context", Map.of());
        Map<String, Object> data = service.executeQuery(body, "t1", "r1", "c1", null);
        @SuppressWarnings("unchecked")
        Map<String, Object> inner = (Map<String, Object>) data.get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> gov = (Map<String, Object>) inner.get("governanceSummary");
        assertNotNull(gov);
        assertEquals(2, ((Number) gov.get("pendingAccessRequestRows")).intValue());
    }
}
