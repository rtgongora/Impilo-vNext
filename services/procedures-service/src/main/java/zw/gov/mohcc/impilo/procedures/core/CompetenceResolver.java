package zw.gov.mohcc.impilo.procedures.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.procedures.api.dto.CompetenceDtos.CompetenceVerdict;
import zw.gov.mohcc.impilo.procedures.api.dto.CompetenceDtos.PrivilegeRecord;
import zw.gov.mohcc.impilo.procedures.persistence.entity.ProcedureDefinitionEntity;
import zw.gov.mohcc.impilo.procedures.persistence.entity.ProcedureRequirementEntity;
import zw.gov.mohcc.impilo.procedures.persistence.repository.ProcedureDefinitionRepository;
import zw.gov.mohcc.impilo.procedures.persistence.repository.ProcedureRequirementRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Competence and privilege resolution (pipeline §7).
 *
 * <p>The specification's requirement is one sentence: <b>a trainee must not be surfaced as an
 * independent authorised operator.</b> The platform could not answer it, and the reason turned
 * out to be a consumption bug rather than a modelling gap.</p>
 *
 * <p>VARAPI has modelled supervision since its V002 — {@code provider_privilege
 * .supervising_provider_id}, exposed as {@code supervisingProviderPublicId}. The existing
 * consumer checks that a privilege is APPROVED and matches a scope, and drops that field on the
 * floor. So a privilege granted <em>under supervision</em> has been indistinguishable from an
 * independent one at the point of use: the data was always there and nobody read it.</p>
 *
 * <p>This resolver reads it. A privilege naming a supervising provider yields SUPERVISED and
 * never INDEPENDENT, whatever else is true of it.</p>
 *
 * <p><b>Fail safe.</b> An unreachable VARAPI yields UNKNOWN with {@code mayProceedAlone=false} —
 * never INDEPENDENT by omission. "Could not ask" is not "is credentialed", and this service sits
 * on the readiness path where that difference decides whether a procedure starts.</p>
 */
@Service
public class CompetenceResolver {

    private static final String APPROVED = "APPROVED";

    private final ProcedureDefinitionRepository definitions;
    private final ProcedureRequirementRepository requirements;
    private final PrivilegeSource privileges;

    public CompetenceResolver(ProcedureDefinitionRepository definitions,
                              ProcedureRequirementRepository requirements,
                              PrivilegeSource privileges) {
        this.definitions = definitions;
        this.requirements = requirements;
        this.privileges = privileges;
    }

    @Transactional(readOnly = true)
    public CompetenceVerdict resolve(UUID tenantId, String providerId, String definitionCode) {
        List<String> reasons = new ArrayList<>();

        Optional<ProcedureDefinitionEntity> maybe = definitions
                .findByTenantIdAndDefinitionCodeAndStatus(tenantId, definitionCode, "PUBLISHED");
        if (maybe.isEmpty()) {
            reasons.add("No published catalogue entry for '" + definitionCode + "'.");
            return new CompetenceVerdict(providerId, definitionCode, "UNKNOWN", false, null, false, reasons);
        }
        ProcedureDefinitionEntity def = maybe.get();
        List<ProcedureRequirementEntity> reqs =
                requirements.findByDefinitionIdOrderByDisplayOrderAsc(def.getId());

        boolean countersignatureRequired = reqs.stream()
                .anyMatch(r -> "COUNTERSIGNATURE".equals(r.getRequirementKind()));
        boolean supervisionAcceptable = reqs.stream()
                .anyMatch(r -> "SUPERVISION".equals(r.getRequirementKind()));

        List<PrivilegeRecord> held;
        try {
            held = privileges.privilegesFor(providerId);
        } catch (RuntimeException e) {
            // Fail safe. An outage is not a credential.
            reasons.add("Credentialing service unavailable (" + e.getClass().getSimpleName()
                    + ") — competence could not be established.");
            return new CompetenceVerdict(providerId, definitionCode, "UNKNOWN", false, null,
                    countersignatureRequired, reasons);
        }

        List<PrivilegeRecord> matching = held.stream()
                .filter(p -> APPROVED.equalsIgnoreCase(p.status()))
                .filter(p -> scopeMatches(p, def.getOwningSpecialty()))
                .toList();

        if (matching.isEmpty()) {
            reasons.add("No APPROVED privilege for " + def.getOwningSpecialty() + ".");
            return new CompetenceVerdict(providerId, definitionCode, "NOT_PERMITTED", false, null,
                    countersignatureRequired, reasons);
        }

        // An independent privilege is one with NO supervising provider. If the operator holds
        // both, the independent one governs — a person may be independent for a specialty and
        // separately supervised for a subspecialty, and the presence of the second must not
        // demote the first.
        Optional<PrivilegeRecord> independent = matching.stream()
                .filter(p -> isBlank(p.supervisingProviderPublicId()))
                .findFirst();
        if (independent.isPresent()) {
            reasons.add("Holds an APPROVED independent privilege for " + def.getOwningSpecialty() + ".");
            return new CompetenceVerdict(providerId, definitionCode, "INDEPENDENT", true, null,
                    countersignatureRequired, reasons);
        }

        // Supervised only. This is the case the platform could not previously see.
        PrivilegeRecord supervised = matching.get(0);
        reasons.add("Privilege for " + def.getOwningSpecialty() + " is held under supervision by "
                + supervised.supervisingProviderPublicId() + ".");
        if (!supervisionAcceptable) {
            reasons.add("The catalogue declares no SUPERVISION requirement for this procedure, so a "
                    + "supervised operator may not perform it alone.");
        }
        return new CompetenceVerdict(providerId, definitionCode, "SUPERVISED", false,
                supervised.supervisingProviderPublicId(),
                // A supervised operator's work is countersigned whether or not the catalogue
                // asked for it: supervision that leaves no signature is unreviewable.
                true, reasons);
    }

    /**
     * Scope is compared at the domain level, which is how VARAPI actually stores it. The theatre
     * programme learned this the hard way: it queried the privilege "SURGEON" when the domain
     * scope is "SURGERY", and every check silently returned unavailable.
     */
    private boolean scopeMatches(PrivilegeRecord p, String owningSpecialty) {
        if (owningSpecialty == null) {
            return false;
        }
        String scope = p.scope() == null ? "" : p.scope();
        String type = p.privilegeType() == null ? "" : p.privilegeType();
        return owningSpecialty.equalsIgnoreCase(scope) || owningSpecialty.equalsIgnoreCase(type);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
