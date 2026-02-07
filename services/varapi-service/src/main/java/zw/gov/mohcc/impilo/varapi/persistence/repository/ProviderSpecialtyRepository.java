package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderSpecialtyEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProviderSpecialtyRepository extends JpaRepository<ProviderSpecialtyEntity, Long> {

    List<ProviderSpecialtyEntity> findByProviderId(Long providerId);

    Optional<ProviderSpecialtyEntity> findByProviderIdAndPrimarySpecialtyTrue(Long providerId);
}
