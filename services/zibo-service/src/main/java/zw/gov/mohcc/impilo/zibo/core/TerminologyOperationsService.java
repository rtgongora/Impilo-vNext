package zw.gov.mohcc.impilo.zibo.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.zibo.domain.ArtifactType;
import zw.gov.mohcc.impilo.zibo.persistence.entity.ArtifactEntity;
import zw.gov.mohcc.impilo.zibo.persistence.entity.ConceptEntity;
import zw.gov.mohcc.impilo.zibo.persistence.repository.ConceptRepository;

/**
 * The FHIR terminology operations, served from the concept index.
 *
 * <p>Before this, ZIBO had no {@code $lookup}, no {@code $expand} and no search of any kind, and
 * <b>13 of its 15 ValueSets contained zero concepts</b> — they reference their CodeSystem through
 * {@code compose.include} without enumerating, which is valid FHIR and was entirely unresolvable
 * here. A consumer asking for one got an empty ValueSet and could not distinguish that from a
 * vocabulary with nothing in it.</p>
 *
 * <h3>Why this is not built on HAPI's IValidationSupport</h3>
 *
 * <p>The dependency is present and the plan proposed it. Measured first: every ValueSet in the
 * estate is exactly one {@code include}, with no {@code exclude}, no {@code filter} and no nested
 * {@code valueSet} reference. HAPI's in-memory expansion also materialises the whole CodeSystem,
 * which is the one thing that cannot happen at LOINC's ~109,000 concepts. So expansion is served
 * from the index for the shapes that exist.</p>
 *
 * <p><b>And it refuses the shapes that do not.</b> A {@code filter}, an {@code exclude} or a nested
 * ValueSet reference raises {@link UnsupportedComposeException} rather than quietly returning the
 * subset it does understand. A partial expansion that looks complete is worse than an error: a
 * clinician's picker would silently omit codes, and nothing downstream could tell. When LOINC and
 * ICD-11 arrive with filtered ValueSets, this fails loudly and gets implemented.</p>
 */
@Service
public class TerminologyOperationsService {

    private static final Logger log = LoggerFactory.getLogger(TerminologyOperationsService.class);

    private static final int DEFAULT_COUNT = 20;
    private static final int MAX_COUNT = 1000;

    private final ConceptRepository conceptRepository;
    private final ArtifactResolutionService artifactResolutionService;
    private final MappingService mappingService;
    private final ObjectMapper objectMapper;

    public TerminologyOperationsService(ConceptRepository conceptRepository,
                                        ArtifactResolutionService artifactResolutionService,
                                        MappingService mappingService,
                                        ObjectMapper objectMapper) {
        this.conceptRepository = conceptRepository;
        this.artifactResolutionService = artifactResolutionService;
        this.mappingService = mappingService;
        this.objectMapper = objectMapper;
    }

    /** Raised when a ValueSet asks for something the index cannot honestly expand. */
    public static class UnsupportedComposeException extends RuntimeException {
        public UnsupportedComposeException(String message) {
            super(message);
        }
    }

    public record LookupResult(boolean found, String system, String version, String code,
                               String display, String definition, boolean active,
                               JsonNode designations, JsonNode properties) {}

    public record ExpansionEntry(String system, String version, String code, String display) {}

    public record Expansion(String url, String version, long total, int offset,
                            List<ExpansionEntry> contains) {}

    public record ValidateResult(boolean result, String message, String display) {}

    // ── CodeSystem/$lookup ────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public LookupResult lookup(String system, String code, String requestedVersion) {
        UUID tenantId = tenantId();
        Optional<ArtifactEntity> cs = resolveCodeSystem(system, requestedVersion, tenantId);
        if (cs.isEmpty()) {
            return new LookupResult(false, system, null, code, null, null, false, null, null);
        }
        String version = versionOf(cs.get());

        List<ConceptEntity> hits = conceptRepository.findForLookup(system, version, code, tenantId);
        if (hits.isEmpty()) {
            return new LookupResult(false, system, version, code, null, null, false, null, null);
        }
        // Ordered so a TENANT override precedes the NATIONAL concept.
        ConceptEntity c = hits.get(0);
        return new LookupResult(true, system, version, c.getCode(), c.getDisplay(),
                c.getDefinition(), c.isActive(), parse(c.getDesignations()), parse(c.getProperties()));
    }

    // ── CodeSystem/$validate-code ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ValidateResult validateCode(String system, String code, String display,
                                       String requestedVersion) {
        LookupResult found = lookup(system, code, requestedVersion);
        if (!found.found()) {
            return new ValidateResult(false,
                    "Code '" + code + "' not found in " + system
                            + (found.version() == null ? " (system not registered)" : " v" + found.version()),
                    null);
        }
        // A display mismatch is reported, not treated as invalid: the code is right, the label the
        // caller carries is stale. Refusing the code would reject correct clinical data over a
        // cosmetic difference.
        if (display != null && !display.isBlank() && found.display() != null
                && !display.equalsIgnoreCase(found.display())) {
            return new ValidateResult(true,
                    "Code is valid; display differs from the governed term '" + found.display() + "'",
                    found.display());
        }
        return new ValidateResult(true, null, found.display());
    }

    // ── ValueSet/$expand ──────────────────────────────────────────────────────────────────────

    /**
     * Expands a ValueSet from the concept index.
     *
     * @param filter free-text; matches display, designations, or a code prefix
     */
    @Transactional(readOnly = true)
    public Expansion expand(String url, String requestedVersion, String filter,
                            Integer count, Integer offset, boolean activeOnly) {
        UUID tenantId = tenantId();
        ArtifactEntity vs = artifactResolutionService
                .resolveCurrentOrVersion(tenantId, url, requestedVersion, ArtifactType.VALUE_SET)
                .orElseThrow(() -> new IllegalArgumentException("ValueSet not found: " + url));

        JsonNode root = parse(vs.getContentJson());
        JsonNode compose = root.path("compose");

        if (compose.has("exclude") && compose.path("exclude").size() > 0) {
            throw new UnsupportedComposeException(
                    "ValueSet " + url + " uses compose.exclude, which this expansion does not "
                    + "implement. Refusing rather than returning an expansion that silently "
                    + "includes codes the ValueSet excludes.");
        }

        JsonNode includes = compose.path("include");
        if (!includes.isArray() || includes.isEmpty()) {
            throw new UnsupportedComposeException(
                    "ValueSet " + url + " has no compose.include to expand.");
        }

        int limit = Math.min(count == null ? DEFAULT_COUNT : count, MAX_COUNT);
        int from = offset == null ? 0 : Math.max(0, offset);

        List<ExpansionEntry> out = new ArrayList<>();
        long total = 0;

        for (JsonNode include : includes) {
            if (include.has("filter")) {
                throw new UnsupportedComposeException(
                        "ValueSet " + url + " uses compose.include.filter, which this expansion does "
                        + "not implement. Refusing rather than returning the unfiltered set, which "
                        + "would contain codes the ValueSet does not.");
            }
            if (include.has("valueSet")) {
                throw new UnsupportedComposeException(
                        "ValueSet " + url + " composes another ValueSet by reference, which this "
                        + "expansion does not resolve. Refusing rather than omitting its contents.");
            }
            String includeSystem = include.path("system").asText(null);
            if (includeSystem == null) {
                throw new UnsupportedComposeException(
                        "ValueSet " + url + " has a compose.include with no system.");
            }

            String includeVersion = include.path("version").asText(null);
            if (includeVersion == null) {
                includeVersion = resolveCodeSystem(includeSystem, null, tenantId)
                        .map(this::versionOf)
                        .orElse(null);
            }
            if (includeVersion == null) {
                // The ValueSet is well-formed; its CodeSystem simply is not registered. That is an
                // empty expansion with a reason, not a malformed request.
                log.info("ZIBO: {} includes {} but no CodeSystem is registered for it — expanding "
                        + "to nothing", url, includeSystem);
                continue;
            }

            List<ConceptEntity> concepts;
            JsonNode explicit = include.path("concept");
            if (explicit.isArray() && !explicit.isEmpty()) {
                List<String> codes = new ArrayList<>();
                explicit.forEach(c -> codes.add(c.path("code").asText()));
                concepts = conceptRepository.findForExpansionOfCodes(
                        includeSystem, includeVersion, codes, tenantId, activeOnly);
                total += concepts.size();
                concepts = page(concepts, from, limit);
            } else if (filter != null && !filter.isBlank()) {
                concepts = conceptRepository.searchForExpansion(includeSystem, includeVersion,
                        tenantId, activeOnly, filter, filter + "%", limit, from);
                total += concepts.size();
            } else {
                total += conceptRepository.countForExpansion(
                        includeSystem, includeVersion, tenantId, activeOnly);
                concepts = page(conceptRepository.findForExpansion(
                        includeSystem, includeVersion, tenantId, activeOnly), from, limit);
            }

            for (ConceptEntity c : concepts) {
                out.add(new ExpansionEntry(c.getSystem(), c.getVersion(), c.getCode(), c.getDisplay()));
            }
        }

        return new Expansion(url, versionOf(vs), total, from, out);
    }

    // ── ValueSet/$validate-code ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ValidateResult validateCodeInValueSet(String url, String system, String code,
                                                 String requestedVersion) {
        // Membership is asked of the expansion, so the answer cannot disagree with what a picker
        // would have offered.
        Expansion expansion = expand(url, requestedVersion, null, MAX_COUNT, 0, false);
        boolean member = expansion.contains().stream()
                .anyMatch(e -> e.code().equals(code)
                        && (system == null || system.equals(e.system())));
        if (member) {
            return new ValidateResult(true, null, expansion.contains().stream()
                    .filter(e -> e.code().equals(code)).findFirst()
                    .map(ExpansionEntry::display).orElse(null));
        }
        return new ValidateResult(false,
                "Code '" + code + "' is not a member of " + url, null);
    }

    // ── ConceptMap/$translate ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public MappingService.MappingResult translate(String sourceSystem, String sourceCode,
                                                  String targetSystem) {
        return mappingService.mapCode(sourceSystem, sourceCode, targetSystem);
    }

    // ── helpers ───────────────────────────────────────────────────────────────────────────────

    private Optional<ArtifactEntity> resolveCodeSystem(String system, String version, UUID tenantId) {
        return artifactResolutionService
                .resolveCurrentOrVersion(tenantId, system, version, ArtifactType.CODE_SYSTEM);
    }

    /**
     * The version the concepts were projected under.
     *
     * <p>{@code content_json.version} wins over the artifact column: the projection keys on the
     * former, and a mismatch between the two would make every lookup miss.</p>
     */
    private String versionOf(ArtifactEntity artifact) {
        JsonNode root = parse(artifact.getContentJson());
        String inContent = root.path("version").asText(null);
        return inContent != null && !inContent.isBlank() ? inContent : artifact.getVersion();
    }

    private static <T> List<T> page(List<T> all, int from, int limit) {
        if (from >= all.size()) {
            return List.of();
        }
        return all.subList(from, Math.min(all.size(), from + limit));
    }

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json == null ? "{}" : json);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private static UUID tenantId() {
        TrustContext ctx = TrustContextHolder.require();
        return ctx.tenantId();
    }
}
