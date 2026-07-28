package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.TopAuthorisationEntity;

import java.util.UUID;

public interface TopAuthorisationRepository extends JpaRepository<TopAuthorisationEntity, UUID> {
}
