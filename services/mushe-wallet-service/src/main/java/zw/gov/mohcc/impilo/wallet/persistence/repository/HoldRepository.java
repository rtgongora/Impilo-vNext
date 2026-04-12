package zw.gov.mohcc.impilo.wallet.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.wallet.persistence.entity.HoldEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HoldRepository extends JpaRepository<HoldEntity, Long> {

    List<HoldEntity> findByWalletIdAndStatus(UUID walletId, String status);

    Optional<HoldEntity> findByHoldId(UUID holdId);

    List<HoldEntity> findByStatusAndExpiresAtBefore(String status, OffsetDateTime now);
}
