package zw.gov.mohcc.impilo.vito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.vito.persistence.entity.BiometricIdentificationEventEntity;

public interface BiometricIdentificationEventRepository extends JpaRepository<BiometricIdentificationEventEntity, Long> {
}
