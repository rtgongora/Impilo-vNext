package zw.gov.mohcc.impilo.msika.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.msika.api.dto.CatalogView;
import zw.gov.mohcc.impilo.msika.api.dto.CreateCatalogRequest;
import zw.gov.mohcc.impilo.msika.domain.UlidGenerator;
import zw.gov.mohcc.impilo.msika.persistence.entity.CatalogEntity;
import zw.gov.mohcc.impilo.msika.persistence.entity.CatalogItemEntity;
import zw.gov.mohcc.impilo.msika.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.msika.persistence.repository.CatalogItemRepository;
import zw.gov.mohcc.impilo.msika.persistence.repository.CatalogRepository;
import zw.gov.mohcc.impilo.msika.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class CatalogService {

    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);

    private final CatalogRepository catalogRepository;
    private final CatalogItemRepository itemRepository;
    private final EventOutboxRepository outboxRepository;
    private final ChangeLogService changeLogService;
    private final ObjectMapper objectMapper;

    public CatalogService(CatalogRepository catalogRepository,
                          CatalogItemRepository itemRepository,
                          EventOutboxRepository outboxRepository,
                          ChangeLogService changeLogService,
                          ObjectMapper objectMapper) {
        this.catalogRepository = catalogRepository;
        this.itemRepository = itemRepository;
        this.outboxRepository = outboxRepository;
        this.changeLogService = changeLogService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CatalogEntity createCatalog(CreateCatalogRequest request) {
        TrustContext ctx = TrustContextHolder.require();

        CatalogEntity catalog = new CatalogEntity();
        catalog.setCatalogId(UlidGenerator.generate());
        catalog.setName(request.name());
        catalog.setDescription(request.description());
        catalog.setScope(request.scope() != null ? request.scope() : "NATIONAL");
        catalog.setVersion(request.version() != null ? request.version() : "0.1.0");
        catalog.setParentCatalogId(request.parentCatalogId());
        catalog.setStatus("DRAFT");
        catalog.setCreatedBy(ctx.actorId() != null ? ctx.actorId() : "SYSTEM");

        if ("TENANT".equals(catalog.getScope())) {
            catalog.setTenantId(ctx.tenantId());
        }

        catalog = catalogRepository.save(catalog);
        changeLogService.record("CREATE", "CATALOG", catalog.getCatalogId(), request);
        log.info("Created catalog {} (scope={}, version={})", catalog.getCatalogId(), catalog.getScope(), catalog.getVersion());
        return catalog;
    }

    public Page<CatalogView> listCatalogs(UUID tenantId, Pageable pageable) {
        Page<CatalogEntity> page;
        if (tenantId != null) {
            page = catalogRepository.findByTenantIdOrTenantIdIsNull(tenantId, pageable);
        } else {
            page = catalogRepository.findAll(pageable);
        }
        return page.map(this::toView);
    }

    public CatalogView getCatalog(String catalogId) {
        CatalogEntity catalog = catalogRepository.findById(catalogId)
                .orElseThrow(() -> new IllegalArgumentException("Catalog not found: " + catalogId));
        return toView(catalog);
    }

    @Transactional
    public CatalogEntity submitForReview(String catalogId) {
        TrustContext ctx = TrustContextHolder.require();
        CatalogEntity catalog = catalogRepository.findById(catalogId)
                .orElseThrow(() -> new IllegalArgumentException("Catalog not found: " + catalogId));

        if (!"DRAFT".equals(catalog.getStatus())) {
            throw new IllegalStateException("Only DRAFT catalogs can be submitted for review. Current status: " + catalog.getStatus());
        }

        catalog.setStatus("REVIEW");
        catalog.setReviewedBy(ctx.actorId());
        catalog = catalogRepository.save(catalog);
        changeLogService.record("UPDATE", "CATALOG", catalogId, "Submitted for review");
        return catalog;
    }

    @Transactional
    public CatalogEntity approveCatalog(String catalogId) {
        TrustContext ctx = TrustContextHolder.require();
        CatalogEntity catalog = catalogRepository.findById(catalogId)
                .orElseThrow(() -> new IllegalArgumentException("Catalog not found: " + catalogId));

        if (!"REVIEW".equals(catalog.getStatus())) {
            throw new IllegalStateException("Only REVIEW catalogs can be approved. Current status: " + catalog.getStatus());
        }

        catalog.setStatus("APPROVED");
        catalog.setApprovedBy(ctx.actorId());
        catalog = catalogRepository.save(catalog);

        emitOutboxEvent("CATALOG", catalogId, "CATALOG_APPROVED", catalog);
        changeLogService.record("APPROVE", "CATALOG", catalogId, "Approved by " + ctx.actorId());
        return catalog;
    }

    @Transactional
    public CatalogEntity publishCatalog(String catalogId) {
        TrustContext ctx = TrustContextHolder.require();
        CatalogEntity catalog = catalogRepository.findById(catalogId)
                .orElseThrow(() -> new IllegalArgumentException("Catalog not found: " + catalogId));

        if (!"APPROVED".equals(catalog.getStatus())) {
            throw new IllegalStateException("Only APPROVED catalogs can be published. Current status: " + catalog.getStatus());
        }

        // Compute checksum of all items
        List<CatalogItemEntity> items = itemRepository.findByCatalogIdAndKind(catalogId, null);
        String checksum = computeChecksum(items);

        catalog.setStatus("PUBLISHED");
        catalog.setPublishedBy(ctx.actorId());
        catalog.setPublishedAt(OffsetDateTime.now());
        catalog.setChecksum(checksum);
        catalog = catalogRepository.save(catalog);

        emitOutboxEvent("CATALOG", catalogId, "CATALOG_PUBLISHED", catalog);
        changeLogService.record("PUBLISH", "CATALOG", catalogId, "Published with checksum " + checksum);
        log.info("Published catalog {} version {} checksum {}", catalogId, catalog.getVersion(), checksum);
        return catalog;
    }

    @Transactional
    public CatalogEntity rollbackCatalog(String catalogId, String targetVersion) {
        TrustContext ctx = TrustContextHolder.require();

        // Find the target version
        CatalogEntity current = catalogRepository.findById(catalogId)
                .orElseThrow(() -> new IllegalArgumentException("Catalog not found: " + catalogId));

        CatalogEntity target = catalogRepository.findByNameAndVersionAndTenantId(
                current.getName(), targetVersion, current.getTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Target version not found: " + targetVersion));

        if (!"PUBLISHED".equals(target.getStatus())) {
            throw new IllegalStateException("Can only rollback to a PUBLISHED version");
        }

        // Create a new catalog version that is a copy of the target
        CatalogEntity rollback = new CatalogEntity();
        rollback.setCatalogId(UlidGenerator.generate());
        rollback.setName(current.getName());
        rollback.setDescription("Rollback to version " + targetVersion);
        rollback.setScope(current.getScope());
        rollback.setTenantId(current.getTenantId());
        rollback.setVersion(bumpPatch(current.getVersion()));
        rollback.setParentCatalogId(target.getCatalogId());
        rollback.setStatus("PUBLISHED");
        rollback.setCreatedBy(ctx.actorId());
        rollback.setPublishedBy(ctx.actorId());
        rollback.setPublishedAt(OffsetDateTime.now());
        rollback = catalogRepository.save(rollback);

        // Copy items from target to rollback catalog
        List<CatalogItemEntity> targetItems = itemRepository.findByCatalogIdAndKind(target.getCatalogId(), null);
        for (CatalogItemEntity item : targetItems) {
            CatalogItemEntity copy = new CatalogItemEntity();
            copy.setItemId(UlidGenerator.generate());
            copy.setCatalogId(rollback.getCatalogId());
            copy.setKind(item.getKind());
            copy.setCanonicalCode(item.getCanonicalCode());
            copy.setDisplayName(item.getDisplayName());
            copy.setDescription(item.getDescription());
            copy.setSynonyms(item.getSynonyms());
            copy.setTags(item.getTags());
            copy.setRestrictions(item.getRestrictions());
            copy.setZiboBindings(item.getZiboBindings());
            copy.setMetadata(item.getMetadata());
            copy.setActive(item.isActive());
            copy.setCreatedBy(ctx.actorId());
            itemRepository.save(copy);
        }

        emitOutboxEvent("CATALOG", rollback.getCatalogId(), "CATALOG_PUBLISHED", rollback);
        changeLogService.record("ROLLBACK", "CATALOG", catalogId,
                "Rolled back to version " + targetVersion + " -> new catalog " + rollback.getCatalogId());
        return rollback;
    }

    private CatalogView toView(CatalogEntity c) {
        long itemCount = itemRepository.countByCatalogId(c.getCatalogId());
        return new CatalogView(
                c.getCatalogId(), c.getTenantId(), c.getScope(), c.getName(),
                c.getDescription(), c.getStatus(), c.getVersion(),
                c.getParentCatalogId(), c.getChecksum(), itemCount,
                c.getPublishedAt(), c.getCreatedAt(), c.getUpdatedAt()
        );
    }

    private void emitOutboxEvent(String aggregateType, String aggregateId, String eventType, Object payload) {
        TrustContext ctx = TrustContextHolder.require();
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setTenantId(ctx.tenantId());
        try {
            event.setPayload(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            event.setPayload("{}");
        }
        outboxRepository.save(event);
    }

    private String computeChecksum(List<CatalogItemEntity> items) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (CatalogItemEntity item : items) {
                String data = item.getCanonicalCode() + "|" + item.getKind() + "|" + item.getDisplayName();
                digest.update(data.getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            return "CHECKSUM_ERROR";
        }
    }

    private String bumpPatch(String version) {
        if (version == null) return "0.1.1";
        String[] parts = version.split("\\.");
        if (parts.length != 3) return version + ".1";
        int patch = Integer.parseInt(parts[2]) + 1;
        return parts[0] + "." + parts[1] + "." + patch;
    }
}
