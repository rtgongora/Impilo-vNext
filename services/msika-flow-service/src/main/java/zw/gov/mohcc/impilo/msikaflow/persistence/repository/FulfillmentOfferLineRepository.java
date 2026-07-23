package zw.gov.mohcc.impilo.msikaflow.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.FulfillmentOfferLineEntity;

import java.util.List;

@Repository
public interface FulfillmentOfferLineRepository extends JpaRepository<FulfillmentOfferLineEntity, String> {

    List<FulfillmentOfferLineEntity> findByOfferId(String offerId);

    List<FulfillmentOfferLineEntity> findByDuraReservationRef(String duraReservationRef);
}
