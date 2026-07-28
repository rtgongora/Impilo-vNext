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
        return type(code, fourEyes, applicantFacing, false);
    }

    private ConfigDefinitionTypeEntity type(String code, boolean fourEyes, boolean applicantFacing,
                                            boolean carriesPolicyValue) {
        ConfigDefinitionTypeEntity type = new ConfigDefinitionTypeEntity();
        type.setTypeCode(code);
        type.setLabel(code);
        type.setRequiresFourEyes(fourEyes);
        type.setApplicantFacing(applicantFacing);
        type.setCarriesPolicyValue(carriesPolicyValue);
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
        type("FEE_SCHEDULE", true, true, true);
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
    void anUndeclaredPolicyValueIsAnErrorRatherThanSilence() {
        // The hole this closes: before V014, a fee authored with amount null and NO declared policy
        // status produced a completely clean report — it read as fully configured. The honest author
        // who declared PENDING got a warning; the one who declared nothing got a clean bill of
        // health. The discipline penalised honesty.
        type("FEE_SCHEDULE", true, true, true);
        ConfigDefinitionVersionEntity fee =
                item("FEE_SCHEDULE", "student-index-fee", "/professional/regulatory/invoices");
        fee.setPolicyStatus(null);
        approve("HID-ONE");
        approve("HID-TWO");

        ConfigReleaseValidator.ValidationReport report = validator.validate(release);

        assertThat(report.valid()).isFalse();
        assertThat(codes(report.errors())).contains("POLICY_STATUS_UNDECLARED");
    }

    @Test
    void aTypeThatCarriesNoPolicyValueNeedsNoDeclaration() {
        // A form does not have a "value the regulator must set" — requiring a declaration there
        // would be ceremony, and ceremony is what makes people stop reading findings.
        type("FORM", false, true, false);
        item("FORM", "student-registration-form", "/professional/regulatory/apply/student");
        approve("HID-ONE");

        assertThat(validator.validate(release).valid()).isTrue();
    }

    @Test
    void pendingWithoutNamingTheDecisionIsAnError() {
        type("PENALTY_POLICY", true, true, true);
        ConfigDefinitionVersionEntity penalty =
                item("PENALTY_POLICY", "renewal-penalty", "/professional/regulatory/renewal");
        penalty.setPolicyStatus("PENDING_REGULATOR_APPROVAL");
        penalty.setPolicyDecisionRef(null);
        approve("HID-ONE");
        approve("HID-TWO");

        ConfigReleaseValidator.ValidationReport report = validator.validate(release);

        assertThat(report.valid()).isFalse();
        assertThat(codes(report.errors())).contains("POLICY_DECISION_REF_MISSING");
    }

    @Test
    void anUnsetValueNothingDependsOnIsTheQuietWarning() {
        // NCZ-DEC-012: CPD rules are unset and deliberately inert — nothing is gated on them yet.
        type("CPD_RULES", false, false, true);
        ConfigDefinitionVersionEntity cpd = item("CPD_RULES", "cpd-cycle", null);
        cpd.setPolicyStatus("PENDING_REGULATOR_APPROVAL");
        cpd.setPolicyDecisionRef("NCZ-DEC-012");
        approve("HID-ONE");

        ConfigReleaseValidator.ValidationReport report = validator.validate(release);

        assertThat(report.valid()).isTrue();
        assertThat(codes(report.warnings())).contains("POLICY_VALUE_UNSET");
        assertThat(codes(report.warnings())).doesNotContain("POLICY_VALUE_UNSET_ON_CRITICAL_PATH");
    }

    @Test
    void anUnsetValueOnALiveJourneyIsTheLoudWarningAndNamesTheJourney() {
        // NCZ-DEC-002: the student index fee is unset AND the registration journey this release
        // publishes depends on it. Same lifecycle state as the CPD rules above; entirely different
        // consequence for an applicant, and the regulator should see which one they are approving.
        type("APPLICATION_TYPE", false, true, false);
        type("FEE_SCHEDULE", true, true, true);
        ConfigDefinitionVersionEntity application = item("APPLICATION_TYPE", "student-registration",
                "/professional/regulatory/apply/student");
        definitions.getLast().setLabel("Student registration");
        ConfigDefinitionVersionEntity fee =
                item("FEE_SCHEDULE", "student-index-fee", "/professional/regulatory/invoices");
        fee.setPolicyStatus("PENDING_REGULATOR_APPROVAL");
        fee.setPolicyDecisionRef("NCZ-DEC-002");
        dependency(application, "FEE_SCHEDULE", "student-index-fee", false);
        approve("HID-ONE");
        approve("HID-TWO");

        ConfigReleaseValidator.ValidationReport report = validator.validate(release);

        assertThat(report.valid()).isTrue();
        assertThat(codes(report.warnings())).contains("POLICY_VALUE_UNSET_ON_CRITICAL_PATH");
        ConfigReleaseValidator.Finding finding = report.warnings().stream()
                .filter(f -> "POLICY_VALUE_UNSET_ON_CRITICAL_PATH".equals(f.code())).findFirst()
                .orElseThrow();
        assertThat(finding.message()).contains("NCZ-DEC-002").contains("Student registration");
    }

    @Test
    void criticalPathReachesThroughAChainOfDependencies() {
        // application -> workflow -> numbering policy. The applicant never touches the numbering
        // policy directly, but they cannot finish registration without an index number.
        type("APPLICATION_TYPE", false, true, false);
        type("WORKFLOW", true, true, false);
        type("NUMBERING_POLICY", false, true, true);
        ConfigDefinitionVersionEntity application = item("APPLICATION_TYPE", "student-registration",
                "/professional/regulatory/apply/student");
        definitions.getLast().setLabel("Student registration");
        ConfigDefinitionVersionEntity workflow =
                item("WORKFLOW", "student-flow", "/professional/regulatory/apply/student");
        ConfigDefinitionVersionEntity numbering =
                item("NUMBERING_POLICY", "student-index", "/professional/regulatory/registration");
        numbering.setPolicyStatus("PENDING_REGULATOR_APPROVAL");
        numbering.setPolicyDecisionRef("NCZ-DEC-001");
        dependency(application, "WORKFLOW", "student-flow", false);
        dependency(workflow, "NUMBERING_POLICY", "student-index", false);
        approve("HID-ONE");
        approve("HID-TWO");

        ConfigReleaseValidator.ValidationReport report = validator.validate(release);

        assertThat(codes(report.warnings())).contains("POLICY_VALUE_UNSET_ON_CRITICAL_PATH");
    }

    @Test
    void anOptionalDependencyDoesNotPutAnUnsetValueOnTheCriticalPath() {
        type("APPLICATION_TYPE", false, true, false);
        type("PENALTY_POLICY", true, false, true);
        ConfigDefinitionVersionEntity application = item("APPLICATION_TYPE", "student-registration",
                "/professional/regulatory/apply/student");
        ConfigDefinitionVersionEntity penalty = item("PENALTY_POLICY", "renewal-penalty", null);
        penalty.setPolicyStatus("PENDING_REGULATOR_APPROVAL");
        penalty.setPolicyDecisionRef("NCZ-DEC-003");
        dependency(application, "PENALTY_POLICY", "renewal-penalty", true);
        approve("HID-ONE");
        approve("HID-TWO");

        ConfigReleaseValidator.ValidationReport report = validator.validate(release);

        assertThat(codes(report.warnings())).contains("POLICY_VALUE_UNSET");
        assertThat(codes(report.warnings())).doesNotContain("POLICY_VALUE_UNSET_ON_CRITICAL_PATH");
    }

    @Test
    void aFeeChangeNeedsTwoApprovers() {
        type("FEE_SCHEDULE", true, true, true);
        item("FEE_SCHEDULE", "student-index-fee", "/professional/regulatory/invoices")
                .setPolicyStatus("CONFIRMED");
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
