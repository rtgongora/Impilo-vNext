package zw.gov.mohcc.impilo.msika.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.msika.persistence.entity.CatalogEntity;
import zw.gov.mohcc.impilo.msika.persistence.entity.CatalogItemEntity;
import zw.gov.mohcc.impilo.msika.persistence.repository.CatalogItemRepository;
import zw.gov.mohcc.impilo.msika.persistence.repository.CatalogRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PackService {

    private static final Logger log = LoggerFactory.getLogger(PackService.class);

    private final CatalogRepository catalogRepository;
    private final CatalogItemRepository itemRepository;
    private final ItemService itemService;
    private final ObjectMapper objectMapper;

    public PackService(CatalogRepository catalogRepository,
                       CatalogItemRepository itemRepository,
                       ItemService itemService,
                       ObjectMapper objectMapper) {
        this.catalogRepository = catalogRepository;
        this.itemRepository = itemRepository;
        this.itemService = itemService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> generatePack(String kind, String tenantId, String version) {
        List<CatalogEntity> catalogs;
        if (version != null) {
            catalogs = catalogRepository.findByStatusAndScope("PUBLISHED", "NATIONAL").stream()
                    .filter(c -> version.equals(c.getVersion()))
                    .collect(Collectors.toList());
        } else {
            catalogs = catalogRepository.findByStatusAndScope("PUBLISHED", "NATIONAL");
        }

        // Add tenant overlay if tenantId provided
        if (tenantId != null) {
            UUID tid = UUID.fromString(tenantId);
            List<CatalogEntity> tenantCatalogs = catalogRepository.findByStatusAndScope("PUBLISHED", "TENANT").stream()
                    .filter(c -> tid.equals(c.getTenantId()))
                    .collect(Collectors.toList());
            catalogs.addAll(tenantCatalogs);
        }

        // Collect items of the requested kind, tenant overlay wins on code conflicts
        Map<String, Object> itemsByCode = new LinkedHashMap<>();
        // National items first
        for (CatalogEntity catalog : catalogs) {
            if ("NATIONAL".equals(catalog.getScope())) {
                List<CatalogItemEntity> items = kind != null
                        ? itemRepository.findByCatalogIdAndKind(catalog.getCatalogId(), kind)
                        : itemRepository.findByCatalogIdAndKind(catalog.getCatalogId(), null);
                for (CatalogItemEntity item : items) {
                    itemsByCode.put(item.getCanonicalCode(), itemService.toView(item));
                }
            }
        }
        // Tenant overlay items override national
        for (CatalogEntity catalog : catalogs) {
            if ("TENANT".equals(catalog.getScope())) {
                List<CatalogItemEntity> items = kind != null
                        ? itemRepository.findByCatalogIdAndKind(catalog.getCatalogId(), kind)
                        : itemRepository.findByCatalogIdAndKind(catalog.getCatalogId(), null);
                for (CatalogItemEntity item : items) {
                    itemsByCode.put(item.getCanonicalCode(), itemService.toView(item));
                }
            }
        }

        String checksum = computePackChecksum(itemsByCode);

        Map<String, Object> pack = new LinkedHashMap<>();
        pack.put("kind", kind);
        pack.put("tenantId", tenantId);
        pack.put("version", version != null ? version : "latest");
        pack.put("checksum", checksum);
        pack.put("producedAt", Instant.now().toString());
        pack.put("itemCount", itemsByCode.size());
        pack.put("items", new ArrayList<>(itemsByCode.values()));

        return pack;
    }

    public Map<String, Object> getOrderablesPack(String tenantId, String version) {
        return generatePack("ORDERABLE", tenantId, version);
    }

    public Map<String, Object> getItemMasterPack(String tenantId, String version) {
        return generatePack("PRODUCT", tenantId, version);
    }

    public Map<String, Object> getChargeablesPack(String tenantId, String version) {
        return generatePack("CHARGEABLE", tenantId, version);
    }

    public Map<String, Object> getFacilityCapabilitiesPack(String tenantId, String version) {
        return generatePack("CAPABILITY_FACILITY", tenantId, version);
    }

    public Map<String, Object> getProviderCapabilitiesPack(String tenantId, String version) {
        return generatePack("CAPABILITY_PROVIDER", tenantId, version);
    }

    private String computePackChecksum(Map<String, Object> items) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String data = objectMapper.writeValueAsString(items);
            digest.update(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            return "CHECKSUM_ERROR";
        }
    }
}
