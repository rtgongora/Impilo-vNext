package zw.gov.mohcc.impilo.msikaflow.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.msikaflow.domain.PickupTokenStatus;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.PickupTokenEntity;

import java.util.Optional;

@Repository
public interface PickupTokenRepository extends JpaRepository<PickupTokenEntity, String> {
    Optional<PickupTokenEntity> findByTokenHash(String tokenHash);
    Optional<PickupTokenEntity> findByOrderIdAndStatus(String orderId, PickupTokenStatus status);
}
