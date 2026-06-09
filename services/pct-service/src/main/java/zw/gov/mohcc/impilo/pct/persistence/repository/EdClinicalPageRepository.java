package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.EdClinicalPageEntity;

import java.util.List;
import java.util.UUID;

public interface EdClinicalPageRepository extends JpaRepository<EdClinicalPageEntity, UUID> {
    List<EdClinicalPageEntity> findByVisitIdOrderBySentAtDesc(UUID visitId);
}
