package zw.gov.mohcc.impilo.credential.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.credential.persistence.entity.VerificationLogEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface VerificationLogRepository extends JpaRepository<VerificationLogEntity, Long> {

    List<VerificationLogEntity> findByCredentialIdOrderByCreatedAtDesc(UUID credentialId);
}
