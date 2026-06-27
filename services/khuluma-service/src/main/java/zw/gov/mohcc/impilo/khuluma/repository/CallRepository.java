package zw.gov.mohcc.impilo.khuluma.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.khuluma.domain.CallEntity;

import java.util.Optional;
import java.util.UUID;

public interface CallRepository extends JpaRepository<CallEntity, UUID> {

    Optional<CallEntity> findByCallIdAndTenantId(UUID callId, UUID tenantId);
}
