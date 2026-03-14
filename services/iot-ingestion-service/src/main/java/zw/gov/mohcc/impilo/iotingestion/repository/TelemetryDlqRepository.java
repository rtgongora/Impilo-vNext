package zw.gov.mohcc.impilo.iotingestion.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.iotingestion.domain.TelemetryDlqEntity;

public interface TelemetryDlqRepository extends JpaRepository<TelemetryDlqEntity, Long> {

    long countByDeviceId(String deviceId);

    long countByErrorCode(String errorCode);
}
