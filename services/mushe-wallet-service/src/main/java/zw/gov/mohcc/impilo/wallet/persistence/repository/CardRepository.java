package zw.gov.mohcc.impilo.wallet.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.wallet.persistence.entity.CardEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CardRepository extends JpaRepository<CardEntity, Long> {

    Optional<CardEntity> findByCardId(UUID cardId);

    List<CardEntity> findByWalletId(UUID walletId);

    List<CardEntity> findByWalletIdAndStatus(UUID walletId, String status);

    Optional<CardEntity> findByCardNumber(String cardNumber);

    Optional<CardEntity> findByCardIdAndTenantId(UUID cardId, UUID tenantId);
}
