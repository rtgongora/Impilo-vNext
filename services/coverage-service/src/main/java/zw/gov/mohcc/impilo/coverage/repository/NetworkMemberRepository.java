package zw.gov.mohcc.impilo.coverage.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.coverage.domain.NetworkMemberEntity;

public interface NetworkMemberRepository extends JpaRepository<NetworkMemberEntity, Long> {

    Optional<NetworkMemberEntity> findByNetworkIdAndProviderIdAndRemovedAtIsNull(UUID networkId, String providerId);

    List<NetworkMemberEntity> findByNetworkIdAndRemovedAtIsNullOrderByJoinedAtDesc(UUID networkId);

    long countByNetworkIdAndRemovedAtIsNull(UUID networkId);
}
