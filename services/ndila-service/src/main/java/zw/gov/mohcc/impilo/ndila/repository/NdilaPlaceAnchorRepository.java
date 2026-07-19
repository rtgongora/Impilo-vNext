package zw.gov.mohcc.impilo.ndila.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.ndila.domain.NdilaPlaceAnchorEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NdilaPlaceAnchorRepository extends JpaRepository<NdilaPlaceAnchorEntity, UUID> {

    Optional<NdilaPlaceAnchorEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
