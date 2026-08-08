package zw.gov.mohcc.impilo.zibo.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.zibo.config.ZiboProperties;
import zw.gov.mohcc.impilo.zibo.core.*;
import zw.gov.mohcc.impilo.zibo.domain.ArtifactStatus;
import zw.gov.mohcc.impilo.zibo.domain.ArtifactType;
import zw.gov.mohcc.impilo.zibo.domain.PolicyMode;
import zw.gov.mohcc.impilo.zibo.domain.ScopeType;
import zw.gov.mohcc.impilo.zibo.persistence.entity.*;
import zw.gov.mohcc.impilo.zibo.persistence.repository.*;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration tests for the ZIBO terminology service.
 *
 * <p>Tests end-to-end workflows using real service instances backed by
 * mocked repositories. Validates cross-service interactions including
 * import, publish, validate, pack creation, and assignment flows.</p>
 */
@ExtendWith(MockitoExtension.class)
class TerminologyIntegrationTest {

    @Mock private ArtifactRepository artifactRepository;
    @Mock private PackRepository packRepository;
    @Mock private PackArtifactRepository packArtifactRepository;
    @Mock private AssignmentRepository assignmentRepository;
    @Mock private MappingIndexRepository mappingIndexRepository;
    @Mock private ValidationJobRepository validationJobRepository;
    @Mock private ValidationLogRepository validationLogRepository;
    @Mock private EventOutboxRepository outboxRepository;

    private ArtifactService artifactService;
    private PackService packService;
    private ValidationEngine validationEngine;
    private MappingService mappingService;
    private GovernanceService governanceService;
    private ExportService exportService;

    private ObjectMapper objectMapper;
    private ZiboProperties ziboProperties;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID FACILITY_ID = UUID.randomUUID();
    private static final String ACTOR_ID = "admin-001";
    private static final UUID CORRELATION_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        ziboProperties = new ZiboProperties();
        ziboProperties.getValidation().setDefaultMode("LENIENT");

        artifactService = new ArtifactService(artifactRepository, outboxRepository, objectMapper);
        packService = new PackService(packRepository, packArtifactRepository,
                artifactRepository, outboxRepository, objectMapper);
        validationEngine = new ValidationEngine(artifactRepository, assignmentRepository,
                validationJobRepository, validationLogRepository, outboxRepository,
                ziboProperties, objectMapper,
                new ArtifactResolutionService(artifactRepository, new SimpleMeterRegistry(),
                        "00000000-0000-0000-0000-000000000001"),
                new SimpleMeterRegistry());
        mappingService = new MappingService(mappingIndexRepository, artifactRepository,
                objectMapper, artifactService);
        governanceService = new GovernanceService(assignmentRepository, packRepository,
                validationLogRepository, outboxRepository, objectMapper);
        exportService = new ExportService(packService, artifactRepository,
                packArtifactRepository, objectMapper);
    }

    private TrustContext createTrustContext() {
        return new TrustContext(TENANT_ID, ACTOR_ID, "ADMIN", "GOVERNANCE",
                null, CORRELATION_ID, FACILITY_ID, null, null, AccessMode.INTERNAL);
    }

    @Test
    @DisplayName("Import CodeSystem, publish, then validate coding against it")
    void test_importCodeSystem_publish_validateCoding() {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(createTrustContext());
            holder.when(TrustContextHolder::get).thenReturn(createTrustContext());

            // Track saved artifacts
            List<ArtifactEntity> savedArtifacts = new ArrayList<>();

            when(artifactRepository.existsByTenantIdAndCanonicalUrlAndVersion(
                    any(), any(), any())).thenReturn(false);
            when(artifactRepository.save(any(ArtifactEntity.class))).thenAnswer(invocation -> {
                ArtifactEntity e = invocation.getArgument(0);
                savedArtifacts.add(e);
                return e;
            });
            when(outboxRepository.save(any(EventOutboxEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Step 1: Create a CodeSystem artifact as draft
            String codeSystemContent = """
                    {
                      "resourceType": "CodeSystem",
                      "url": "http://example.org/CodeSystem/test-diagnoses",
                      "name": "TestDiagnoses",
                      "version": "1.0.0",
                      "concept": [
                        {"code": "A01", "display": "Typhoid fever"},
                        {"code": "A02", "display": "Salmonella"},
                        {"code": "B01", "display": "Varicella"}
                      ]
                    }
                    """;

            ArtifactEntity draft = artifactService.createDraft(
                    ArtifactType.CODE_SYSTEM,
                    "http://example.org/CodeSystem/test-diagnoses",
                    "1.0.0",
                    "TestDiagnoses",
                    "Test Diagnoses",
                    "A test code system",
                    codeSystemContent,
                    "Test Publisher");

            assertThat(draft).isNotNull();
            assertThat(draft.getStatus()).isEqualTo(ArtifactStatus.DRAFT);

            // Step 2: Publish the artifact
            when(artifactRepository.findByTenantIdAndArtifactId(TENANT_ID, draft.getArtifactId()))
                    .thenReturn(Optional.of(draft));

            ArtifactEntity published = artifactService.publish(draft.getArtifactId(), ACTOR_ID);

            assertThat(published.getStatus()).isEqualTo(ArtifactStatus.PUBLISHED);
            assertThat(published.getPublishedAt()).isNotNull();

            // Step 3: Validate a known coding
            when(artifactRepository.findCandidatesAcrossPlanes(
                    "http://example.org/CodeSystem/test-diagnoses", java.util.List.of(TENANT_ID, java.util.UUID.fromString("00000000-0000-0000-0000-000000000001")),
                    zw.gov.mohcc.impilo.zibo.domain.ArtifactType.CODE_SYSTEM))
                    .thenReturn(List.of(published));
            when(assignmentRepository.findByTenantIdAndScopeTypeAndScopeIdAndActiveTrue(
                    any(), any(), any())).thenReturn(Collections.emptyList());

            ValidationEngine.ValidationResult validResult = validationEngine.validateCoding(
                    "http://example.org/CodeSystem/test-diagnoses",
                    "A01", "Typhoid fever", null, null);

            assertThat(validResult.valid()).isTrue();
            assertThat(validResult.issues()).isEmpty();

            // Step 4: Validate an unknown coding
            when(validationLogRepository.save(any(ValidationLogEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            ValidationEngine.ValidationResult invalidResult = validationEngine.validateCoding(
                    "http://example.org/CodeSystem/test-diagnoses",
                    "Z99", null, PolicyMode.STRICT, null);

            assertThat(invalidResult.valid()).isFalse();
            assertThat(invalidResult.issues()).hasSize(1);
            assertThat(invalidResult.issues().get(0).code()).isEqualTo("code-invalid");
        }
    }

    @Test
    @DisplayName("Create pack, add artifacts, publish, then export as bundle")
    void test_createPack_addArtifacts_publish_export() {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(createTrustContext());

            // Setup artifact
            UUID artifactId = UUID.randomUUID();
            ArtifactEntity artifact = new ArtifactEntity();
            artifact.setArtifactId(artifactId);
            artifact.setTenantId(TENANT_ID);
            artifact.setFhirType(ArtifactType.CODE_SYSTEM);
            artifact.setCanonicalUrl("http://example.org/CodeSystem/export-test");
            artifact.setVersion("1.0.0");
            artifact.setName("ExportTest");
            artifact.setStatus(ArtifactStatus.PUBLISHED);
            artifact.setContentJson("{\"resourceType\":\"CodeSystem\",\"url\":\"http://example.org/CodeSystem/export-test\",\"concept\":[{\"code\":\"X1\",\"display\":\"Test\"}]}");
            artifact.setContentHash("hash");
            artifact.setCreatedBy(ACTOR_ID);
            artifact.setPublishedAt(OffsetDateTime.now());
            artifact.setPublishedBy(ACTOR_ID);
            artifact.setCreatedAt(OffsetDateTime.now());
            artifact.setUpdatedAt(OffsetDateTime.now());

            // Step 1: Create pack
            when(packRepository.findByTenantIdAndNameAndVersion(TENANT_ID, "TestPack", "1.0.0"))
                    .thenReturn(Optional.empty());
            when(packRepository.save(any(PackEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            PackEntity pack = packService.createPack("TestPack", "1.0.0", "A test pack", null);

            assertThat(pack).isNotNull();
            assertThat(pack.getStatus()).isEqualTo(ArtifactStatus.DRAFT);
            assertThat(pack.getName()).isEqualTo("TestPack");

            // Step 2: Add artifact to pack
            when(packRepository.findByTenantIdAndPackId(TENANT_ID, pack.getPackId()))
                    .thenReturn(Optional.of(pack));
            when(artifactRepository.findByTenantIdAndArtifactId(TENANT_ID, artifactId))
                    .thenReturn(Optional.of(artifact));
            when(packArtifactRepository.existsByPackIdAndArtifactId(pack.getPackId(), artifactId))
                    .thenReturn(false);
            when(packArtifactRepository.save(any(PackArtifactEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            PackArtifactEntity pa = packService.addArtifact(pack.getPackId(), artifactId);
            assertThat(pa).isNotNull();
            assertThat(pa.getArtifactId()).isEqualTo(artifactId);

            // Step 3: Publish pack
            when(packArtifactRepository.findByPackId(pack.getPackId()))
                    .thenReturn(List.of(pa));
            when(outboxRepository.save(any(EventOutboxEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            PackEntity publishedPack = packService.publishPack(pack.getPackId());
            assertThat(publishedPack.getStatus()).isEqualTo(ArtifactStatus.PUBLISHED);

            // Step 4: Export pack as bundle
            String bundle = exportService.exportPackAsBundle(pack.getPackId());
            assertThat(bundle).isNotNull();
            assertThat(bundle).contains("Bundle");
            assertThat(bundle).contains("collection");
        }
    }

    @Test
    @DisplayName("Assign pack to facility scope and validate in STRICT mode")
    void test_assignPack_validateInStrictMode() {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(createTrustContext());
            holder.when(TrustContextHolder::get).thenReturn(createTrustContext());

            // Setup published CodeSystem
            UUID artifactId = UUID.randomUUID();
            ArtifactEntity codeSystem = new ArtifactEntity();
            codeSystem.setArtifactId(artifactId);
            codeSystem.setTenantId(TENANT_ID);
            codeSystem.setFhirType(ArtifactType.CODE_SYSTEM);
            codeSystem.setCanonicalUrl("http://example.org/CodeSystem/strict-test");
            codeSystem.setVersion("1.0.0");
            codeSystem.setName("StrictTest");
            codeSystem.setStatus(ArtifactStatus.PUBLISHED);
            codeSystem.setContentJson("{\"concept\":[{\"code\":\"VALID\",\"display\":\"Valid Code\"}]}");
            codeSystem.setContentHash("hash");
            codeSystem.setCreatedBy(ACTOR_ID);
            codeSystem.setPublishedAt(OffsetDateTime.now());
            codeSystem.setPublishedBy(ACTOR_ID);
            codeSystem.setCreatedAt(OffsetDateTime.now());
            codeSystem.setUpdatedAt(OffsetDateTime.now());

            // Setup pack
            UUID packId = UUID.randomUUID();
            PackEntity pack = new PackEntity();
            pack.setPackId(packId);
            pack.setTenantId(TENANT_ID);
            pack.setName("StrictPack");
            pack.setVersion("1.0.0");
            pack.setStatus(ArtifactStatus.PUBLISHED);
            pack.setCreatedBy(ACTOR_ID);
            pack.setCreatedAt(OffsetDateTime.now());
            pack.setUpdatedAt(OffsetDateTime.now());

            // Step 1: Assign pack with STRICT mode
            when(packRepository.findByTenantIdAndPackId(TENANT_ID, packId))
                    .thenReturn(Optional.of(pack));
            when(assignmentRepository.findByTenantIdAndScopeTypeAndScopeIdAndPackId(
                    TENANT_ID, ScopeType.FACILITY, FACILITY_ID, packId))
                    .thenReturn(Optional.empty());
            when(assignmentRepository.save(any(AssignmentEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(outboxRepository.save(any(EventOutboxEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            AssignmentEntity assignment = governanceService.assignPack(
                    ScopeType.FACILITY, FACILITY_ID, packId, "1.0.0", PolicyMode.STRICT);

            assertThat(assignment).isNotNull();
            assertThat(assignment.getPolicyMode()).isEqualTo(PolicyMode.STRICT);
            assertThat(assignment.isActive()).isTrue();

            // Step 2: Setup facility assignment for policy resolution
            AssignmentEntity facilityAssignment = new AssignmentEntity();
            facilityAssignment.setPolicyMode(PolicyMode.STRICT);
            facilityAssignment.setActive(true);

            when(assignmentRepository.findByTenantIdAndScopeTypeAndScopeIdAndActiveTrue(
                    eq(TENANT_ID), eq(ScopeType.FACILITY), eq(FACILITY_ID)))
                    .thenReturn(List.of(facilityAssignment));

            // Step 3: Validate a known code
            when(artifactRepository.findCandidatesAcrossPlanes(
                    "http://example.org/CodeSystem/strict-test", java.util.List.of(TENANT_ID, java.util.UUID.fromString("00000000-0000-0000-0000-000000000001")),
                    zw.gov.mohcc.impilo.zibo.domain.ArtifactType.CODE_SYSTEM))
                    .thenReturn(List.of(codeSystem));

            ValidationEngine.ValidationResult validResult = validationEngine.validateCoding(
                    "http://example.org/CodeSystem/strict-test",
                    "VALID", "Valid Code", null, FACILITY_ID);

            assertThat(validResult.valid()).isTrue();
            assertThat(validResult.issues()).isEmpty();

            // Step 4: Validate an unknown code in STRICT mode
            when(validationLogRepository.save(any(ValidationLogEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            ValidationEngine.ValidationResult invalidResult = validationEngine.validateCoding(
                    "http://example.org/CodeSystem/strict-test",
                    "INVALID_CODE", null, null, FACILITY_ID);

            assertThat(invalidResult.valid()).isFalse();
            assertThat(invalidResult.issues()).hasSize(1);
            assertThat(invalidResult.issues().get(0).severity())
                    .isEqualTo(zw.gov.mohcc.impilo.zibo.domain.ValidationSeverity.ERROR);
        }
    }
}
