package zw.gov.mohcc.impilo.vito.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.vito.core.CardStatus;
import zw.gov.mohcc.impilo.vito.persistence.entity.SmartCardEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SmartCardRepository extends JpaRepository<SmartCardEntity, Long> {

    Optional<SmartCardEntity> findByTenantIdAndHealthIdAndStatus(
            UUID tenantId, UUID healthId, CardStatus status);

    List<SmartCardEntity> findByTenantIdAndHealthIdOrderByCreatedAtDesc(
            UUID tenantId, UUID healthId);

    Page<SmartCardEntity> findByTenantIdAndStatus(
            UUID tenantId, CardStatus status, Pageable pageable);

    Optional<SmartCardEntity> findByTenantIdAndCardNumber(
            UUID tenantId, String cardNumber);

    /** B0: dereference a scanned-QR pointer (HMAC of health_id) to its card. */
    List<SmartCardEntity> findByTenantIdAndCardPointerOrderByCreatedAtDesc(
            UUID tenantId, String cardPointer);

    Optional<SmartCardEntity> findByDidUri(String didUri);
}
