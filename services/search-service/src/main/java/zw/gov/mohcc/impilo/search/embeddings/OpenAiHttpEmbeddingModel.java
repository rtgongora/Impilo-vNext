package zw.gov.mohcc.impilo.search.embeddings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

/**
 * Calls an OpenAI-compatible embeddings endpoint (e.g. OpenAI, Azure OpenAI, vLLM with OpenAI shim).
 */
public final class OpenAiHttpEmbeddingModel implements EmbeddingModel {

    private static final Logger log = LoggerFactory.getLogger(OpenAiHttpEmbeddingModel.class);

    private final RestClient client;
    private final String path;
    private final String model;
    private final ObjectMapper objectMapper;

    public OpenAiHttpEmbeddingModel(RestClient client,
                                    String path,
                                    String model,
                                    ObjectMapper objectMapper) {
        this.client = client;
        this.path = path.startsWith("/") ? path : "/" + path;
        this.model = model;
        this.objectMapper = objectMapper;
    }

    @Override
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("input", text);
            String raw = client.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            if (raw == null) {
                return null;
            }
            JsonNode root = objectMapper.readTree(raw);
            JsonNode data = root.path("data");
            if (!data.isArray() || data.isEmpty()) {
                log.warn("Embeddings HTTP response missing data[]");
                return null;
            }
            JsonNode emb = data.get(0).path("embedding");
            if (!emb.isArray() || emb.isEmpty()) {
                return null;
            }
            float[] v = new float[emb.size()];
            for (int i = 0; i < emb.size(); i++) {
                v[i] = (float) emb.get(i).asDouble();
            }
            return EmbeddingVectors.l2Normalize(v);
        } catch (Exception e) {
            log.warn("Embeddings HTTP call failed: {}", e.getMessage());
            return null;
        }
    }
}
