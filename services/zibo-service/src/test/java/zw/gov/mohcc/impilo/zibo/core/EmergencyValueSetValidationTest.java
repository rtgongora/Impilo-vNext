package zw.gov.mohcc.impilo.zibo.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.zibo.config.ZiboProperties;
import zw.gov.mohcc.impilo.zibo.domain.ArtifactStatus;
import zw.gov.mohcc.impilo.zibo.domain.ArtifactType;
import zw.gov.mohcc.impilo.zibo.domain.PolicyMode;
import zw.gov.mohcc.impilo.zibo.persistence.entity.ArtifactEntity;
import zw.gov.mohcc.impilo.zibo.persistence.repository.ArtifactRepository;
import zw.gov.mohcc.impilo.zibo.persistence.repository.AssignmentRepository;
import zw.gov.mohcc.impilo.zibo.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.zibo.persistence.repository.ValidationJobRepository;
import zw.gov.mohcc.impilo.zibo.persistence.repository.ValidationLogRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * $validate-code proof for the emergency-pack value sets (zibo V200). Uses the SAME concept content
 * the migration seeds, so a real IITT priority and a real triage tool validate and an invented code
 * is flagged — the guard that keeps the terminology in step with the vocabularies the emergency
 * backend enforces (WhoIittEngine priorities, pct V202 chk_ed_triage_tool). If someone renames a
 * code here without matching the backend enum, this test does not catch the drift by itself, but a
 * code the backend uses that this rejects means the two have diverged.
 */
@ExtendWith(MockitoExtension.class)
class EmergencyValueSetValidationTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID CORRELATION = UUID.randomUUID();
    private static final String PRIORITY_SYSTEM = "https://impilo.gov.zw/fhir/CodeSystem/emergency-triage-priority";
    private static final String TOOL_SYSTEM = "https://impilo.gov.zw/fhir/CodeSystem/emergency-triage-tool";

    // Mirrors the concept[] seeded by V200__emergency_value_sets.sql.
    private static final String PRIORITY_CONTENT = """
            {"resourceType":"CodeSystem","url":"https://impilo.gov.zw/fhir/CodeSystem/emergency-triage-priority",
             "version":"1.0.0","status":"active","content":"complete","concept":[
              {"code":"RED","display":"Red — immediate"},
              {"code":"YELLOW","display":"Yellow — urgent"},
              {"code":"GREEN","display":"Green — non-urgent"},
              {"code":"NOT_TRIAGEABLE","display":"Not triageable — insufficient assessment to triage"}]}""";

    private static final String TOOL_CONTENT = """
            {"resourceType":"CodeSystem","url":"https://impilo.gov.zw/fhir/CodeSystem/emergency-triage-tool",
             "version":"1.0.0","status":"active","content":"complete","concept":[
              {"code":"WHO_IITT","display":"WHO Interagency Integrated Triage Tool"},
              {"code":"ESI","display":"Emergency Severity Index (advisory)"},
              {"code":"MTS","display":"Manchester Triage System (advisory)"},
              {"code":"IMPILO_5","display":"Manually-entered 5-level acuity"}]}""";

    @Mock private ArtifactRepository artifactRepository;
    @Mock private AssignmentRepository assignmentRepository;
    @Mock private ValidationJobRepository validationJobRepository;
    @Mock private ValidationLogRepository validationLogRepository;
    @Mock private EventOutboxRepository outboxRepository;

    private ValidationEngine engine;

    @BeforeEach
    void setUp() {
        ZiboProperties props = new ZiboProperties();
        props.getValidation().setDefaultMode("STRICT");
        engine = new ValidationEngine(artifactRepository, assignmentRepository, validationJobRepository,
                validationLogRepository, outboxRepository, props, new ObjectMapper());
    }

    private TrustContext ctx() {
        return new TrustContext(TENANT, "triage-nurse-1", "PROVIDER", "TREATMENT",
                null, CORRELATION, null, null, null, AccessMode.INTERNAL);
    }

    private ArtifactEntity publishedCodeSystem(String system, String content) {
        ArtifactEntity a = new ArtifactEntity();
        a.setArtifactId(UUID.randomUUID());
        a.setTenantId(TENANT);
        a.setFhirType(ArtifactType.CODE_SYSTEM);
        a.setCanonicalUrl(system);
        a.setVersion("1.0.0");
        a.setName("ImpiloEmergency");
        a.setStatus(ArtifactStatus.PUBLISHED);
        a.setContentJson(content);
        a.setContentHash("hash-emergency");
        a.setCreatedBy("emergency-valueset-seed");
        a.setPublishedBy("emergency-valueset-seed");
        a.setPublishedAt(OffsetDateTime.now());
        a.setCreatedAt(OffsetDateTime.now());
        a.setUpdatedAt(OffsetDateTime.now());
        return a;
    }

    @Test
    void iittPriorityCodesValidate() {
        try (MockedStatic<TrustContextHolder> h = mockStatic(TrustContextHolder.class)) {
            h.when(TrustContextHolder::require).thenReturn(ctx());
            h.when(TrustContextHolder::get).thenReturn(ctx());
            when(artifactRepository.findByTenantIdAndCanonicalUrl(TENANT, PRIORITY_SYSTEM))
                    .thenReturn(List.of(publishedCodeSystem(PRIORITY_SYSTEM, PRIORITY_CONTENT)));

            for (String code : List.of("RED", "YELLOW", "GREEN", "NOT_TRIAGEABLE")) {
                ValidationEngine.ValidationResult r =
                        engine.validateCoding(PRIORITY_SYSTEM, code, null, PolicyMode.STRICT, null);
                assertThat(r.valid()).as("IITT priority %s should validate", code).isTrue();
            }
        }
    }

    @Test
    void triageToolCodesValidateAndAnInventedToolIsRejected() {
        try (MockedStatic<TrustContextHolder> h = mockStatic(TrustContextHolder.class)) {
            h.when(TrustContextHolder::require).thenReturn(ctx());
            h.when(TrustContextHolder::get).thenReturn(ctx());
            when(artifactRepository.findByTenantIdAndCanonicalUrl(TENANT, TOOL_SYSTEM))
                    .thenReturn(List.of(publishedCodeSystem(TOOL_SYSTEM, TOOL_CONTENT)));

            assertThat(engine.validateCoding(TOOL_SYSTEM, "WHO_IITT", null, PolicyMode.STRICT, null).valid()).isTrue();
            ValidationEngine.ValidationResult bad =
                    engine.validateCoding(TOOL_SYSTEM, "GESTALT", null, PolicyMode.STRICT, null);
            assertThat(bad.valid()).isFalse();
            assertThat(bad.issues()).isNotEmpty();
        }
    }
}
