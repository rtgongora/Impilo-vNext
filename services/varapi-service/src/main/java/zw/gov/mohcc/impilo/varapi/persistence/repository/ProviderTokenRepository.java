package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderTokenEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProviderTokenRepository extends JpaRepository<ProviderTokenEntity, Long> {

    Optional<ProviderTokenEntity> findByLookupHash(String lookupHash);

    List<ProviderTokenEntity> findByProviderIdAndStatus(Long providerId, String status);

    List<ProviderTokenEntity> findByProviderIdOrderByIssuedAtDesc(Long providerId);
}
