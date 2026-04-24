package zw.gov.mohcc.impilo.vito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.vito.persistence.entity.BiometricVerificationEventEntity;

public interface BiometricVerificationEventRepository extends JpaRepository<BiometricVerificationEventEntity, Long> {
}
