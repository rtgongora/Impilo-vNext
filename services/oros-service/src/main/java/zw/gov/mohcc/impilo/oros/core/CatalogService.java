package zw.gov.mohcc.impilo.oros.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.oros.domain.OrderType;
import zw.gov.mohcc.impilo.oros.persistence.entity.CatalogItemEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.CatalogItemRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages the orderable catalog for tenants.
 */
@Service
public class CatalogService {

    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);

    private final CatalogItemRepository catalogRepository;

    public CatalogService(CatalogItemRepository catalogRepository) {
        this.catalogRepository = catalogRepository;
    }

    /**
     * Get catalog items, optionally filtered by order type.
     */
    public Page<CatalogItemEntity> getCatalog(OrderType orderType, Pageable pageable) {
        TrustContext ctx = TrustContextHolder.require();
        if (orderType != null) {
            return catalogRepository.findByTenantIdAndOrderTypeAndActiveTrue(
                    ctx.tenantId(), orderType, pageable);
        }
        return catalogRepository.findByTenantIdAndActiveTrue(ctx.tenantId(), pageable);
    }

    /**
     * Import a batch of catalog items for the current tenant.
     */
    @Transactional
    public List<CatalogItemEntity> importCatalog(List<CatalogItemImport> items) {
        TrustContext ctx = TrustContextHolder.require();
        List<CatalogItemEntity> imported = new ArrayList<>();

        for (CatalogItemImport item : items) {
            // Upsert: check if code already exists for this tenant
            CatalogItemEntity entity = catalogRepository
                    .findByTenantIdAndCode(ctx.tenantId(), item.code())
                    .orElseGet(() -> {
                        CatalogItemEntity newItem = new CatalogItemEntity();
                        newItem.setTenantId(ctx.tenantId());
                        newItem.setCode(item.code());
                        return newItem;
                    });

            entity.setOrderType(item.orderType());
            entity.setDisplayName(item.displayName());
            entity.setCategory(item.category());
            entity.setZiboCanonical(item.ziboCanonical());
            entity.setActive(true);
            imported.add(catalogRepository.save(entity));
        }

        log.info("Catalog imported: {} items for tenant {}", imported.size(), ctx.tenantId());
        return imported;
    }

    /**
     * Look up a single catalog item by code.
     */
    public CatalogItemEntity lookupItem(String code) {
        TrustContext ctx = TrustContextHolder.require();
        return catalogRepository.findByTenantIdAndCode(ctx.tenantId(), code)
                .orElseThrow(() -> new IllegalArgumentException("Catalog item not found: " + code));
    }

    /**
     * Data transfer object for catalog import.
     */
    public record CatalogItemImport(
            OrderType orderType,
            String code,
            String displayName,
            String category,
            String ziboCanonical
    ) {}
}
