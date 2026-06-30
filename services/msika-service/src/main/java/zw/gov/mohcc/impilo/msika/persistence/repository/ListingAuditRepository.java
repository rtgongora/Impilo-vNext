package zw.gov.mohcc.impilo.msika.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.msika.persistence.entity.ListingAuditEntity;

import java.util.List;

@Repository
public interface ListingAuditRepository extends JpaRepository<ListingAuditEntity, String> {
    List<ListingAuditEntity> findTop100ByListingIdOrderByCreatedAtDesc(String listingId);
}
