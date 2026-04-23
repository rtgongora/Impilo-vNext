package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.CpdCycleEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface CpdCycleRepository extends JpaRepository<CpdCycleEntity, Long> {

    List<CpdCycleEntity> findByProviderId(Long providerId);

    List<CpdCycleEntity> findByProviderIdAndStatus(Long providerId, String status);

    Optional<CpdCycleEntity> findFirstByProvider_IdAndStatusOrderByIdDesc(Long providerId, String status);
}
