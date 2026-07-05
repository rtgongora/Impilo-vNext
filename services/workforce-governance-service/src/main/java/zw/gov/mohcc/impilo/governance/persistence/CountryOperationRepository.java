package zw.gov.mohcc.impilo.governance.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CountryOperationRepository extends JpaRepository<CountryOperationEntity, UUID> {

    Optional<CountryOperationEntity> findByTenantId(UUID tenantId);

    List<CountryOperationEntity> findAllByOrderByCreatedAtAsc();
}
