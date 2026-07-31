package zw.gov.mohcc.impilo.orgregistry.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.*;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The Regulatory Configuration Registry (NCZ-W1A).
 *
 * <p>Three layers, and this service governs the middle one: code owns the configuration language
 * and the execution engines; the regulator owns approved runtime definitions; every regulatory case
 * owns an immutable reference to the exact versions that governed it.</p>
 *
 * <p>What it replaces: {@code varapi.council_regulatory_configs} is one mutable row per council,
 * four opaque JSONB blobs overwritten in place by a destructive PUT. Under that shape the question
 * "what were the rules when this application was submitted?" has no answer, because the answer was
 * overwritten. Here it is answered by {@link #resolveForCase}.</p>
 *
 * <p>A release is a COMPLETE statement of a pack's configuration rather than a patch — the only
 * semantics under which a case's binding to a release fully determines the rules it was decided
 * under. {@link #cloneActiveRelease} exists so that making a small change does not mean restating
 * everything by hand.</p>
 */
@Service
public class RegulatoryConfigService {

    private static final Logger log = LoggerFactory.getLogger(RegulatoryConfigService.class);

    private static final Set<String> EDITABLE_RELEASE_STATES = Set.of("DRAFT", "IN_REVIEW");

    private final ConfigPackRepository packRepository;
    private final ConfigDefinitionRepository definitionRepository;
    private final ConfigDefinitionVersionRepository versionRepository;
    private final ConfigDefinitionTypeRepository typeRepository;
    private final ConfigDependencyRepository dependencyRepository;
    private final ConfigReleaseRepository releaseRepository;
    private final ConfigReleaseItemRepository releaseItemRepository;
    private final ConfigApprovalRepository approvalRepository;
    private final ConfigActivationRepository activationRepository;
    private final CaseConfigBindingRepository bindingRepository;
    private final CaseConfigPinRepository pinRepository;
    private final ConfigAuditEventRepository auditRepository;
    private final ConfigReleaseValidator validator;
    private final ConfigContentHasher hasher;
    private final OrgRegistryOutboxWriter outbox;
    private final ObjectMapper objectMapper;

    public RegulatoryConfigService(ConfigPackRepository packRepository,
                                   ConfigDefinitionRepository definitionRepository,
                                   ConfigDefinitionVersionRepository versionRepository,
                                   ConfigDefinitionTypeRepository typeRepository,
                                   ConfigDependencyRepository dependencyRepository,
                                   ConfigReleaseRepository releaseRepository,
                                   ConfigReleaseItemRepository releaseItemRepository,
                                   ConfigApprovalRepository approvalRepository,
                                   ConfigActivationRepository activationRepository,
                                   CaseConfigBindingRepository bindingRepository,
                                   CaseConfigPinRepository pinRepository,
                                   ConfigAuditEventRepository auditRepository,
                                   ConfigReleaseValidator validator,
                                   ConfigContentHasher hasher,
                                   OrgRegistryOutboxWriter outbox,
                                   ObjectMapper objectMapper) {
        this.packRepository = packRepository;
        this.definitionRepository = definitionRepository;
        this.versionRepository = versionRepository;
        this.typeRepository = typeRepository;
        this.dependencyRepository = dependencyRepository;
        this.releaseRepository = releaseRepository;
        this.releaseItemRepository = releaseItemRepository;
        this.approvalRepository = approvalRepository;
        this.activationRepository = activationRepository;
        this.bindingRepository = bindingRepository;
        this.pinRepository = pinRepository;
        this.auditRepository = auditRepository;
        this.validator = validator;
        this.hasher = hasher;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    // ── Catalogue ────────────────────────────────────────────────────────────────────────────

    public List<ConfigDefinitionTypeEntity> listTypes() {
        return typeRepository.findAllByOrderBySortOrderAsc();
    }

    // ── Packs ────────────────────────────────────────────────────────────────────────────────

    @Transactional
    public ConfigPackEntity createPack(UUID tenantId, UUID organizationId, String packKey,
                                       String label, String description, String actor) {
        return packRepository.findByTenantIdAndOrganizationIdAndPackKey(tenantId, organizationId, packKey)
                .orElseGet(() -> {
                    ConfigPackEntity pack = new ConfigPackEntity();
                    pack.setTenantId(tenantId);
                    pack.setOrganizationId(organizationId);
                    pack.setPackKey(packKey);
                    pack.setLabel(label);
                    pack.setDescription(description);
                    ConfigPackEntity saved = packRepository.save(pack);
                    audit(tenantId, saved, "PACK", saved.getId(), "created", actor, null, saved);
                    return saved;
                });
    }

    /**
     * Org-scoped configuration audit trail. This is configuration and pack lifecycle truth — not a
     * council desk of every register access. Callers must state that residual gap in the UI.
     */
    @Transactional(readOnly = true)
    public List<ConfigAuditEventEntity> listAuditEventsForOrganization(UUID tenantId, UUID organizationId) {
        return auditRepository.findByTenantIdAndOrganizationIdOrderByOccurredAtDesc(tenantId, organizationId);
    }

    public List<ConfigPackEntity> listPacks(UUID tenantId, UUID organizationId) {
        return packRepository.findByTenantIdAndOrganizationId(tenantId, organizationId);
    }

    public ConfigPackEntity requirePack(UUID tenantId, UUID packId) {
        return packRepository.findByTenantIdAndId(tenantId, packId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No configuration pack " + packId));
    }

    // ── Definitions and versions ─────────────────────────────────────────────────────────────

    /**
     * Author a DRAFT version. If the definition does not exist yet it is created — the definition is
     * a stable key, not a governed act in its own right; the governed act is approving a version.
     *
     * <p>Re-authoring the same {@code (definition, semanticVersion)} with different content is
     * refused rather than silently accepted: that is the checksum-immutability discipline, and it is
     * what stops a source pack edit from mutating rules a live case is pinned to. Re-authoring with
     * identical content is a no-op, so imports are idempotent.</p>
     */
    @Transactional
    public ConfigDefinitionVersionEntity authorVersion(UUID tenantId, UUID packId, String typeCode,
                                                       String definitionKey, String label,
                                                       String semanticVersion, JsonNode payload,
                                                       String selfServiceRoute, String policyStatus,
                                                       String policyDecisionRef, String sourcePackRef,
                                                       List<DependencySpec> dependencies, String actor) {
        requirePack(tenantId, packId);
        ConfigDefinitionTypeEntity type = typeRepository.findById(typeCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unknown definition type '" + typeCode + "'. Code owns the configuration "
                                + "language: a type with no engine behind it is configuration nobody "
                                + "executes."));


        ConfigDefinitionEntity definition = definitionRepository
                .findByTenantIdAndPackIdAndTypeCodeAndDefinitionKey(tenantId, packId, typeCode, definitionKey)
                .orElseGet(() -> {
                    ConfigDefinitionEntity created = new ConfigDefinitionEntity();
                    created.setTenantId(tenantId);
                    created.setPackId(packId);
                    created.setTypeCode(typeCode);
                    created.setDefinitionKey(definitionKey);
                    created.setLabel(label == null ? definitionKey : label);
                    return definitionRepository.save(created);
                });

        String contentHash = hasher.hash(payload);

        Optional<ConfigDefinitionVersionEntity> existing = versionRepository
                .findByTenantIdAndDefinitionIdAndSemanticVersion(tenantId, definition.getId(), semanticVersion);
        if (existing.isPresent()) {
            ConfigDefinitionVersionEntity current = existing.get();
            if (!current.getContentHash().equals(contentHash)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        definition.getTypeCode() + ":" + definitionKey + " version " + semanticVersion
                                + " is already recorded with different content. A change to a published "
                                + "version is a NEW version, never an edit — cases are pinned to this one.");
            }
            return current;
        }

        // Checked only once we know we are actually authoring something new: attempting to change a
        // published version is the more fundamental objection, and it must not be masked by this one.
        // V014 refuses both at the table too; the guards are here so a caller gets a clean 400
        // naming what to declare, rather than a 500 from a constraint they cannot see.
        if (type.isCarriesPolicyValue() && (policyStatus == null || policyStatus.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    typeCode + ":" + definitionKey + " carries a value only the regulator may set, so "
                            + "it must declare policyStatus — CONFIRMED, or PENDING_REGULATOR_APPROVAL "
                            + "with a decision reference. An undeclared value is indistinguishable "
                            + "from a configured one.");
        }
        if ("PENDING_REGULATOR_APPROVAL".equals(policyStatus)
                && (policyDecisionRef == null || policyDecisionRef.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    typeCode + ":" + definitionKey + " is pending a regulator decision but does not "
                            + "say which one. Reference the entry in the council decision register.");
        }

        ConfigDefinitionVersionEntity version = new ConfigDefinitionVersionEntity();
        version.setTenantId(tenantId);
        version.setDefinitionId(definition.getId());
        version.setSemanticVersion(semanticVersion);
        version.setPayload(payload);
        version.setContentHash(contentHash);
        version.setSelfServiceRoute(selfServiceRoute);
        version.setPolicyStatus(policyStatus);
        version.setPolicyDecisionRef(policyDecisionRef);
        version.setSourcePackRef(sourcePackRef);
        version.setAuthoredBy(actor);
        versionRepository.findByTenantIdAndDefinitionIdAndLifecycleState(tenantId, definition.getId(), "ACTIVE")
                .ifPresent(active -> version.setSupersedesVersionId(active.getId()));
        ConfigDefinitionVersionEntity saved = versionRepository.save(version);

        if (dependencies != null) {
            for (DependencySpec spec : dependencies) {
                ConfigDependencyEntity dependency = new ConfigDependencyEntity();
                dependency.setTenantId(tenantId);
                dependency.setDefinitionVersionId(saved.getId());
                dependency.setRequiredTypeCode(spec.typeCode());
                dependency.setRequiredDefinitionKey(spec.definitionKey());
                dependency.setOptional(spec.optional());
                dependencyRepository.save(dependency);
            }
        }

        audit(tenantId, null, "DEFINITION_VERSION", saved.getId(), "authored", actor, null, saved);
        return saved;
    }

    public record DependencySpec(String typeCode, String definitionKey, boolean optional) {
    }

    public List<ConfigDefinitionEntity> listDefinitions(UUID tenantId, UUID packId) {
        return definitionRepository.findByTenantIdAndPackId(tenantId, packId);
    }

    public List<ConfigDefinitionVersionEntity> listVersions(UUID tenantId, UUID definitionId) {
        return versionRepository.findByTenantIdAndDefinitionIdOrderByCreatedAtDesc(tenantId, definitionId);
    }

    // ── Releases ─────────────────────────────────────────────────────────────────────────────

    @Transactional
    public ConfigReleaseEntity createRelease(UUID tenantId, UUID packId, String releaseKey,
                                             String label, String notes, String actor) {
        requirePack(tenantId, packId);
        ConfigReleaseEntity release = new ConfigReleaseEntity();
        release.setTenantId(tenantId);
        release.setPackId(packId);
        release.setReleaseKey(releaseKey);
        release.setLabel(label);
        release.setNotes(notes);
        ConfigReleaseEntity saved = releaseRepository.save(release);
        audit(tenantId, null, "RELEASE", saved.getId(), "created", actor, null, saved);
        return saved;
    }

    /**
     * Start a new release from the currently active one. Because a release is a complete statement,
     * this is how an ordinary small change is made: clone, replace the one definition that changed,
     * submit.
     */
    @Transactional
    public ConfigReleaseEntity cloneActiveRelease(UUID tenantId, UUID packId, String releaseKey,
                                                  String label, String actor) {
        ConfigReleaseEntity created = createRelease(tenantId, packId, releaseKey, label,
                "Cloned from the active release", actor);
        activeRelease(tenantId, packId).ifPresent(active ->
                releaseItemRepository.findByReleaseId(active.getId()).forEach(item ->
                        addItem(tenantId, created.getId(), item.getDefinitionVersionId(), actor)));
        return created;
    }

    @Transactional
    public ConfigReleaseItemEntity addItem(UUID tenantId, UUID releaseId, UUID versionId, String actor) {
        ConfigReleaseEntity release = requireRelease(tenantId, releaseId);
        requireEditable(release);
        ConfigDefinitionVersionEntity version = versionRepository.findByTenantIdAndId(tenantId, versionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No definition version " + versionId));

        // Replacing a definition already in this release is the ordinary edit — swapping the
        // version on the existing row rather than delete-then-insert, because Hibernate orders
        // inserts before deletes at flush and the (release, definition) unique index would reject
        // the pair. Updating in place is both correct and one statement.
        ConfigReleaseItemEntity item = releaseItemRepository
                .findByReleaseIdAndDefinitionId(releaseId, version.getDefinitionId())
                .orElseGet(() -> {
                    ConfigReleaseItemEntity fresh = new ConfigReleaseItemEntity();
                    fresh.setTenantId(tenantId);
                    fresh.setReleaseId(releaseId);
                    fresh.setDefinitionId(version.getDefinitionId());
                    return fresh;
                });
        item.setDefinitionVersionId(versionId);
        return releaseItemRepository.save(item);
    }

    @Transactional
    public ConfigReleaseEntity submitForReview(UUID tenantId, UUID releaseId, String actor) {
        ConfigReleaseEntity release = requireRelease(tenantId, releaseId);
        if (!"DRAFT".equals(release.getLifecycleState())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only a DRAFT release may be submitted for review; this one is "
                            + release.getLifecycleState() + ".");
        }
        release.setLifecycleState("IN_REVIEW");
        release.setSubmittedBy(actor);
        release.setSubmittedAt(OffsetDateTime.now());
        ConfigReleaseEntity saved = releaseRepository.save(release);
        audit(tenantId, null, "RELEASE", saved.getId(), "submitted_for_review", actor, null, saved);
        return saved;
    }

    /**
     * Record one approver's decision. Four-eyes is enforced by the database — a trigger refuses an
     * approval from the person who submitted the release, and a unique index refuses a second
     * decision from the same approver — so those refusals are translated here rather than
     * re-checked, and no code path can reach the table without them.
     */
    @Transactional
    public ConfigApprovalEntity recordApproval(UUID tenantId, UUID releaseId, String approverHealthId,
                                               String approverRole, String decision, String note) {
        ConfigReleaseEntity release = requireRelease(tenantId, releaseId);
        if (!"IN_REVIEW".equals(release.getLifecycleState())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Approvals may only be recorded while a release is IN_REVIEW; this one is "
                            + release.getLifecycleState() + ".");
        }
        if (approverHealthId == null || approverHealthId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An approver identity is required");
        }
        if (approverHealthId.equals(release.getSubmittedBy())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Four-eyes rule: " + approverHealthId + " submitted this configuration release "
                            + "and cannot approve it.");
        }
        if (approvalRepository.existsByReleaseIdAndApproverHealthId(releaseId, approverHealthId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    approverHealthId + " has already recorded a decision on this release.");
        }

        ConfigApprovalEntity approval = new ConfigApprovalEntity();
        approval.setTenantId(tenantId);
        approval.setReleaseId(releaseId);
        approval.setApproverHealthId(approverHealthId);
        approval.setApproverRole(approverRole);
        approval.setDecision(decision == null ? "APPROVE" : decision.toUpperCase());
        approval.setNote(note);
        ConfigApprovalEntity saved = approvalRepository.save(approval);
        audit(tenantId, null, "RELEASE", releaseId, "approval_recorded", approverHealthId, null, saved);
        return saved;
    }

    /** Validate without changing anything — the preview a regulator sees before committing. */
    public ConfigReleaseValidator.ValidationReport preview(UUID tenantId, UUID releaseId) {
        ConfigReleaseEntity release = requireRelease(tenantId, releaseId);
        return validator.validate(release);
    }

    /**
     * Approve a release: promote its versions, then validate. Promotion happens first because a
     * version's approval is part of what is being decided; if validation then fails, the whole
     * transaction rolls back and nothing was approved.
     */
    @Transactional
    public ConfigReleaseValidator.ValidationReport approve(UUID tenantId, UUID releaseId, String actor) {
        ConfigReleaseEntity release = requireRelease(tenantId, releaseId);
        if (!"IN_REVIEW".equals(release.getLifecycleState())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only a release IN_REVIEW may be approved; this one is "
                            + release.getLifecycleState() + ".");
        }

        for (ConfigReleaseItemEntity item : releaseItemRepository.findByReleaseId(releaseId)) {
            versionRepository.findById(item.getDefinitionVersionId()).ifPresent(version -> {
                if ("DRAFT".equals(version.getLifecycleState())) {
                    version.setLifecycleState("APPROVED");
                    version.setApprovedAt(OffsetDateTime.now());
                    versionRepository.save(version);
                }
            });
        }

        ConfigReleaseValidator.ValidationReport report = validator.validate(release);
        if (!report.valid()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "This release cannot be approved: " + summarise(report));
        }
        release.setLifecycleState("APPROVED");
        releaseRepository.save(release);
        audit(tenantId, null, "RELEASE", releaseId, "approved", actor, null, report);
        return report;
    }

    /**
     * Take a release live. Refuses on any validation error, supersedes the previous active release
     * and its replaced versions, and records the validation report that justified the activation.
     */
    @Transactional
    public ConfigActivationEntity activate(UUID tenantId, UUID releaseId, String actor,
                                           String correlationId) {
        ConfigReleaseEntity release = requireRelease(tenantId, releaseId);
        if (!Set.of("APPROVED", "SCHEDULED").contains(release.getLifecycleState())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only an approved or scheduled release may be activated; this one is "
                            + release.getLifecycleState() + ".");
        }

        ConfigReleaseValidator.ValidationReport report = validator.validate(release);
        if (!report.valid()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "This release cannot be activated: " + summarise(report));
        }

        Optional<ConfigReleaseEntity> previous = activeRelease(tenantId, release.getPackId());
        List<ConfigReleaseItemEntity> items = releaseItemRepository.findByReleaseId(releaseId);

        // Supersede first: the database permits only one ACTIVE version per definition, so the
        // outgoing version must step down before the incoming one steps up.
        for (ConfigReleaseItemEntity item : items) {
            versionRepository
                    .findByTenantIdAndDefinitionIdAndLifecycleState(tenantId, item.getDefinitionId(), "ACTIVE")
                    .filter(active -> !active.getId().equals(item.getDefinitionVersionId()))
                    .ifPresent(active -> {
                        active.setLifecycleState("SUPERSEDED");
                        versionRepository.save(active);
                    });
        }
        versionRepository.flush();

        previous.ifPresent(prior -> {
            prior.setLifecycleState("SUPERSEDED");
            prior.setRetiredAt(OffsetDateTime.now());
            prior.setRetiredBy(actor);
            releaseRepository.save(prior);
        });
        releaseRepository.flush();

        for (ConfigReleaseItemEntity item : items) {
            versionRepository.findById(item.getDefinitionVersionId()).ifPresent(version -> {
                if (!"ACTIVE".equals(version.getLifecycleState())) {
                    version.setLifecycleState("ACTIVE");
                    versionRepository.save(version);
                }
            });
        }

        release.setLifecycleState("ACTIVE");
        release.setActivatedAt(OffsetDateTime.now());
        release.setActivatedBy(actor);
        releaseRepository.save(release);

        ConfigActivationEntity activation = new ConfigActivationEntity();
        activation.setTenantId(tenantId);
        activation.setPackId(release.getPackId());
        activation.setReleaseId(releaseId);
        activation.setPreviousReleaseId(previous.map(ConfigReleaseEntity::getId).orElse(null));
        activation.setActivatedBy(actor);
        activation.setValidationReport(objectMapper.valueToTree(report));
        activation.setCorrelationId(correlationId);
        ConfigActivationEntity saved = activationRepository.save(activation);

        audit(tenantId, null, "RELEASE", releaseId, "activated", actor, null, report);
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("packId", release.getPackId());
            // Whose configuration this is. Without it a consumer can only learn the subject of the
            // event by calling back for the pack, which makes every consumer depend on org-registry
            // being reachable to interpret an event org-registry already had in hand.
            payload.put("organisationId", packRepository.findByTenantIdAndId(tenantId, release.getPackId())
                    .map(ConfigPackEntity::getOrganizationId).orElse(null));
            payload.put("releaseId", releaseId);
            payload.put("releaseKey", release.getReleaseKey());
            payload.put("previousReleaseId", activation.getPreviousReleaseId());
            payload.put("itemCount", report.itemCount());
            payload.put("warnings", report.warnings().size());
            // A release reaches ACTIVE at most once (approve requires IN_REVIEW, activate requires
            // APPROVED or SCHEDULED), so the release id alone is a sound idempotency key here.
            outbox.publish(tenantId, "REGULATORY_CONFIG_RELEASE", releaseId.toString(),
                    "regulatory_config_release", "activated",
                    "org-registry:config-release:" + releaseId + ":activated", payload);
        } catch (Exception ex) {
            log.warn("Configuration release {} activated but its event could not be written", releaseId, ex);
        }
        return saved;
    }

    public Optional<ConfigReleaseEntity> activeRelease(UUID tenantId, UUID packId) {
        return releaseRepository.findByTenantIdAndPackIdAndLifecycleState(tenantId, packId, "ACTIVE");
    }

    public List<ConfigReleaseEntity> listReleases(UUID tenantId, UUID packId) {
        return releaseRepository.findByTenantIdAndPackIdOrderByCreatedAtDesc(tenantId, packId);
    }

    // ── Case bindings: the point of the whole exercise ───────────────────────────────────────

    /**
     * Pin a case to the configuration that is active NOW. Idempotent by construction and by
     * intent: a case binds once, and asking again returns the original binding rather than moving
     * the case onto a newer release. That refusal is the guarantee — without it, activating a
     * release would silently rewrite the rules that live cases are being decided under.
     */
    @Transactional
    public CaseConfigBindingEntity bindCase(UUID tenantId, String caseSystem, String caseType,
                                            String caseRef, UUID organizationId, String actor,
                                            String correlationId) {
        Optional<CaseConfigBindingEntity> existing = bindingRepository
                .findByTenantIdAndCaseSystemAndCaseTypeAndCaseRef(tenantId, caseSystem, caseType, caseRef);
        if (existing.isPresent()) {
            return existing.get();
        }

        List<ConfigPackEntity> packs = packRepository.findByTenantIdAndOrganizationId(tenantId, organizationId);
        ConfigReleaseEntity active = packs.stream()
                .map(pack -> activeRelease(tenantId, pack.getId()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "This regulator has no active configuration release, so a case cannot be "
                                + "opened against it. Activate a release first."));

        CaseConfigBindingEntity binding = new CaseConfigBindingEntity();
        binding.setTenantId(tenantId);
        binding.setCaseSystem(caseSystem);
        binding.setCaseType(caseType);
        binding.setCaseRef(caseRef);
        binding.setOrganizationId(organizationId);
        binding.setPackId(active.getPackId());
        binding.setReleaseId(active.getId());
        binding.setBoundBy(actor);
        binding.setCorrelationId(correlationId);
        CaseConfigBindingEntity saved = bindingRepository.save(binding);
        audit(tenantId, null, "CASE_BINDING", saved.getId(), "bound", actor, null, saved);
        return saved;
    }

    /**
     * Pin one definition at a moment later than case creation — a penalty policy at liability
     * assessment, a certificate template at issuance. Naming the moment is more honest than
     * assuming every rule was fixed on the day the application arrived.
     */
    @Transactional
    public CaseConfigPinEntity pinDefinition(UUID tenantId, UUID bindingId, String typeCode,
                                             String definitionKey, String moment, String actor) {
        CaseConfigBindingEntity binding = bindingRepository.findByTenantIdAndId(tenantId, bindingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No case configuration binding " + bindingId));

        ConfigDefinitionEntity definition = definitionRepository
                .findByTenantIdAndPackIdAndTypeCodeAndDefinitionKey(
                        tenantId, binding.getPackId(), typeCode, definitionKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No definition " + typeCode + ":" + definitionKey + " in this pack"));

        ConfigDefinitionVersionEntity version = versionRepository
                .findByTenantIdAndDefinitionIdAndLifecycleState(tenantId, definition.getId(), "ACTIVE")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        typeCode + ":" + definitionKey + " has no active version to pin."));

        CaseConfigPinEntity pin = new CaseConfigPinEntity();
        pin.setTenantId(tenantId);
        pin.setBindingId(bindingId);
        pin.setDefinitionId(definition.getId());
        pin.setDefinitionVersionId(version.getId());
        pin.setPinnedAtMoment(moment);
        pin.setPinnedBy(actor);
        CaseConfigPinEntity saved = pinRepository.save(pin);
        audit(tenantId, null, "CASE_PIN", saved.getId(), "pinned", actor, null, saved);
        return saved;
    }

    /** One resolved definition as a case sees it. */
    public record ResolvedDefinition(String typeCode, String definitionKey, String label,
                                     String semanticVersion, JsonNode payload, String contentHash,
                                     String selfServiceRoute, String policyStatus,
                                     String policyDecisionRef, String pinnedAtMoment) {
    }

    /**
     * The rules that governed a case — the question {@code council_regulatory_configs} could not
     * answer. Resolves from the case's bound release, with any later pin taking precedence for its
     * own definition. Never consults what happens to be active today.
     */
    public List<ResolvedDefinition> resolveForCase(UUID tenantId, String caseSystem, String caseType,
                                                   String caseRef) {
        CaseConfigBindingEntity binding = bindingRepository
                .findByTenantIdAndCaseSystemAndCaseTypeAndCaseRef(tenantId, caseSystem, caseType, caseRef)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Case " + caseSystem + "/" + caseType + "/" + caseRef
                                + " is not bound to a configuration release."));

        Map<UUID, String> pinnedMoments = new HashMap<>();
        Map<UUID, UUID> pinnedVersions = new LinkedHashMap<>();
        for (CaseConfigPinEntity pin : pinRepository.findByBindingId(binding.getId())) {
            pinnedVersions.put(pin.getDefinitionId(), pin.getDefinitionVersionId());
            pinnedMoments.put(pin.getDefinitionId(), pin.getPinnedAtMoment());
        }

        Map<UUID, UUID> effective = new LinkedHashMap<>();
        for (ConfigReleaseItemEntity item : releaseItemRepository.findByReleaseId(binding.getReleaseId())) {
            effective.put(item.getDefinitionId(),
                    pinnedVersions.getOrDefault(item.getDefinitionId(), item.getDefinitionVersionId()));
        }
        pinnedVersions.forEach(effective::putIfAbsent);

        List<ResolvedDefinition> resolved = new ArrayList<>();
        effective.forEach((definitionId, versionId) -> {
            ConfigDefinitionEntity definition = definitionRepository.findById(definitionId).orElse(null);
            ConfigDefinitionVersionEntity version = versionRepository.findById(versionId).orElse(null);
            if (definition == null || version == null) {
                return;
            }
            resolved.add(new ResolvedDefinition(definition.getTypeCode(), definition.getDefinitionKey(),
                    definition.getLabel(), version.getSemanticVersion(), version.getPayload(),
                    version.getContentHash(), version.getSelfServiceRoute(), version.getPolicyStatus(),
                    version.getPolicyDecisionRef(), pinnedMoments.get(definitionId)));
        });
        return resolved;
    }

    public Optional<CaseConfigBindingEntity> findBinding(UUID tenantId, String caseSystem,
                                                         String caseType, String caseRef) {
        return bindingRepository
                .findByTenantIdAndCaseSystemAndCaseTypeAndCaseRef(tenantId, caseSystem, caseType, caseRef);
    }

    /** What a NEW case would get: the pack's currently active configuration. */
    public List<ResolvedDefinition> resolveActive(UUID tenantId, UUID packId) {
        ConfigReleaseEntity active = activeRelease(tenantId, packId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "This pack has no active configuration release."));
        List<ResolvedDefinition> resolved = new ArrayList<>();
        for (ConfigReleaseItemEntity item : releaseItemRepository.findByReleaseId(active.getId())) {
            ConfigDefinitionEntity definition = definitionRepository.findById(item.getDefinitionId()).orElse(null);
            ConfigDefinitionVersionEntity version =
                    versionRepository.findById(item.getDefinitionVersionId()).orElse(null);
            if (definition == null || version == null) {
                continue;
            }
            resolved.add(new ResolvedDefinition(definition.getTypeCode(), definition.getDefinitionKey(),
                    definition.getLabel(), version.getSemanticVersion(), version.getPayload(),
                    version.getContentHash(), version.getSelfServiceRoute(), version.getPolicyStatus(),
                    version.getPolicyDecisionRef(), null));
        }
        return resolved;
    }

    // ── Internals ────────────────────────────────────────────────────────────────────────────

    private ConfigReleaseEntity requireRelease(UUID tenantId, UUID releaseId) {
        return releaseRepository.findByTenantIdAndId(tenantId, releaseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No configuration release " + releaseId));
    }

    private void requireEditable(ConfigReleaseEntity release) {
        if (!EDITABLE_RELEASE_STATES.contains(release.getLifecycleState())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Release " + release.getReleaseKey() + " is " + release.getLifecycleState()
                            + " — its contents are frozen. Clone it into a new release instead.");
        }
    }

    private static String summarise(ConfigReleaseValidator.ValidationReport report) {
        return String.join("; ", report.errors().stream()
                .map(f -> f.code() + " (" + f.subject() + ") — " + f.message()).toList());
    }

    private void audit(UUID tenantId, ConfigPackEntity pack, String subjectType, UUID subjectId,
                       String eventType, String actor, Object before, Object after) {
        try {
            ConfigAuditEventEntity event = new ConfigAuditEventEntity();
            event.setTenantId(tenantId);
            event.setOrganizationId(pack != null ? pack.getOrganizationId() : null);
            event.setPackId(pack != null ? pack.getId() : null);
            event.setSubjectType(subjectType);
            event.setSubjectId(subjectId);
            event.setEventType(eventType);
            event.setActorHealthId(actor);
            event.setBeforeState(before == null ? null : objectMapper.valueToTree(before));
            event.setAfterState(after == null ? null : objectMapper.valueToTree(after));
            auditRepository.save(event);
        } catch (Exception ex) {
            // An audit failure must never silently swallow the governed act it describes, but it
            // must also not roll one back — log loudly and let the caller's transaction stand.
            log.error("Failed to write configuration audit event {} for {} {}",
                    eventType, subjectType, subjectId, ex);
        }
    }

}
