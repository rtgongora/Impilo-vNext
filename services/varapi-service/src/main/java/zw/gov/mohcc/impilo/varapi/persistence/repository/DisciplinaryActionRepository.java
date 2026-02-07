package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.DisciplinaryActionEntity;

import java.util.List;

@Repository
public interface DisciplinaryActionRepository extends JpaRepository<DisciplinaryActionEntity, Long> {

    List<DisciplinaryActionEntity> findByProviderId(Long providerId);

    List<DisciplinaryActionEntity> findByProviderIdAndStatus(Long providerId, String status);
}
