package zw.gov.mohcc.impilo.mushex.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.mushex.domain.entity.RemittanceTokenEntity;
import zw.gov.mohcc.impilo.mushex.domain.enums.RemittanceStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RemittanceTokenRepository extends JpaRepository<RemittanceTokenEntity, String> {

    Optional<RemittanceTokenEntity> findByTokenHash(String tokenHash);

    List<RemittanceTokenEntity> findByIntentId(String intentId);

    long countByIntentIdAndStatusAndCreatedAtAfter(String intentId, RemittanceStatus status, OffsetDateTime after);
}
