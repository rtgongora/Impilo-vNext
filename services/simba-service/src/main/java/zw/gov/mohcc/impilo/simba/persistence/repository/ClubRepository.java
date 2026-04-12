package zw.gov.mohcc.impilo.simba.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.simba.persistence.entity.ClubEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClubRepository extends JpaRepository<ClubEntity, Long> {

    List<ClubEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<ClubEntity> findByClubIdAndTenantId(UUID clubId, UUID tenantId);
}
