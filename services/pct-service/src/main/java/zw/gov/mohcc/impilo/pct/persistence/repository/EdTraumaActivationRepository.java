package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.EdTraumaActivationEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EdTraumaActivationRepository extends JpaRepository<EdTraumaActivationEntity, UUID> {
    List<EdTraumaActivationEntity> findByVisitIdOrderByActivatedAtDesc(UUID visitId);
    Optional<EdTraumaActivationEntity> findFirstByVisitIdAndStatusOrderByActivatedAtDesc(UUID visitId, String status);
}
