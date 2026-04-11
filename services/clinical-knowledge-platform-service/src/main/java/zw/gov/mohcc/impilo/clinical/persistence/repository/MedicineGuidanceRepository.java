package zw.gov.mohcc.impilo.clinical.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.clinical.persistence.entity.MedicineGuidanceEntity;

import java.util.List;
import java.util.UUID;

public interface MedicineGuidanceRepository extends JpaRepository<MedicineGuidanceEntity, UUID> {

    @Query("""
            select m from MedicineGuidanceEntity m
            where lower(coalesce(m.genericName,'')) like lower(concat('%', :q, '%'))
               or lower(coalesce(m.indication,'')) like lower(concat('%', :q, '%'))
               or lower(coalesce(m.medicineName,'')) like lower(concat('%', :q, '%'))
            """)
    List<MedicineGuidanceEntity> search(@Param("q") String q);
}
