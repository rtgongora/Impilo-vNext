package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.InstrumentSetEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InstrumentSetRepository extends JpaRepository<InstrumentSetEntity, UUID> {

    List<InstrumentSetEntity> findByTenantIdAndSterilisationStateOrderByNameAsc(UUID tenantId, String state);

    List<InstrumentSetEntity> findByTenantIdOrderByNameAsc(UUID tenantId);

    Optional<InstrumentSetEntity> findByTenantIdAndId(UUID tenantId, UUID id);
}
