package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.ZwAdminDistrictEntity;

import java.util.List;
import java.util.Optional;

public interface ZwAdminDistrictRepository extends JpaRepository<ZwAdminDistrictEntity, Long> {

    List<ZwAdminDistrictEntity> findByProvinceCodeOrderByNameAsc(String provinceCode);

    Optional<ZwAdminDistrictEntity> findByDistrictCode(String districtCode);
}
