package zw.gov.mohcc.impilo.msika.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.msika.api.dto.CreateCatalogRequest;
import zw.gov.mohcc.impilo.msika.persistence.entity.CatalogEntity;
import zw.gov.mohcc.impilo.msika.persistence.repository.CatalogItemRepository;
import zw.gov.mohcc.impilo.msika.persistence.repository.CatalogRepository;
import zw.gov.mohcc.impilo.msika.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {

    @Mock private CatalogRepository catalogRepository;
    @Mock private CatalogItemRepository itemRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private ChangeLogService changeLogService;

    private CatalogService catalogService;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        catalogService = new CatalogService(catalogRepository, itemRepository, outboxRepository, changeLogService, objectMapper);
        TrustContext ctx = new TrustContext(
                UUID.randomUUID(), "actor-1", "ADMIN", "ADMIN",
                "test-device", UUID.randomUUID(), null, null, null, AccessMode.INTERNAL
        );
        TrustContextHolder.set(ctx);
    }

    @Test
    void createCatalog_nationalScope() {
        when(catalogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateCatalogRequest request = new CreateCatalogRequest("Test Catalog", "Description", "NATIONAL", "1.0.0", null);
        CatalogEntity result = catalogService.createCatalog(request);

        assertNotNull(result.getCatalogId());
        assertEquals("Test Catalog", result.getName());
        assertEquals("NATIONAL", result.getScope());
        assertEquals("DRAFT", result.getStatus());
        assertEquals("1.0.0", result.getVersion());
        assertNull(result.getTenantId());

        verify(catalogRepository).save(any());
        verify(changeLogService).record(eq("CREATE"), eq("CATALOG"), any(), any());
    }

    @Test
    void createCatalog_tenantScope_setsTenantId() {
        when(catalogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateCatalogRequest request = new CreateCatalogRequest("Tenant Catalog", null, "TENANT", "1.0.0", null);
        CatalogEntity result = catalogService.createCatalog(request);

        assertEquals("TENANT", result.getScope());
        assertNotNull(result.getTenantId());
    }

    @Test
    void submitForReview_fromDraft() {
        CatalogEntity catalog = new CatalogEntity();
        catalog.setCatalogId("TEST123");
        catalog.setStatus("DRAFT");
        when(catalogRepository.findById("TEST123")).thenReturn(Optional.of(catalog));
        when(catalogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CatalogEntity result = catalogService.submitForReview("TEST123");
        assertEquals("REVIEW", result.getStatus());
    }

    @Test
    void submitForReview_fromPublished_throws() {
        CatalogEntity catalog = new CatalogEntity();
        catalog.setCatalogId("TEST123");
        catalog.setStatus("PUBLISHED");
        when(catalogRepository.findById("TEST123")).thenReturn(Optional.of(catalog));

        assertThrows(IllegalStateException.class, () -> catalogService.submitForReview("TEST123"));
    }

    @Test
    void approveCatalog_fromReview() {
        CatalogEntity catalog = new CatalogEntity();
        catalog.setCatalogId("TEST123");
        catalog.setStatus("REVIEW");
        when(catalogRepository.findById("TEST123")).thenReturn(Optional.of(catalog));
        when(catalogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CatalogEntity result = catalogService.approveCatalog("TEST123");
        assertEquals("APPROVED", result.getStatus());
        verify(outboxRepository).save(any());
    }

    @Test
    void publishCatalog_fromApproved() {
        CatalogEntity catalog = new CatalogEntity();
        catalog.setCatalogId("TEST123");
        catalog.setStatus("APPROVED");
        when(catalogRepository.findById("TEST123")).thenReturn(Optional.of(catalog));
        when(catalogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(itemRepository.findByCatalogIdAndKind("TEST123", null)).thenReturn(java.util.List.of());

        CatalogEntity result = catalogService.publishCatalog("TEST123");
        assertEquals("PUBLISHED", result.getStatus());
        assertNotNull(result.getPublishedAt());
        assertNotNull(result.getChecksum());
    }

    @Test
    void publishCatalog_fromDraft_throws() {
        CatalogEntity catalog = new CatalogEntity();
        catalog.setCatalogId("TEST123");
        catalog.setStatus("DRAFT");
        when(catalogRepository.findById("TEST123")).thenReturn(Optional.of(catalog));

        assertThrows(IllegalStateException.class, () -> catalogService.publishCatalog("TEST123"));
    }
}
