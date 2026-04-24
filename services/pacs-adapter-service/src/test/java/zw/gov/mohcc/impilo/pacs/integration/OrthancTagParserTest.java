package zw.gov.mohcc.impilo.pacs.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OrthancTagParserTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void readsStringValueFromTextualValueNode() throws Exception {
        JsonNode tags = mapper.readTree("{\"00080018\":{\"Name\":\"SOPInstanceUID\",\"Type\":\"String\",\"Value\":\"1.2.3.4.5\"}}");
        assertEquals("1.2.3.4.5", OrthancTagParser.stringTag(tags, "00080018"));
    }

    @Test
    void readsStringValueFromArrayValueNode() throws Exception {
        JsonNode tags = mapper.readTree("{\"00200013\":{\"Name\":\"InstanceNumber\",\"Type\":\"String\",\"Value\":[\"14\"]}}");
        assertEquals(14, OrthancTagParser.intTag(tags, "00200013"));
    }

    @Test
    void returnsNullForMissingTag() throws Exception {
        JsonNode tags = mapper.readTree("{}");
        assertNull(OrthancTagParser.stringTag(tags, "00080018"));
    }
}
