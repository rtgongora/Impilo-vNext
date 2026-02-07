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
import zw.gov.mohcc.impilo.zibo.domain.ArtifactStatus;
import zw.gov.mohcc.impilo.zibo.domain.ArtifactType;
import zw.gov.mohcc.impilo.zibo.persistence.entity.ArtifactEntity;
import zw.gov.mohcc.impilo.zibo.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.zibo.persistence.repository.ArtifactRepository;
import zw.gov.mohcc.impilo.zibo.persistence.repository.EventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ArtifactService}.
 *
 * <p>Validates the artifact lifecycle state machine including creation,
 * update, publish, deprecation, and retirement transitions.</p>
 */
@ExtendWith(MockitoExtension.class)
class ArtifactServiceTest {

    @Mock
    private ArtifactRepository artifactRepository;

    @Mock
    private EventOutboxRepository outboxRepository;

    private ArtifactService artifactService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID FACILITY_ID = UUID.randomUUID();
    private static final String ACTOR_ID = "admin-001";
    private static final UUID CORRELATION_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        artifactService = new ArtifactService(artifactRepository, outboxRepository, objectMapper);
    }

    private TrustContext createTrustContext() {
        return new TrustContext(TENANT_ID, ACTOR_ID, "ADMIN", "GOVERNANCE",
                null, CORRELATION_ID, FACILITY_ID, null, null, AccessMode.INTERNAL);
    }

    private ArtifactEntity createArtifactInStatus(ArtifactStatus status) {
        ArtifactEntity artifact = new ArtifactEntity();
        artifact.setArtifactId(UUID.randomUUID());
        artifact.setTenantId(TENANT_ID);
        artifact.setFhirType(ArtifactType.CODE_SYSTEM);
        artifact.setCanonicalUrl("http://example.org/CodeSystem/test");
        artifact.setVersion("1.0.0");
        artifact.setName("TestCodeSystem");
        artifact.setStatus(status);
        artifact.setContentJson("{\"resourceType\":\"CodeSystem\",\"concept\":[{\"code\":\"A\",\"display\":\"Alpha\"}]}");
        artifact.setContentHash("abc123");
        artifact.setCreatedBy(ACTOR_ID);
        artifact.setCreatedAt(OffsetDateTime.now());
        artifact.setUpdatedAt(OffsetDateTime.now());
        if (status == ArtifactStatus.PUBLISHED || status == ArtifactStatus.DEPRECATED || status == ArtifactStatus.RETIRED) {
            artifact.setPublishedAt(OffsetDateTime.now());
            artifact.setPublishedBy(ACTOR_ID);
        }
        if (status == ArtifactStatus.DEPRECATED || status == ArtifactStatus.RETIRED) {
            artifact.setDeprecatedAt(OffsetDateTime.now());
        }
        return artifact;
    }

    @Test
    @DisplayName("createDraft: success with valid inputs")
    void test_createDraft_success() {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(createTrustContext());

            when(artifactRepository.existsByTenantIdAndCanonicalUrlAndVersion(
                    any(UUID.class), anyString(), anyString())).thenReturn(false);
            when(artifactRepository.save(any(ArtifactEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(outboxRepository.save(any(EventOutboxEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            ArtifactEntity result = artifactService.createDraft(
                    ArtifactType.CODE_SYSTEM,
                    "http://example.org/CodeSystem/new",
                    "1.0.0",
                    "NewCodeSystem",
                    "New Code System",
                    "A test code system",
                    "{\"resourceType\":\"CodeSystem\"}",
                    "Test Publisher");

            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(ArtifactStatus.DRAFT);
            assertThat(result.getFhirType()).isEqualTo(ArtifactType.CODE_SYSTEM);
            assertThat(result.getCanonicalUrl()).isEqualTo("http://example.org/CodeSystem/new");
            assertThat(result.getVersion()).isEqualTo("1.0.0");
            assertThat(result.getCreatedBy()).isEqualTo(ACTOR_ID);
            assertThat(result.getTenantId()).isEqualTo(TENANT_ID);
            assertThat(result.getContentHash()).isNotBlank();

            verify(artifactRepository).save(any(ArtifactEntity.class));
            verify(outboxRepository).save(any(EventOutboxEntity.class));
        }
    }

    @Test
    @DisplayName("createDraft: duplicate canonical URL + version throws IllegalArgumentException")
    void test_createDraft_duplicateCanonicalVersion_throws() {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(createTrustContext());

            when(artifactRepository.existsByTenantIdAndCanonicalUrlAndVersion(
                    eq(TENANT_ID), anyString(), anyString())).thenReturn(true);

            assertThatThrownBy(() -> artifactService.createDraft(
                    ArtifactType.CODE_SYSTEM,
                    "http://example.org/CodeSystem/existing",
                    "1.0.0",
                    "ExistingCS",
                    null,
                    null,
                    "{\"resourceType\":\"CodeSystem\"}",
                    null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already exists");

            verify(artifactRepository, never()).save(any());
        }
    }

    @Test
    @DisplayName("publish: from DRAFT succeeds and transitions to PUBLISHED")
    void test_publish_fromDraft_succeeds() {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(createTrustContext());

            ArtifactEntity draft = createArtifactInStatus(ArtifactStatus.DRAFT);
            when(artifactRepository.findByTenantIdAndArtifactId(TENANT_ID, draft.getArtifactId()))
                    .thenReturn(Optional.of(draft));
            when(artifactRepository.save(any(ArtifactEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(outboxRepository.save(any(EventOutboxEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            ArtifactEntity result = artifactService.publish(draft.getArtifactId(), ACTOR_ID);

            assertThat(result.getStatus()).isEqualTo(ArtifactStatus.PUBLISHED);
            assertThat(result.getPublishedBy()).isEqualTo(ACTOR_ID);
            assertThat(result.getPublishedAt()).isNotNull();
        }
    }

    @Test
    @DisplayName("publish: already PUBLISHED throws IllegalStateException")
    void test_publish_alreadyPublished_throws() {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(createTrustContext());

            ArtifactEntity published = createArtifactInStatus(ArtifactStatus.DEPRECATED);
            when(artifactRepository.findByTenantIdAndArtifactId(TENANT_ID, published.getArtifactId()))
                    .thenReturn(Optional.of(published));

            assertThatThrownBy(() -> artifactService.publish(published.getArtifactId(), ACTOR_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("DRAFT");
        }
    }

    @Test
    @DisplayName("updateDraft: published artifact throws IllegalStateException (immutability)")
    void test_updateDraft_published_throws() {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(createTrustContext());

            ArtifactEntity published = createArtifactInStatus(ArtifactStatus.PUBLISHED);
            when(artifactRepository.findByTenantIdAndArtifactId(TENANT_ID, published.getArtifactId()))
                    .thenReturn(Optional.of(published));

            assertThatThrownBy(() -> artifactService.updateDraft(
                    published.getArtifactId(),
                    "UpdatedName",
                    "Updated Title",
                    "Updated Description",
                    "{\"resourceType\":\"CodeSystem\"}"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("DRAFT");

            verify(artifactRepository, never()).save(any());
        }
    }

    @Test
    @DisplayName("deprecate: from PUBLISHED succeeds and transitions to DEPRECATED")
    void test_deprecate_fromPublished_succeeds() {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(createTrustContext());

            ArtifactEntity published = createArtifactInStatus(ArtifactStatus.PUBLISHED);
            when(artifactRepository.findByTenantIdAndArtifactId(TENANT_ID, published.getArtifactId()))
                    .thenReturn(Optional.of(published));
            when(artifactRepository.save(any(ArtifactEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(outboxRepository.save(any(EventOutboxEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            ArtifactEntity result = artifactService.deprecate(published.getArtifactId());

            assertThat(result.getStatus()).isEqualTo(ArtifactStatus.DEPRECATED);
            assertThat(result.getDeprecatedAt()).isNotNull();
        }
    }

    @Test
    @DisplayName("retire: from DEPRECATED succeeds and transitions to RETIRED")
    void test_retire_fromDeprecated_succeeds() {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(createTrustContext());

            ArtifactEntity deprecated = createArtifactInStatus(ArtifactStatus.DEPRECATED);
            when(artifactRepository.findByTenantIdAndArtifactId(TENANT_ID, deprecated.getArtifactId()))
                    .thenReturn(Optional.of(deprecated));
            when(artifactRepository.save(any(ArtifactEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(outboxRepository.save(any(EventOutboxEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            ArtifactEntity result = artifactService.retire(deprecated.getArtifactId());

            assertThat(result.getStatus()).isEqualTo(ArtifactStatus.RETIRED);
        }
    }
}
