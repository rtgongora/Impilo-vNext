package zw.gov.mohcc.impilo.surgery.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
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

    private final SurgicalDecisionRepository repository;
    private final SurgicalEpisodeRepository episodeRepository;

    public SurgicalDecisionService(SurgicalDecisionRepository repository,
                                   SurgicalEpisodeRepository episodeRepository) {
        this.repository = repository;
        this.episodeRepository = episodeRepository;
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
        return m;
    }
}
