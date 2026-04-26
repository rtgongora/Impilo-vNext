package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.ZwAdminWardEntity;

import java.util.List;

public interface ZwAdminWardRepository extends JpaRepository<ZwAdminWardEntity, Long> {

    List<ZwAdminWardEntity> findByDistrictCodeOrderByNameAsc(String districtCode);
}
