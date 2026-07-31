package zw.gov.mohcc.impilo.zibo.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.zibo.persistence.entity.MappingIndexEntity;
import zw.gov.mohcc.impilo.zibo.persistence.repository.MappingIndexRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Answers "is this clinical content confidential by nature?" from the governed value set, so that
 * no clinical service has to decide it locally.
 *
 * <p>Zibo is the terminology system of record, so the list of confidential categories and the
 * clinical codes that fall in them is a versioned, reviewable artifact here (seeded by migration
 * {@code V008}) rather than a hardcoded list in each service. That matters because the alternative
 * — every service carrying its own list — means the decision cannot be reviewed, changing it needs
 * a code deploy in several places at once, and no two services agree on the edge cases.</p>
 *
 * <h2>Longest-prefix matching</h2>
 * <p>ICD-10 confidentiality follows blocks, not individual codes, so the governed map stores code
 * <em>prefixes</em>. Matching takes the longest prefix, which is what makes a narrower mapping beat
 * a broader one: {@code F10}–{@code F19} resolve to {@code SUBSTANCE_USE} instead of being swallowed
 * by the {@code F} mental-health block. Ties cannot occur — two rows with the same prefix and
 * different categories would be a governance error in the seed, and the first is taken with a
 * warning rather than failing the clinical write.</p>
 *
 * <h2>What this service does not do</h2>
 * <p>It classifies content. It does not decide access — that is the trust plane's
 * {@code SPECIALLY_PROTECTED} enforcement — and it does not stamp records; the clinical system of
 * record does that, using this answer.</p>
 */
@Service
public class ConfidentialCategoryService {

    private static final Logger log = LoggerFactory.getLogger(ConfidentialCategoryService.class);

    /** Target code system of the governed confidential-category map (see zibo V008). */
    public static final String CATEGORY_SYSTEM =
            "https://impilo.gov.zw/fhir/CodeSystem/confidential-clinical-category";

    /** The sensitivity class a clinical record must carry when any of its codes is confidential. */
    public static final String PROTECTED_SENSITIVITY_CLASS = "SPECIALLY_PROTECTED";

    /**
     * Whether the seeded map has been ratified. While false the service still reports the categories
     * it matched — so the content can be reviewed, and a governance surface can show what ratifying
     * would do — but it returns NO sensitivity class to stamp.
     *
     * <p>That distinction is the point. The trust plane's enforcement is inert until MoHCC ratifies
     * the policy pack, so a record stamped SPECIALLY_PROTECTED today would carry a label that does
     * not protect it. Handing back a stampable class before enforcement is live would manufacture
     * exactly the false assurance this whole seam exists to remove.</p>
     *
     * <p>Flip to true only together with the tshepo-authz policy pack ratification and the V048 rule
     * activation. They are one governance act, not three.</p>
     */
    public static final boolean CATEGORY_MAP_RATIFIED = true;

    private final MappingIndexRepository mappingIndexRepository;

    public ConfidentialCategoryService(MappingIndexRepository mappingIndexRepository) {
        this.mappingIndexRepository = mappingIndexRepository;
    }

    /** One coded clinical entry to classify. */
    public record CodedEntry(String system, String code) {}

    /**
     * The governed verdict for a set of coded entries.
     *
     * @param confidential          whether the record should be treated as confidential. FALSE while
     *                              the governed map is unratified even when categories matched — read
     *                              {@code categories} to see what the seed would classify
     * @param sensitivityClass      the class to stamp, or {@code null} when not confidential OR when
     *                              the governed map is not yet ratified (see {@link #CATEGORY_MAP_RATIFIED})
     * @param categories            every confidential category matched, in first-matched order
     * @param unmatchedCodeSystems  code systems present in the request that the governed map does not
     *                              cover. Reported rather than swallowed: an uncovered code system is
     *                              a governance gap, not evidence that the content is ordinary.
     */
    public record ClassificationResult(
            boolean confidential,
            String sensitivityClass,
            List<String> categories,
            List<String> unmatchedCodeSystems) {

        static ClassificationResult ordinary(List<String> unmatchedCodeSystems) {
            return new ClassificationResult(false, null, List.of(), unmatchedCodeSystems);
        }

        static ClassificationResult protectedAs(List<String> categories, List<String> unmatchedCodeSystems) {
            // sensitivityClass is withheld while the map is unratified: the caller learns WHICH
            // categories matched but is given nothing to stamp, because the enforcement that would
            // make the stamp meaningful is not live yet.
            return new ClassificationResult(
                    CATEGORY_MAP_RATIFIED,
                    CATEGORY_MAP_RATIFIED ? PROTECTED_SENSITIVITY_CLASS : null,
                    categories,
                    unmatchedCodeSystems);
        }
    }

    /**
     * Classify a set of coded clinical entries against the governed map.
     *
     * <p>A record is confidential if <em>any</em> of its codes is: a consultation that touches a
     * confidential category does not become ordinary because it also recorded a sore throat.</p>
     */
    @Transactional(readOnly = true)
    public ClassificationResult classify(UUID tenantId, Collection<CodedEntry> entries) {
        List<String> unmatchedSystems = new ArrayList<>();
        if (entries == null || entries.isEmpty()) {
            return ClassificationResult.ordinary(unmatchedSystems);
        }

        List<MappingIndexEntity> governedMap =
                mappingIndexRepository.findByTenantIdAndTargetSystem(tenantId, CATEGORY_SYSTEM);
        if (governedMap.isEmpty()) {
            // The governed list is absent (un-migrated tenant). Say so instead of reporting the
            // content as ordinary — "no list loaded" and "nothing is confidential" must not look
            // the same to a caller deciding whether to stamp a record.
            log.warn("Confidential-category map is empty for tenant {} — classification unavailable", tenantId);
            List<String> systems = entries.stream()
                    .map(CodedEntry::system).filter(s -> s != null && !s.isBlank())
                    .distinct().toList();
            return ClassificationResult.ordinary(systems.isEmpty() ? List.of("UNKNOWN") : systems);
        }

        Set<String> knownSystems = new LinkedHashSet<>();
        for (MappingIndexEntity row : governedMap) {
            knownSystems.add(normalise(row.getSourceSystem()));
        }

        Set<String> categories = new LinkedHashSet<>();
        Set<String> unmatched = new LinkedHashSet<>();
        for (CodedEntry entry : entries) {
            if (entry == null || entry.code() == null || entry.code().isBlank()) {
                continue;
            }
            String system = normalise(entry.system());
            if (!knownSystems.contains(system)) {
                unmatched.add(entry.system() == null || entry.system().isBlank() ? "UNKNOWN" : entry.system());
                continue;
            }
            String category = longestPrefixCategory(governedMap, system, normalise(entry.code()));
            if (category != null) {
                categories.add(category);
            }
        }

        unmatchedSystems.addAll(unmatched);
        if (categories.isEmpty()) {
            return ClassificationResult.ordinary(unmatchedSystems);
        }
        return ClassificationResult.protectedAs(List.copyOf(categories), unmatchedSystems);
    }

    /** Convenience for the single-code case. */
    @Transactional(readOnly = true)
    public ClassificationResult classifyCode(UUID tenantId, String system, String code) {
        return classify(tenantId, List.of(new CodedEntry(system, code)));
    }

    /** Whether the governed map may drive record stamping. */
    public boolean isRatified() {
        return CATEGORY_MAP_RATIFIED;
    }

    /** The governed category vocabulary in use for this tenant (for review surfaces). */
    @Transactional(readOnly = true)
    public List<String> activeCategories(UUID tenantId) {
        return mappingIndexRepository.findByTenantIdAndTargetSystem(tenantId, CATEGORY_SYSTEM).stream()
                .map(MappingIndexEntity::getTargetCode)
                .filter(c -> c != null && !c.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    private String longestPrefixCategory(List<MappingIndexEntity> governedMap, String system, String code) {
        MappingIndexEntity best = null;
        for (MappingIndexEntity row : governedMap) {
            if (!system.equals(normalise(row.getSourceSystem()))) {
                continue;
            }
            String prefix = normalise(row.getSourceCode());
            if (prefix.isEmpty() || !code.startsWith(prefix)) {
                continue;
            }
            if (best == null || prefix.length() > normalise(best.getSourceCode()).length()) {
                best = row;
            } else if (prefix.length() == normalise(best.getSourceCode()).length()
                    && !prefix.equals(normalise(best.getSourceCode()))) {
                log.warn("Ambiguous confidential-category prefixes for code {}: keeping {} ({})",
                        code, best.getSourceCode(), best.getTargetCode());
            }
        }
        return best == null ? null : best.getTargetCode();
    }

    private static String normalise(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    }
}
