package zw.gov.mohcc.impilo.varapi.core;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.varapi.integration.OrgRegistryConfigClient;
import zw.gov.mohcc.impilo.varapi.persistence.entity.CouncilEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProfessionalRegisterEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.CouncilRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProfessionalRegisterRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Materialises a council's statutory registers from its ACTIVE regulatory configuration (task #97).
 *
 * <p><strong>Why this exists.</strong> V039 created NCZ's four standing registers with literal
 * {@code INSERT} statements. That made registers <em>code</em>: a second council needed a migration
 * to get its own, so the claim that "a council is a configuration pack" was true of everything
 * except the registers themselves. The NCZ pack has shipped four {@code REGISTER} definitions as
 * configuration since NCZ-W1B and nothing consumed them. This closes that loop, and it is the last
 * item in Wave 1's premise rather than a new feature.</p>
 *
 * <p><strong>The four rules.</strong></p>
 * <ol>
 *   <li><em>Idempotent.</em> Re-running changes nothing. Matching is on
 *       {@code (tenant, council, register_code)}.</li>
 *   <li><em>A register is never deleted.</em> One dropped from a pack is retired. The database
 *       refuses the delete outright (V043), so this is a guarantee rather than a convention.</li>
 *   <li><em>{@code statutory_title} stays NULL unless the pack states it.</em> A title reaches
 *       certificates and cannot be quietly corrected, so it is carried, never inferred.</li>
 *   <li><em>Only an ACTIVE release materialises.</em> A DRAFT pack must not create registers —
 *       that would let a deploy produce registers nobody approved.</li>
 * </ol>
 *
 * <p><strong>The register code is composed, not copied.</strong> The pack declares
 * {@code registerCode: "STUDENT"}; V030 and V039 seeded {@code NCZ_STUDENT}, and every one of the
 * nine seeded councils uses the same {@code {councilCode}_{code}} form. Copying the pack's code
 * verbatim would not reconcile NCZ's existing registers — it would create four more, giving NCZ
 * eight standing registers where it has four. The PCZ fixture would not catch that, because it only
 * inspects PCZ. So the code is composed here, and a test asserts NCZ still has exactly four.</p>
 *
 * <p><strong>What this service will not write.</strong> Only {@code professional_registers}. Fee
 * schedules and numbering policies are materialised per council by the V041/V042 seeds and are a
 * separate question; generalising this into a "materialise everything from configuration" engine is
 * explicitly out of scope.</p>
 */
@Service
public class RegisterMaterialisationService {

    private static final Logger log = LoggerFactory.getLogger(RegisterMaterialisationService.class);

    private static final String REGISTER_TYPE = "REGISTER";
    private static final String SOURCE_CONFIG_PACK = "CONFIG_PACK";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_RETIRED = "RETIRED";

    private final CouncilRepository councilRepository;
    private final ProfessionalRegisterRepository registerRepository;
    private final OrgRegistryConfigClient configClient;

    public RegisterMaterialisationService(CouncilRepository councilRepository,
                                          ProfessionalRegisterRepository registerRepository,
                                          OrgRegistryConfigClient configClient) {
        this.councilRepository = councilRepository;
        this.registerRepository = registerRepository;
        this.configClient = configClient;
    }

    /** What a reconcile did. Every number is a count of rows actually written. */
    public record ReconcileReport(String outcome, String councilCode, int declared, int created,
                                  int updated, int unchanged, int retired, int reinstated,
                                  List<String> notes) {
    }

    /**
     * Reconcile the registers of whichever council an org-registry organisation regulates.
     *
     * <p>Configuration events name an organisation, because org-registry is the system of record
     * for organisation identity and knows nothing about councils. This is the translation, and it
     * is deliberately a no-op when no council is linked to the organisation: activating the
     * configuration of a regulator that does not regulate professionals — a hospital group, say —
     * is a legitimate event that this materialiser has no business acting on.</p>
     *
     * @return empty when no council is linked to the organisation
     */
    @Transactional
    public Optional<ReconcileReport> reconcileForOrganisation(UUID tenantId, UUID organisationId,
                                                              String actor) {
        return councilRepository.findByTenantIdAndOrgRegistryOrgId(tenantId, organisationId)
                .map(council -> reconcile(tenantId, council.getId(), actor));
    }

    /**
     * Reconcile one council's registers against its ACTIVE configuration.
     *
     * @return a report of what changed; {@code outcome = CONFIGURATION_UNAVAILABLE} when the
     *         Registry could not be consulted, in which case nothing was written
     */
    @Transactional
    public ReconcileReport reconcile(UUID tenantId, Long councilId, String actor) {
        CouncilEntity council = councilRepository.findByIdAndTenantId(councilId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No council " + councilId));

        List<String> notes = new ArrayList<>();

        if (council.getOrgRegistryOrgId() == null) {
            // Without the organisation link there is no pack to read. This is a configuration gap
            // to report, not a reason to touch any register.
            return new ReconcileReport("NO_ORGANISATION_LINK", council.getCouncilCode(),
                    0, 0, 0, 0, 0, 0,
                    List.of("Council " + council.getCouncilCode() + " has no org_registry_org_id, so "
                            + "it has no configuration pack to materialise from. Link it before "
                            + "reconciling."));
        }

        Optional<List<JsonNode>> packs = configClient.packsForOrganisation(council.getOrgRegistryOrgId());
        if (packs.isEmpty()) {
            return unavailable(council, "The Regulatory Configuration Registry could not be reached, "
                    + "so no register was created, changed or retired. An outage must not be able to "
                    + "retire a council's registers.");
        }

        // Collect the REGISTER definitions across the organisation's ACTIVE releases.
        List<JsonNode> registerDefinitions = new ArrayList<>();
        for (JsonNode pack : packs.get()) {
            String packId = pack.path("id").asText(null);
            if (packId == null || packId.isBlank()) {
                continue;
            }
            Optional<List<JsonNode>> active = configClient.activeDefinitions(UUID.fromString(packId));
            if (active.isEmpty()) {
                return unavailable(council, "Pack " + packId + " could not be read, so the reconcile "
                        + "stopped without writing anything rather than acting on a partial view of "
                        + "the council's configuration.");
            }
            for (JsonNode definition : active.get()) {
                if (REGISTER_TYPE.equals(definition.path("typeCode").asText(null))) {
                    registerDefinitions.add(definition);
                }
            }
        }

        int created = 0;
        int updated = 0;
        int unchanged = 0;
        int reinstated = 0;
        Set<String> declaredCodes = new LinkedHashSet<>();

        for (JsonNode definition : registerDefinitions) {
            JsonNode payload = definition.path("payload");
            String declaredCode = text(payload, "registerCode");
            if (declaredCode == null) {
                notes.add("A REGISTER definition (" + definition.path("definitionKey").asText("?")
                        + ") declares no registerCode and was skipped — a register with no code "
                        + "cannot be reconciled or admitted to.");
                continue;
            }

            String registerCode = composeRegisterCode(council.getCouncilCode(), declaredCode);
            declaredCodes.add(registerCode);

            String name = firstNonBlank(text(payload, "workingTitle"),
                    definition.path("label").asText(null), registerCode);

            ProfessionalRegisterEntity register = registerRepository
                    .findByTenantIdAndCouncilIdAndRegisterCode(tenantId, councilId, registerCode)
                    .orElse(null);

            boolean isNew = register == null;
            if (isNew) {
                register = new ProfessionalRegisterEntity();
                register.setTenantId(tenantId);
                register.setCouncilId(councilId);
                register.setRegisterCode(registerCode);
            }

            boolean wasRetired = STATUS_RETIRED.equals(register.getStatus());
            String beforeFingerprint = fingerprint(register);

            register.setName(name);
            register.setDescription(text(payload, "admits"));

            // Rule 3. The pack may state a statutory title; it may equally state that the council
            // has not decided one. Only a stated title is carried, and the decision it awaits is
            // carried with it so a screen can name what is outstanding.
            register.setStatutoryTitle(text(payload, "statutoryTitle"));
            register.setStatutoryTitleDecisionRef(text(payload, "statutoryTitleDecisionRef"));

            // The axis is the council's statutory model, so it comes from the pack or not at all.
            // Inferring it would be guessing at a council's own classification — the same reasoning
            // that left the other councils' registers UNCLASSIFIED in V039 (NCZ-DEC-016).
            String axis = text(payload, "registerAxis");
            if (axis != null) {
                register.setRegisterAxis(axis);
            } else if (isNew) {
                notes.add(registerCode + " was materialised without a register axis: the pack does "
                        + "not declare one, and inferring whether a register measures standing or "
                        + "qualification would be guessing at the council's statutory model.");
            }

            register.setSource(SOURCE_CONFIG_PACK);
            register.setConfigDefinitionKey(definition.path("definitionKey").asText(null));
            register.setConfigSemanticVersion(definition.path("semanticVersion").asText(null));
            register.setConfigContentHash(definition.path("contentHash").asText(null));
            register.setMaterialisedAt(Instant.now());

            if (wasRetired) {
                // The pack declares it again. Reinstatement is the counterpart of retirement, and
                // it is why retirement is a state: the register kept its entries throughout.
                register.setStatus(STATUS_ACTIVE);
                register.setRetiredAt(null);
                reinstated++;
                notes.add(registerCode + " was retired and is declared again — reinstated with its "
                        + "entries intact.");
            }

            registerRepository.save(register);

            if (isNew) {
                created++;
            } else if (wasRetired || !fingerprint(register).equals(beforeFingerprint)) {
                updated++;
            } else {
                unchanged++;
            }
        }

        // Rule 2. A register this materialiser owns that the pack no longer declares is retired.
        //
        // Scoped deliberately to CONFIG_PACK rows. A MIGRATION_SEED register is not this pack's to
        // retire: NCZ carries four seeded registers (NCZ_GENERAL, NCZ_MIDWIFE, NCZ_MENTAL_HEALTH,
        // NCZ_SPECIALIST) that are a qualification taxonomy the pack does not describe, and whether
        // they are statutory registers at all is an OPEN council decision (NCZ-DEC-016). Retiring
        // them because a pack is silent about them would be answering that decision by side effect.
        int retired = 0;
        for (ProfessionalRegisterEntity existing
                : registerRepository.findByTenantIdAndCouncilIdOrderByRegisterCodeAsc(tenantId, councilId)) {
            if (!SOURCE_CONFIG_PACK.equals(existing.getSource())
                    || STATUS_RETIRED.equals(existing.getStatus())
                    || declaredCodes.contains(existing.getRegisterCode())) {
                continue;
            }
            existing.setStatus(STATUS_RETIRED);
            existing.setRetiredAt(Instant.now());
            registerRepository.save(existing);
            retired++;
            notes.add(existing.getRegisterCode() + " is no longer declared by the council's active "
                    + "configuration and was retired. It keeps its entries and its history; it "
                    + "admits nobody new.");
        }

        log.info("Reconciled registers for council {} (actor={}): declared={} created={} updated={} "
                        + "unchanged={} retired={} reinstated={}",
                council.getCouncilCode(), actor, declaredCodes.size(), created, updated, unchanged,
                retired, reinstated);

        return new ReconcileReport("RECONCILED", council.getCouncilCode(), declaredCodes.size(),
                created, updated, unchanged, retired, reinstated, notes);
    }

    /**
     * The pack declares a register code within the council's own namespace ({@code STUDENT}); the
     * register table is council-scoped but its codes are globally prefixed ({@code NCZ_STUDENT}) —
     * the convention every one of the nine seeded councils follows. Composing here is what lets a
     * reconcile recognise a register a migration already created, instead of duplicating it.
     *
     * <p>A pack that already states the fully-qualified code is honoured as-is, so a council whose
     * configuration was authored against the stored form is not double-prefixed.</p>
     */
    static String composeRegisterCode(String councilCode, String declaredCode) {
        String prefix = councilCode.toUpperCase() + "_";
        String code = declaredCode.trim().toUpperCase();
        return code.startsWith(prefix) ? code : prefix + code;
    }

    private ReconcileReport unavailable(CouncilEntity council, String why) {
        log.warn("Register reconcile for {} degraded: {}", council.getCouncilCode(), why);
        return new ReconcileReport("CONFIGURATION_UNAVAILABLE", council.getCouncilCode(),
                0, 0, 0, 0, 0, 0, List.of(why));
    }

    /** The fields a reconcile owns, so an unchanged run can be reported as unchanged. */
    private static String fingerprint(ProfessionalRegisterEntity r) {
        return String.join("\u001f",
                String.valueOf(r.getName()),
                String.valueOf(r.getDescription()),
                String.valueOf(r.getStatutoryTitle()),
                String.valueOf(r.getStatutoryTitleDecisionRef()),
                String.valueOf(r.getRegisterAxis()),
                String.valueOf(r.getSource()),
                String.valueOf(r.getConfigContentHash()),
                String.valueOf(r.getStatus()));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String s = value.asText();
        return s == null || s.isBlank() ? null : s;
    }

    private static String firstNonBlank(String... candidates) {
        for (String c : candidates) {
            if (c != null && !c.isBlank()) {
                return c;
            }
        }
        return null;
    }
}
