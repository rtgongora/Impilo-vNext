package zw.gov.mohcc.impilo.msika.core;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.msika.api.dto.CatalogItemView;
import zw.gov.mohcc.impilo.msika.persistence.entity.CatalogItemEntity;
import zw.gov.mohcc.impilo.msika.persistence.repository.CatalogItemRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

/**
 * Catalog item discovery over PUBLISHED catalogs.
 *
 * Tenant resolution is honest: an explicit tenantId wins, else the caller's
 * TrustContext tenant, else the platform-catalogue branch (null tenant =
 * tenant-null/NATIONAL rows only). No fabricated tenants — a random UUID
 * would silently narrow every anonymous search to platform rows while
 * pretending a tenant existed.
 */
@Service
public class SearchService {

    private final CatalogItemRepository itemRepository;
    private final ItemService itemService;

    public SearchService(CatalogItemRepository itemRepository, ItemService itemService) {
        this.itemRepository = itemRepository;
        this.itemService = itemService;
    }

    public Page<CatalogItemView> search(String query, String kind, String restriction, String tag,
                                        String ziboCode, String tenantId, Pageable pageable) {
        String effectiveTenantId = resolveTenant(tenantId);
        String qn = blankToNull(query);
        Page<CatalogItemEntity> page;
        if (qn != null) {
            page = itemRepository.searchItems(qn, blankToNull(kind), blankToNull(restriction),
                    blankToNull(tag), blankToNull(ziboCode), effectiveTenantId, pageable);
        } else {
            page = itemRepository.findPublishedItems(blankToNull(kind), blankToNull(restriction),
                    blankToNull(tag), blankToNull(ziboCode), effectiveTenantId, pageable);
        }
        return page.map(itemService::toView);
    }

    private static String resolveTenant(String tenantId) {
        if (tenantId != null && !tenantId.isBlank()) {
            return tenantId;
        }
        TrustContext ctx = TrustContextHolder.get();
        if (ctx != null && ctx.tenantId() != null) {
            return ctx.tenantId().toString();
        }
        return null; // platform-catalogue branch: tenant-null rows only
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
