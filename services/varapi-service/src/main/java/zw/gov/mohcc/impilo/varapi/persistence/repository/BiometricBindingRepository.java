package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.BiometricBindingEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface BiometricBindingRepository extends JpaRepository<BiometricBindingEntity, Long> {

    List<BiometricBindingEntity> findByProviderIdAndStatus(Long providerId, String status);

    Optional<BiometricBindingEntity> findByBiometricRefAndStatus(String biometricRef, String status);
}
