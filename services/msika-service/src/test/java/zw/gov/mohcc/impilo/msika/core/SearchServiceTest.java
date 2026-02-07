package zw.gov.mohcc.impilo.msika.core;

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

import java.util.List;

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
    }

    @Test
    void search_withQuery_usesFullTextSearch() {
        CatalogItemEntity entity = new CatalogItemEntity();
        entity.setItemId("ITEM1");
        entity.setDisplayName("Paracetamol 500mg");
        Page<CatalogItemEntity> page = new PageImpl<>(List.of(entity));
        Pageable pageable = PageRequest.of(0, 20);

        when(itemRepository.searchItems(eq("paracetamol"), isNull(), anyString(), eq(pageable))).thenReturn(page);
        when(itemService.toView(any())).thenReturn(mock(CatalogItemView.class));

        Page<CatalogItemView> result = searchService.search("paracetamol", null, null, pageable);
        assertEquals(1, result.getTotalElements());
        verify(itemRepository).searchItems(eq("paracetamol"), isNull(), anyString(), eq(pageable));
    }

    @Test
    void search_withoutQuery_usesPublishedItems() {
        Page<CatalogItemEntity> page = new PageImpl<>(List.of());
        Pageable pageable = PageRequest.of(0, 20);

        when(itemRepository.findPublishedItems(isNull(), anyString(), eq(pageable))).thenReturn(page);

        Page<CatalogItemView> result = searchService.search(null, null, null, pageable);
        assertEquals(0, result.getTotalElements());
        verify(itemRepository).findPublishedItems(isNull(), anyString(), eq(pageable));
    }
}
