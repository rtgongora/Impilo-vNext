package zw.gov.mohcc.impilo.msika.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import zw.gov.mohcc.impilo.msika.persistence.entity.CatalogItemEntity;
import zw.gov.mohcc.impilo.msika.persistence.entity.ExternalMappingEntity;
import zw.gov.mohcc.impilo.msika.persistence.entity.ExternalSourceEntity;
import zw.gov.mohcc.impilo.msika.persistence.entity.ImportJobEntity;
import zw.gov.mohcc.impilo.msika.persistence.repository.*;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * The CSV import is the PRODUCER for the external-mapping review queue:
 * duplicate/ambiguous codes must land as PENDING mapping rows (hanging off a
 * lazily-created per-tenant CSV_IMPORT source), never silently vanish.
 */
@ExtendWith(MockitoExtension.class)
class ImportServiceTest {

    @Mock private ExternalSourceRepository sourceRepository;
    @Mock private ExternalMappingRepository mappingRepository;
    @Mock private ImportJobRepository jobRepository;
    @Mock private CatalogItemRepository itemRepository;
    @Mock private ProductDetailRepository productDetailRepository;
    @Mock private ChangeLogService changeLogService;

    private ImportService importService;
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        importService = new ImportService(sourceRepository, mappingRepository, jobRepository,
                itemRepository, productDetailRepository, changeLogService, new ObjectMapper());
        TrustContextHolder.set(new TrustContext(tenantId, "actor-1", "CATALOG_ADMIN", "TREATMENT",
                "dev", UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
        lenient().when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void clearCtx() {
        TrustContextHolder.clear();
    }

    @Test
    void importCsv_duplicateCode_queuesPendingMapping_andCreatesCsvSource() {
        String csv = "canonical_code,display_name,kind\n"
                + "NEW-1,Brand New Item,PRODUCT\n"
                + "DUP-1,Duplicate Item,PRODUCT\n";
        MockMultipartFile file = new MockMultipartFile("file", "items.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));

        CatalogItemEntity existing = new CatalogItemEntity();
        existing.setItemId("EXISTING1");
        when(itemRepository.findByCatalogIdAndCanonicalCode("CAT1", "NEW-1")).thenReturn(Optional.empty());
        when(itemRepository.findByCatalogIdAndCanonicalCode("CAT1", "DUP-1")).thenReturn(Optional.of(existing));
        when(itemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sourceRepository.findByTenantId(tenantId)).thenReturn(List.of());
        when(sourceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mappingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ImportJobEntity job = importService.importCsv(file, "CAT1");

        assertEquals("COMPLETED", job.getStatus());
        assertEquals(1, job.getImportedRows());
        assertEquals(1, job.getDuplicateRows());
        assertTrue(job.getStats().contains("\"mappingsQueued\":1"));

        // the lazily-created CSV source
        ArgumentCaptor<ExternalSourceEntity> sourceCaptor = ArgumentCaptor.forClass(ExternalSourceEntity.class);
        verify(sourceRepository).save(sourceCaptor.capture());
        assertEquals("CSV_IMPORT", sourceCaptor.getValue().getName());
        assertEquals("CSV", sourceCaptor.getValue().getMode());

        // the PENDING mapping row aligned to the existing item
        ArgumentCaptor<ExternalMappingEntity> mappingCaptor = ArgumentCaptor.forClass(ExternalMappingEntity.class);
        verify(mappingRepository).save(mappingCaptor.capture());
        ExternalMappingEntity mapping = mappingCaptor.getValue();
        assertEquals("PENDING", mapping.getStatus());
        assertEquals("DUP-1", mapping.getExternalCode());
        assertEquals("Duplicate Item", mapping.getExternalName());
        assertEquals("EXISTING1", mapping.getInternalItemId());
        assertEquals("EXACT", mapping.getMapType());
    }

    @Test
    void importCsv_reusesExistingCsvSource() {
        String csv = "canonical_code,display_name,kind\nDUP-1,Duplicate Item,PRODUCT\n";
        MockMultipartFile file = new MockMultipartFile("file", "items.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));

        CatalogItemEntity existing = new CatalogItemEntity();
        existing.setItemId("EXISTING1");
        when(itemRepository.findByCatalogIdAndCanonicalCode("CAT1", "DUP-1")).thenReturn(Optional.of(existing));

        ExternalSourceEntity csvSource = new ExternalSourceEntity();
        csvSource.setSourceId("CSVSRC1");
        csvSource.setName("CSV_IMPORT");
        csvSource.setMode("CSV");
        when(sourceRepository.findByTenantId(tenantId)).thenReturn(List.of(csvSource));
        when(mappingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        importService.importCsv(file, "CAT1");

        verify(sourceRepository, never()).save(any());
        ArgumentCaptor<ExternalMappingEntity> mappingCaptor = ArgumentCaptor.forClass(ExternalMappingEntity.class);
        verify(mappingRepository).save(mappingCaptor.capture());
        assertEquals("CSVSRC1", mappingCaptor.getValue().getSourceId());
    }

    @Test
    void importCsv_cleanRows_queueNoMappings() {
        String csv = "canonical_code,display_name,kind\nNEW-1,Brand New Item,SERVICE\n";
        MockMultipartFile file = new MockMultipartFile("file", "items.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));
        when(itemRepository.findByCatalogIdAndCanonicalCode("CAT1", "NEW-1")).thenReturn(Optional.empty());
        when(itemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ImportJobEntity job = importService.importCsv(file, "CAT1");

        assertEquals("COMPLETED", job.getStatus());
        verify(mappingRepository, never()).save(any());
        verify(sourceRepository, never()).save(any());
    }
}
