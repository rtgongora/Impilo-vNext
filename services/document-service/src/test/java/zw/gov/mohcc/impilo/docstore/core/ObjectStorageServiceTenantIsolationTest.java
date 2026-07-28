package zw.gov.mohcc.impilo.docstore.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.docstore.config.DocumentStoreProperties;
import zw.gov.mohcc.impilo.docstore.core.scan.ScanService;
import zw.gov.mohcc.impilo.docstore.core.storage.ObjectStorageProvider;
import zw.gov.mohcc.impilo.docstore.core.storage.ObjectStorageProviderRouter;
import zw.gov.mohcc.impilo.docstore.persistence.entity.ObjectEntity;
import zw.gov.mohcc.impilo.docstore.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.docstore.persistence.repository.ObjectRepository;
import zw.gov.mohcc.impilo.docstore.persistence.repository.SignedUrlRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Stored objects are tenant-scoped on READ (FCV-W0d).
 *
 * <p>The four read paths took only an object id. The entity has carried {@code tenantId} since it
 * was created and nothing compared it, so any authenticated internal caller who learned an id could
 * fetch the bytes — and the signed-url path handed back a URL needing no further authentication.
 * Evidence refs for facility claims travel through review queues, events and logs; "hard to guess"
 * is not an authorisation model.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ObjectStorageServiceTenantIsolationTest {

    private static final UUID MY_TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_TENANT = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock private ObjectStorageProviderRouter storageProviderRouter;
    @Mock private ObjectStorageProvider storageProvider;
    @Mock private ObjectRepository objectRepository;
    @Mock private SignedUrlRepository signedUrlRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private ScanService scanService;

    private ObjectStorageService service;
    private final UUID objectId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ObjectStorageService(new DocumentStoreProperties(), storageProviderRouter,
                objectRepository, signedUrlRepository, outboxRepository, scanService, new ObjectMapper());
        TrustContextHolder.set(new TrustContext(MY_TENANT, "caller-1", "SERVICE", null,
                null, UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private void stubObjectOwnedBy(UUID tenantId) {
        ObjectEntity e = new ObjectEntity();
        e.setObjectId(objectId);
        e.setTenantId(tenantId);
        e.setBucket("impilo");
        e.setObjectKey(tenantId + "/" + objectId + "/evidence.pdf");
        e.setOriginalFilename("evidence.pdf");
        e.setMimeType("application/pdf");
        when(objectRepository.findByObjectIdAndDeletedAtIsNull(objectId)).thenReturn(Optional.of(e));
    }

    @Test
    void anotherTenantsObjectIsNotReadable() {
        stubObjectOwnedBy(OTHER_TENANT);

        assertThatThrownBy(() -> service.getObject(objectId)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> service.getObjectEntity(objectId)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> service.downloadObject(objectId)).isInstanceOf(NoSuchElementException.class);

        // The bytes are never even requested from storage.
        verifyNoInteractions(storageProvider);
    }

    @Test
    void aSignedUrlIsNotMintedForAnotherTenantsObject() {
        // The worst of the four: a pre-signed URL needs no further authentication, so handing one
        // out is handing out the content itself.
        stubObjectOwnedBy(OTHER_TENANT);

        assertThatThrownBy(() -> service.generateSignedUrl(objectId))
                .isInstanceOf(NoSuchElementException.class);
        verifyNoInteractions(storageProvider);
    }

    @Test
    void anOutOfTenantObjectIsIndistinguishableFromOneThatDoesNotExist() {
        // Same exception either way, so a caller cannot use the difference to learn an id is real.
        stubObjectOwnedBy(OTHER_TENANT);
        Throwable crossTenant = org.assertj.core.api.Assertions
                .catchThrowable(() -> service.getObject(objectId));

        UUID unknown = UUID.randomUUID();
        when(objectRepository.findByObjectIdAndDeletedAtIsNull(unknown)).thenReturn(Optional.empty());
        Throwable absent = org.assertj.core.api.Assertions
                .catchThrowable(() -> service.getObject(unknown));

        assertThat(crossTenant).isInstanceOf(NoSuchElementException.class);
        assertThat(absent).isInstanceOf(NoSuchElementException.class);
        assertThat(crossTenant.getMessage().replace(objectId.toString(), "ID"))
                .isEqualTo(absent.getMessage().replace(unknown.toString(), "ID"));
    }

    @Test
    void myOwnTenantsObjectIsStillReadable() {
        stubObjectOwnedBy(MY_TENANT);

        assertThat(service.getObject(objectId)).isNotNull();
        assertThat(service.getObjectEntity(objectId).getTenantId()).isEqualTo(MY_TENANT);
    }
}
