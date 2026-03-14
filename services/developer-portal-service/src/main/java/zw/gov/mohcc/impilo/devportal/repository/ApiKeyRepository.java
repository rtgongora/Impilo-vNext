package zw.gov.mohcc.impilo.devportal.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.devportal.domain.ApiKeyEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKeyEntity, UUID> {
    List<ApiKeyEntity> findByClientId(UUID clientId);
    Optional<ApiKeyEntity> findByKeyPrefix(String keyPrefix);
}
