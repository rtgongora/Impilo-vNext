package zw.gov.mohcc.impilo.vito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.vito.persistence.entity.BiometricExceptionRecordEntity;

public interface BiometricExceptionRecordRepository extends JpaRepository<BiometricExceptionRecordEntity, Long> {
}
