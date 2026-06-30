package zw.gov.mohcc.impilo.msika.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.msika.persistence.entity.ListingFavouriteEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface ListingFavouriteRepository extends JpaRepository<ListingFavouriteEntity, String> {
    List<ListingFavouriteEntity> findByActorIdOrderByCreatedAtDesc(String actorId);
    Optional<ListingFavouriteEntity> findByActorIdAndListingId(String actorId, String listingId);
    void deleteByActorIdAndListingId(String actorId, String listingId);
}
