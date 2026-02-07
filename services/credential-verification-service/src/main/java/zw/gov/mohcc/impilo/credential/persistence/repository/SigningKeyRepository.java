package zw.gov.mohcc.impilo.credential.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.credential.persistence.entity.SigningKeyEntity;

import java.util.Optional;

@Repository
public interface SigningKeyRepository extends JpaRepository<SigningKeyEntity, Long> {

    Optional<SigningKeyEntity> findByKeyId(String keyId);

    Optional<SigningKeyEntity> findFirstByStatusOrderByCreatedAtDesc(String status);
}
