package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderIdentifierEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProviderIdentifierRepository extends JpaRepository<ProviderIdentifierEntity, Long> {

    List<ProviderIdentifierEntity> findByProviderId(Long providerId);

    Optional<ProviderIdentifierEntity> findByIdentifierSystemAndIdentifierValue(String identifierSystem,
                                                                                 String identifierValue);
}
