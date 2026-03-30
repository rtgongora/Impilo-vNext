package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.OccupancySnapshotEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface OccupancySnapshotRepository extends JpaRepository<OccupancySnapshotEntity, Long> {

    @Query("SELECT os FROM OccupancySnapshotEntity os " +
           "WHERE os.facility.id = :facilityId " +
           "ORDER BY os.snapshotTime DESC LIMIT 1")
    OccupancySnapshotEntity findLatestByFacility(@Param("facilityId") Long facilityId);

    Page<OccupancySnapshotEntity> findByFacility_IdOrderBySnapshotTimeDesc(Long facilityId, Pageable pageable);

    List<OccupancySnapshotEntity> findByFacility_IdAndWardOrderBySnapshotTimeDesc(Long facilityId, String ward);

    Optional<OccupancySnapshotEntity> findTopByFacilityIdOrderBySnapshotTimeDesc(Long facilityId);
}
