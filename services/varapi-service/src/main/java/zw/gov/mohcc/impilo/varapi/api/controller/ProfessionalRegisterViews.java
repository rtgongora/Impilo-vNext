package zw.gov.mohcc.impilo.varapi.api.controller;

import zw.gov.mohcc.impilo.varapi.persistence.entity.ProfessionalRegisterEntity;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The operator-facing shape of a professional register. Shared by the council-code browse and the
 * organisation-scoped materialisation surface so provenance cannot drift between the two.
 *
 * <p>{@link java.util.Map#of} refuses null values; several of these fields are legitimately null
 * (an undecided statutory title, a migration-seeded row with no content hash), so the view is built
 * with a mutable map.</p>
 */
final class ProfessionalRegisterViews {

    private ProfessionalRegisterViews() {
    }

    static Map<String, Object> toOperatorView(ProfessionalRegisterEntity r) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", r.getId());
        view.put("registerCode", r.getRegisterCode());
        view.put("name", r.getName());
        view.put("description", r.getDescription());
        view.put("status", r.getStatus());
        view.put("registerAxis", r.getRegisterAxis());
        view.put("statutoryTitle", r.getStatutoryTitle());
        view.put("statutoryTitleDecisionRef", r.getStatutoryTitleDecisionRef());
        view.put("source", r.getSource());
        view.put("configDefinitionKey", r.getConfigDefinitionKey());
        view.put("configSemanticVersion", r.getConfigSemanticVersion());
        view.put("configContentHash", r.getConfigContentHash());
        view.put("materialisedAt", r.getMaterialisedAt() == null ? null : r.getMaterialisedAt().toString());
        view.put("retiredAt", r.getRetiredAt() == null ? null : r.getRetiredAt().toString());
        return view;
    }
}
