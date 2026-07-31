package zw.gov.mohcc.impilo.surgery.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.surgery.integration.PctMdtDecisionClient;
import zw.gov.mohcc.impilo.surgery.persistence.entity.SurgicalDecisionEntity;
import zw.gov.mohcc.impilo.surgery.persistence.repository.SurgicalDecisionRepository;
import zw.gov.mohcc.impilo.surgery.persistence.repository.SurgicalEpisodeRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * S3 — the surgical decision-making record (surgical domain pack §8). Refined over time on one
 * row per episode, the same idiom V003's assessment uses. See V004's own migration header for
 * why diagnosis/certainty are read from {@code surgical_episode.pct_problem_ref} rather than
 * repeated here, and why {@code material_risks_considered} is distinct from mvumo's
 * consent-conversation field of a similar name.
 *
 * <p>{@code recordDecision} enforces the same all-or-nothing pairing V004's own CHECK enforces:
 * a final decision, its author and its timestamp travel together or not at all.</p>
 */
@Service
public class SurgicalDecisionService {

    private static final List<String> FINAL_DECISIONS = List.of("PROCEED", "DO_NOT_PROCEED", "DEFER");
    private static final List<String> DECISION_FORUMS = List.of("INDIVIDUAL", "MDT");
    private static final List<String> MDT_SOURCES =
            List.of(PctMdtDecisionClient.SOURCE_MDT_DECISION, PctMdtDecisionClient.SOURCE_MDT_CASE_ITEM);

    private final SurgicalDecisionRepository repository;
    private final SurgicalEpisodeRepository episodeRepository;
    private final PctMdtDecisionClient mdtClient;

    public SurgicalDecisionService(SurgicalDecisionRepository repository,
                                   SurgicalEpisodeRepository episodeRepository,
                                   PctMdtDecisionClient mdtClient) {
        this.repository = repository;
        this.episodeRepository = episodeRepository;
        this.mdtClient = mdtClient;
    }

    private UUID currentTenant() {
        return TrustContextHolder.require().tenantId();
    }

    private String currentActor() {
        try {
            String actor = TrustContextHolder.require().actorId();
            return actor != null && !actor.isBlank() ? actor : "system";
        } catch (IllegalStateException e) {
            return "system";
        }
    }

    @Transactional
    public Map<String, Object> recordDecision(UUID episodeId, Map<String, Object> body) {
        UUID tenant = currentTenant();
        episodeRepository.findByIdAndTenantId(episodeId, tenant)
                .orElseThrow(() -> new SurgeryDomainException("SURGICAL_EPISODE_NOT_FOUND", 404,
                        "No surgical episode " + episodeId));

        SurgicalDecisionEntity e = repository.findBySurgicalEpisodeIdAndTenantId(episodeId, tenant)
                .orElseGet(() -> {
                    SurgicalDecisionEntity fresh = new SurgicalDecisionEntity();
                    fresh.setTenantId(tenant);
                    fresh.setSurgicalEpisodeId(episodeId);
                    return fresh;
                });

        applyIfPresent(body, "naturalHistory", e::setNaturalHistory);
        applyIfPresent(body, "expectedBenefit", e::setExpectedBenefit);
        applyIfPresent(body, "materialRisksConsidered", e::setMaterialRisksConsidered);
        applyIfPresent(body, "anaestheticImplications", e::setAnaestheticImplications);
        applyIfPresent(body, "bloodImplications", e::setBloodImplications);
        applyIfPresent(body, "functionalImplications", e::setFunctionalImplications);
        applyIfPresent(body, "fertilityImplications", e::setFertilityImplications);
        applyIfPresent(body, "stomaPossibilityNotes", e::setStomaPossibilityNotes);
        applyIfPresent(body, "implantPossibilityNotes", e::setImplantPossibilityNotes);
        applyIfPresent(body, "rehabilitationExpectation", e::setRehabilitationExpectation);
        applyIfPresent(body, "financialAccessImplications", e::setFinancialAccessImplications);
        applyIfPresent(body, "patientPreference", e::setPatientPreference);

        if (body.containsKey("stomaPossibility") && body.get("stomaPossibility") != null) {
            e.setStomaPossibility(bool(body, "stomaPossibility"));
        }
        if (body.containsKey("implantPossibility") && body.get("implantPossibility") != null) {
            e.setImplantPossibility(bool(body, "implantPossibility"));
        }

        applyForum(e, body);

        // The three-way pairing V004's own CHECK enforces: a final decision, its author and its
        // timestamp travel together, or none of them are set at all.
        if (body.get("finalDecision") != null) {
            String decision = body.get("finalDecision").toString();
            if (!FINAL_DECISIONS.contains(decision)) {
                throw new SurgeryDomainException("INVALID_FINAL_DECISION", 400,
                        "finalDecision must be one of " + FINAL_DECISIONS);
            }
            String decidedBy = body.get("decidedBy") != null ? body.get("decidedBy").toString() : currentActor();
            e.setFinalDecision(decision);
            e.setDecidedBy(decidedBy);
            e.setDecidedAt(OffsetDateTime.now());
        }

        e = repository.save(e);
        return toView(e);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDecision(UUID episodeId) {
        SurgicalDecisionEntity e = repository
                .findBySurgicalEpisodeIdAndTenantId(episodeId, currentTenant())
                .orElseThrow(() -> new SurgeryDomainException("SURGICAL_DECISION_NOT_FOUND", 404,
                        "No surgical decision record for episode " + episodeId));
        return toView(e);
    }

    /**
     * Demonstration 3 — record WHICH forum decided, and reference PCT's board record.
     *
     * <p>An MDT decision must name a board: an unreferenced "the MDT agreed" is exactly the
     * unattributable claim this migration exists to remove. Because PCT keeps two MDT systems of
     * record, the reference is a pair — the id and the table it points into.</p>
     *
     * <p>The reference is resolved against PCT before it is stored, and the three possible answers
     * are treated as three different things. Found: verified. Definitively absent: refused, since
     * PCT has answered and the reference is wrong. Unreachable: accepted and left unverified,
     * because a PCT outage must not block recording that a board decided, and must not be allowed
     * to look like confirmation that it did.</p>
     */
    private void applyForum(SurgicalDecisionEntity e, Map<String, Object> body) {
        if (body.get("decisionForum") == null) {
            return;
        }
        String forum = body.get("decisionForum").toString().trim().toUpperCase(java.util.Locale.ROOT);
        if (!DECISION_FORUMS.contains(forum)) {
            throw new SurgeryDomainException("INVALID_DECISION_FORUM", 400,
                    "decisionForum must be one of " + DECISION_FORUMS);
        }

        if ("INDIVIDUAL".equals(forum)) {
            e.setDecisionForum(forum);
            e.setMdtDecisionRef(null);
            e.setMdtDecisionSource(null);
            e.setMdtDecisionVerifiedAt(null);
            return;
        }

        Object rawRef = body.get("mdtDecisionRef");
        Object rawSource = body.get("mdtDecisionSource");
        if (rawRef == null || rawSource == null) {
            throw new SurgeryDomainException("MDT_REFERENCE_REQUIRED", 400,
                    "An MDT decision must reference the board record that made it — supply "
                            + "mdtDecisionRef together with mdtDecisionSource (one of " + MDT_SOURCES
                            + "), because PCT keeps two MDT systems of record and a bare id is ambiguous");
        }
        String source = rawSource.toString().trim().toUpperCase(java.util.Locale.ROOT);
        if (!MDT_SOURCES.contains(source)) {
            throw new SurgeryDomainException("INVALID_MDT_SOURCE", 400,
                    "mdtDecisionSource must be one of " + MDT_SOURCES);
        }
        UUID ref;
        try {
            ref = UUID.fromString(rawRef.toString().trim());
        } catch (IllegalArgumentException ex) {
            throw new SurgeryDomainException("INVALID_MDT_REFERENCE", 400,
                    "mdtDecisionRef must be a UUID");
        }

        PctMdtDecisionClient.MdtLookup lookup = mdtClient.lookup(source, ref);
        if (lookup.known() && !lookup.present()) {
            throw new SurgeryDomainException("MDT_DECISION_NOT_FOUND", 404,
                    "PCT has no " + source + " " + ref + " — a surgical decision may not cite a "
                            + "board record that does not exist");
        }

        e.setDecisionForum(forum);
        e.setMdtDecisionSource(source);
        e.setMdtDecisionRef(ref);
        // Unverified is not invalid. Null here means PCT could not be asked, and
        // idx_surgical_decision_mdt_unverified is the reconciliation query for exactly these rows.
        e.setMdtDecisionVerifiedAt(lookup.present() ? OffsetDateTime.now() : null);
    }

    private void applyIfPresent(Map<String, Object> body, String key, Consumer<String> setter) {
        if (body.containsKey(key) && body.get(key) != null) {
            setter.accept(body.get(key).toString());
        }
    }

    private Boolean bool(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null) return null;
        if (v instanceof Boolean b) return b;
        return Boolean.parseBoolean(v.toString());
    }

    private Map<String, Object> toView(SurgicalDecisionEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId().toString());
        m.put("surgicalEpisodeId", e.getSurgicalEpisodeId().toString());
        m.put("naturalHistory", e.getNaturalHistory());
        m.put("expectedBenefit", e.getExpectedBenefit());
        m.put("materialRisksConsidered", e.getMaterialRisksConsidered());
        m.put("anaestheticImplications", e.getAnaestheticImplications());
        m.put("bloodImplications", e.getBloodImplications());
        m.put("functionalImplications", e.getFunctionalImplications());
        m.put("fertilityImplications", e.getFertilityImplications());
        m.put("stomaPossibility", e.getStomaPossibility());
        m.put("stomaPossibilityNotes", e.getStomaPossibilityNotes());
        m.put("implantPossibility", e.getImplantPossibility());
        m.put("implantPossibilityNotes", e.getImplantPossibilityNotes());
        m.put("rehabilitationExpectation", e.getRehabilitationExpectation());
        m.put("financialAccessImplications", e.getFinancialAccessImplications());
        m.put("patientPreference", e.getPatientPreference());
        m.put("finalDecision", e.getFinalDecision());
        m.put("decidedBy", e.getDecidedBy());
        m.put("decidedAt", e.getDecidedAt() != null ? e.getDecidedAt().toString() : null);
        m.put("decisionForum", e.getDecisionForum());
        m.put("mdtDecisionRef", e.getMdtDecisionRef() != null ? e.getMdtDecisionRef().toString() : null);
        m.put("mdtDecisionSource", e.getMdtDecisionSource());
        m.put("mdtDecisionVerifiedAt",
                e.getMdtDecisionVerifiedAt() != null ? e.getMdtDecisionVerifiedAt().toString() : null);
        // The surface must be able to say "referenced but not confirmed" rather than showing a
        // board reference that reads as though somebody checked it.
        m.put("mdtDecisionVerified", e.getMdtDecisionVerifiedAt() != null);
        return m;
    }
}
