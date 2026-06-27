package zw.gov.mohcc.impilo.oros.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.oros.persistence.entity.ResultObservationEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ResultObservationRepository extends JpaRepository<ResultObservationEntity, UUID> {

    /** Observations for a result, in display order. */
    List<ResultObservationEntity> findByResultIdOrderBySequenceAsc(UUID resultId);

    /** Observations for an order across all result versions (newest first) — trend source. */
    List<ResultObservationEntity> findByOrderIdOrderByCreatedAtDesc(String orderId);
}
