package zw.gov.mohcc.impilo.abis.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import zw.gov.mohcc.impilo.abis.core.TemplateCrypto;
import zw.gov.mohcc.impilo.abis.persistence.entity.TemplateEntity;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * ABIS → matching-engine adapter. This is the governance boundary that turns
 * fail-closed custody into REAL matching: ABIS owns encrypted templates, consent
 * and audit; this bean decrypts the enrolled template(s) and delegates the actual
 * comparison to the core-infra {@code matcher-engine} over HTTP — the exact
 * pacs-adapter→Orthanc / rtc-gateway→LiveKit pattern.
 *
 * <p>Registered {@code @Primary}, so it displaces {@link FailClosedMatchingEngine}
 * (which is {@code @ConditionalOnMissingBean}). It stays care-first: ANY transport
 * or protocol error, or the engine reporting UNAVAILABLE, yields a fail-closed
 * decision — a biometric outage never fabricates a match and never blocks care by
 * asserting one.</p>
 *
 * <p>Disable (revert to the local fail-closed default) with
 * {@code abis.matcher.enabled=false}.</p>
 */
@Component
@Primary
@ConditionalOnProperty(name = "abis.matcher.enabled", havingValue = "true", matchIfMissing = true)
public class BmeClientMatchingEngine implements BiometricMatchingEngine {

    private static final Logger log = LoggerFactory.getLogger(BmeClientMatchingEngine.class);

    private final RestClient engine;
    private final TemplateCrypto templateCrypto;

    public BmeClientMatchingEngine(RestClient.Builder builder,
                                   @Value("${abis.matcher.base-url:http://localhost:9200}") String baseUrl,
                                   TemplateCrypto templateCrypto) {
        this.engine = builder.baseUrl(baseUrl).build();
        this.templateCrypto = templateCrypto;
        log.info("ABIS matching delegated to engine at {}", baseUrl);
    }

    @Override
    public ExtractionResult extractTemplate(zw.gov.mohcc.impilo.abis.core.BiometricModality modality,
                                            byte[] sampleImage, int width, int height, int dpi) {
        if (sampleImage == null || sampleImage.length == 0) {
            return ExtractionResult.unavailable("no_sample");
        }
        try {
            Map<String, Object> req = Map.of(
                    "modality", modality.name(),
                    "sampleBase64", b64(sampleImage),
                    "width", width, "height", height, "dpi", dpi);
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = engine.post().uri("/v1/engine/extract")
                    .body(req).retrieve().body(Map.class);
            if (resp == null || !Boolean.TRUE.equals(resp.get("ok"))) {
                return ExtractionResult.unavailable(resp == null ? "no_body"
                        : String.valueOf(resp.getOrDefault("detail", "extract_failed")));
            }
            byte[] template = Base64.getDecoder().decode(String.valueOf(resp.get("templateBase64")));
            return new ExtractionResult(true, template, num(resp.get("quality")), "matcher-engine");
        } catch (RuntimeException e) {
            log.warn("matcher-engine extract failed (fail-closed): {}", e.getMessage());
            return ExtractionResult.unavailable("engine_unreachable");
        }
    }

    @Override
    public VerificationDecision verify(byte[] probeTemplate, TemplateEntity enrolled, BiometricProbeContext ctx) {
        if (probeTemplate == null || probeTemplate.length == 0 || enrolled == null) {
            return new VerificationDecision("NO_REFERENCE", 0.0, "Missing probe or enrolled template");
        }
        try {
            byte[] enrolledPlain = templateCrypto.decrypt(enrolled.getTemplateEncrypted());
            Map<String, Object> req = Map.of(
                    "modality", enrolled.getModality().name(),
                    "probeTemplateBase64", b64(probeTemplate),
                    "enrolledTemplateBase64", b64(enrolledPlain));
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = engine.post().uri("/v1/engine/verify")
                    .body(req).retrieve().body(Map.class);
            if (resp == null) {
                return unavailable("engine returned no body");
            }
            String result = String.valueOf(resp.getOrDefault("result", "UNAVAILABLE"));
            double confidence = num(resp.get("confidence"));
            return new VerificationDecision(result, confidence,
                    "matcher-engine score=" + resp.get("score"));
        } catch (RuntimeException e) {
            log.warn("matcher-engine verify failed (fail-closed): {}", e.getMessage());
            return unavailable("engine_unreachable");
        }
    }

    @Override
    public List<IdentificationCandidate> identify(byte[] probeTemplate, List<TemplateEntity> candidates,
                                                  BiometricProbeContext ctx) {
        if (probeTemplate == null || probeTemplate.length == 0 || candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        try {
            List<Map<String, String>> gallery = new ArrayList<>();
            for (TemplateEntity c : candidates) {
                gallery.add(Map.of(
                        "subjectRef", c.getSubjectRef(),
                        "templateBase64", b64(templateCrypto.decrypt(c.getTemplateEncrypted()))));
            }
            Map<String, Object> req = Map.of(
                    "modality", candidates.get(0).getModality().name(),
                    "probeTemplateBase64", b64(probeTemplate),
                    "gallery", gallery,
                    "maxCandidates", 10);
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = engine.post().uri("/v1/engine/identify")
                    .body(req).retrieve().body(Map.class);
            if (resp == null || !(resp.get("candidates") instanceof List<?> raw)) {
                return List.of();
            }
            List<IdentificationCandidate> out = new ArrayList<>();
            for (Object o : raw) {
                if (o instanceof Map<?, ?> m) {
                    out.add(new IdentificationCandidate(
                            String.valueOf(m.get("subjectRef")), num(m.get("confidence")),
                            "matcher-engine score=" + m.get("score")));
                }
            }
            return out;
        } catch (RuntimeException e) {
            log.warn("matcher-engine identify failed (fail-closed): {}", e.getMessage());
            return List.of();
        }
    }

    private static VerificationDecision unavailable(String detail) {
        return new VerificationDecision("UNAVAILABLE", 0.0, detail);
    }

    private static String b64(byte[] b) {
        return Base64.getEncoder().encodeToString(b);
    }

    private static double num(Object o) {
        return (o instanceof Number n) ? n.doubleValue() : 0.0;
    }
}
