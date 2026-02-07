package zw.gov.mohcc.impilo.docstore.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.docstore.persistence.entity.SignedUrlEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SignedUrlRepository extends JpaRepository<SignedUrlEntity, Long> {

    Optional<SignedUrlEntity> findByUrlToken(String urlToken);

    List<SignedUrlEntity> findByObjectId(UUID objectId);

    List<SignedUrlEntity> findByExpiresAtBefore(OffsetDateTime dateTime);
}
