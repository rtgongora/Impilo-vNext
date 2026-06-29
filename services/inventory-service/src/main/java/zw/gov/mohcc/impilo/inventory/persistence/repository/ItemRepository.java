package zw.gov.mohcc.impilo.inventory.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.inventory.persistence.entity.ItemEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ItemRepository extends JpaRepository<ItemEntity, UUID> {

    Optional<ItemEntity> findByTenantIdAndItemCode(UUID tenantId, String itemCode);

    Optional<ItemEntity> findByItemCode(String itemCode);

    List<ItemEntity> findByBarcode(String barcode);

    Optional<ItemEntity> findFirstByBarcode(String barcode);

    List<ItemEntity> findByTenantIdAndCategory(UUID tenantId, String category);

    List<ItemEntity> findByTenantIdAndSubstitutionGroup(UUID tenantId, String substitutionGroup);

    /**
     * Dura commodity catalogue search. All filters except tenant are optional
     * (pass {@code null} to ignore). Matches active commodities only.
     */
    @Query("""
            SELECT i FROM ItemEntity i
            WHERE i.tenantId = :tenantId
              AND i.active = true
              AND (:q IS NULL
                   OR LOWER(i.name) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(i.itemCode) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(COALESCE(i.genericName, '')) LIKE LOWER(CONCAT('%', :q, '%')))
              AND (:categoryId IS NULL OR i.categoryId = :categoryId)
              AND (:programmeArea IS NULL OR i.programmeArea = :programmeArea)
              AND (:controlled IS NULL OR i.controlled = :controlled)
              AND (:coldChain IS NULL OR i.coldChainRequired = :coldChain)
            ORDER BY i.name
            """)
    List<ItemEntity> searchCommodities(@Param("tenantId") UUID tenantId,
                                       @Param("q") String q,
                                       @Param("categoryId") UUID categoryId,
                                       @Param("programmeArea") String programmeArea,
                                       @Param("controlled") Boolean controlled,
                                       @Param("coldChain") Boolean coldChain);
}
