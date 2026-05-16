package zw.gov.mohcc.impilo.docstore.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.docstore.persistence.entity.SignatureJobEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SignatureJobRepository extends JpaRepository<SignatureJobEntity, Long> {
    Optional<SignatureJobEntity> findTopByObjectIdOrderByRequestedAtDesc(UUID objectId);
    Optional<SignatureJobEntity> findByJobId(UUID jobId);
}
