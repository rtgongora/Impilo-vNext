package zw.gov.mohcc.impilo.tshepo.identity.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.entity.MosipLinkEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MosipLinkRepository extends JpaRepository<MosipLinkEntity, Long> {

    Optional<MosipLinkEntity> findByLinkRefHash(String linkRefHash);

    Optional<MosipLinkEntity> findByTenantIdAndHealthId(UUID tenantId, UUID healthId);
}
