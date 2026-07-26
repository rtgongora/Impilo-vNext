package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pct.persistence.entity.CtgAnnotationEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface CtgAnnotationRepository extends JpaRepository<CtgAnnotationEntity, UUID> {

    List<CtgAnnotationEntity> findBySessionIdOrderByRecordedAtAsc(UUID sessionId);
}
