package zw.gov.mohcc.impilo.docstore.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.docstore.config.DocumentStoreProperties;
import zw.gov.mohcc.impilo.docstore.core.scan.ScanService;
import zw.gov.mohcc.impilo.docstore.core.storage.ObjectStorageProvider;
import zw.gov.mohcc.impilo.docstore.core.storage.ObjectStorageProviderRouter;
import zw.gov.mohcc.impilo.docstore.dto.RegisterExternalObjectRequest;
import zw.gov.mohcc.impilo.docstore.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.docstore.persistence.entity.ObjectEntity;
import zw.gov.mohcc.impilo.docstore.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.docstore.persistence.repository.ObjectRepository;
import zw.gov.mohcc.impilo.docstore.persistence.repository.SignedUrlRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Proves the W1 external-adoption path: recording artifacts written straight to MinIO
 * by rtc-gateway are catalogued through the same metadata path as multipart ingest —
 * existence-verified (statObject), idempotent on bucket+key, and audited via the
 * document.stored outbox event.
 */
@ExtendWith(MockitoExtension.class)
class ObjectStorageServiceRegisterExternalTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String BUCKET = "impilo-recordings";
    private static final String KEY = "rtc/sessions/sess-1/egress-9.mp4";

    @Mock private ObjectStorageProviderRouter storageProviderRouter;
    @Mock private ObjectStorageProvider storageProvider;
    @Mock private ObjectRepository objectRepository;
    @Mock private SignedUrlRepository signedUrlRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private ScanService scanService;

    private ObjectStorageService service;

    @BeforeEach
    void setUp() {
        service = new ObjectStorageService(new DocumentStoreProperties(), storageProviderRouter,
                objectRepository, signedUrlRepository, outboxRepository, scanService, new ObjectMapper());
        TrustContextHolder.set(new TrustContext(TENANT_ID, "live-service", "SERVICE", null,
                null, UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    @Test
    void registerExternal_adoptsExistingMinioObjectThroughSharedCataloguePath() {
        when(objectRepository.findFirstByBucketAndObjectKeyAndDeletedAtIsNull(BUCKET, KEY))
                .thenReturn(Optional.empty());
        when(storageProviderRouter.activeProvider()).thenReturn(storageProvider);
        when(storageProvider.providerType()).thenReturn("MINIO");
        when(storageProvider.statObject(BUCKET, KEY))
                .thenReturn(new ObjectStorageProvider.ObjectStat(4_242L, "video/mp4"));
        when(objectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service.registerExternal(request(null));

        assertThat(result.created()).isTrue();
        assertThat(result.response().objectId()).isNotNull();
        assertThat(result.response().tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.response().bucket()).isEqualTo(BUCKET);
        assertThat(result.response().objectKey()).isEqualTo(KEY);
        assertThat(result.response().mimeType()).isEqualTo("video/mp4");
        assertThat(result.response().fileSizeBytes()).isEqualTo(4_242L);
        assertThat(result.response().originalFilename()).isEqualTo("Town hall — replay recording");
        assertThat(result.response().scanStatus()).isEqualTo("SKIPPED");
        assertThat(result.response().metadata())
                .containsEntry("ownerService", "LIVE")
                .containsEntry("source", "EXTERNAL_REGISTRATION")
                .containsEntry("egressId", "egress-9");

        // Cataloguing reuse: the same document.stored outbox event as multipart ingest.
        ArgumentCaptor<EventOutboxEntity> event = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(event.capture());
        assertThat(event.getValue().getEventType()).isEqualTo("document.stored");
        assertThat(event.getValue().getPayload()).contains("video/mp4");
    }

    @Test
    void registerExternal_missingObjectInStorage_failsWithoutCataloguing() {
        when(objectRepository.findFirstByBucketAndObjectKeyAndDeletedAtIsNull(BUCKET, KEY))
                .thenReturn(Optional.empty());
        when(storageProviderRouter.activeProvider()).thenReturn(storageProvider);
        when(storageProvider.statObject(BUCKET, KEY)).thenReturn(null);

        assertThatThrownBy(() -> service.registerExternal(request(null)))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining(BUCKET + "/" + KEY);

        verify(objectRepository, never()).save(any());
        verifyNoInteractions(outboxRepository);
    }

    @Test
    void registerExternal_alreadyCatalogued_isIdempotentAndReturnsExistingObject() {
        UUID existingObjectId = UUID.randomUUID();
        ObjectEntity existing = new ObjectEntity();
        existing.setObjectId(existingObjectId);
        existing.setTenantId(TENANT_ID);
        existing.setBucket(BUCKET);
        existing.setObjectKey(KEY);
        existing.setMimeType("video/mp4");
        existing.setFileSizeBytes(4_242L);
        existing.setHashSha256("");
        existing.setCreatedBy("live-service");
        when(objectRepository.findFirstByBucketAndObjectKeyAndDeletedAtIsNull(BUCKET, KEY))
                .thenReturn(Optional.of(existing));

        var first = service.registerExternal(request(null));
        var second = service.registerExternal(request("also-idempotent-with-sha"));

        assertThat(first.created()).isFalse();
        assertThat(second.created()).isFalse();
        assertThat(first.response().objectId()).isEqualTo(existingObjectId);
        assertThat(second.response().objectId()).isEqualTo(existingObjectId);
        verify(objectRepository, never()).save(any());
        verifyNoInteractions(storageProviderRouter, outboxRepository);
    }

    @Test
    void registerExternal_rejectsIncompleteRequests() {
        assertThatThrownBy(() -> service.registerExternal(new RegisterExternalObjectRequest(
                null, KEY, "video/mp4", null, null, "LIVE", null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bucket");
        assertThatThrownBy(() -> service.registerExternal(new RegisterExternalObjectRequest(
                BUCKET, KEY, "video/mp4", null, null, " ", null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ownerService");
        verify(objectRepository, never()).findFirstByBucketAndObjectKeyAndDeletedAtIsNull(anyString(), anyString());
    }

    private static RegisterExternalObjectRequest request(String sha256) {
        return new RegisterExternalObjectRequest(
                BUCKET, KEY, "video/mp4", 4_242L, sha256, "LIVE",
                "Town hall — replay recording", Map.of("egressId", "egress-9"));
    }
}
