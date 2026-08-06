package zw.gov.mohcc.impilo.vashandi.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.vashandi.api.VashandiDtos;
import zw.gov.mohcc.impilo.vashandi.integration.FundoIntegrationClient;
import zw.gov.mohcc.impilo.vashandi.integration.IntegrationCheckResult;
import zw.gov.mohcc.impilo.vashandi.integration.TusoIntegrationClient;
import zw.gov.mohcc.impilo.vashandi.integration.VarapiIntegrationClient;
import zw.gov.mohcc.impilo.vashandi.integration.WorkforceGovernanceIntegrationClient;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.TrainingRequirementEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.WorkforceAssignmentEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.WorkforceProfileEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.LeaveAvailabilityRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.TrainingRequirementRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.WorkforceAssignmentRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.WorkforceProfileRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WorkforceEligibilityService {

    private final WorkforceProfileRepository profileRepository;
    private final WorkforceAssignmentRepository assignmentRepository;
    private final LeaveAvailabilityRepository leaveRepository;
    private final VarapiIntegrationClient varapiClient;
    private final WorkforceGovernanceIntegrationClient governanceClient;
    private final TusoIntegrationClient tusoClient;
    private final FundoIntegrationClient fundoClient;
    private final TrainingRequirementRepository trainingRequirementRepository;

    public WorkforceEligibilityService(WorkforceProfileRepository profileRepository,
                                       WorkforceAssignmentRepository assignmentRepository,
                                       LeaveAvailabilityRepository leaveRepository,
                                       VarapiIntegrationClient varapiClient,
                                       WorkforceGovernanceIntegrationClient governanceClient,
                                       TusoIntegrationClient tusoClient,
                                       FundoIntegrationClient fundoClient,
                                       TrainingRequirementRepository trainingRequirementRepository) {
        this.profileRepository = profileRepository;
        this.assignmentRepository = assignmentRepository;
        this.leaveRepository = leaveRepository;
        this.varapiClient = varapiClient;
        this.governanceClient = governanceClient;
        this.tusoClient = tusoClient;
        this.fundoClient = fundoClient;
        this.trainingRequirementRepository = trainingRequirementRepository;
    }

    public VashandiDtos.WorkforceEligibilityResult evaluate(WorkforceAssignmentEntity assignment, String opaDecisionId) {
        List<IntegrationCheckResult> checks = new ArrayList<>();
        WorkforceProfileEntity profile = profileRepository.findByTenantIdAndId(
                assignment.getTenantId(), assignment.getWorkforceProfileId()).orElse(null);

        if (profile == null) {
            return new VashandiDtos.WorkforceEligibilityResult(
                    assignment.getId(), "denied", opaDecisionId, checks, "workforce profile missing");
        }

        checks.add(varapiClient.fetchProfessionalStatus(profile.getProviderWorkerId()));
        checks.add(governanceClient.fetchHscEmploymentSummary(profile.getHealthId()));
        checks.add(fundoClient.fetchTrainingEvidence(profile.getProviderWorkerId()));
        if (assignment.getFacilityId() != null) {
            checks.add(tusoClient.validateFacility(assignment.getFacilityId()));
        }

        // Fundo training-gate (PO-20260629-01, G-FU-02 enforcement half): evaluate the
        // governed role→course requirements with graduated levels. Only runs when the
        // role actually has active requirements — no mapping, no gate, no new block.
        String trainingGateDecision = null;
        List<String> trainingGateBlocking = List.of();
        List<TrainingRequirementEntity> trainingRequirements = assignment.getRoleTemplateId() == null
                ? List.of()
                : trainingRequirementRepository.findByTenantIdAndRoleTemplateIdAndActiveTrue(
                        assignment.getTenantId(), assignment.getRoleTemplateId());
        if (!trainingRequirements.isEmpty()) {
            List<String> codeLevels = trainingRequirements.stream()
                    .map(r -> r.getCourseCode() + ":" + r.getEnforcementLevel())
                    .toList();
            IntegrationCheckResult gate = fundoClient.fetchTrainingGate(
                    profile.getProviderWorkerId(), codeLevels);
            checks.add(gate);
            Map<String, Object> gatePayload = gate.payload().orElse(Map.of());
            Object decision = gatePayload.get("decision");
            trainingGateDecision = decision == null ? null : decision.toString();
            Object blocking = gatePayload.get("blocking");
            if (blocking instanceof List<?> list) {
                trainingGateBlocking = list.stream().map(String::valueOf).toList();
            }
        }

        boolean onLeave = leaveRepository
                .findByTenantIdAndWorkforceProfileIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        assignment.getTenantId(), profile.getId(), "approved", LocalDate.now(), LocalDate.now())
                .stream().findAny().isPresent();
        if (onLeave) {
            return new VashandiDtos.WorkforceEligibilityResult(
                    assignment.getId(), "denied", opaDecisionId, checks, "worker on approved leave");
        }

        List<WorkforceAssignmentEntity> conflicts = assignmentRepository
                .findByTenantIdAndWorkforceProfileIdAndStatusIn(
                        assignment.getTenantId(), profile.getId(), List.of("active", "approved"));
        boolean conflict = conflicts.stream().anyMatch(a -> !a.getId().equals(assignment.getId())
                && assignment.getFacilityId() != null
                && assignment.getFacilityId().equals(a.getFacilityId()));
        if (conflict) {
            return new VashandiDtos.WorkforceEligibilityResult(
                    assignment.getId(), "denied", opaDecisionId, checks, "conflicting active assignment");
        }

        boolean anyDegraded = checks.stream().anyMatch(c -> IntegrationCheckResult.DEGRADED.equals(c.status()));
        if (anyDegraded) {
            return new VashandiDtos.WorkforceEligibilityResult(
                    assignment.getId(), "pending_backend", opaDecisionId, checks, "upstream dependency unavailable");
        }

        // Professional standing was fetched and then never read.
        //
        // Until now the VARAPI check counted only as live-or-degraded: if the call succeeded
        // the assignment proceeded, whatever the registry actually said. A practitioner whose
        // licence had been suspended or revoked would be activated onto a facility roster,
        // because "the registry answered" was being taken for "the registry approved".
        //
        // This denies on an affirmatively barring licence, not on an unknown one, and that
        // distinction is load-bearing. Exactly one provider in this estate carries a
        // licence_status at all, so requiring a positive ACTIVE licence would deny 4,267 of
        // 4,268 providers and every assignment with them. Blocking on absent data would be an
        // outage wearing a safety badge, and it would teach operators to route around the
        // check rather than populate it.
        //
        // Unknown therefore passes — but it is SAID. It reaches the caller in the result
        // message instead of passing silently, so "we do not know" stops masquerading as
        // "we checked".
        LicenceVerdict licence = assessLicence(checks);
        if (licence.barred()) {
            return new VashandiDtos.WorkforceEligibilityResult(
                    assignment.getId(), "denied", opaDecisionId, checks,
                    "professional licence is " + licence.state().toLowerCase(java.util.Locale.ROOT)
                            + " — the registry bars this provider from practice");
        }

        if (opaDecisionId == null || opaDecisionId.isBlank()) {
            return new VashandiDtos.WorkforceEligibilityResult(
                    assignment.getId(), "pending", opaDecisionId, checks, "opa decision required");
        }

        if ("suspended".equals(profile.getCurrentStatus()) || "offboarded".equals(profile.getCurrentStatus())) {
            return new VashandiDtos.WorkforceEligibilityResult(
                    assignment.getId(), "denied", opaDecisionId, checks, "worker not eligible");
        }

        // Graduated training-gate enforcement (PO decision): BLOCK denies, CONDITIONAL
        // withholds activation pending an explicit governance override (re-levelling the
        // requirement), ADVISE allows with an audited warning in the message.
        if ("BLOCK".equals(trainingGateDecision)) {
            return new VashandiDtos.WorkforceEligibilityResult(
                    assignment.getId(), "denied", opaDecisionId, checks,
                    "required training incomplete (hard requirement): " + String.join(", ", trainingGateBlocking));
        }
        if ("CONDITIONAL".equals(trainingGateDecision)) {
            return new VashandiDtos.WorkforceEligibilityResult(
                    assignment.getId(), "conditional_training", opaDecisionId, checks,
                    "required training incomplete (soft requirement — governance override needed): "
                            + String.join(", ", trainingGateBlocking));
        }
        if ("ADVISE".equals(trainingGateDecision)) {
            return new VashandiDtos.WorkforceEligibilityResult(
                    assignment.getId(), "allowed", opaDecisionId, checks,
                    "eligible (training advisory: outstanding advisory learning — see fundo-training-gate check)");
        }

        return new VashandiDtos.WorkforceEligibilityResult(
                assignment.getId(), "allowed", opaDecisionId, checks,
                licence.known() ? "eligible" : "eligible (" + licence.note() + ")");
    }

    /**
     * What the provider registry says about this practitioner's licence.
     *
     * @param state  the registry's word: a licence status, a barring lifecycle state, or
     *               {@code UNKNOWN} when it carries neither.
     * @param barred the registry affirmatively bars practice — the only condition that denies.
     */
    private record LicenceVerdict(String state, boolean barred) {
        boolean known() {
            return !"UNKNOWN".equals(state);
        }

        String note() {
            return "professional licence state not recorded in the registry — assignment not "
                    + "blocked on missing data, but standing is unverified";
        }
    }

    /**
     * States in which the registry affirmatively bars practice.
     *
     * <p>Deliberately a closed list of <em>barring</em> states rather than an allow-list of good
     * ones. An allow-list would make every value the registry has not yet learned to emit into a
     * denial, which is how a safety check becomes an outage the first time a vocabulary grows.
     *
     * <p>{@code RESTRICTED} is absent on purpose: a restricted licence limits scope of practice,
     * it does not bar it. Scope belongs to the privilege and role checks, not to this gate.
     */
    private static final java.util.Set<String> BARRING_STATES =
            java.util.Set.of("SUSPENDED", "REVOKED", "WITHDRAWN", "EXPIRED", "STRUCK_OFF", "CANCELLED");

    /** Read the VARAPI standing payload that {@code fetchProfessionalStatus} already collects. */
    private static LicenceVerdict assessLicence(List<IntegrationCheckResult> checks) {
        Map<String, Object> standing = checks.stream()
                .filter(c -> "varapi".equals(c.dependency()) && c.isLive())
                .map(c -> c.payload().orElse(Map.of()))
                .findFirst()
                .orElse(Map.of());

        // Either field can carry the bar: licenceStatus is the licence itself, lifecycleStatus
        // is the registry's disposition of the practitioner. A bar in either is a bar.
        for (String field : List.of("licenceStatus", "lifecycleStatus")) {
            Object raw = standing.get(field);
            if (raw == null) {
                continue;
            }
            String value = String.valueOf(raw).trim().toUpperCase(java.util.Locale.ROOT);
            if (BARRING_STATES.contains(value)) {
                return new LicenceVerdict(value, true);
            }
        }

        Object licence = standing.get("licenceStatus");
        if (licence == null || String.valueOf(licence).isBlank()) {
            return new LicenceVerdict("UNKNOWN", false);
        }
        return new LicenceVerdict(String.valueOf(licence).trim().toUpperCase(java.util.Locale.ROOT), false);
    }
}
