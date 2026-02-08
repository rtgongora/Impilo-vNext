package zw.gov.mohcc.impilo.msikaflow.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.msikaflow.domain.ReservationStatus;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.ReservationEntity;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface ReservationRepository extends JpaRepository<ReservationEntity, String> {
    List<ReservationEntity> findByOrderId(String orderId);
    List<ReservationEntity> findByStatusAndExpiresAtBefore(ReservationStatus status, OffsetDateTime cutoff);
}
