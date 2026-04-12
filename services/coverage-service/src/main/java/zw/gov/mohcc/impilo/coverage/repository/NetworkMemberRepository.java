package zw.gov.mohcc.impilo.coverage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.coverage.domain.NetworkMemberEntity;

import java.util.Optional;
import java.util.UUID;

public interface NetworkMemberRepository extends JpaRepository<NetworkMemberEntity, Long> {

    Optional<NetworkMemberEntity> findByNetworkIdAndProviderIdAndRemovedAtIsNull(UUID networkId, String providerId);
}
