package zw.gov.mohcc.impilo.inpatient.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.WardRoundEntity;

import java.util.List;
import java.util.UUID;

public interface WardRoundRepository extends JpaRepository<WardRoundEntity, UUID> {

    List<WardRoundEntity> findByAdmissionRefOrderByStartedAtDesc(UUID admissionRef);

    List<WardRoundEntity> findByWardIdOrderByStartedAtDesc(UUID wardId);
}
