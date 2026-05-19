package zw.gov.mohcc.impilo.community.social.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SocialJson is package-private; we reflectively invoke its static helpers
 * to verify defensive defaults and round-trip behaviour without changing the
 * visibility of an internal helper.
 */
class SocialJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    private <T> T invoke(String name, Class<?>[] sig, Object... args) {
        try {
            Method m = SocialJson.class.getDeclaredMethod(name, sig);
            m.setAccessible(true);
            return (T) m.invoke(null, args);
        } catch (Exception e) {
            throw new AssertionError("invocation failed: " + name, e);
        }
    }

    @Test
    void writeJson_emptyForNullInput() {
        String json = invoke("writeJson", new Class[]{ObjectMapper.class, Object.class}, mapper, null);
        assertEquals("{}", json);
    }

    @Test
    void readStringList_emptyForBlankInput() {
        List<String> out = invoke("readStringList", new Class[]{ObjectMapper.class, String.class}, mapper, "");
        assertNotNull(out);
        assertTrue(out.isEmpty());
    }

    @Test
    void readStringList_recoversList() {
        List<String> out = invoke("readStringList",
                new Class[]{ObjectMapper.class, String.class},
                mapper, "[\"maternal\",\"diabetes\"]");
        assertEquals(2, out.size());
        assertTrue(out.contains("diabetes"));
    }

    @Test
    void readMap_emptyForBlankInput() {
        Map<String, Object> out = invoke("readMap", new Class[]{ObjectMapper.class, String.class}, mapper, "");
        assertNotNull(out);
        assertTrue(out.isEmpty());
    }

    @Test
    void readMap_recoversMap() {
        Map<String, Object> out = invoke("readMap",
                new Class[]{ObjectMapper.class, String.class},
                mapper, "{\"flagged\":true,\"category\":\"safety\"}");
        assertEquals(true, out.get("flagged"));
        assertEquals("safety", out.get("category"));
    }

    @Test
    void malformedInputs_returnDefensiveDefaults() {
        List<String> badList = invoke("readStringList", new Class[]{ObjectMapper.class, String.class}, mapper, "not-json");
        Map<String, Object> badMap = invoke("readMap", new Class[]{ObjectMapper.class, String.class}, mapper, "not-json");
        assertTrue(badList.isEmpty());
        assertTrue(badMap.isEmpty());
    }
}
