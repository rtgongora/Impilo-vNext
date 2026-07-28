package zw.gov.mohcc.impilo.orgregistry.core;

import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.*;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.*;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Decides whether a configuration release may go live.
 *
 * <p>A release is a COMPLETE statement of a pack's active configuration, not a patch. That is a
 * deliberate choice: it is the only semantics under which binding a case to a release fully
 * determines the rules that governed it. Building a new release therefore starts by cloning the
 * active one ({@code RegulatoryConfigService.cloneActiveRelease}), and validation can resolve every
 * reference within the release itself rather than against a moving background.</p>
 *
 * <p>The distinction this validator draws most carefully is between an unfinished release and an
 * undecided policy. A workflow that points at a form nobody wrote is an ERROR — the configuration
 * is incoherent and activating it would produce a broken journey. A fee schedule whose amount the
 * Council has not yet set is a WARNING — the seam is built, the value is honestly absent, and
 * blocking activation would mean a council could not go live until every policy question was
 * answered. Implementation readiness and policy activation readiness are different things.</p>
 */
@Component
public class ConfigReleaseValidator {

    /** Lifecycle states a version may be in when its release activates. */
    private static final Set<String> ACTIVATABLE_STATES = Set.of("APPROVED", "ACTIVE");

    private final ConfigReleaseItemRepository releaseItemRepository;
    private final ConfigDefinitionVersionRepository versionRepository;
    private final ConfigDefinitionRepository definitionRepository;
    private final ConfigDefinitionTypeRepository typeRepository;
    private final ConfigDependencyRepository dependencyRepository;
    private final ConfigApprovalRepository approvalRepository;

    public ConfigReleaseValidator(ConfigReleaseItemRepository releaseItemRepository,
                                  ConfigDefinitionVersionRepository versionRepository,
                                  ConfigDefinitionRepository definitionRepository,
                                  ConfigDefinitionTypeRepository typeRepository,
                                  ConfigDependencyRepository dependencyRepository,
                                  ConfigApprovalRepository approvalRepository) {
        this.releaseItemRepository = releaseItemRepository;
        this.versionRepository = versionRepository;
        this.definitionRepository = definitionRepository;
        this.typeRepository = typeRepository;
        this.dependencyRepository = dependencyRepository;
        this.approvalRepository = approvalRepository;
    }

    /** A finding against a release. {@code code} is stable so tests and UI can key on it. */
    public record Finding(String code, String severity, String subject, String message) {
    }

    /**
     * The outcome of validating a release. Stored verbatim on the activation record, because the
     * question asked later is "what did we check before activating?", and re-running today's
     * validator against today's data does not answer it.
     */
    public record ValidationReport(boolean valid, List<Finding> errors, List<Finding> warnings,
                                   int itemCount, boolean fourEyesRequired, int approvalCount) {
    }

    public ValidationReport validate(ConfigReleaseEntity release) {
        List<Finding> errors = new ArrayList<>();
        List<Finding> warnings = new ArrayList<>();

        List<ConfigReleaseItemEntity> items = releaseItemRepository.findByReleaseId(release.getId());
        if (items.isEmpty()) {
            errors.add(new Finding("RELEASE_EMPTY", "ERROR", release.getReleaseKey(),
                    "A release must contain at least one definition version."));
            return new ValidationReport(false, errors, warnings, 0, false, 0);
        }

        List<UUID> versionIds = items.stream().map(ConfigReleaseItemEntity::getDefinitionVersionId).toList();
        Map<UUID, ConfigDefinitionVersionEntity> versions = new HashMap<>();
        versionRepository.findByIdIn(versionIds).forEach(v -> versions.put(v.getId(), v));

        Map<UUID, ConfigDefinitionEntity> definitions = new HashMap<>();
        definitionRepository.findByTenantIdAndPackId(release.getTenantId(), release.getPackId())
                .forEach(d -> definitions.put(d.getId(), d));

        Map<String, ConfigDefinitionTypeEntity> types = new HashMap<>();
        typeRepository.findAll().forEach(t -> types.put(t.getTypeCode(), t));

        // Every (type, key) this release provides — the resolution universe for dependencies.
        Set<String> provided = new HashSet<>();
        // Definitions whose policy value the regulator has not yet set. Graded below by whether
        // anything an applicant can actually reach depends on them.
        Map<UUID, ConfigDefinitionVersionEntity> unsetPolicies = new LinkedHashMap<>();
        boolean fourEyesRequired = false;

        for (ConfigReleaseItemEntity item : items) {
            ConfigDefinitionVersionEntity version = versions.get(item.getDefinitionVersionId());
            ConfigDefinitionEntity definition = definitions.get(item.getDefinitionId());

            if (version == null) {
                errors.add(new Finding("VERSION_MISSING", "ERROR", item.getDefinitionVersionId().toString(),
                        "The release references a definition version that no longer exists."));
                continue;
            }
            if (definition == null) {
                errors.add(new Finding("DEFINITION_FOREIGN", "ERROR", item.getDefinitionId().toString(),
                        "The release references a definition that does not belong to its pack."));
                continue;
            }
            if (!version.getDefinitionId().equals(definition.getId())) {
                errors.add(new Finding("ITEM_MISMATCH", "ERROR", definition.getDefinitionKey(),
                        "The release item pairs a version with a definition it does not belong to."));
                continue;
            }

            provided.add(key(definition.getTypeCode(), definition.getDefinitionKey()));

            if (!ACTIVATABLE_STATES.contains(version.getLifecycleState())) {
                errors.add(new Finding("VERSION_NOT_APPROVED", "ERROR", definition.getDefinitionKey(),
                        "Version " + version.getSemanticVersion() + " is " + version.getLifecycleState()
                                + " — only an approved version may be activated."));
            }

            ConfigDefinitionTypeEntity type = types.get(definition.getTypeCode());
            if (type == null) {
                errors.add(new Finding("TYPE_UNKNOWN", "ERROR", definition.getTypeCode(),
                        "The definition declares a type the platform has no engine for."));
                continue;
            }
            if (type.isRequiresFourEyes()) {
                fourEyesRequired = true;
            }

            // The no-regulator-only-capability rule: an applicant-facing capability with nowhere for
            // an applicant to reach it is a half-built service, and it fails activation.
            if (type.isApplicantFacing()
                    && (version.getSelfServiceRoute() == null || version.getSelfServiceRoute().isBlank())) {
                errors.add(new Finding("NO_SELF_SERVICE_PROJECTION", "ERROR", definition.getDefinitionKey(),
                        "'" + definition.getLabel() + "' is applicant-facing but declares no self-service "
                                + "route. Every applicant-facing action needs a usable applicant projection."));
            }

            // Backstop for the write-time guard in V014. A definition whose type carries a
            // regulator-only value must DECLARE whether that value is set; an undeclared value is
            // indistinguishable from a configured one, which would let a release read as complete
            // while a council still owes a decision on it.
            if (type.isCarriesPolicyValue() && version.getPolicyStatus() == null) {
                errors.add(new Finding("POLICY_STATUS_UNDECLARED", "ERROR", definition.getDefinitionKey(),
                        "'" + definition.getLabel() + "' carries a value only the regulator may set, "
                                + "but does not declare whether it is set. Declare CONFIRMED, or "
                                + "PENDING_REGULATOR_APPROVAL with a decision reference."));
            }
            if ("PENDING_REGULATOR_APPROVAL".equals(version.getPolicyStatus())
                    && (version.getPolicyDecisionRef() == null
                        || version.getPolicyDecisionRef().isBlank())) {
                errors.add(new Finding("POLICY_DECISION_REF_MISSING", "ERROR",
                        definition.getDefinitionKey(),
                        "'" + definition.getLabel() + "' is pending a regulator decision but does not "
                                + "say which one. Reference the council decision register."));
            }
            if ("PENDING_REGULATOR_APPROVAL".equals(version.getPolicyStatus())) {
                unsetPolicies.put(definition.getId(), version);
            }
        }

        // Referential integrity across the whole set.
        List<ConfigDependencyEntity> dependencies = dependencyRepository.findByDefinitionVersionIdIn(versionIds);
        for (ConfigDependencyEntity dependency : dependencies) {
            if (provided.contains(key(dependency.getRequiredTypeCode(), dependency.getRequiredDefinitionKey()))) {
                continue;
            }
            ConfigDefinitionVersionEntity from = versions.get(dependency.getDefinitionVersionId());
            ConfigDefinitionEntity fromDefinition =
                    from == null ? null : definitions.get(from.getDefinitionId());
            String subject = fromDefinition == null ? "unknown" : fromDefinition.getDefinitionKey();
            String required = dependency.getRequiredTypeCode() + ":" + dependency.getRequiredDefinitionKey();

            if (dependency.isOptional()) {
                warnings.add(new Finding("OPTIONAL_DEPENDENCY_UNRESOLVED", "WARNING", subject,
                        "'" + subject + "' optionally references " + required + ", which this release "
                                + "does not contain."));
            } else {
                errors.add(new Finding("DEPENDENCY_UNRESOLVED", "ERROR", subject,
                        "'" + subject + "' references " + required + ", which this release does not "
                                + "contain. A release is a complete statement of the pack's configuration."));
            }
        }

        gradeUnsetPolicies(unsetPolicies, items, versions, definitions, types, dependencies, warnings);

        // Four-eyes. Fees, penalties, register-entry rules and decision workflows need two people.
        List<ConfigApprovalEntity> approvals = approvalRepository.findByReleaseId(release.getId());
        long rejections = approvals.stream().filter(a -> "REJECT".equals(a.getDecision())).count();
        long approvalCount = approvals.stream().filter(a -> "APPROVE".equals(a.getDecision())).count();

        if (rejections > 0) {
            errors.add(new Finding("RELEASE_REJECTED", "ERROR", release.getReleaseKey(),
                    rejections + " approver(s) rejected this release."));
        }
        int required = fourEyesRequired ? 2 : 1;
        if (approvalCount < required) {
            errors.add(new Finding("INSUFFICIENT_APPROVALS", "ERROR", release.getReleaseKey(),
                    "This release changes " + (fourEyesRequired
                            ? "fees, penalties, register-entry rules or decision workflows, so it needs "
                            : "configuration, so it needs ")
                            + required + " approval(s); it has " + approvalCount + "."));
        }

        return new ValidationReport(errors.isEmpty(), errors, warnings, items.size(),
                fourEyesRequired, (int) approvalCount);
    }

    /**
     * Not every unset value is alike, and treating them alike is what makes a warning ignorable.
     *
     * <p>The CPD rules are unset and deliberately inert — nothing is gated on them yet. The student
     * index fee is unset and sits on the critical path of a registration journey this very release
     * puts in front of applicants. Both were previously a single undifferentiated line.</p>
     *
     * <p>This walks the mandatory-dependency graph outward from every applicant-facing definition in
     * the release. An unset value that anything reachable that way depends on gets a distinct code
     * and names the journeys it affects, so a regulator activates knowing which of its own services
     * cannot complete yet — rather than approving a one-liner.</p>
     */
    private void gradeUnsetPolicies(Map<UUID, ConfigDefinitionVersionEntity> unsetPolicies,
                                    List<ConfigReleaseItemEntity> items,
                                    Map<UUID, ConfigDefinitionVersionEntity> versions,
                                    Map<UUID, ConfigDefinitionEntity> definitions,
                                    Map<String, ConfigDefinitionTypeEntity> types,
                                    List<ConfigDependencyEntity> dependencies,
                                    List<Finding> warnings) {
        if (unsetPolicies.isEmpty()) {
            return;
        }

        // (type, key) -> definition id, for the definitions this release actually contains.
        Map<String, UUID> byKey = new HashMap<>();
        for (ConfigReleaseItemEntity item : items) {
            ConfigDefinitionEntity definition = definitions.get(item.getDefinitionId());
            if (definition != null) {
                byKey.put(key(definition.getTypeCode(), definition.getDefinitionKey()), definition.getId());
            }
        }

        // definition id -> the definitions it mandatorily depends on.
        Map<UUID, List<UUID>> edges = new HashMap<>();
        for (ConfigDependencyEntity dependency : dependencies) {
            if (dependency.isOptional()) {
                continue;
            }
            ConfigDefinitionVersionEntity from = versions.get(dependency.getDefinitionVersionId());
            if (from == null) {
                continue;
            }
            UUID target = byKey.get(key(dependency.getRequiredTypeCode(),
                    dependency.getRequiredDefinitionKey()));
            if (target != null) {
                edges.computeIfAbsent(from.getDefinitionId(), k -> new ArrayList<>()).add(target);
            }
        }

        // Walk out from each applicant-facing definition, recording which journeys reach what.
        Map<UUID, Set<String>> reachedBy = new HashMap<>();
        for (ConfigReleaseItemEntity item : items) {
            ConfigDefinitionEntity root = definitions.get(item.getDefinitionId());
            if (root == null) {
                continue;
            }
            ConfigDefinitionTypeEntity type = types.get(root.getTypeCode());
            if (type == null || !type.isApplicantFacing()) {
                continue;
            }
            Set<UUID> seen = new HashSet<>();
            Deque<UUID> queue = new ArrayDeque<>();
            queue.add(root.getId());
            while (!queue.isEmpty()) {
                UUID current = queue.poll();
                if (!seen.add(current)) {
                    continue;
                }
                // A definition does not "depend on itself". An applicant-facing leaf with an unset
                // value shows an applicant one not-configured screen; it does not publish a journey
                // they cannot finish. Only a declared dependency makes it the latter, so the
                // dependency graph is the sole authority here.
                if (!current.equals(root.getId())) {
                    reachedBy.computeIfAbsent(current, k -> new LinkedHashSet<>()).add(root.getLabel());
                }
                edges.getOrDefault(current, List.of()).forEach(queue::add);
            }
        }

        unsetPolicies.forEach((definitionId, version) -> {
            ConfigDefinitionEntity definition = definitions.get(definitionId);
            if (definition == null) {
                return;
            }
            String pending = version.getPolicyDecisionRef() == null
                    ? "a regulator decision" : version.getPolicyDecisionRef();
            Set<String> journeys = reachedBy.getOrDefault(definitionId, Set.of());

            if (journeys.isEmpty()) {
                warnings.add(new Finding("POLICY_VALUE_UNSET", "WARNING", definition.getDefinitionKey(),
                        "'" + definition.getLabel() + "' is built and its value is not set (pending "
                                + pending + "). Nothing an applicant can reach depends on it yet, so "
                                + "this release does not expose the gap."));
            } else {
                warnings.add(new Finding("POLICY_VALUE_UNSET_ON_CRITICAL_PATH", "WARNING",
                        definition.getDefinitionKey(),
                        "'" + definition.getLabel() + "' is built and its value is not set (pending "
                                + pending + "), and " + String.join(", ", journeys)
                                + " depends on it. Activating this release publishes "
                                + (journeys.size() == 1 ? "a journey" : "journeys")
                                + " an applicant can start but cannot complete until the decision is "
                                + "made. The capability will surface as not configured rather than "
                                + "guess a value."));
            }
        });
    }

    private static String key(String typeCode, String definitionKey) {
        return typeCode + "\0" + definitionKey;
    }
}
