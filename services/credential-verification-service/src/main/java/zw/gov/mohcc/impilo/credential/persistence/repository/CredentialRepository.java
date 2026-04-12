package zw.gov.mohcc.impilo.credential.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.credential.persistence.entity.CredentialEntity;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CredentialRepository extends JpaRepository<CredentialEntity, Long> {

    Optional<CredentialEntity> findByCredentialId(UUID credentialId);

    Optional<CredentialEntity> findByVerificationToken(String verificationToken);

    @Query("""
        SELECT c FROM CredentialEntity c
        WHERE c.tenantId = :tenantId
          AND (:subjectType IS NULL OR c.subjectType = :subjectType)
          AND (:subjectId IS NULL OR c.subjectId = :subjectId)
          AND (:credentialType IS NULL OR c.credentialType = :credentialType)
          AND (:status IS NULL OR c.status = :status)
        ORDER BY c.createdAt DESC
        """)
    Page<CredentialEntity> searchCredentials(
            @Param("tenantId") UUID tenantId,
            @Param("subjectType") String subjectType,
            @Param("subjectId") String subjectId,
            @Param("credentialType") String credentialType,
            @Param("status") String status,
            Pageable pageable);

    @Query("""
            SELECT COUNT(c) FROM CredentialEntity c
            WHERE c.tenantId = :tenantId
              AND c.subjectType = 'FACILITY'
              AND c.subjectId = :facilityId
              AND c.status = 'ACTIVE'
              AND c.validFrom <= :today
              AND (c.validTo IS NULL OR c.validTo >= :today)
            """)
    long countActiveFacilityCredentials(
            @Param("tenantId") UUID tenantId,
            @Param("facilityId") String facilityId,
            @Param("today") LocalDate today);
}
