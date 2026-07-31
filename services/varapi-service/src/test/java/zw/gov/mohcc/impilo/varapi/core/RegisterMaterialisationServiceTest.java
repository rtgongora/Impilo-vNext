package zw.gov.mohcc.impilo.varapi.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.varapi.integration.OrgRegistryConfigClient;
import zw.gov.mohcc.impilo.varapi.persistence.entity.CouncilEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProfessionalRegisterEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.CouncilRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProfessionalRegisterRepository;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * The register materialiser (task #97) — the piece that makes "a council is a configuration pack"
 * literally true rather than nearly true.
 *
 * <p>The repository is mocked but backed by real in-memory state, deliberately. A fully mocked
 * repository would let the idempotency test assert "nothing was written" while proving only that a
 * stub was not called — the check could not fail. Backing it with a list means the second run
 * genuinely re-reads what the first run genuinely wrote.</p>
 */
class RegisterMaterialisationServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID NCZ_ORG = UUID.randomUUID();
    private static final UUID PACK = UUID.randomUUID();
    private static final Long COUNCIL_ID = 7L;

    private final ObjectMapper mapper = new ObjectMapper();

    private CouncilRepository councilRepository;
    private ProfessionalRegisterRepository registerRepository;
    private OrgRegistryConfigClient configClient;
    private RegisterMaterialisationService service;

    private List<ProfessionalRegisterEntity> table;
    private long nextId;

    @BeforeEach
    void setUp() {
        table = new ArrayList<>();
        nextId = 1L;

        councilRepository = mock(CouncilRepository.class);
        registerRepository = mock(ProfessionalRegisterRepository.class);
        configClient = mock(OrgRegistryConfigClient.class);

        when(councilRepository.findByIdAndTenantId(COUNCIL_ID, TENANT))
                .thenReturn(Optional.of(council("NCZ", NCZ_ORG)));

        when(registerRepository.findByTenantIdAndCouncilIdAndRegisterCode(eq(TENANT), eq(COUNCIL_ID), any()))
                .thenAnswer(inv -> {
                    String code = inv.getArgument(2);
                    return table.stream().filter(r -> code.equals(r.getRegisterCode())).findFirst();
                });
        when(registerRepository.findByTenantIdAndCouncilIdOrderByRegisterCodeAsc(TENANT, COUNCIL_ID))
                .thenAnswer(inv -> table.stream()
                        .sorted(Comparator.comparing(ProfessionalRegisterEntity::getRegisterCode))
                        .toList());
        when(registerRepository.save(any(ProfessionalRegisterEntity.class))).thenAnswer(inv -> {
            ProfessionalRegisterEntity r = inv.getArgument(0);
            if (r.getId() == null) {
                setId(r, nextId++);
                table.add(r);
            }
            return r;
        });

        when(configClient.packsForOrganisation(NCZ_ORG))
                .thenReturn(Optional.of(List.of(packNode(PACK))));

        service = new RegisterMaterialisationService(councilRepository, registerRepository, configClient);
    }

    // ── The core behaviour ───────────────────────────────────────────────────────────────────

    @Test
    void materialisesRegistersFromTheCouncilsActiveConfiguration() {
        activeDefinitions(register("student-register", "STUDENT", "Student Register", "STANDING"));

        var report = service.reconcile(TENANT, COUNCIL_ID, "registrar-1");

        assertEquals("RECONCILED", report.outcome());
        assertEquals(1, report.created());
        assertEquals(1, table.size());

        ProfessionalRegisterEntity r = table.get(0);
        assertEquals("NCZ_STUDENT", r.getRegisterCode(),
                "the register code is composed from the council code, matching the stored convention");
        assertEquals("Student Register", r.getName());
        assertEquals("CONFIG_PACK", r.getSource(),
                "provenance is what distinguishes materialisation from another hardcoded migration");
        assertEquals("hash-student", r.getConfigContentHash());
        assertEquals("STANDING", r.getRegisterAxis());
        assertNotNull(r.getMaterialisedAt());
    }

    @Test
    void aSecondRunWritesNothing() {
        activeDefinitions(register("student-register", "STUDENT", "Student Register", "STANDING"));
        service.reconcile(TENANT, COUNCIL_ID, "registrar-1");

        var second = service.reconcile(TENANT, COUNCIL_ID, "registrar-1");

        assertEquals(0, second.created());
        assertEquals(0, second.updated());
        assertEquals(1, second.unchanged());
        assertEquals(1, table.size(), "re-running must not add a second copy of the same register");
    }

    /**
     * The failure this materialiser is most likely to cause, and the one the PCZ fixture cannot
     * catch because it only inspects PCZ.
     *
     * <p>The NCZ pack declares {@code registerCode: "STUDENT"}; V039 seeded {@code NCZ_STUDENT}.
     * A materialiser that copied the pack's code verbatim would create a second row and leave NCZ
     * with eight standing registers instead of four — while every assertion about "registers exist"
     * still passed.</p>
     */
    @Test
    void doesNotDuplicateARegisterThatAMigrationAlreadySeeded() {
        seeded("NCZ_STUDENT", "Student Register", "STANDING");
        activeDefinitions(register("student-register", "STUDENT", "Student Register", "STANDING"));

        var report = service.reconcile(TENANT, COUNCIL_ID, "registrar-1");

        assertEquals(0, report.created(), "the seeded register must be recognised, not duplicated");
        assertEquals(1, table.size(), "NCZ must still have exactly one Student Register");
        assertEquals("CONFIG_PACK", table.get(0).getSource(),
                "the seeded row is adopted by configuration, which is how the leak closes");
    }

    @Test
    void aPackDeclaringTheFullyQualifiedCodeIsNotDoublePrefixed() {
        assertEquals("NCZ_STUDENT", RegisterMaterialisationService.composeRegisterCode("NCZ", "STUDENT"));
        assertEquals("NCZ_STUDENT", RegisterMaterialisationService.composeRegisterCode("NCZ", "NCZ_STUDENT"));
        assertEquals("PCZ_INTERN", RegisterMaterialisationService.composeRegisterCode("PCZ", "intern"));
    }

    // ── Rule 4: only an ACTIVE release materialises ──────────────────────────────────────────

    @Test
    void aDraftReleaseCreatesNoRegister() {
        // A pack whose only release is DRAFT has no active configuration: the Registry answers 404,
        // which the client reports as "asked, and there is nothing active".
        when(configClient.activeDefinitions(PACK)).thenReturn(Optional.of(List.of()));

        var report = service.reconcile(TENANT, COUNCIL_ID, "registrar-1");

        assertEquals(0, report.created());
        assertTrue(table.isEmpty(),
                "a deploy must not be able to produce registers nobody approved");
    }

    // ── Rule 2: a register is never deleted ──────────────────────────────────────────────────

    @Test
    void aRegisterDroppedFromThePackIsRetiredNotRemoved() {
        activeDefinitions(register("student-register", "STUDENT", "Student Register", "STANDING"),
                register("provisional-register", "PROVISIONAL", "Provisional Register", "STANDING"));
        service.reconcile(TENANT, COUNCIL_ID, "registrar-1");
        assertEquals(2, table.size());

        // The council's next release stops declaring the Provisional Register.
        activeDefinitions(register("student-register", "STUDENT", "Student Register", "STANDING"));
        var report = service.reconcile(TENANT, COUNCIL_ID, "registrar-1");

        assertEquals(1, report.retired());
        assertEquals(2, table.size(), "the register still exists — it carries the entries admitted to it");
        ProfessionalRegisterEntity dropped = byCode("NCZ_PROVISIONAL");
        assertEquals("RETIRED", dropped.getStatus());
        assertNotNull(dropped.getRetiredAt(), "retirement is dated");
    }

    @Test
    void aRetiredRegisterDeclaredAgainIsReinstated() {
        activeDefinitions(register("student-register", "STUDENT", "Student Register", "STANDING"));
        service.reconcile(TENANT, COUNCIL_ID, "registrar-1");
        activeDefinitions();
        service.reconcile(TENANT, COUNCIL_ID, "registrar-1");
        assertEquals("RETIRED", byCode("NCZ_STUDENT").getStatus());

        activeDefinitions(register("student-register", "STUDENT", "Student Register", "STANDING"));
        var report = service.reconcile(TENANT, COUNCIL_ID, "registrar-1");

        assertEquals(1, report.reinstated());
        assertEquals("ACTIVE", byCode("NCZ_STUDENT").getStatus());
        assertNull(byCode("NCZ_STUDENT").getRetiredAt());
    }

    /**
     * NCZ carries four seeded registers that are a qualification taxonomy the pack does not
     * describe, and whether they are statutory registers at all is an open council decision
     * (NCZ-DEC-016). Retiring them because a pack is silent would answer that decision by accident.
     */
    @Test
    void aSeededRegisterThePackDoesNotMentionIsLeftAlone() {
        seeded("NCZ_GENERAL", "Register of General Nurses", null);
        activeDefinitions(register("student-register", "STUDENT", "Student Register", "STANDING"));

        var report = service.reconcile(TENANT, COUNCIL_ID, "registrar-1");

        assertEquals(0, report.retired());
        assertEquals("ACTIVE", byCode("NCZ_GENERAL").getStatus());
        assertEquals("MIGRATION_SEED", byCode("NCZ_GENERAL").getSource());
    }

    // ── Rule 3: a statutory title is carried, never invented ─────────────────────────────────

    @Test
    void statutoryTitleStaysNullUntilTheCouncilStatesIt() {
        activeDefinitions(register("student-register", "STUDENT", "Student Register", "STANDING"));

        service.reconcile(TENANT, COUNCIL_ID, "registrar-1");

        ProfessionalRegisterEntity r = byCode("NCZ_STUDENT");
        assertNull(r.getStatutoryTitle(),
                "a title reaches certificates and cannot be quietly corrected, so it is never guessed");
        assertEquals("NCZ-DEC-013", r.getStatutoryTitleDecisionRef(),
                "the decision the title awaits is carried so a screen can name what is outstanding");
    }

    @Test
    void aStatedStatutoryTitleIsCarriedThrough() {
        ObjectNode definition = register("student-register", "STUDENT", "Student Register", "STANDING");
        ((ObjectNode) definition.get("payload")).put("statutoryTitle", "The Register of Student Nurses");
        activeDefinitions(definition);

        service.reconcile(TENANT, COUNCIL_ID, "registrar-1");

        assertEquals("The Register of Student Nurses", byCode("NCZ_STUDENT").getStatutoryTitle());
    }

    @Test
    void anUndeclaredAxisIsLeftUnclassifiedRatherThanGuessed() {
        ObjectNode definition = register("student-register", "STUDENT", "Student Register", null);
        activeDefinitions(definition);

        var report = service.reconcile(TENANT, COUNCIL_ID, "registrar-1");

        assertNull(byCode("NCZ_STUDENT").getRegisterAxis());
        assertTrue(report.notes().stream().anyMatch(n -> n.contains("register axis")),
                "the reconcile says out loud that it declined to guess");
    }

    // ── Degradation: an outage must not retire a council's registers ─────────────────────────

    @Test
    void anUnreachableRegistryChangesNothing() {
        activeDefinitions(register("student-register", "STUDENT", "Student Register", "STANDING"));
        service.reconcile(TENANT, COUNCIL_ID, "registrar-1");
        assertEquals(1, table.size());

        when(configClient.activeDefinitions(PACK)).thenReturn(Optional.empty());
        var report = service.reconcile(TENANT, COUNCIL_ID, "registrar-1");

        assertEquals("CONFIGURATION_UNAVAILABLE", report.outcome());
        assertEquals(0, report.retired(), "an outage is not a decision to retire anything");
        assertEquals("ACTIVE", byCode("NCZ_STUDENT").getStatus());
    }

    @Test
    void aCouncilWithNoOrganisationLinkIsReportedNotGuessedAt() {
        when(councilRepository.findByIdAndTenantId(COUNCIL_ID, TENANT))
                .thenReturn(Optional.of(council("NCZ", null)));

        var report = service.reconcile(TENANT, COUNCIL_ID, "registrar-1");

        assertEquals("NO_ORGANISATION_LINK", report.outcome());
        assertTrue(table.isEmpty());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────────────

    private void activeDefinitions(JsonNode... definitions) {
        when(configClient.activeDefinitions(PACK)).thenReturn(Optional.of(List.of(definitions)));
    }

    private ObjectNode register(String definitionKey, String registerCode, String workingTitle,
                                String axis) {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("registerCode", registerCode);
        payload.put("workingTitle", workingTitle);
        payload.putNull("statutoryTitle");
        payload.put("statutoryTitleDecisionRef", "NCZ-DEC-013");
        payload.put("admits", "Someone the council admits to this register.");
        if (axis != null) {
            payload.put("registerAxis", axis);
        }

        ObjectNode definition = mapper.createObjectNode();
        definition.put("typeCode", "REGISTER");
        definition.put("definitionKey", definitionKey);
        definition.put("label", workingTitle);
        definition.put("semanticVersion", "1.0.0");
        definition.put("contentHash", "hash-" + registerCode.toLowerCase());
        definition.set("payload", payload);
        return definition;
    }

    private ObjectNode packNode(UUID id) {
        ObjectNode pack = mapper.createObjectNode();
        pack.put("id", id.toString());
        pack.put("packKey", "nurses-council");
        return pack;
    }

    private CouncilEntity council(String code, UUID orgId) {
        CouncilEntity c = new CouncilEntity();
        c.setTenantId(TENANT);
        c.setCouncilCode(code);
        c.setName("Nurses Council of Zimbabwe");
        c.setOrgRegistryOrgId(orgId);
        return c;
    }

    private void seeded(String code, String name, String axis) {
        ProfessionalRegisterEntity r = new ProfessionalRegisterEntity();
        r.setTenantId(TENANT);
        r.setCouncilId(COUNCIL_ID);
        r.setRegisterCode(code);
        r.setName(name);
        r.setRegisterAxis(axis);
        r.setSource("MIGRATION_SEED");
        r.setStatus("ACTIVE");
        setId(r, nextId++);
        table.add(r);
    }

    private ProfessionalRegisterEntity byCode(String code) {
        return table.stream().filter(r -> code.equals(r.getRegisterCode())).findFirst()
                .orElseThrow(() -> new AssertionError("no register " + code + " in " + table.stream()
                        .map(ProfessionalRegisterEntity::getRegisterCode).toList()));
    }

    private static void setId(ProfessionalRegisterEntity r, long id) {
        try {
            Field f = ProfessionalRegisterEntity.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(r, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
