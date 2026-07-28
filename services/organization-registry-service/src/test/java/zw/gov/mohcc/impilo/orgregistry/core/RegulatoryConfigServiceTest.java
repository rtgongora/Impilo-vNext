package zw.gov.mohcc.impilo.orgregistry.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.*;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The guards that make the Registry worth having, at the service boundary.
 *
 * <p>The schema-level invariants (immutability, four-eyes, append-only bindings) are proven against
 * a real Postgres by {@code scripts/runtime-proof/regulatory-config-registry-invariants.sh} — JVM
 * tests here never apply Flyway migrations, so asserting them with mocks would prove only that the
 * mocks agree with the test. What these tests cover is the service's own behaviour: the refusals it
 * must make before the database is ever reached, and the resolution rule that answers "what were the
 * rules when this case was submitted?".</p>
 */
@ExtendWith(MockitoExtension.class)
class RegulatoryConfigServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID PACK = UUID.randomUUID();
    private static final UUID ORG = UUID.randomUUID();

    @Mock private ConfigPackRepository packRepository;
    @Mock private ConfigDefinitionRepository definitionRepository;
    @Mock private ConfigDefinitionVersionRepository versionRepository;
    @Mock private ConfigDefinitionTypeRepository typeRepository;
    @Mock private ConfigDependencyRepository dependencyRepository;
    @Mock private ConfigReleaseRepository releaseRepository;
    @Mock private ConfigReleaseItemRepository releaseItemRepository;
    @Mock private ConfigApprovalRepository approvalRepository;
    @Mock private ConfigActivationRepository activationRepository;
    @Mock private CaseConfigBindingRepository bindingRepository;
    @Mock private CaseConfigPinRepository pinRepository;
    @Mock private ConfigAuditEventRepository auditRepository;
    @Mock private ConfigReleaseValidator validator;
    @Mock private OrgRegistryOutboxWriter outbox;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RegulatoryConfigService service;

    @BeforeEach
    void setUp() {
        service = new RegulatoryConfigService(packRepository, definitionRepository, versionRepository,
                typeRepository, dependencyRepository, releaseRepository, releaseItemRepository,
                approvalRepository, activationRepository, bindingRepository, pinRepository,
                auditRepository, validator, new ConfigContentHasher(objectMapper), outbox, objectMapper);

        ConfigPackEntity pack = new ConfigPackEntity();
        pack.setId(PACK);
        pack.setTenantId(TENANT);
        pack.setOrganizationId(ORG);
        pack.setPackKey("nurses-council");
        lenient().when(packRepository.findByTenantIdAndId(TENANT, PACK)).thenReturn(Optional.of(pack));
    }

    private JsonNode json(String raw) {
        try {
            return objectMapper.readTree(raw);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private ConfigDefinitionTypeEntity feeType(boolean carriesPolicyValue) {
        ConfigDefinitionTypeEntity type = new ConfigDefinitionTypeEntity();
        type.setTypeCode("FEE_SCHEDULE");
        type.setLabel("Fee schedule");
        type.setRequiresFourEyes(true);
        type.setApplicantFacing(true);
        type.setCarriesPolicyValue(carriesPolicyValue);
        return type;
    }

    private ConfigDefinitionEntity definition() {
        ConfigDefinitionEntity definition = new ConfigDefinitionEntity();
        definition.setId(UUID.randomUUID());
        definition.setTenantId(TENANT);
        definition.setPackId(PACK);
        definition.setTypeCode("FEE_SCHEDULE");
        definition.setDefinitionKey("student-index-fee");
        definition.setLabel("Student index fee");
        return definition;
    }

    // ── Checksum immutability ────────────────────────────────────────────────────────────────

    @Test
    void reAuthoringAVersionWithDifferentContentIsRefused() {
        ConfigDefinitionEntity definition = definition();
        when(typeRepository.findById("FEE_SCHEDULE")).thenReturn(Optional.of(feeType(true)));
        when(definitionRepository.findByTenantIdAndPackIdAndTypeCodeAndDefinitionKey(
                TENANT, PACK, "FEE_SCHEDULE", "student-index-fee")).thenReturn(Optional.of(definition));

        ConfigDefinitionVersionEntity existing = new ConfigDefinitionVersionEntity();
        existing.setId(UUID.randomUUID());
        existing.setDefinitionId(definition.getId());
        existing.setSemanticVersion("1.0.0");
        existing.setContentHash(new ConfigContentHasher(objectMapper).hash(json("{\"amount\":null}")));
        when(versionRepository.findByTenantIdAndDefinitionIdAndSemanticVersion(
                TENANT, definition.getId(), "1.0.0")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.authorVersion(TENANT, PACK, "FEE_SCHEDULE",
                "student-index-fee", "Student index fee", "1.0.0", json("{\"amount\":25}"),
                "/x", "CONFIRMED", null, null, List.of(), "HID-AUTHOR"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already recorded with different content");

        verify(versionRepository, never()).save(any());
    }

    @Test
    void reAuthoringAVersionWithIdenticalContentIsANoOpSoImportsAreIdempotent() {
        ConfigDefinitionEntity definition = definition();
        when(typeRepository.findById("FEE_SCHEDULE")).thenReturn(Optional.of(feeType(true)));
        when(definitionRepository.findByTenantIdAndPackIdAndTypeCodeAndDefinitionKey(
                TENANT, PACK, "FEE_SCHEDULE", "student-index-fee")).thenReturn(Optional.of(definition));

        JsonNode payload = json("{\"amount\":null,\"currency\":\"USD\"}");
        ConfigDefinitionVersionEntity existing = new ConfigDefinitionVersionEntity();
        existing.setId(UUID.randomUUID());
        existing.setDefinitionId(definition.getId());
        existing.setSemanticVersion("1.0.0");
        existing.setContentHash(new ConfigContentHasher(objectMapper).hash(payload));
        when(versionRepository.findByTenantIdAndDefinitionIdAndSemanticVersion(
                TENANT, definition.getId(), "1.0.0")).thenReturn(Optional.of(existing));

        // Re-importing the pack with the keys in a different order must not read as a change.
        ConfigDefinitionVersionEntity result = service.authorVersion(TENANT, PACK, "FEE_SCHEDULE",
                "student-index-fee", "Student index fee", "1.0.0",
                json("{\"currency\":\"USD\",\"amount\":null}"), "/x",
                "PENDING_REGULATOR_APPROVAL", "NCZ-DEC-002", null, List.of(), "HID-AUTHOR");

        assertThat(result).isSameAs(existing);
        verify(versionRepository, never()).save(any());
    }

    @Test
    void aDefinitionTypeWithNoEngineIsRefusedAtAuthoringTime() {
        when(typeRepository.findById("EXOTIC")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authorVersion(TENANT, PACK, "EXOTIC", "x", "X", "1.0.0",
                json("{}"), null, null, null, null, List.of(), "HID-AUTHOR"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unknown definition type");
    }

    @Test
    void authoringAPolicyCarryingDefinitionWithoutDeclaringItsStatusIsRefused() {
        // Before this guard, a fee authored with amount null and no declared status validated
        // completely clean — it read as fully configured. Silence must not be cheaper than honesty.
        when(typeRepository.findById("FEE_SCHEDULE")).thenReturn(Optional.of(feeType(true)));

        assertThatThrownBy(() -> service.authorVersion(TENANT, PACK, "FEE_SCHEDULE",
                "student-index-fee", "Student index fee", "1.0.0", json("{\"amount\":null}"),
                "/x", null, null, null, List.of(), "HID-AUTHOR"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("must declare policyStatus");

        verify(versionRepository, never()).save(any());
    }

    @Test
    void declaringPendingWithoutNamingTheDecisionIsRefused() {
        when(typeRepository.findById("FEE_SCHEDULE")).thenReturn(Optional.of(feeType(true)));

        assertThatThrownBy(() -> service.authorVersion(TENANT, PACK, "FEE_SCHEDULE",
                "student-index-fee", "Student index fee", "1.0.0", json("{\"amount\":null}"),
                "/x", "PENDING_REGULATOR_APPROVAL", "  ", null, List.of(), "HID-AUTHOR"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("does not say which one");

        verify(versionRepository, never()).save(any());
    }

    // ── Four-eyes ────────────────────────────────────────────────────────────────────────────

    @Test
    void theSubmitterOfAReleaseCannotApproveIt() {
        ConfigReleaseEntity release = new ConfigReleaseEntity();
        release.setId(UUID.randomUUID());
        release.setTenantId(TENANT);
        release.setPackId(PACK);
        release.setLifecycleState("IN_REVIEW");
        release.setSubmittedBy("HID-AUTHOR");
        when(releaseRepository.findByTenantIdAndId(TENANT, release.getId()))
                .thenReturn(Optional.of(release));

        assertThatThrownBy(() -> service.recordApproval(TENANT, release.getId(), "HID-AUTHOR",
                "REGISTRAR", "APPROVE", null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Four-eyes");

        verify(approvalRepository, never()).save(any());
    }

    @Test
    void approvalsAreOnlyAcceptedWhileTheReleaseIsInReview() {
        ConfigReleaseEntity release = new ConfigReleaseEntity();
        release.setId(UUID.randomUUID());
        release.setTenantId(TENANT);
        release.setLifecycleState("ACTIVE");
        when(releaseRepository.findByTenantIdAndId(TENANT, release.getId()))
                .thenReturn(Optional.of(release));

        assertThatThrownBy(() -> service.recordApproval(TENANT, release.getId(), "HID-OTHER",
                "REGISTRAR", "APPROVE", null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("IN_REVIEW");
    }

    // ── Frozen releases ──────────────────────────────────────────────────────────────────────

    @Test
    void anApprovedReleaseCannotGainOrLoseItems() {
        ConfigReleaseEntity release = new ConfigReleaseEntity();
        release.setId(UUID.randomUUID());
        release.setTenantId(TENANT);
        release.setReleaseKey("ncz-v1");
        release.setLifecycleState("APPROVED");
        when(releaseRepository.findByTenantIdAndId(TENANT, release.getId()))
                .thenReturn(Optional.of(release));

        assertThatThrownBy(() -> service.addItem(TENANT, release.getId(), UUID.randomUUID(), "HID"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("frozen");
    }

    // ── The acceptance discriminator ─────────────────────────────────────────────────────────

    @Test
    void aCaseThatIsAlreadyBoundIsNeverMovedOntoANewerRelease() {
        // This refusal IS the guarantee. Without it, activating a release would silently rewrite the
        // rules that live cases are being decided under.
        CaseConfigBindingEntity existing = new CaseConfigBindingEntity();
        existing.setId(UUID.randomUUID());
        existing.setReleaseId(UUID.randomUUID());
        when(bindingRepository.findByTenantIdAndCaseSystemAndCaseTypeAndCaseRef(
                TENANT, "VARAPI", "PROVIDER_APPLICATION", "app-1")).thenReturn(Optional.of(existing));

        CaseConfigBindingEntity result = service.bindCase(TENANT, "VARAPI", "PROVIDER_APPLICATION",
                "app-1", ORG, "HID", null);

        assertThat(result).isSameAs(existing);
        verify(bindingRepository, never()).save(any());
        // It must not even look for the currently active release — there is nothing to decide.
        verify(releaseRepository, never()).findByTenantIdAndPackIdAndLifecycleState(any(), any(), any());
    }

    @Test
    void aCaseCannotBeOpenedAgainstARegulatorWithNoActiveConfiguration() {
        when(bindingRepository.findByTenantIdAndCaseSystemAndCaseTypeAndCaseRef(
                TENANT, "VARAPI", "PROVIDER_APPLICATION", "app-2")).thenReturn(Optional.empty());
        ConfigPackEntity pack = new ConfigPackEntity();
        pack.setId(PACK);
        pack.setTenantId(TENANT);
        when(packRepository.findByTenantIdAndOrganizationId(TENANT, ORG)).thenReturn(List.of(pack));
        when(releaseRepository.findByTenantIdAndPackIdAndLifecycleState(TENANT, PACK, "ACTIVE"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.bindCase(TENANT, "VARAPI", "PROVIDER_APPLICATION", "app-2",
                ORG, "HID", null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no active configuration release");
    }

    @Test
    void aCaseResolvesFromItsOwnBindingAndNotFromWhateverIsActiveToday() {
        UUID boundRelease = UUID.randomUUID();
        CaseConfigBindingEntity binding = new CaseConfigBindingEntity();
        binding.setId(UUID.randomUUID());
        binding.setTenantId(TENANT);
        binding.setPackId(PACK);
        binding.setReleaseId(boundRelease);
        when(bindingRepository.findByTenantIdAndCaseSystemAndCaseTypeAndCaseRef(
                TENANT, "VARAPI", "PROVIDER_APPLICATION", "app-1")).thenReturn(Optional.of(binding));
        when(pinRepository.findByBindingId(binding.getId())).thenReturn(List.of());

        ConfigDefinitionEntity definition = definition();
        ConfigDefinitionVersionEntity oldVersion = new ConfigDefinitionVersionEntity();
        oldVersion.setId(UUID.randomUUID());
        oldVersion.setDefinitionId(definition.getId());
        oldVersion.setSemanticVersion("1.0.0");
        oldVersion.setPayload(json("{\"amount\":null}"));
        oldVersion.setContentHash("a".repeat(64));
        // The old version has since been SUPERSEDED — resolution must return it regardless.
        oldVersion.setLifecycleState("SUPERSEDED");

        ConfigReleaseItemEntity item = new ConfigReleaseItemEntity();
        item.setReleaseId(boundRelease);
        item.setDefinitionId(definition.getId());
        item.setDefinitionVersionId(oldVersion.getId());
        when(releaseItemRepository.findByReleaseId(boundRelease)).thenReturn(List.of(item));
        when(definitionRepository.findById(definition.getId())).thenReturn(Optional.of(definition));
        when(versionRepository.findById(oldVersion.getId())).thenReturn(Optional.of(oldVersion));

        List<RegulatoryConfigService.ResolvedDefinition> resolved =
                service.resolveForCase(TENANT, "VARAPI", "PROVIDER_APPLICATION", "app-1");

        assertThat(resolved).hasSize(1);
        assertThat(resolved.getFirst().semanticVersion()).isEqualTo("1.0.0");
        assertThat(resolved.getFirst().definitionKey()).isEqualTo("student-index-fee");
        // Never consults the active release.
        verify(releaseRepository, never()).findByTenantIdAndPackIdAndLifecycleState(any(), any(), any());
    }

    @Test
    void aLaterPinOverridesTheBoundReleaseForItsOwnDefinition() {
        // A penalty policy is pinned at liability assessment, not at case creation.
        UUID boundRelease = UUID.randomUUID();
        CaseConfigBindingEntity binding = new CaseConfigBindingEntity();
        binding.setId(UUID.randomUUID());
        binding.setTenantId(TENANT);
        binding.setPackId(PACK);
        binding.setReleaseId(boundRelease);
        when(bindingRepository.findByTenantIdAndCaseSystemAndCaseTypeAndCaseRef(
                TENANT, "VARAPI", "PROVIDER_APPLICATION", "app-1")).thenReturn(Optional.of(binding));

        ConfigDefinitionEntity definition = definition();
        ConfigDefinitionVersionEntity atCaseOpen = new ConfigDefinitionVersionEntity();
        atCaseOpen.setId(UUID.randomUUID());
        atCaseOpen.setDefinitionId(definition.getId());
        atCaseOpen.setSemanticVersion("1.0.0");
        ConfigDefinitionVersionEntity atAssessment = new ConfigDefinitionVersionEntity();
        atAssessment.setId(UUID.randomUUID());
        atAssessment.setDefinitionId(definition.getId());
        atAssessment.setSemanticVersion("2.0.0");

        ConfigReleaseItemEntity item = new ConfigReleaseItemEntity();
        item.setReleaseId(boundRelease);
        item.setDefinitionId(definition.getId());
        item.setDefinitionVersionId(atCaseOpen.getId());

        CaseConfigPinEntity pin = new CaseConfigPinEntity();
        pin.setBindingId(binding.getId());
        pin.setDefinitionId(definition.getId());
        pin.setDefinitionVersionId(atAssessment.getId());
        pin.setPinnedAtMoment("LIABILITY_ASSESSMENT");

        when(pinRepository.findByBindingId(binding.getId())).thenReturn(List.of(pin));
        when(releaseItemRepository.findByReleaseId(boundRelease)).thenReturn(List.of(item));
        when(definitionRepository.findById(definition.getId())).thenReturn(Optional.of(definition));
        when(versionRepository.findById(atAssessment.getId())).thenReturn(Optional.of(atAssessment));

        List<RegulatoryConfigService.ResolvedDefinition> resolved =
                service.resolveForCase(TENANT, "VARAPI", "PROVIDER_APPLICATION", "app-1");

        assertThat(resolved).hasSize(1);
        assertThat(resolved.getFirst().semanticVersion()).isEqualTo("2.0.0");
        assertThat(resolved.getFirst().pinnedAtMoment()).isEqualTo("LIABILITY_ASSESSMENT");
    }

    @Test
    void resolvingAnUnboundCaseFailsRatherThanFallingBackToTheActiveRelease() {
        // Falling back would be the silent lie: an answer that looks authoritative and is not the
        // configuration the case was actually decided under.
        when(bindingRepository.findByTenantIdAndCaseSystemAndCaseTypeAndCaseRef(
                TENANT, "VARAPI", "PROVIDER_APPLICATION", "ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveForCase(TENANT, "VARAPI", "PROVIDER_APPLICATION", "ghost"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not bound to a configuration release");
    }

    // ── Activation ───────────────────────────────────────────────────────────────────────────

    @Test
    void anInvalidReleaseCannotBeActivated() {
        ConfigReleaseEntity release = new ConfigReleaseEntity();
        release.setId(UUID.randomUUID());
        release.setTenantId(TENANT);
        release.setPackId(PACK);
        release.setLifecycleState("APPROVED");
        when(releaseRepository.findByTenantIdAndId(TENANT, release.getId()))
                .thenReturn(Optional.of(release));
        when(validator.validate(release)).thenReturn(new ConfigReleaseValidator.ValidationReport(
                false,
                List.of(new ConfigReleaseValidator.Finding("DEPENDENCY_UNRESOLVED", "ERROR",
                        "student-registration-flow", "references FORM:student-registration-form")),
                List.of(), 1, false, 1));

        assertThatThrownBy(() -> service.activate(TENANT, release.getId(), "HID", null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("DEPENDENCY_UNRESOLVED");

        verify(activationRepository, never()).save(any());
    }

    @Test
    void aDraftReleaseCannotJumpStraightToActive() {
        ConfigReleaseEntity release = new ConfigReleaseEntity();
        release.setId(UUID.randomUUID());
        release.setTenantId(TENANT);
        release.setLifecycleState("DRAFT");
        when(releaseRepository.findByTenantIdAndId(TENANT, release.getId()))
                .thenReturn(Optional.of(release));

        assertThatThrownBy(() -> service.activate(TENANT, release.getId(), "HID", null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("approved or scheduled");
    }
}
