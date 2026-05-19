package zw.gov.mohcc.impilo.sharedkernel.integration.ports;

import zw.gov.mohcc.impilo.sharedkernel.integration.ports.CanonicalReferences.*;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Canonical device integration port. Adapters: BluetoothGlucometerAdapter,
 * BluetoothBpDeviceAdapter, ColdChainSensorAdapter, VehicleTrackerAdapter,
 * DroneTelemetryAdapter, NativeDeviceAdapter.
 */
public interface DeviceIntegrationPort extends Adapter {

    OperationResult<DeviceReadingAck>   ingestReading(DeviceReading reading, OperationContext ctx);
    OperationResult<DeviceHealth>       checkDeviceHealth(String deviceId, OperationContext ctx);

    record DeviceReading(
            String deviceId, String deviceType, PatientReference patient,
            String measurement, double value, String unit,
            OffsetDateTime measuredAt, Map<String, Object> deviceMetadata
    ) {}
    record DeviceReadingAck(String ingestionId, String status) {}
    record DeviceHealth(String deviceId, String status, OffsetDateTime lastSeen, String firmwareVersion) {}
}
