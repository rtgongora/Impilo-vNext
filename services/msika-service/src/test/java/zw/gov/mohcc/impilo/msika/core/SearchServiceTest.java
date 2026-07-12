package zw.gov.mohcc.impilo.msika.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import zw.gov.mohcc.impilo.msika.api.dto.CatalogItemView;
import zw.gov.mohcc.impilo.msika.persistence.entity.CatalogItemEntity;
import zw.gov.mohcc.impilo.msika.persistence.repository.CatalogItemRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock private CatalogItemRepository itemRepository;
    @Mock private ItemService itemService;

    private SearchService searchService;

    @BeforeEach
    void setUp() {
        searchService = new SearchService(itemRepository, itemService);
        TrustContextHolder.clear();
    }

    @AfterEach
    void clearCtx() {
        TrustContextHolder.clear();
    }

    @Test
    void search_withQuery_usesFullTextSearch_withExplicitTenant() {
        CatalogItemEntity entity = new CatalogItemEntity();
        entity.setItemId("ITEM1");
        entity.setDisplayName("Paracetamol 500mg");
        Page<CatalogItemEntity> page = new PageImpl<>(List.of(entity));
        Pageable pageable = PageRequest.of(0, 20);

        when(itemRepository.searchItems(eq("paracetamol"), isNull(), isNull(), isNull(), isNull(),
                eq("11111111-1111-1111-1111-111111111111"), eq(pageable))).thenReturn(page);
        when(itemService.toView(any())).thenReturn(mock(CatalogItemView.class));

        Page<CatalogItemView> result = searchService.search("paracetamol", null, null, null, null,
                "11111111-1111-1111-1111-111111111111", pageable);
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void search_withoutTenant_usesTrustContextTenant() {
        UUID tenant = UUID.randomUUID();
        TrustContextHolder.set(new TrustContext(tenant, "actor", "PROVIDER", "TREATMENT",
                "dev", UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
        Pageable pageable = PageRequest.of(0, 20);
        when(itemRepository.findPublishedItems(isNull(), isNull(), isNull(), isNull(),
                eq(tenant.toString()), eq(pageable))).thenReturn(new PageImpl<>(List.of()));

        Page<CatalogItemView> result = searchService.search(null, null, null, null, null, null, pageable);
        assertEquals(0, result.getTotalElements());
        verify(itemRepository).findPublishedItems(isNull(), isNull(), isNull(), isNull(),
                eq(tenant.toString()), eq(pageable));
    }

    @Test
    void search_noTenantNoContext_platformBranch_nullTenant_noFabrication() {
        Pageable pageable = PageRequest.of(0, 20);
        when(itemRepository.findPublishedItems(isNull(), isNull(), isNull(), isNull(),
                isNull(), eq(pageable))).thenReturn(new PageImpl<>(List.of()));

        Page<CatalogItemView> result = searchService.search(null, null, null, null, null, null, pageable);
        assertEquals(0, result.getTotalElements());
        // The honest platform branch: tenant is null, never a fabricated UUID.
        verify(itemRepository).findPublishedItems(isNull(), isNull(), isNull(), isNull(),
                isNull(), eq(pageable));
    }

    @Test
    void search_passesRestrictionTagAndZiboPredicates() {
        Pageable pageable = PageRequest.of(0, 20);
        when(itemRepository.searchItems(eq("gloves"), eq("PRODUCT"), eq("prescription_required"),
                eq("ppe"), eq("ZB-001"), isNull(), eq(pageable))).thenReturn(new PageImpl<>(List.of()));

        searchService.search("gloves", "PRODUCT", "prescription_required", "ppe", "ZB-001", null, pageable);
        verify(itemRepository).searchItems(eq("gloves"), eq("PRODUCT"), eq("prescription_required"),
                eq("ppe"), eq("ZB-001"), isNull(), eq(pageable));
    }

    @Test
    void search_blankFiltersNormalisedToNull() {
        Pageable pageable = PageRequest.of(0, 20);
        when(itemRepository.findPublishedItems(isNull(), isNull(), isNull(), isNull(),
                isNull(), eq(pageable))).thenReturn(new PageImpl<>(List.of()));

        searchService.search("  ", " ", "", "  ", "", "", pageable);
        verify(itemRepository).findPublishedItems(isNull(), isNull(), isNull(), isNull(),
                isNull(), eq(pageable));
    }
}
