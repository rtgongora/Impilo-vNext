package zw.gov.mohcc.impilo.procedures.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.procedures.persistence.entity.ComplicationClassEntity;

import java.util.List;
import java.util.UUID;

public interface ComplicationClassRepository extends JpaRepository<ComplicationClassEntity, UUID> {
    List<ComplicationClassEntity> findByProfileIdOrderByDisplayOrderAsc(UUID profileId);
}
