package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderBiometricVerificationEventEntity;

public interface ProviderBiometricVerificationEventRepository
        extends JpaRepository<ProviderBiometricVerificationEventEntity, Long> {
}
