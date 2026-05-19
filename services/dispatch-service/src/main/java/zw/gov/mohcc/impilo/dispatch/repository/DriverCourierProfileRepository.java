package zw.gov.mohcc.impilo.dispatch.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.dispatch.domain.DriverCourierProfileEntity;

import java.util.List;
import java.util.UUID;

public interface DriverCourierProfileRepository extends JpaRepository<DriverCourierProfileEntity, UUID> {
    List<DriverCourierProfileEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
