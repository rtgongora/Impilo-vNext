/**
 * deviceConfig.ts — real medical-device BLE identifiers, ported verbatim from the
 * Impilo Practitioner desktop app (`src/Constants/DeivceUUID.js`).
 *
 * These are vendor-specific UUIDs for the actual hardware (Jumper/AOJ oximeters,
 * OTC glucometers, AOJ / MT thermometers, blood-pressure monitors). They are NOT
 * the standard 16-bit GATT assigned numbers — using the standard ones is exactly
 * why scanning failed before.
 */

import type { DeviceKind } from "./dataParser";

// ── Vendor service / characteristic UUIDs ─────────────────────────────────────

export const UUID_SERVICE_OXIMETER = "cdeacb80-5235-4c07-8846-93a37ee6b86d";
export const UUID_CHAR_OXIMETER = "cdeacb81-5235-4c07-8846-93a37ee6b86d";

export const UUID_SERVICE_GLUCOMETER = "0000fff0-0000-1000-8000-00805f9b34fb";
export const UUID_CHAR_GLUCOMETER = "0000fff1-0000-1000-8000-00805f9b34fb";

export const UUID_SERVICE_THERMOMETER = "0000ffe0-0000-1000-8000-00805f9b34fb";
export const UUID_CHAR_THERMOMETER = "0000ffe1-0000-1000-8000-00805f9b34fb";

export const UUID_SERVICE_BT_THERMOMETER = "4d540100-0074-6865-726d-6f6d65746572";
export const UUID_CHAR_BT_THERMOMETER = "4d540001-0074-6865-726d-6f6d65746572";

export const UUID_SERVICE_BLOOD_PRESSURE = "0000fff0-0000-1000-8000-00805f9b34fb";
export const UUID_CHAR_BLOOD_PRESSURE = "0000fff1-0000-1000-8000-00805f9b34fb";

/** Client Characteristic Configuration Descriptor — used to enable notifications. */
export const UUID_CLIENT_CHARACTER_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
/** Standard GATT health-thermometer fallbacks (some new units advertise these). */
export const UUID_HEALTH_THERMOMETER_SERVICE = "00001809-0000-1000-8000-00805f9b34fb";
export const UUID_GATT_TEMPERATURE_MEASUREMENT = "00002a1c-0000-1000-8000-00805f9b34fb";

// ── Per-vital BLE wiring ──────────────────────────────────────────────────────

export interface VitalBleConfig {
  /** primary device kind (drives byte parsing) */
  deviceKind: DeviceKind;
  serviceUuid: string;
  characteristicUuid: string;
  /** legacy/alternate profile (thermometer) */
  fallbackServiceUuid?: string;
  fallbackCharacteristicUuid?: string;
  fallbackDeviceKind?: DeviceKind;
  /** device may not advertise the custom UUID in scan packets — list everything */
  scanAcceptAll?: boolean;
  /** extra services to expose after pairing */
  optionalScanServices?: string[];
  /**
   * oximeter streams continuously; one-shot devices (glucometer) should
   * disconnect after a valid reading.
   */
  streaming?: boolean;
}

export const BLE_OXIMETER: VitalBleConfig = {
  deviceKind: "Oximeter",
  serviceUuid: UUID_SERVICE_OXIMETER,
  characteristicUuid: UUID_CHAR_OXIMETER,
  streaming: true,
};

export const BLE_THERMOMETER: VitalBleConfig = {
  deviceKind: "BluetoothThermometer",
  serviceUuid: UUID_SERVICE_BT_THERMOMETER,
  characteristicUuid: UUID_CHAR_BT_THERMOMETER,
  fallbackServiceUuid: UUID_SERVICE_THERMOMETER,
  fallbackCharacteristicUuid: UUID_CHAR_THERMOMETER,
  fallbackDeviceKind: "Thermometer",
  scanAcceptAll: true,
  optionalScanServices: [
    UUID_SERVICE_BT_THERMOMETER,
    UUID_SERVICE_THERMOMETER,
    UUID_HEALTH_THERMOMETER_SERVICE,
    UUID_GATT_TEMPERATURE_MEASUREMENT,
  ],
  streaming: true,
};

export const BLE_BLOOD_PRESSURE: VitalBleConfig = {
  deviceKind: "BloodPressure",
  serviceUuid: UUID_SERVICE_BLOOD_PRESSURE,
  characteristicUuid: UUID_CHAR_BLOOD_PRESSURE,
  streaming: true,
};

export const BLE_GLUCOMETER: VitalBleConfig = {
  deviceKind: "Glucometer",
  serviceUuid: UUID_SERVICE_GLUCOMETER,
  characteristicUuid: UUID_CHAR_GLUCOMETER,
  streaming: false,
};
