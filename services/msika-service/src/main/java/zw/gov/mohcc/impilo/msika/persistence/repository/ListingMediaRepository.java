package zw.gov.mohcc.impilo.msika.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.msika.persistence.entity.ListingMediaEntity;

import java.util.List;

@Repository
public interface ListingMediaRepository extends JpaRepository<ListingMediaEntity, String> {
    List<ListingMediaEntity> findByListingIdOrderBySortOrderAsc(String listingId);
}
