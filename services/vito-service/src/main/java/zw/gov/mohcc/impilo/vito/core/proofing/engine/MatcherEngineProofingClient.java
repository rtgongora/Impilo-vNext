package zw.gov.mohcc.impilo.vito.core.proofing.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A5: the vito-side client for the core-infra matcher-engine, used for stateless
 * proofing comparison of two transient images (selfie vs document portrait) — as
 * opposed to ABIS's enrolled-subject verify. Face extraction is ONNX-model-gated in
 * the engine, so {@link #extractFaceTemplate} returns empty when no model is loaded,
 * and the facial-comparison engine then fails closed (never a fabricated match).
 */
public interface MatcherEngineProofingClient {

    /** Extract a FACE template from raw image bytes. Empty = engine can't (no model / bad image). */
    Optional<String> extractFaceTemplate(byte[] imageBytes);

    /** 1:1 compare two FACE templates. */
    FaceVerdict verifyFace(String probeTemplateB64, String enrolledTemplateB64);

    /** result ∈ {MATCH, NO_MATCH, UNAVAILABLE}; confidence 0..1. */
    record FaceVerdict(String result, double confidence) {
        static FaceVerdict unavailable() { return new FaceVerdict("UNAVAILABLE", 0.0); }
    }

    @Component
    class RestClientMatcherEngineProofingClient implements MatcherEngineProofingClient {

        private static final Logger log =
                LoggerFactory.getLogger(RestClientMatcherEngineProofingClient.class);

        private final RestClient restClient;
        private final String engineBaseUrl;

        public RestClientMatcherEngineProofingClient(
                @Value("${MATCHER_ENGINE_URL:${vito.proofing.matcher-engine.base-url:http://localhost:9200}}")
                String engineBaseUrl) {
            this.engineBaseUrl = trimSlash(engineBaseUrl);
            this.restClient = RestClient.create();
        }

        @Override
        public Optional<String> extractFaceTemplate(byte[] imageBytes) {
            if (imageBytes == null || imageBytes.length == 0) {
                return Optional.empty();
            }
            try {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("modality", "FACE");
                body.put("sampleBase64", Base64.getEncoder().encodeToString(imageBytes));
                body.put("width", 0);
                body.put("height", 0);
                body.put("dpi", 0);
                @SuppressWarnings("unchecked")
                Map<String, Object> resp = restClient.post()
                        .uri(engineBaseUrl + "/v1/engine/extract")
                        .body(body)
                        .retrieve()
                        .body(Map.class);
                if (resp == null || !Boolean.TRUE.equals(resp.get("ok"))) {
                    return Optional.empty();
                }
                Object tpl = resp.get("templateBase64");
                return tpl == null ? Optional.empty() : Optional.of(String.valueOf(tpl));
            } catch (RuntimeException e) {
                log.warn("Matcher-engine face extract failed (fail-closed): {}", e.getMessage());
                return Optional.empty();
            }
        }

        @Override
        public FaceVerdict verifyFace(String probeTemplateB64, String enrolledTemplateB64) {
            try {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("modality", "FACE");
                body.put("probeTemplateBase64", probeTemplateB64);
                body.put("enrolledTemplateBase64", enrolledTemplateB64);
                @SuppressWarnings("unchecked")
                Map<String, Object> resp = restClient.post()
                        .uri(engineBaseUrl + "/v1/engine/verify")
                        .body(body)
                        .retrieve()
                        .body(Map.class);
                if (resp == null) {
                    return FaceVerdict.unavailable();
                }
                String result = String.valueOf(resp.getOrDefault("result", "UNAVAILABLE"));
                double confidence = resp.get("confidence") instanceof Number n ? n.doubleValue() : 0.0;
                return new FaceVerdict(result, confidence);
            } catch (RuntimeException e) {
                log.warn("Matcher-engine face verify failed (fail-closed): {}", e.getMessage());
                return FaceVerdict.unavailable();
            }
        }

        private static String trimSlash(String s) {
            return (s != null && s.endsWith("/")) ? s.substring(0, s.length() - 1) : s;
        }
    }
}
