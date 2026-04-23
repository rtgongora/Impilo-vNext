package zw.gov.mohcc.impilo.vito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.vito.persistence.entity.PatientShareContributionEntity;

import java.util.List;

public interface PatientShareContributionRepository extends JpaRepository<PatientShareContributionEntity, Long> {

    List<PatientShareContributionEntity> findByAccessGrantIdOrderByCreatedAtDesc(long accessGrantId);
}
