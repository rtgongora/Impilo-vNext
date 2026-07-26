package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.CoverageServiceClient;
import zw.gov.mohcc.impilo.experience.client.MsikaServiceClient;
import zw.gov.mohcc.impilo.experience.client.SimbaServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;
import zw.gov.mohcc.impilo.experience.service.DiscoveryOrchestrationService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicGatewayDiscoveryBffControllerTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void search_okReturns200() {
        var controller = new PublicGatewayDiscoveryBffController(new StubDiscovery(false));
        var resp = controller.search("clinic", "all", null, null, 0, 20, new MockHttpServletRequest());
        assertEquals(200, resp.getStatusCode().value());
    }

    @Test
    void search_downstreamFailureMapsTo502() {
        var controller = new PublicGatewayDiscoveryBffController(new StubDiscovery(true));
        var resp = controller.search("clinic", "all", null, null, 0, 20, new MockHttpServletRequest());
        assertEquals(502, resp.getStatusCode().value());
    }

    @Test
    void marketplaceLane_normalisesRealListings() {
        ArrayNode arr = mapper.createArrayNode();
        ObjectNode l = mapper.createObjectNode();
        l.put("id", "L1");
        l.put("title", "Paracetamol 500mg");
        l.put("summary", "Pain relief");
        l.put("priceDisplay", "$2.00");
        l.put("riskClassification", "OTC");
        l.put("fulfilmentMode", "COLLECT_IN_STORE");
        arr.add(l);

        var svc = new DiscoveryOrchestrationService(null, new StubMsika(arr), null, null);
        var out = svc.search("paracetamol", "marketplace", null, null, 0, 20, "127.0.0.1");

        assertEquals(1, out.results().size());
        var r = out.results().get(0);
        assertEquals("marketplace", r.category());
        assertEquals("Health product", r.type());
        assertEquals("Paracetamol 500mg", r.title());
        assertTrue(r.meta().contains("$2.00"));
        assertEquals("/welcome/marketplace", r.href());
        assertEquals(Integer.valueOf(1), out.categoryCounts().get("marketplace"));
    }

    @Test
    void coverageLane_normalisesRealPlans() {
        ArrayNode arr = mapper.createArrayNode();
        ObjectNode p = mapper.createObjectNode();
        p.put("planCode", "PC1");
        p.put("planName", "National Cover");
        p.put("planType", "MEDICAL_AID");
        p.put("status", "ACTIVE");
        arr.add(p);

        var svc = new DiscoveryOrchestrationService(null, null, new StubCoverage(arr), null);
        var out = svc.search(null, "coverage", null, null, 0, 20, "127.0.0.1");

        assertEquals(1, out.results().size());
        assertEquals("coverage", out.results().get(0).category());
        assertEquals("National Cover", out.results().get(0).title());
    }

    @Test
    void wellnessLane_normalisesRealProgrammes() {
        ArrayNode arr = mapper.createArrayNode();
        ObjectNode w = mapper.createObjectNode();
        w.put("programmeCode", "SCR1");
        w.put("title", "Cervical screening");
        w.put("description", "Regular check");
        w.put("ageMin", 25);
        w.put("ageMax", 49);
        w.put("frequencyMonths", 36);
        arr.add(w);

        var svc = new DiscoveryOrchestrationService(null, null, null, new StubSimba(arr));
        var out = svc.search(null, "wellness", null, null, 0, 20, "127.0.0.1");

        assertEquals(1, out.results().size());
        var r = out.results().get(0);
        assertEquals("wellness", r.category());
        assertTrue(r.meta().stream().anyMatch(m -> m.contains("25")));
        assertTrue(r.meta().stream().anyMatch(m -> m.contains("36")));
    }

    @Test
    void laneFailure_isSkippedWithHonestNote_notThrown() {
        var svc = new DiscoveryOrchestrationService(null, new ExplodingMsika(), null, null);
        var out = svc.search("x", "marketplace", null, null, 0, 20, "127.0.0.1");
        assertTrue(out.results().isEmpty());
        assertFalse(out.notes().isEmpty());
    }

    // ---- stubs (extend the real components, override the one method under test) ----

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static final class StubDiscovery extends DiscoveryOrchestrationService {
        private final boolean explode;

        StubDiscovery(boolean explode) {
            super(null, null, null, null);
            this.explode = explode;
        }

        @Override
        public DiscoveryResponse search(String q, String category, String lat, String lng,
                                        int page, int size, String clientIp) {
            if (explode) {
                throw new RuntimeException("downstream unavailable");
            }
            return new DiscoveryResponse(q, category, java.util.List.of(), java.util.Map.of(), java.util.List.of());
        }
    }

    private static class StubMsika extends MsikaServiceClient {
        private final JsonNode listings;

        StubMsika(JsonNode listings) {
            super(new RestTemplate(), endpoints());
            this.listings = listings;
        }

        @Override
        public JsonNode publicListings(MultiValueMap<String, String> queryParams) {
            return listings;
        }
    }

    private static final class ExplodingMsika extends MsikaServiceClient {
        ExplodingMsika() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode publicListings(MultiValueMap<String, String> queryParams) {
            throw new RuntimeException("boom");
        }
    }

    private static final class StubCoverage extends CoverageServiceClient {
        private final JsonNode plans;

        StubCoverage(JsonNode plans) {
            super(new RestTemplate(), endpoints());
            this.plans = plans;
        }

        @Override
        public JsonNode publicCoveragePlans() {
            return plans;
        }
    }

    private static final class StubSimba extends SimbaServiceClient {
        private final JsonNode programmes;

        StubSimba(JsonNode programmes) {
            super(new RestTemplate(), endpoints(), new ObjectMapper());
            this.programmes = programmes;
        }

        @Override
        public JsonNode publicScreeningProgrammes() {
            return programmes;
        }
    }
}
