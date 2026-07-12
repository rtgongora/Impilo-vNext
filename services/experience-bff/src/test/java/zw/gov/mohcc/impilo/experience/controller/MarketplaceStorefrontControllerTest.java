package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import zw.gov.mohcc.impilo.experience.client.KhulumaServiceClient;
import zw.gov.mohcc.impilo.experience.client.MsikaFlowServiceClient;
import zw.gov.mohcc.impilo.experience.client.MsikaServiceClient;
import zw.gov.mohcc.impilo.experience.client.RitoServiceClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketplaceStorefrontControllerTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Mock private MsikaServiceClient msika;
    @Mock private MsikaFlowServiceClient msikaFlow;
    @Mock private RitoServiceClient rito;
    @Mock private KhulumaServiceClient khuluma;

    private MarketplaceStorefrontController controller() {
        return new MarketplaceStorefrontController(msika, msikaFlow, rito, khuluma);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> dataOf(ResponseEntity<Map<String, Object>> resp) {
        return (Map<String, Object>) resp.getBody().get("data");
    }

    @Test
    void home_composesFeaturedFavouritesActivity() {
        when(msika.searchListings(any())).thenReturn(ResponseEntity.ok("{\"featured\":[]}"));
        when(msika.myFavourites()).thenReturn(ResponseEntity.ok("[]"));
        when(msikaFlow.listOrders(0, 10, null)).thenReturn(ResponseEntity.ok("{\"orders\":[]}"));

        ResponseEntity<Map<String, Object>> resp = controller().home("t1", "req-1", "corr-1");

        assertEquals(200, resp.getStatusCode().value());
        Map<String, Object> data = dataOf(resp);
        assertNotNull(data.get("featured"));
        assertNotNull(data.get("favourites"));
        assertNotNull(data.get("activity"));
        assertEquals("corr-1", ((Map<?, ?>) resp.getBody().get("meta")).get("correlation_id"));
    }

    @Test
    void home_safePartial_whenActivityDown() {
        when(msika.searchListings(any())).thenReturn(ResponseEntity.ok("[]"));
        when(msika.myFavourites()).thenReturn(ResponseEntity.ok("[]"));
        when(msikaFlow.listOrders(0, 10, null)).thenThrow(new RuntimeException("msika-flow down"));

        ResponseEntity<Map<String, Object>> resp = controller().home("t1", "req-1", "corr-1");

        assertEquals(200, resp.getStatusCode().value());
        Object activity = dataOf(resp).get("activity");
        assertTrue(activity instanceof Map);
        assertEquals("UNAVAILABLE", ((Map<?, ?>) activity).get("status"));
    }

    @Test
    void search_forwardsQueryParams() {
        when(msika.searchListings(any())).thenReturn(ResponseEntity.ok("{\"content\":[]}"));
        ResponseEntity<Map<String, Object>> resp = controller().search("t1", "req", "corr", "panadol", "LOW_RISK", null, 0, 20);
        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(dataOf(resp).get("listings"));
        verify(msika).searchListings(any());
    }

    @Test
    void listingDetail_returnsListing() {
        when(msika.getListing("L1")).thenReturn(ResponseEntity.ok("{\"status\":\"PUBLISHED\"}"));
        ResponseEntity<Map<String, Object>> resp = controller().listing("t1", "req", "corr", "L1");
        assertNotNull(dataOf(resp).get("listing"));
    }

    @Test
    void activityDetail_composesOrderAndTracking() {
        when(msikaFlow.getOrder("O1")).thenReturn(ResponseEntity.ok("{\"id\":\"O1\"}"));
        when(msikaFlow.getTracking("O1")).thenReturn(ResponseEntity.ok("{\"steps\":[]}"));
        ResponseEntity<Map<String, Object>> resp = controller().activityDetail("req", "corr", "O1");
        Map<String, Object> data = dataOf(resp);
        assertNotNull(data.get("order"));
        assertNotNull(data.get("tracking"));
    }

    @Test
    void feedback_routesToRitoWithMarketplaceSource() {
        ObjectNode caseNode = mapper.createObjectNode();
        caseNode.put("caseId", "C1");
        when(rito.createCase(any())).thenReturn(caseNode);
        ResponseEntity<Map<String, Object>> resp = controller().reportToRito(
                "t1", "req", "corr", "CPID-1", Map.of("title", "bad listing", "description", "x"));
        assertNotNull(dataOf(resp).get("case"));
        verify(rito).createCase(argThat(b ->
                "MSIKA_MARKETPLACE".equals(b.get("source")) && "CPID-1".equals(b.get("reporterActorId"))));
    }

    @Test
    void notify_requestsKhulumaDispatch() {
        when(khuluma.dispatch(eq("t1"), any())).thenReturn(ResponseEntity.ok("{\"data\":[]}"));
        ResponseEntity<Map<String, Object>> resp = controller().requestKhulumaUpdate(
                "t1", "req", "corr", Map.of("channels", java.util.List.of("SMS"), "messageRef", "M1", "recipient", "CPID-1"));
        assertNotNull(dataOf(resp).get("dispatch"));
        verify(khuluma).dispatch(eq("t1"), any());
    }
}
