package zw.gov.mohcc.impilo.telemonitoring.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.MonitoringProgrammeEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MonitoringProgrammeRepository extends JpaRepository<MonitoringProgrammeEntity, UUID> {

    Optional<MonitoringProgrammeEntity> findByCode(String code);

    List<MonitoringProgrammeEntity> findByActiveTrueOrderByCodeAsc();
}
