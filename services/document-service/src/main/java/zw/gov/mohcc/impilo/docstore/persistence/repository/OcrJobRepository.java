package zw.gov.mohcc.impilo.docstore.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.docstore.persistence.entity.OcrJobEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OcrJobRepository extends JpaRepository<OcrJobEntity, Long> {
    Optional<OcrJobEntity> findTopByObjectIdOrderByRequestedAtDesc(UUID objectId);
}
