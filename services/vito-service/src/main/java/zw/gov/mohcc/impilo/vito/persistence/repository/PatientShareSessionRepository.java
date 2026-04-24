package zw.gov.mohcc.impilo.vito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.vito.persistence.entity.PatientShareSessionEntity;

import java.util.Optional;

public interface PatientShareSessionRepository extends JpaRepository<PatientShareSessionEntity, Long> {

    Optional<PatientShareSessionEntity> findBySessionTokenHash(String sessionTokenHash);
}
