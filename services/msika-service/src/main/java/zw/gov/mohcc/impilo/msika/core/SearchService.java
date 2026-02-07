package zw.gov.mohcc.impilo.msika.core;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.msika.api.dto.CatalogItemView;
import zw.gov.mohcc.impilo.msika.persistence.entity.CatalogItemEntity;
import zw.gov.mohcc.impilo.msika.persistence.repository.CatalogItemRepository;

import java.util.UUID;

@Service
public class SearchService {

    private final CatalogItemRepository itemRepository;
    private final ItemService itemService;

    public SearchService(CatalogItemRepository itemRepository, ItemService itemService) {
        this.itemRepository = itemRepository;
        this.itemService = itemService;
    }

    public Page<CatalogItemView> search(String query, String kind, String tenantId, Pageable pageable) {
        String effectiveTenantId = tenantId != null ? tenantId : UUID.randomUUID().toString();
        Page<CatalogItemEntity> page;

        if (query != null && !query.isBlank()) {
            page = itemRepository.searchItems(query, kind, effectiveTenantId, pageable);
        } else {
            page = itemRepository.findPublishedItems(kind, effectiveTenantId, pageable);
        }

        return page.map(itemService::toView);
    }
}
