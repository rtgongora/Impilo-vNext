package zw.gov.mohcc.impilo.simba.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.simba.persistence.entity.ScreeningProgrammeEntity;

import java.util.List;
import java.util.UUID;

public interface ScreeningProgrammeRepository extends JpaRepository<ScreeningProgrammeEntity, Long> {

    List<ScreeningProgrammeEntity> findByTenantIdAndStatusOrderByTitleAsc(UUID tenantId, String status);
}
