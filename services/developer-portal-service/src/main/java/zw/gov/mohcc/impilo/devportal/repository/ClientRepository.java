package zw.gov.mohcc.impilo.devportal.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.devportal.domain.ClientEntity;

import java.util.List;
import java.util.UUID;

public interface ClientRepository extends JpaRepository<ClientEntity, UUID> {
    List<ClientEntity> findByTenantId(UUID tenantId);
}
