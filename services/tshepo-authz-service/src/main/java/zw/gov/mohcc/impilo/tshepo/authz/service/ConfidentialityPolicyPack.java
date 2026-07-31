package zw.gov.mohcc.impilo.tshepo.authz.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The governed confidentiality policy pack — the content that decides what the
 * specially-protected mechanism actually means.
 *
 * <h2>Why this is separate from the mechanism</h2>
 * <p>Two questions govern behaviour here and neither is an engineering call:</p>
 * <ol>
 *   <li>At what age does a young person's record become confidential from their parent? That is
 *       Zimbabwean law and MoHCC policy, and it is not uniform — independent consent for HIV
 *       testing, contraception and mental health care sit under different instruments.</li>
 *   <li>Which clinical codes are confidential by nature? Governed terminology (zibo).</li>
 * </ol>
 *
 * <p>So the mechanism ships complete and the content ships {@code ENGINEERING_SEED} /
 * {@code PENDING_MOHCC_RATIFICATION}, exactly like the danger-sign, dosing, IMNCI, EPI and growth
 * packs already in the tree. Until the pack is ratified <strong>and</strong> marked effective, it
 * grants nothing and withholds nothing.</p>
 *
 * <h2>The inertness rule</h2>
 * <p>{@link #isEffective()} requires {@code approvalStatus == RATIFIED} <em>and</em>
 * {@code effective == true}. Both, deliberately: ratification is a governance act and activation is
 * an operational one, and a single flag would let either happen by accident. While inert, no record
 * can carry a protection label that does not protect it — which is the failure this whole seam
 * exists to prevent.</p>
 *
 * <h2>Who consumes what</h2>
 * <p>The PDP consumes {@link #grantableCategories()} only. The <em>age</em> rules are for the
 * clinical system of record at stamp time: deciding whether a record is confidential from a guardian
 * needs the patient's age, and the PDP sees a subject identifier, never a date of birth. Pretending
 * otherwise would be the same category of self-deception as the decorative class was.</p>
 */
@Service
public class ConfidentialityPolicyPack {

    private static final Logger log = LoggerFactory.getLogger(ConfidentialityPolicyPack.class);

    private static final String PACK_PATH = "policy/adolescent-confidentiality-pack.json";
    private static final String RATIFIED = "RATIFIED";

    private final ObjectMapper objectMapper;

    private String packId = "unloaded";
    private String version = "unloaded";
    private String approvalStatus = "ABSENT";
    private boolean effectiveFlag;
    private List<String> protectedCategories = List.of();
    private List<AgeRule> ageRules = List.of();

    public ConfidentialityPolicyPack(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * A guardian-confidentiality age rule. {@code confidentialFromGuardianAgeYears} is deliberately
     * nullable — an unanswered legal question reads as null, never as a plausible default, because a
     * guessed threshold that looks authoritative is worse than an obviously missing one.
     *
     * @param treatment optional SRH sub-type (CONTRACEPTION, STI, TOP) when category is
     *                  SEXUAL_REPRODUCTIVE_HEALTH
     * @param assessmentMode CASE_BY_CASE when no single age threshold applies (PO 2026-07-31 STI/TOP)
     */
    public record AgeRule(
            String category,
            String treatment,
            Integer confidentialFromGuardianAgeYears,
            String verificationStatus,
            String assessmentMode,
            String legalBasisToVerify) {

        /** Usable only when the threshold has actually been supplied and verified. */
        public boolean usable() {
            return confidentialFromGuardianAgeYears != null
                    && "VERIFIED".equalsIgnoreCase(verificationStatus);
        }
    }

    @PostConstruct
    void load() {
        try (InputStream in = new ClassPathResource(PACK_PATH).getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            packId = root.path("packId").asText("unknown");
            version = root.path("version").asText("unknown");
            approvalStatus = root.path("approvalStatus").asText("ABSENT");
            effectiveFlag = root.path("effective").asBoolean(false);

            List<String> categories = new ArrayList<>();
            root.path("protectedCategories").forEach(n -> categories.add(n.asText()));
            protectedCategories = List.copyOf(categories);

            List<AgeRule> rules = new ArrayList<>();
            root.path("guardianConfidentialityRules").forEach(n -> rules.add(new AgeRule(
                    n.path("category").asText(null),
                    n.hasNonNull("treatment") ? n.get("treatment").asText(null) : null,
                    n.hasNonNull("confidentialFromGuardianAgeYears")
                            ? n.get("confidentialFromGuardianAgeYears").asInt() : null,
                    n.path("verificationStatus").asText("UNVERIFIED"),
                    n.hasNonNull("assessmentMode") ? n.get("assessmentMode").asText(null) : null,
                    n.path("legalBasisToVerify").asText(null))));
            ageRules = List.copyOf(rules);
        } catch (Exception e) {
            // An unreadable pack must leave the mechanism inert, not fall back to a guess.
            log.error("Confidentiality policy pack could not be loaded from {} — the confidentiality "
                    + "control will remain INERT: {}", PACK_PATH, e.getMessage());
            approvalStatus = "ABSENT";
            effectiveFlag = false;
            protectedCategories = List.of();
            ageRules = List.of();
            return;
        }

        if (isEffective()) {
            log.info("Confidentiality policy pack {} {} is RATIFIED and EFFECTIVE — {} categories grantable",
                    packId, version, protectedCategories.size());
        } else {
            log.info("Confidentiality policy pack {} {} loaded but INERT (approvalStatus={}, effective={}). "
                            + "The mechanism is built; it grants nothing until MoHCC ratifies the content.",
                    packId, version, approvalStatus, effectiveFlag);
        }
    }

    /**
     * Whether the pack may drive behaviour. Requires ratification AND activation — see the class
     * docs on why one flag is not enough.
     */
    public boolean isEffective() {
        return RATIFIED.equalsIgnoreCase(approvalStatus) && effectiveFlag;
    }

    /**
     * The categories a policy rule may grant. Empty while the pack is inert, so a rule overlay
     * naming a category grants nothing — the mechanism runs, the content does not yet mean anything.
     */
    public List<String> grantableCategories() {
        return isEffective() ? protectedCategories : List.of();
    }

    /** Whether {@code category} is a recognised, currently-grantable confidential category. */
    public boolean isGrantable(String category) {
        if (category == null || category.isBlank()) {
            return false;
        }
        String wanted = category.trim().toUpperCase(Locale.ROOT);
        if (wanted.equals("*")) {
            // The whole-set grant (self-access, audited emergency waiver) is a property of the
            // mechanism, not of the content, so it survives an inert pack.
            return true;
        }
        return grantableCategories().stream()
                .anyMatch(c -> c != null && c.trim().toUpperCase(Locale.ROOT).equals(wanted));
    }

    /**
     * Filter a requested category set down to what the pack currently permits. Categories dropped
     * because the pack is inert are reported by the caller, never silently discarded.
     */
    public Set<String> retainGrantable(List<String> requested) {
        Set<String> kept = new LinkedHashSet<>();
        if (requested == null) {
            return kept;
        }
        for (String c : requested) {
            if (isGrantable(c)) {
                kept.add(c.trim().toUpperCase(Locale.ROOT));
            }
        }
        return kept;
    }

    /** Guardian-confidentiality age rules loaded from the pack (may include SRH treatment splits). */
    public List<AgeRule> ageRules() {
        return ageRules;
    }

    /** The guardian-confidentiality age rule for a category, or null when the pack declares none. */
    public AgeRule ageRuleFor(String category) {
        if (category == null) {
            return null;
        }
        String wanted = category.trim().toUpperCase(Locale.ROOT);
        return ageRules.stream()
                .filter(r -> r.category() != null && r.category().trim().toUpperCase(Locale.ROOT).equals(wanted))
                .findFirst()
                .orElse(null);
    }

    public String packId() {
        return packId;
    }

    public String version() {
        return version;
    }

    public String approvalStatus() {
        return approvalStatus;
    }

    /** Human-readable state for audit payloads and governance surfaces. */
    public String stateLabel() {
        return isEffective() ? "EFFECTIVE" : ("INERT(" + approvalStatus + ",effective=" + effectiveFlag + ")");
    }
}
