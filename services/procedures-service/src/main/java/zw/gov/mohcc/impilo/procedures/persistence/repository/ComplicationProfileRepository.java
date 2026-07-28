package zw.gov.mohcc.impilo.procedures.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.procedures.persistence.entity.ComplicationProfileEntity;

import java.util.Optional;
import java.util.UUID;

public interface ComplicationProfileRepository extends JpaRepository<ComplicationProfileEntity, UUID> {
    Optional<ComplicationProfileEntity> findByTenantIdAndProfileCodeAndStatus(
            UUID tenantId, String profileCode, String status);
}
