package zw.gov.mohcc.impilo.khuluma.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.khuluma.domain.ChannelAdapterEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChannelAdapterRepository extends JpaRepository<ChannelAdapterEntity, UUID> {

    Optional<ChannelAdapterEntity> findByTenantIdAndChannel(UUID tenantId, String channel);

    List<ChannelAdapterEntity> findByTenantIdOrderByChannelAsc(UUID tenantId);
}
