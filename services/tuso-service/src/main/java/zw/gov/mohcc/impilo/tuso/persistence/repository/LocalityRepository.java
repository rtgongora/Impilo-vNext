package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.tuso.persistence.entity.LocalityEntity;

import java.util.List;

public interface LocalityRepository extends JpaRepository<LocalityEntity, Long> {

    @Query("SELECT l FROM LocalityEntity l WHERE l.districtCode = :districtCode AND l.status = 'APPROVED' "
            + "AND (LOWER(l.name) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(l.nameNormalized) LIKE LOWER(CONCAT('%', :q, '%'))) "
            + "ORDER BY l.name ASC")
    List<LocalityEntity> searchApproved(@Param("districtCode") String districtCode, @Param("q") String q);
}
