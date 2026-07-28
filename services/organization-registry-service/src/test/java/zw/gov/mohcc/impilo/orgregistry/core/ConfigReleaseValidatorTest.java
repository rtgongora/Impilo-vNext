package zw.gov.mohcc.impilo.orgregistry.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.*;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * What may go live, and — more interestingly — what may not.
 *
 * <p>The distinction these tests pin down is between an unfinished release and an undecided policy.
 * A workflow pointing at a form nobody wrote is incoherent configuration and blocks activation. A
 * fee whose amount the Council has not set is honest and must NOT block activation, or a council
 * could never go live until every policy question was answered. Implementation readiness and policy
 * activation readiness are different things.</p>
 */
@ExtendWith(MockitoExtension.class)
class ConfigReleaseValidatorTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock private ConfigReleaseItemRepository releaseItemRepository;
    @Mock private ConfigDefinitionVersionRepository versionRepository;
    @Mock private ConfigDefinitionRepository definitionRepository;
    @Mock private ConfigDefinitionTypeRepository typeRepository;
    @Mock private ConfigDependencyRepository dependencyRepository;
    @Mock private ConfigApprovalRepository approvalRepository;

    private ConfigReleaseValidator validator;
    private ConfigReleaseEntity release;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final List<ConfigReleaseItemEntity> items = new ArrayList<>();
    private final List<ConfigDefinitionEntity> definitions = new ArrayList<>();
    private final List<ConfigDefinitionVersionEntity> versions = new ArrayList<>();
    private final List<ConfigDefinitionTypeEntity> types = new ArrayList<>();
    private final List<ConfigDependencyEntity> dependencies = new ArrayList<>();
    private final List<ConfigApprovalEntity> approvals = new ArrayList<>();

    @BeforeEach
    void setUp() {
        validator = new ConfigReleaseValidator(releaseItemRepository, versionRepository,
                definitionRepository, typeRepository, dependencyRepository, approvalRepository);

        release = new ConfigReleaseEntity();
        release.setId(UUID.randomUUID());
        release.setTenantId(TENANT);
        release.setPackId(UUID.randomUUID());
        release.setReleaseKey("ncz-v1");
        release.setLifecycleState("IN_REVIEW");
        release.setSubmittedBy("HID-AUTHOR");

        lenient().when(releaseItemRepository.findByReleaseId(any())).thenReturn(items);
        lenient().when(versionRepository.findByIdIn(anyList())).thenReturn(versions);
        lenient().when(definitionRepository.findByTenantIdAndPackId(any(), any())).thenReturn(definitions);
        lenient().when(typeRepository.findAll()).thenReturn(types);
        lenient().when(dependencyRepository.findByDefinitionVersionIdIn(anyList())).thenReturn(dependencies);
        lenient().when(approvalRepository.findByReleaseId(any())).thenReturn(approvals);
    }

    // ── fixtures ─────────────────────────────────────────────────────────────────────────────

    private ConfigDefinitionTypeEntity type(String code, boolean fourEyes, boolean applicantFacing) {
        ConfigDefinitionTypeEntity type = new ConfigDefinitionTypeEntity();
        type.setTypeCode(code);
        type.setLabel(code);
        type.setRequiresFourEyes(fourEyes);
        type.setApplicantFacing(applicantFacing);
        types.add(type);
        return type;
    }

    private ConfigDefinitionVersionEntity item(String typeCode, String key, String selfServiceRoute) {
        ConfigDefinitionEntity definition = new ConfigDefinitionEntity();
        definition.setId(UUID.randomUUID());
        definition.setTenantId(TENANT);
        definition.setPackId(release.getPackId());
        definition.setTypeCode(typeCode);
        definition.setDefinitionKey(key);
        definition.setLabel(key);
        definitions.add(definition);

        ConfigDefinitionVersionEntity version = new ConfigDefinitionVersionEntity();
        version.setId(UUID.randomUUID());
        version.setTenantId(TENANT);
        version.setDefinitionId(definition.getId());
        version.setSemanticVersion("1.0.0");
        version.setLifecycleState("APPROVED");
        version.setSelfServiceRoute(selfServiceRoute);
        version.setPayload(objectMapper.createObjectNode());
        versions.add(version);

        ConfigReleaseItemEntity releaseItem = new ConfigReleaseItemEntity();
        releaseItem.setId(UUID.randomUUID());
        releaseItem.setReleaseId(release.getId());
        releaseItem.setDefinitionId(definition.getId());
        releaseItem.setDefinitionVersionId(version.getId());
        items.add(releaseItem);
        return version;
    }

    private void approve(String healthId) {
        ConfigApprovalEntity approval = new ConfigApprovalEntity();
        approval.setReleaseId(release.getId());
        approval.setApproverHealthId(healthId);
        approval.setDecision("APPROVE");
        approvals.add(approval);
    }

    private void dependency(ConfigDefinitionVersionEntity from, String typeCode, String key,
                            boolean optional) {
        ConfigDependencyEntity entity = new ConfigDependencyEntity();
        entity.setDefinitionVersionId(from.getId());
        entity.setRequiredTypeCode(typeCode);
        entity.setRequiredDefinitionKey(key);
        entity.setOptional(optional);
        dependencies.add(entity);
    }

    private static List<String> codes(List<ConfigReleaseValidator.Finding> findings) {
        return findings.stream().map(ConfigReleaseValidator.Finding::code).toList();
    }

    // ── tests ────────────────────────────────────────────────────────────────────────────────

    @Test
    void anEmptyReleaseCannotGoLive() {
        ConfigReleaseValidator.ValidationReport report = validator.validate(release);

        assertThat(report.valid()).isFalse();
        assertThat(codes(report.errors())).containsExactly("RELEASE_EMPTY");
    }

    @Test
    void anApplicantFacingDefinitionWithNoSelfServiceRouteBlocksActivation() {
        type("APPLICATION_TYPE", false, true);
        item("APPLICATION_TYPE", "student-registration", null);
        approve("HID-APPROVER");

        ConfigReleaseValidator.ValidationReport report = validator.validate(release);

        assertThat(report.valid()).isFalse();
        assertThat(codes(report.errors())).contains("NO_SELF_SERVICE_PROJECTION");
    }

    @Test
    void anApplicantFacingDefinitionWithARouteIsFine() {
        type("APPLICATION_TYPE", false, true);
        item("APPLICATION_TYPE", "student-registration", "/professional/regulatory/apply/student");
        approve("HID-APPROVER");

        assertThat(validator.validate(release).valid()).isTrue();
    }

    @Test
    void anUnresolvedMandatoryReferenceBlocksActivation() {
        type("WORKFLOW", true, true);
        ConfigDefinitionVersionEntity workflow =
                item("WORKFLOW", "student-registration-flow", "/professional/regulatory/apply/student");
        dependency(workflow, "FORM", "student-registration-form", false);
        approve("HID-ONE");
        approve("HID-TWO");

        ConfigReleaseValidator.ValidationReport report = validator.validate(release);

        assertThat(report.valid()).isFalse();
        assertThat(codes(report.errors())).contains("DEPENDENCY_UNRESOLVED");
    }

    @Test
    void aReferenceSatisfiedWithinTheReleaseResolves() {
        type("WORKFLOW", true, true);
        type("FORM", false, true);
        ConfigDefinitionVersionEntity workflow =
                item("WORKFLOW", "student-registration-flow", "/professional/regulatory/apply/student");
        item("FORM", "student-registration-form", "/professional/regulatory/apply/student");
        dependency(workflow, "FORM", "student-registration-form", false);
        approve("HID-ONE");
        approve("HID-TWO");

        assertThat(validator.validate(release).valid()).isTrue();
    }

    @Test
    void anUnresolvedOptionalReferenceIsOnlyAWarning() {
        type("WORKFLOW", true, true);
        ConfigDefinitionVersionEntity workflow =
                item("WORKFLOW", "student-registration-flow", "/professional/regulatory/apply/student");
        dependency(workflow, "CORRESPONDENCE", "reminder-letter", true);
        approve("HID-ONE");
        approve("HID-TWO");

        ConfigReleaseValidator.ValidationReport report = validator.validate(release);

        assertThat(report.valid()).isTrue();
        assertThat(codes(report.warnings())).contains("OPTIONAL_DEPENDENCY_UNRESOLVED");
    }

    @Test
    void anUnsetPolicyValueIsAWarningAndNeverBlocksActivation() {
        // NCZ-DEC-002: the student index fee amount is a Council decision. The seam ships built and
        // the value absent; the capability surfaces as not configured rather than guessing a number.
        type("FEE_SCHEDULE", true, true);
        ConfigDefinitionVersionEntity fee =
                item("FEE_SCHEDULE", "student-index-fee", "/professional/regulatory/invoices");
        fee.setPolicyStatus("PENDING_REGULATOR_APPROVAL");
        fee.setPolicyDecisionRef("NCZ-DEC-002");
        approve("HID-ONE");
        approve("HID-TWO");

        ConfigReleaseValidator.ValidationReport report = validator.validate(release);

        assertThat(report.valid()).isTrue();
        assertThat(codes(report.errors())).isEmpty();
        assertThat(codes(report.warnings())).contains("POLICY_VALUE_UNSET");
        assertThat(report.warnings().getFirst().message()).contains("NCZ-DEC-002");
    }

    @Test
    void aFeeChangeNeedsTwoApprovers() {
        type("FEE_SCHEDULE", true, true);
        item("FEE_SCHEDULE", "student-index-fee", "/professional/regulatory/invoices");
        approve("HID-ONE");

        ConfigReleaseValidator.ValidationReport report = validator.validate(release);

        assertThat(report.valid()).isFalse();
        assertThat(report.fourEyesRequired()).isTrue();
        assertThat(codes(report.errors())).contains("INSUFFICIENT_APPROVALS");
    }

    @Test
    void anOrdinaryChangeNeedsOnlyOneApprover() {
        type("CORRESPONDENCE", false, true);
        item("CORRESPONDENCE", "acknowledgement", "/professional/regulatory/messages");
        approve("HID-ONE");

        ConfigReleaseValidator.ValidationReport report = validator.validate(release);

        assertThat(report.valid()).isTrue();
        assertThat(report.fourEyesRequired()).isFalse();
    }

    @Test
    void aRejectionBlocksActivationEvenWithEnoughApprovals() {
        type("CORRESPONDENCE", false, true);
        item("CORRESPONDENCE", "acknowledgement", "/professional/regulatory/messages");
        approve("HID-ONE");
        ConfigApprovalEntity rejection = new ConfigApprovalEntity();
        rejection.setReleaseId(release.getId());
        rejection.setApproverHealthId("HID-TWO");
        rejection.setDecision("REJECT");
        approvals.add(rejection);

        ConfigReleaseValidator.ValidationReport report = validator.validate(release);

        assertThat(report.valid()).isFalse();
        assertThat(codes(report.errors())).contains("RELEASE_REJECTED");
    }

    @Test
    void anUnapprovedVersionCannotBeActivated() {
        type("CORRESPONDENCE", false, true);
        ConfigDefinitionVersionEntity version =
                item("CORRESPONDENCE", "acknowledgement", "/professional/regulatory/messages");
        version.setLifecycleState("DRAFT");
        approve("HID-ONE");

        ConfigReleaseValidator.ValidationReport report = validator.validate(release);

        assertThat(report.valid()).isFalse();
        assertThat(codes(report.errors())).contains("VERSION_NOT_APPROVED");
    }

    @Test
    void aDefinitionTypeWithNoEngineIsRefused() {
        // Code owns the configuration language: a type the platform cannot execute is not
        // configuration, it is a promise nobody keeps.
        item("EXOTIC_TYPE", "something", "/somewhere");
        approve("HID-ONE");

        ConfigReleaseValidator.ValidationReport report = validator.validate(release);

        assertThat(report.valid()).isFalse();
        assertThat(codes(report.errors())).contains("TYPE_UNKNOWN");
    }
}
