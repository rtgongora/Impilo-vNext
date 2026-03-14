package zw.gov.mohcc.impilo.offlineedge.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.offlineedge.domain.EntitlementEntity;
import java.util.Optional;
import java.util.UUID;

public interface EntitlementRepository extends JpaRepository<EntitlementEntity, UUID> {
    Optional<EntitlementEntity> findByTokenHash(String tokenHash);
}
