package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.EdDispositionEntity;

import java.util.Optional;
import java.util.UUID;

public interface EdDispositionRepository extends JpaRepository<EdDispositionEntity, UUID> {
    Optional<EdDispositionEntity> findByVisitId(UUID visitId);
}
