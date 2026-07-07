package zw.gov.mohcc.impilo.simba.social.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.simba.social.entity.SocialGroupEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SocialGroupRepository extends JpaRepository<SocialGroupEntity, Long> {

    Optional<SocialGroupEntity> findByGroupIdAndTenantId(UUID groupId, UUID tenantId);

    List<SocialGroupEntity> findByTenantIdAndStatusOrderByNameAsc(UUID tenantId, String status);
}
