package zw.gov.mohcc.impilo.zibo.api.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.zibo.core.MappingService;
import zw.gov.mohcc.impilo.zibo.core.ValidationEngine;

/**
 * The routes two services have been calling for months, and which ZIBO has never served.
 *
 * <p>Measured 2026-08-08. Both callers degrade silently, so neither failure was visible:</p>
 *
 * <table>
 *   <tr><th>Caller</th><th>Calls</th><th>ZIBO serves</th></tr>
 *   <tr>
 *     <td>{@code oros-service} {@code ZiboIntegration:133}</td>
 *     <td>{@code GET /v1/mappings/translate?sourceSystem&sourceCode&targetSystem}</td>
 *     <td>{@code POST /v1/map} with a JSON body — wrong path, wrong verb, wrong parameter style</td>
 *   </tr>
 *   <tr>
 *     <td>{@code butano-service} {@code TerminologyValidationInterceptor:81}</td>
 *     <td>{@code POST /api/v1/terminology/validate}</td>
 *     <td>nothing at all — no controller has ever mapped that path</td>
 *   </tr>
 * </table>
 *
 * <p>Aliasing here rather than changing the clients is deliberate: it fixes both without a
 * coordinated multi-service release, and the alternative — editing two services to match ZIBO —
 * would break them again the moment either is deployed at a different version.</p>
 *
 * <h3>The response shape is the part that matters</h3>
 *
 * <p>Both clients parse a <b>flat</b> body. oros reads {@code body.get("targetCode")} directly;
 * butano deserialises {@code {valid, display, message}}. ZIBO's house style is
 * {@code ApiResponse<T>} with the payload under {@code data}. Returning the envelope here would
 * answer 200 and still hand both callers a null — a route that looks fixed and is not. These
 * methods therefore return the flat shape each client already expects, and delegate to exactly the
 * same engine as the canonical routes, so there is one implementation and two spellings.</p>
 *
 * <p>⚠️ <b>Neither caller currently authenticates.</b> oros builds trust headers with no bearer
 * token and butano uses a bare {@code new RestTemplate()} with only tenant and correlation headers.
 * ZIBO authenticates every route, so both will now receive 401 rather than 404 — a different
 * failure, not a fixed one. The route contract is correct here; the callers still need service
 * credentials, which is a separate change in those services and must not be "fixed" by weakening
 * ZIBO's security posture.</p>
 */
@RestController
public class TerminologyCompatibilityController {

    private static final Logger log = LoggerFactory.getLogger(TerminologyCompatibilityController.class);

    private final MappingService mappingService;
    private final ValidationEngine validationEngine;

    public TerminologyCompatibilityController(MappingService mappingService,
                                              ValidationEngine validationEngine) {
        this.mappingService = mappingService;
        this.validationEngine = validationEngine;
    }

    /**
     * oros's ConceptMap translation, in the spelling oros has always used.
     *
     * <p>Flat body with {@code targetCode} at the top level — {@code ZiboIntegration.lookupMapping}
     * reads {@code response.getBody().get("targetCode")}. A miss returns 200 with a null
     * {@code targetCode}, matching {@code POST /v1/map}: "no mapping exists" is an answer, not an
     * error, and oros already treats a null as {@code Optional.empty()}.</p>
     */
    @GetMapping("/v1/mappings/translate")
    public ResponseEntity<Map<String, Object>> translate(
            @RequestParam String sourceSystem,
            @RequestParam String sourceCode,
            @RequestParam String targetSystem) {

        MappingService.MappingResult result =
                mappingService.mapCode(sourceSystem, sourceCode, targetSystem);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sourceSystem", sourceSystem);
        body.put("sourceCode", sourceCode);
        body.put("targetSystem", result.targetSystem());
        body.put("targetCode", result.targetCode());
        body.put("targetDisplay", result.targetDisplay());
        body.put("equivalence", result.equivalence());
        body.put("confidence", result.confidence());
        return ResponseEntity.ok(body);
    }

    /**
     * butano's terminology check, in the spelling butano has always used.
     *
     * <p>Flat {@code {valid, display, message}} — {@code TerminologyValidationInterceptor}
     * deserialises exactly that record and reads {@code response.valid()}.</p>
     *
     * <p>Mode is deliberately not overridden: the effective policy mode resolves from the pack
     * assignment for the caller's scope, so a facility held to STRICT stays STRICT and everyone
     * else stays LENIENT. Butano has its own {@code TerminologyMode} on top of this; that is its
     * decision about what to do with the answer, not a decision about what the answer is.</p>
     */
    @PostMapping("/api/v1/terminology/validate")
    public ResponseEntity<Map<String, Object>> validateTerminology(
            @RequestBody ValidateCodingBody request) {

        ValidationEngine.ValidationResult result = validationEngine.validateCoding(
                request.system(), request.code(), null, null, null);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("valid", result.valid());
        body.put("display", null);
        body.put("message", result.issues().isEmpty()
                ? null
                : result.issues().get(0).message());
        return ResponseEntity.ok(body);
    }

    /** Mirrors butano's {@code ZiboValidationRequest} record. */
    public record ValidateCodingBody(String system, String code) {}

    /** Unused today; present so a future caller can pass a facility scope without a new route. */
    @SuppressWarnings("unused")
    private static UUID parseFacility(String raw) {
        try {
            return raw == null || raw.isBlank() ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            log.debug("ZIBO: ignoring unparseable facility id on a compatibility route");
            return null;
        }
    }
}
