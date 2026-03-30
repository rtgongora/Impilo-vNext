package zw.gov.mohcc.impilo.zibo.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import zw.gov.mohcc.impilo.zibo.domain.ScopeType;
import zw.gov.mohcc.impilo.zibo.domain.ValidationSeverity;
import zw.gov.mohcc.impilo.zibo.persistence.entity.ArtifactEntity;
import zw.gov.mohcc.impilo.zibo.persistence.entity.AssignmentEntity;
import zw.gov.mohcc.impilo.zibo.persistence.entity.ValidationLogEntity;
import zw.gov.mohcc.impilo.zibo.persistence.repository.ArtifactRepository;
import zw.gov.mohcc.impilo.zibo.persistence.repository.AssignmentRepository;
import zw.gov.mohcc.impilo.zibo.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.zibo.persistence.repository.ValidationJobRepository;
import zw.gov.mohcc.impilo.zibo.persistence.repository.ValidationLogRepository;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ValidationEngine}.
 *
 * <p>Validates coding validation logic, including known/unknown code handling,
 * policy mode resolution, and strict vs lenient behavior.</p>
 */
@ExtendWith(MockitoExtension.class)
class ValidationEngineTest {

    @Mock
    private ArtifactRepository artifactRepository;

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private ValidationJobRepository validationJobRepository;

    @Mock
    private ValidationLogRepository validationLogRepository;

    @Mock
    private EventOutboxRepository outboxRepository;

    private ValidationEngine validationEngine;
    private ZiboProperties ziboProperties;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID FACILITY_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final String ACTOR_ID = "clinician-001";
    private static final UUID CORRELATION_ID = UUID.randomUUID();
    private static final String CODE_SYSTEM_URL = "http://example.org/CodeSystem/diagnoses";

    @BeforeEach
    void setUp() {
        ziboProperties = new ZiboProperties();
        ziboProperties.getValidation().setDefaultMode("LENIENT");

        ObjectMapper objectMapper = new ObjectMapper();
        validationEngine = new ValidationEngine(
                artifactRepository,
                assignmentRepository,
                validationJobRepository,
                validationLogRepository,
                outboxRepository,
                ziboProperties,
                objectMapper);
    }

    private TrustContext createTrustContext() {
        return new TrustContext(TENANT_ID, ACTOR_ID, "PROVIDER", "TREATMENT",
                null, CORRELATION_ID, FACILITY_ID, WORKSPACE_ID, null, "national", AccessMode.INTERNAL);
    }

    private TrustContext createTrustContextNoWorkspace() {
        return new TrustContext(TENANT_ID, ACTOR_ID, "PROVIDER", "TREATMENT",
                null, CORRELATION_ID, FACILITY_ID, null, null, "national", AccessMode.INTERNAL);
    }

    private ArtifactEntity createPublishedCodeSystem(String contentJson) {
        ArtifactEntity artifact = new ArtifactEntity();
        artifact.setArtifactId(UUID.randomUUID());
        artifact.setTenantId(TENANT_ID);
        artifact.setFhirType(ArtifactType.CODE_SYSTEM);
        artifact.setCanonicalUrl(CODE_SYSTEM_URL);
        artifact.setVersion("1.0.0");
        artifact.setName("Diagnoses");
        artifact.setStatus(ArtifactStatus.PUBLISHED);
        artifact.setContentJson(contentJson);
        artifact.setContentHash("hash123");
        artifact.setCreatedBy(ACTOR_ID);
        artifact.setPublishedAt(OffsetDateTime.now());
        artifact.setPublishedBy(ACTOR_ID);
        artifact.setCreatedAt(OffsetDateTime.now());
        artifact.setUpdatedAt(OffsetDateTime.now());
        return artifact;
    }

    @Test
    @DisplayName("validateCoding: known code returns valid result")
    void test_validateCoding_knownCode_valid() {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(createTrustContext());
            holder.when(TrustContextHolder::get).thenReturn(createTrustContext());

            String content = "{\"concept\":[{\"code\":\"A01\",\"display\":\"Typhoid fever\"},{\"code\":\"B02\",\"display\":\"Zoster\"}]}";
            ArtifactEntity codeSystem = createPublishedCodeSystem(content);

            when(artifactRepository.findByTenantIdAndCanonicalUrl(TENANT_ID, CODE_SYSTEM_URL))
                    .thenReturn(List.of(codeSystem));
            when(assignmentRepository.findByTenantIdAndScopeTypeAndScopeIdAndActiveTrue(
                    any(), any(), any())).thenReturn(Collections.emptyList());

            ValidationEngine.ValidationResult result = validationEngine.validateCoding(
                    CODE_SYSTEM_URL, "A01", "Typhoid fever", null, null);

            assertThat(result.valid()).isTrue();
            assertThat(result.issues()).isEmpty();
        }
    }

    @Test
    @DisplayName("validateCoding: unknown code in STRICT mode returns ERROR")
    void test_validateCoding_unknownCode_strict_returnsError() {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(createTrustContext());
            holder.when(TrustContextHolder::get).thenReturn(createTrustContext());

            String content = "{\"concept\":[{\"code\":\"A01\",\"display\":\"Typhoid fever\"}]}";
            ArtifactEntity codeSystem = createPublishedCodeSystem(content);

            when(artifactRepository.findByTenantIdAndCanonicalUrl(TENANT_ID, CODE_SYSTEM_URL))
                    .thenReturn(List.of(codeSystem));
            when(assignmentRepository.findByTenantIdAndScopeTypeAndScopeIdAndActiveTrue(
                    any(), any(), any())).thenReturn(Collections.emptyList());
            when(validationLogRepository.save(any(ValidationLogEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            ValidationEngine.ValidationResult result = validationEngine.validateCoding(
                    CODE_SYSTEM_URL, "UNKNOWN_CODE", null, PolicyMode.STRICT, null);

            assertThat(result.valid()).isFalse();
            assertThat(result.issues()).hasSize(1);
            assertThat(result.issues().get(0).severity()).isEqualTo(ValidationSeverity.ERROR);
            assertThat(result.issues().get(0).code()).isEqualTo("code-invalid");
        }
    }

    @Test
    @DisplayName("validateCoding: unknown code in LENIENT mode returns WARNING")
    void test_validateCoding_unknownCode_lenient_returnsWarning() {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(createTrustContext());
            holder.when(TrustContextHolder::get).thenReturn(createTrustContext());

            String content = "{\"concept\":[{\"code\":\"A01\",\"display\":\"Typhoid fever\"}]}";
            ArtifactEntity codeSystem = createPublishedCodeSystem(content);

            when(artifactRepository.findByTenantIdAndCanonicalUrl(TENANT_ID, CODE_SYSTEM_URL))
                    .thenReturn(List.of(codeSystem));
            when(assignmentRepository.findByTenantIdAndScopeTypeAndScopeIdAndActiveTrue(
                    any(), any(), any())).thenReturn(Collections.emptyList());
            when(validationLogRepository.save(any(ValidationLogEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            ValidationEngine.ValidationResult result = validationEngine.validateCoding(
                    CODE_SYSTEM_URL, "UNKNOWN_CODE", null, PolicyMode.LENIENT, null);

            assertThat(result.valid()).isTrue();
            assertThat(result.issues()).hasSize(1);
            assertThat(result.issues().get(0).severity()).isEqualTo(ValidationSeverity.WARNING);
            assertThat(result.issues().get(0).code()).isEqualTo("code-invalid");
        }
    }

    @Test
    @DisplayName("resolveEffectiveMode: explicit override takes precedence")
    void test_resolveEffectiveMode_parameterOverride() {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::get).thenReturn(createTrustContext());

            PolicyMode result = validationEngine.resolveEffectiveMode(
                    TENANT_ID, FACILITY_ID, PolicyMode.STRICT);

            assertThat(result).isEqualTo(PolicyMode.STRICT);
        }
    }

    @Test
    @DisplayName("resolveEffectiveMode: facility assignment overrides tenant default")
    void test_resolveEffectiveMode_facilityOverride() {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::get).thenReturn(createTrustContextNoWorkspace());

            AssignmentEntity facilityAssignment = new AssignmentEntity();
            facilityAssignment.setPolicyMode(PolicyMode.STRICT);
            facilityAssignment.setActive(true);

            when(assignmentRepository.findByTenantIdAndScopeTypeAndScopeIdAndActiveTrue(
                    TENANT_ID, ScopeType.FACILITY, FACILITY_ID))
                    .thenReturn(List.of(facilityAssignment));

            PolicyMode result = validationEngine.resolveEffectiveMode(
                    TENANT_ID, FACILITY_ID, null);

            assertThat(result).isEqualTo(PolicyMode.STRICT);
        }
    }

    @Test
    @DisplayName("resolveEffectiveMode: tenant default from configuration when no assignments")
    void test_resolveEffectiveMode_tenantDefault() {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::get).thenReturn(createTrustContextNoWorkspace());

            when(assignmentRepository.findByTenantIdAndScopeTypeAndScopeIdAndActiveTrue(
                    any(), any(), any())).thenReturn(Collections.emptyList());

            ziboProperties.getValidation().setDefaultMode("LENIENT");

            PolicyMode result = validationEngine.resolveEffectiveMode(
                    TENANT_ID, FACILITY_ID, null);

            assertThat(result).isEqualTo(PolicyMode.LENIENT);
        }
    }
}
