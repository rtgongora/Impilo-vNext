package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.CpdEvidenceEntity;

import java.util.List;

@Repository
public interface CpdEvidenceRepository extends JpaRepository<CpdEvidenceEntity, Long> {

    List<CpdEvidenceEntity> findByEventId(Long eventId);
}
