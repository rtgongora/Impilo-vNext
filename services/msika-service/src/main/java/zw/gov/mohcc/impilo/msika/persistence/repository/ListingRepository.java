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

    /** Published listings filtered (optionally) by free-text title/summary and risk class. */
    @Query(value = "SELECT * FROM msika_listings " +
            "WHERE status = 'PUBLISHED' " +
            "AND (:risk IS NULL OR risk_classification = :risk) " +
            "AND (:q IS NULL OR title ILIKE CONCAT('%', :q, '%') OR summary ILIKE CONCAT('%', :q, '%')) " +
            "ORDER BY published_at DESC",
            countQuery = "SELECT count(*) FROM msika_listings " +
            "WHERE status = 'PUBLISHED' " +
            "AND (:risk IS NULL OR risk_classification = :risk) " +
            "AND (:q IS NULL OR title ILIKE CONCAT('%', :q, '%') OR summary ILIKE CONCAT('%', :q, '%'))",
            nativeQuery = true)
    Page<ListingEntity> searchPublished(@Param("q") String q, @Param("risk") String risk, Pageable pageable);
}
