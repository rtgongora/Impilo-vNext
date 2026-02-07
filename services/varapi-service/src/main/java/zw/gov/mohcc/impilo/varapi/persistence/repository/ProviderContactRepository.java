package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderContactEntity;

import java.util.List;

@Repository
public interface ProviderContactRepository extends JpaRepository<ProviderContactEntity, Long> {

    List<ProviderContactEntity> findByProviderId(Long providerId);

    void deleteByProviderId(Long providerId);
}
