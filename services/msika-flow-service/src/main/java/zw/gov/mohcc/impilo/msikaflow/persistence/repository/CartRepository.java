package zw.gov.mohcc.impilo.msikaflow.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.msikaflow.domain.CartStatus;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.CartEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CartRepository extends JpaRepository<CartEntity, String> {
    Optional<CartEntity> findByTenantIdAndActorIdAndStatus(UUID tenantId, String actorId, CartStatus status);
    List<CartEntity> findTop50ByTenantIdAndActorIdOrderByUpdatedAtDesc(UUID tenantId, String actorId);
}

