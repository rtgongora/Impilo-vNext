package zw.gov.mohcc.impilo.msika.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.msika.persistence.entity.ListingEntity;

import java.util.List;

@Repository
public interface ListingRepository extends JpaRepository<ListingEntity, String> {

    Page<ListingEntity> findByStatusOrderByPublishedAtDesc(String status, Pageable pageable);

    List<ListingEntity> findBySellerTypeAndSellerIdOrderByUpdatedAtDesc(String sellerType, String sellerId);

    Page<ListingEntity> findByStatusInOrderByUpdatedAtDesc(List<String> statuses, Pageable pageable);

    /**
     * Published listings filtered (optionally) by free-text title/summary,
     * risk class and sourcing category (via the linked catalog item). Keep
     * the value and countQuery predicates in lockstep.
     */
    @Query(value = "SELECT l.* FROM msika_listings l " +
            "JOIN msika_catalog_items ci ON ci.item_id = l.catalog_item_id " +
            "WHERE l.status = 'PUBLISHED' " +
            "AND (:risk IS NULL OR l.risk_classification = :risk) " +
            "AND (:category IS NULL OR ci.sourcing_category_code = :category) " +
            "AND (:q IS NULL OR l.title ILIKE CONCAT('%', :q, '%') OR l.summary ILIKE CONCAT('%', :q, '%')) " +
            "ORDER BY l.published_at DESC",
            countQuery = "SELECT count(*) FROM msika_listings l " +
            "JOIN msika_catalog_items ci ON ci.item_id = l.catalog_item_id " +
            "WHERE l.status = 'PUBLISHED' " +
            "AND (:risk IS NULL OR l.risk_classification = :risk) " +
            "AND (:category IS NULL OR ci.sourcing_category_code = :category) " +
            "AND (:q IS NULL OR l.title ILIKE CONCAT('%', :q, '%') OR l.summary ILIKE CONCAT('%', :q, '%'))",
            nativeQuery = true)
    Page<ListingEntity> searchPublished(@Param("q") String q, @Param("risk") String risk,
                                        @Param("category") String category, Pageable pageable);

    /** Published-listing counts per sourcing category (marketplace supply truth). */
    @Query(value = "SELECT ci.sourcing_category_code, count(*) FROM msika_listings l " +
            "JOIN msika_catalog_items ci ON ci.item_id = l.catalog_item_id " +
            "WHERE l.status = 'PUBLISHED' AND ci.sourcing_category_code IS NOT NULL " +
            "GROUP BY ci.sourcing_category_code",
            nativeQuery = true)
    List<Object[]> countPublishedByCategory();
}
