package zw.gov.mohcc.impilo.wallet.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.wallet.persistence.entity.CardHealthDataEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CardHealthDataRepository extends JpaRepository<CardHealthDataEntity, Long> {

    Optional<CardHealthDataEntity> findByCardIdAndDataType(UUID cardId, String dataType);

    List<CardHealthDataEntity> findByCardId(UUID cardId);
}
