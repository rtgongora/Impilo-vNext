package zw.gov.mohcc.impilo.experience.trust;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrustBreakGlassResourceMapperTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void toRequestResource_mapsUpstreamFields() {
        ObjectNode upstream = mapper.createObjectNode();
        upstream.put("id", "bg-1");
        upstream.put("actorId", "provider-1");
        upstream.put("reason", "Unresponsive patient");
        upstream.put("resourceType", "PATIENT_RECORD");
        upstream.put("resourceId", "cpid-1");
        upstream.put("reviewStatus", "PENDING_REVIEW");

        Map<String, Object> resource = TrustBreakGlassResourceMapper.toRequestResource(upstream);

        assertEquals("bg-1", resource.get("id"));
        assertEquals("break_glass_request", resource.get("type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> attrs = (Map<String, Object>) resource.get("attributes");
        assertEquals("provider-1", attrs.get("actorId"));
        assertEquals("Unresponsive patient", attrs.get("reason"));
        assertEquals("PENDING_REVIEW", attrs.get("reviewStatus"));
    }

    @Test
    void toReviewResources_handlesArrayAndSingleNode() {
        ObjectNode single = mapper.createObjectNode();
        single.put("id", "bg-2");
        single.put("actor_id", "provider-2");
        single.put("reason", "Emergency imaging");
        single.put("approved", true);
        single.put("reviewed_by", "supervisor-1");

        List<Map<String, Object>> one = TrustBreakGlassResourceMapper.toReviewResources(single);
        assertEquals(1, one.size());
        assertEquals("break_glass_review", one.get(0).get("type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> attrs = (Map<String, Object>) one.get(0).get("attributes");
        assertTrue((Boolean) attrs.get("approved"));
        assertEquals("supervisor-1", attrs.get("reviewedBy"));

        ArrayNode array = mapper.createArrayNode();
        array.add(single);
        List<Map<String, Object>> many = TrustBreakGlassResourceMapper.toReviewResources(array);
        assertEquals(1, many.size());
    }

    @Test
    void toReviewResources_returnsEmptyForNull() {
        assertTrue(TrustBreakGlassResourceMapper.toReviewResources(null).isEmpty());
    }
}
