"use client";

/**
 * VitalCard — single vital metric with real medical-device BLE integration.
 *
 * The Bluetooth engine is ported from the proven Impilo Practitioner desktop app
 * (Electron + Web Bluetooth). Real devices expose vendor-specific services and
 * stream raw byte packets, so we:
 *
 *   1. requestDevice({ acceptAllDevices, optionalServices })   ← lists every nearby device
 *   2. device.gatt.connect()
 *   3. resolve the vendor service + characteristic (with fallbacks / GATT discovery)
 *   4. write the CCCD descriptor (0x2902) to enable notifications
 *   5. startNotifications() and feed raw bytes to DataParser
 *   6. DataParser decodes per-device-kind → live SpO₂/pulse/temp/BP/glucose
 *
 * Streaming devices (oximeter, thermometer, BP) stay connected so the clinician
 * can watch the live value and tap Save. One-shot devices (glucometer) disconnect
 * after the first valid reading.
 */

import React, { useCallback, useEffect, useRef, useState } from "react";
import { Bluetooth, BluetoothOff, Clock, History, Pencil, Save, X } from "lucide-react";
import type { BleScanState } from "./DeviceModal";
import { DeviceModal } from "./DeviceModal";
import { DataParser, type DeviceReading, type DeviceKind } from "./dataParser";
import {
  UUID_CLIENT_CHARACTER_CONFIG,
  UUID_GATT_TEMPERATURE_MEASUREMENT,
  UUID_HEALTH_THERMOMETER_SERVICE,
  type VitalBleConfig,
} from "./deviceConfig";
import { formatBluetoothError } from "./bluetoothErrorMessage";

// ── Minimal Web Bluetooth API types (avoids needing @types/web-bluetooth) ────
type BleCharValueCallback = (ev: Event) => void;

interface BleCharacteristicProperties {
  notify: boolean;
  indicate: boolean;
}
interface BluetoothGATTDescriptor {
  writeValue(value: BufferSource): Promise<void>;
}
interface BluetoothGATTCharacteristic {
  uuid: string;
  value: DataView | null;
  properties: BleCharacteristicProperties;
  addEventListener(type: "characteristicvaluechanged", listener: BleCharValueCallback): void;
  removeEventListener(type: "characteristicvaluechanged", listener: BleCharValueCallback): void;
  startNotifications(): Promise<BluetoothGATTCharacteristic>;
  getDescriptor(uuid: string): Promise<BluetoothGATTDescriptor>;
}
interface BluetoothGATTService {
  uuid: string;
  getCharacteristic(uuid: string): Promise<BluetoothGATTCharacteristic>;
  getCharacteristics(): Promise<BluetoothGATTCharacteristic[]>;
}
interface BluetoothGATTServer {
  connected: boolean;
  connect(): Promise<BluetoothGATTServer>;
  disconnect(): void;
  getPrimaryService(uuid: string): Promise<BluetoothGATTService>;
  getPrimaryServices(): Promise<BluetoothGATTService[]>;
}
interface BleDevice {
  id: string;
  name?: string;
  gatt: BluetoothGATTServer;
  addEventListener(type: "gattserverdisconnected", listener: () => void): void;
}
interface BleApi {
  requestDevice(options: {
    acceptAllDevices?: boolean;
    filters?: Array<{ services?: string[]; name?: string; namePrefix?: string }>;
    optionalServices?: string[];
  }): Promise<BleDevice>;
}
declare global {
  interface Navigator {
    bluetooth?: BleApi;
  }
}

// ── Public types ──────────────────────────────────────────────────────────────

export interface VitalReading {
  value: number;
  value2?: number;
  timestamp: string;
  source: "device" | "manual";
}

export interface VitalCardConfig {
  key: string;
  label: string;
  unit: string;
  icon: string;
  color: string;
  /** BLE wiring — omit to make the card manual-entry only */
  ble?: VitalBleConfig;
  /** Field names on the POST body (primary and optional secondary) */
  postField: string;
  postField2?: string;
  /** Label for the secondary reading shown live (e.g. "Pulse") */
  secondaryLabel?: string;
  /** decimal places for display/save (e.g. temperature = 1) */
  decimals?: number;
  /** Plausibility guards */
  min?: number;
  max?: number;
  placeholder?: string;
  placeholder2?: string;
}

interface VitalCardProps {
  config: VitalCardConfig;
  history: VitalReading[];
  onSave: (primary: number, secondary?: number, source?: "device" | "manual") => Promise<void>;
}

// ── Helpers ───────────────────────────────────────────────────────────────────

const normalizeUuid = (uuid: string | undefined): string => (uuid || "").replace(/-/g, "").toLowerCase();

const bleValueToBytes = (value: DataView | null): Uint8Array => {
  if (value instanceof DataView) {
    return new Uint8Array(value.buffer, value.byteOffset, value.byteLength);
  }
  return new Uint8Array();
};

const round = (n: number, decimals = 0): number => {
  const f = Math.pow(10, decimals);
  return Math.round(n * f) / f;
};

/**
 * Map a decoded DeviceReading to this card's primary/secondary numeric values.
 * Returns null when the reading does not apply to this device kind.
 */
function mapReading(
  reading: DeviceReading,
  kind: DeviceKind,
  decimals = 0,
): { primary: number; secondary?: number } | null {
  switch (kind) {
    case "Oximeter":
    case "BloodOxygen": {
      if (reading.device !== "Oximeter" && reading.device !== "BloodOxygen") return null;
      const spo2 = reading.spo2 > 70 ? Math.round(reading.spo2) : null;
      const pulse = reading.pulseRate > 30 ? Math.round(reading.pulseRate) : null;
      if (spo2 === null && pulse === null) return null;
      return { primary: spo2 ?? 0, secondary: pulse ?? undefined };
    }
    case "BloodPressure": {
      if (reading.device !== "BloodPressure") return null;
      const sys = Number(reading.systolic);
      const dia = Number(reading.diastolic);
      if (!Number.isFinite(sys) || !Number.isFinite(dia)) return null;
      if (sys < 50 || sys > 250 || dia < 30 || dia > 150) return null;
      return { primary: sys, secondary: dia };
    }
    case "Thermometer":
    case "BluetoothThermometer": {
      if (reading.device !== "Thermometer") return null;
      const temp = Number(reading.celcius);
      if (!Number.isFinite(temp) || temp < 32 || temp > 43) return null;
      return { primary: round(temp, decimals || 1) };
    }
    case "Glucometer": {
      if (reading.device !== "Glucometer") return null;
      if (reading.glucose === null || reading.glucose === undefined) return null;
      const mmol = round(reading.glucose / 18, decimals || 1);
      if (!Number.isFinite(mmol) || mmol <= 0) return null;
      return { primary: mmol };
    }
    default:
      return null;
  }
}

// ── Component ────────────────────────────────────────────────────────────────

export const VitalCard: React.FC<VitalCardProps> = ({ config, history, onSave }) => {
  const { label, unit, icon, color, ble } = config;

  const [modalOpen, setModalOpen] = useState(false);
  const [scanState, setScanState] = useState<BleScanState>("idle");
  const [bleError, setBleError] = useState<string | undefined>();
  const [liveValue, setLiveValue] = useState<number | null>(null);
  const [liveValue2, setLiveValue2] = useState<number | null>(null);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [manualMode, setManualMode] = useState(false);
  const [manualVal, setManualVal] = useState("");
  const [manualVal2, setManualVal2] = useState("");
  const [saveOk, setSaveOk] = useState(false);

  const deviceRef = useRef<BleDevice | null>(null);
  const charRef = useRef<BluetoothGATTCharacteristic | null>(null);
  const listenerRef = useRef<BleCharValueCallback | null>(null);
  const connectedKindRef = useRef<DeviceKind>(ble?.deviceKind ?? "Oximeter");
  const parserRef = useRef<DataParser | null>(null);

  const latest = history[0];

  // ── Disconnect ──────────────────────────────────────────────────────────────

  const disconnectBle = useCallback(() => {
    try {
      if (charRef.current && listenerRef.current) {
        charRef.current.removeEventListener("characteristicvaluechanged", listenerRef.current);
      }
      if (deviceRef.current?.gatt?.connected) {
        deviceRef.current.gatt.disconnect();
      }
    } catch {
      /* ignore */
    }
    deviceRef.current = null;
    charRef.current = null;
    listenerRef.current = null;
    parserRef.current = null;
    setLiveValue(null);
    setLiveValue2(null);
    setScanState("idle");
  }, []);

  useEffect(() => {
    return () => {
      if (deviceRef.current?.gatt?.connected) {
        try {
          deviceRef.current.gatt.disconnect();
        } catch {
          /* ignore */
        }
      }
    };
  }, []);

  // ── CCCD enable (notify/indicate) ─────────────────────────────────────────────

  const enableNotifications = useCallback(async (characteristic: BluetoothGATTCharacteristic) => {
    try {
      const descriptor = await characteristic.getDescriptor(UUID_CLIENT_CHARACTER_CONFIG);
      try {
        await descriptor.writeValue(Uint8Array.from([0x01, 0x00]));
      } catch {
        await descriptor.writeValue(Uint8Array.from([0x02, 0x00]));
      }
    } catch {
      // Descriptor write may be unsupported — startNotifications still works on many devices.
    }
    await characteristic.startNotifications();
  }, []);

  // ── Resolve the vendor service + characteristic ───────────────────────────────

  const resolveCharacteristic = useCallback(
    async (server: BluetoothGATTServer, deviceName: string): Promise<BluetoothGATTCharacteristic> => {
      if (!ble) throw new Error("No BLE configuration");

      const candidates: Array<{ serviceId: string; charId: string; kind: DeviceKind }> = [
        { serviceId: ble.serviceUuid, charId: ble.characteristicUuid, kind: ble.deviceKind },
      ];
      if (ble.fallbackServiceUuid && ble.fallbackCharacteristicUuid) {
        candidates.push({
          serviceId: ble.fallbackServiceUuid,
          charId: ble.fallbackCharacteristicUuid,
          kind: ble.fallbackDeviceKind ?? ble.deviceKind,
        });
      }
      const isThermometer =
        ble.deviceKind === "BluetoothThermometer" || ble.deviceKind === "Thermometer";
      if (isThermometer) {
        candidates.push({
          serviceId: UUID_HEALTH_THERMOMETER_SERVICE,
          charId: UUID_GATT_TEMPERATURE_MEASUREMENT,
          kind: "BluetoothThermometer",
        });
      }

      // 1) Try each known service/characteristic pair directly.
      for (const candidate of candidates) {
        try {
          const service = await server.getPrimaryService(candidate.serviceId);
          const characteristic = await service.getCharacteristic(candidate.charId);
          connectedKindRef.current = candidate.kind;
          return characteristic;
        } catch {
          continue;
        }
      }

      // 2) Fall back to scanning all GATT services for a matching characteristic.
      const targetCharIds = new Set(candidates.map((c) => normalizeUuid(c.charId)));
      const services = await server.getPrimaryServices();
      for (const service of services) {
        const characteristics = await service.getCharacteristics();
        for (const characteristic of characteristics) {
          const charUuid = normalizeUuid(characteristic.uuid);
          const matchesKnownChar = targetCharIds.has(charUuid);
          const matchesThermometerChar =
            isThermometer &&
            (charUuid.includes("4d540001") ||
              charUuid.includes("0000ffe1") ||
              charUuid.includes("00002a1c"));
          // Broad notify/indicate sniffing is only safe for thermometers, which
          // may advertise non-standard characteristic UUIDs.
          const matchesThermometerNotify =
            isThermometer && (characteristic.properties.notify || characteristic.properties.indicate);

          if (matchesKnownChar || matchesThermometerChar || matchesThermometerNotify) {
            if (isThermometer) {
              connectedKindRef.current = charUuid.includes("0000ffe1")
                ? "Thermometer"
                : "BluetoothThermometer";
            }
            return characteristic;
          }
        }
      }

      throw new Error(`No compatible ${label} service found on this device`);
    },
    [ble, label],
  );

  // ── Connect ────────────────────────────────────────────────────────────────

  const requestDevice = useCallback(async () => {
    if (!ble) return;

    if (typeof navigator === "undefined" || !navigator.bluetooth) {
      setScanState("error");
      setBleError("Web Bluetooth is not supported in this browser. Use Chrome or Edge on desktop.");
      return;
    }

    setScanState("requesting");
    setBleError(undefined);
    setLiveValue(null);
    setLiveValue2(null);

    // optionalServices must list every service we may read after pairing.
    const optionalServices = [
      ble.serviceUuid,
      ble.fallbackServiceUuid,
      UUID_CLIENT_CHARACTER_CONFIG,
      ...(ble.optionalScanServices || []),
    ].filter(Boolean) as string[];

    // Some devices don't advertise their custom service in scan packets, so we
    // accept all devices and let the clinician pick from the full list.
    const requestOptions = ble.scanAcceptAll
      ? { acceptAllDevices: true, optionalServices: [...new Set(optionalServices)] }
      : { filters: [{ services: [ble.serviceUuid] }], optionalServices: [...new Set(optionalServices)] };

    try {
      const device = await navigator.bluetooth.requestDevice(requestOptions);
      setScanState("connecting");
      deviceRef.current = device;

      device.addEventListener("gattserverdisconnected", () => {
        disconnectBle();
      });

      const server = await device.gatt.connect();
      const characteristic = await resolveCharacteristic(server, device.name ?? "");
      charRef.current = characteristic;

      // Build the parser bound to this card's live state.
      const parser = new DataParser({
        onReadingChanged: (reading) => {
          const mapped = mapReading(reading, connectedKindRef.current, config.decimals);
          if (!mapped) return;
          setLiveValue(mapped.primary);
          if (mapped.secondary !== undefined) setLiveValue2(mapped.secondary);
          // One-shot devices: stop after the first valid reading.
          if (ble.streaming === false) {
            setTimeout(() => disconnectBle(), 300);
          }
        },
      });
      parserRef.current = parser;

      const listener: BleCharValueCallback = (evt) => {
        const target = evt.target as unknown as { value: DataView | null };
        const bytes = bleValueToBytes(target.value);
        if (bytes.length === 0) return;
        const kind = connectedKindRef.current;

        // Length guards mirror the desktop app's handleNotifications().
        if (kind === "Oximeter" || kind === "BloodOxygen") {
          if (bytes.length === 10) parser.add(bytes, "Oximeter");
        } else if (kind === "BloodPressure") {
          if (bytes.length === 8) parser.add(bytes, "BloodPressure");
        } else if (kind === "Thermometer" || kind === "BluetoothThermometer") {
          parser.add(bytes, kind);
        } else if (kind === "Glucometer") {
          parser.add(bytes, "Glucometer");
        }
      };
      listenerRef.current = listener;

      characteristic.addEventListener("characteristicvaluechanged", listener);
      await enableNotifications(characteristic);

      setScanState("connected");
      setModalOpen(false);
    } catch (err: unknown) {
      const e = err as { name?: string; message?: string };
      if (e?.name === "NotFoundError" || /user cancelled|user canceled/i.test(e?.message ?? "")) {
        setScanState("idle");
        setModalOpen(false);
        disconnectBle();
        return;
      }
      const friendly = formatBluetoothError(err, label);
      setScanState("error");
      setBleError(friendly.message);
    }
  }, [ble, config.decimals, disconnectBle, enableNotifications, label, resolveCharacteristic]);

  // ── Save ──────────────────────────────────────────────────────────────────

  const handleSave = async (primary: number, secondary?: number, source: "device" | "manual" = "device") => {
    setSaving(true);
    try {
      await onSave(primary, secondary, source);
      setSaveOk(true);
      setTimeout(() => setSaveOk(false), 2000);
    } finally {
      setSaving(false);
    }
  };

  const handleManualSave = async () => {
    const v = parseFloat(manualVal);
    const v2 = manualVal2 ? parseFloat(manualVal2) : undefined;
    if (isNaN(v)) return;
    await handleSave(v, v2, "manual");
    setManualVal("");
    setManualVal2("");
    setManualMode(false);
  };

  // ── Render ──────────────────────────────────────────────────────────────────

  const isConnected = scanState === "connected" && !!deviceRef.current?.gatt?.connected;
  const dec = config.decimals ?? 0;

  return (
    <>
      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden flex flex-col">
        {/* Card header */}
        <div className={`px-4 pt-4 pb-3 ${color} rounded-t-2xl`}>
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span className="text-2xl">{icon}</span>
              <span className="text-sm font-semibold text-white/90">{label}</span>
            </div>
            {isConnected ? (
              <button
                type="button"
                onClick={disconnectBle}
                title="Disconnect"
                className="p-1.5 rounded-lg bg-white/20 hover:bg-white/30 transition-colors"
              >
                <BluetoothOff className="h-3.5 w-3.5 text-white" />
              </button>
            ) : ble ? (
              <button
                type="button"
                onClick={() => {
                  setScanState("idle");
                  setBleError(undefined);
                  setModalOpen(true);
                }}
                title="Connect Bluetooth device"
                className="p-1.5 rounded-lg bg-white/20 hover:bg-white/30 transition-colors"
              >
                <Bluetooth className="h-3.5 w-3.5 text-white" />
              </button>
            ) : null}
          </div>

          {/* Live / latest value */}
          <div className="mt-3">
            {liveValue !== null ? (
              <div>
                <div className="flex items-end gap-1">
                  <span className="text-3xl font-bold text-white">{round(liveValue, dec)}</span>
                  {liveValue2 !== null && (
                    <span className="text-lg font-semibold text-white/80 mb-0.5">
                      /{round(liveValue2, dec)}
                    </span>
                  )}
                  <span className="text-sm text-white/70 mb-0.5 ml-0.5">{unit}</span>
                </div>
                {liveValue2 !== null && config.secondaryLabel && (
                  <p className="text-xs text-white/70 mt-0.5">
                    {config.secondaryLabel}: {round(liveValue2, dec)}
                  </p>
                )}
                <div className="flex items-center gap-2 mt-1">
                  <span className="text-xs text-white/60 flex items-center gap-1">
                    <span className="h-1.5 w-1.5 rounded-full bg-green-300 animate-pulse inline-block" />
                    Live
                  </span>
                  <button
                    type="button"
                    disabled={saving}
                    onClick={() => handleSave(liveValue, liveValue2 ?? undefined)}
                    className="px-2 py-0.5 rounded-full bg-white/20 hover:bg-white/30 text-white text-xs font-medium flex items-center gap-1 transition-colors disabled:opacity-60"
                  >
                    {saving ? (
                      <span className="h-3 w-3 border-2 border-white/60 border-t-white rounded-full animate-spin" />
                    ) : saveOk ? (
                      "Saved ✓"
                    ) : (
                      <>
                        <Save className="h-3 w-3" /> Save
                      </>
                    )}
                  </button>
                </div>
              </div>
            ) : latest ? (
              <div>
                <div className="flex items-end gap-1">
                  <span className="text-3xl font-bold text-white">{latest.value}</span>
                  {latest.value2 !== undefined && (
                    <span className="text-lg font-semibold text-white/80 mb-0.5">/{latest.value2}</span>
                  )}
                  <span className="text-sm text-white/70 mb-0.5 ml-0.5">{unit}</span>
                </div>
                <p className="text-xs text-white/60 mt-0.5">
                  Last: {new Date(latest.timestamp).toLocaleString()}
                </p>
              </div>
            ) : (
              <div>
                <span className="text-3xl font-bold text-white/50">—</span>
                <p className="text-xs text-white/50 mt-0.5">No reading yet</p>
              </div>
            )}
          </div>
        </div>

        {/* Card actions */}
        <div className="px-4 py-3 flex items-center gap-2 border-b border-gray-100">
          {ble && !isConnected && (
            <button
              type="button"
              onClick={() => {
                setScanState("idle");
                setBleError(undefined);
                setModalOpen(true);
              }}
              className="flex-1 flex items-center justify-center gap-1.5 py-2 rounded-xl bg-impilo-50 text-impilo-700 text-xs font-medium hover:bg-impilo-100 transition-colors"
            >
              <Bluetooth className="h-3.5 w-3.5" />
              Connect device
            </button>
          )}
          <button
            type="button"
            onClick={() => setManualMode((v) => !v)}
            className="flex-1 flex items-center justify-center gap-1.5 py-2 rounded-xl bg-gray-50 text-gray-600 text-xs font-medium hover:bg-gray-100 transition-colors"
          >
            <Pencil className="h-3.5 w-3.5" />
            Manual entry
          </button>
        </div>

        {/* Manual entry inline form */}
        {manualMode && (
          <div className="px-4 py-3 border-b border-gray-100 bg-gray-50/60">
            <div className="flex gap-2">
              <input
                type="number"
                value={manualVal}
                onChange={(e) => setManualVal(e.target.value)}
                placeholder={config.placeholder ?? `${label} (${unit})`}
                className="flex-1 min-w-0 text-sm border border-gray-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-impilo-400"
              />
              {config.postField2 && (
                <input
                  type="number"
                  value={manualVal2}
                  onChange={(e) => setManualVal2(e.target.value)}
                  placeholder={config.placeholder2 ?? "Value 2"}
                  className="flex-1 min-w-0 text-sm border border-gray-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-impilo-400"
                />
              )}
              <button
                type="button"
                disabled={!manualVal || saving}
                onClick={handleManualSave}
                className="px-3 py-2 rounded-lg bg-impilo-600 text-white text-xs font-medium disabled:opacity-50 hover:bg-impilo-700 transition-colors"
              >
                {saving ? "…" : "Save"}
              </button>
              <button
                type="button"
                onClick={() => setManualMode(false)}
                className="p-2 rounded-lg text-gray-400 hover:text-gray-600 hover:bg-gray-100 transition-colors"
              >
                <X className="h-4 w-4" />
              </button>
            </div>
          </div>
        )}

        {/* History launcher */}
        <div className="px-4 py-2.5">
          <button
            type="button"
            onClick={() => setHistoryOpen(true)}
            disabled={history.length === 0}
            className="w-full flex items-center justify-center gap-1.5 text-xs font-medium text-gray-500 hover:text-impilo-700 disabled:text-gray-300 disabled:cursor-not-allowed transition-colors"
          >
            <History className="h-3.5 w-3.5" />
            {history.length > 0
              ? `View history (${history.length})`
              : "No previous records"}
          </button>
        </div>
      </div>

      {/* History popup */}
      {historyOpen && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 px-4"
          onClick={() => setHistoryOpen(false)}
        >
          <div
            className="w-full max-w-md rounded-2xl bg-white shadow-2xl overflow-hidden"
            onClick={(e) => e.stopPropagation()}
          >
            {/* Header */}
            <div className={`px-5 py-4 ${color} flex items-center justify-between`}>
              <div className="flex items-center gap-2">
                <span className="text-2xl">{icon}</span>
                <div>
                  <h2 className="text-base font-semibold text-white">{label} history</h2>
                  <p className="text-xs text-white/70">
                    {history.length} record{history.length !== 1 ? "s" : ""}
                  </p>
                </div>
              </div>
              <button
                type="button"
                onClick={() => setHistoryOpen(false)}
                className="p-1.5 rounded-lg bg-white/20 hover:bg-white/30 transition-colors"
              >
                <X className="h-4 w-4 text-white" />
              </button>
            </div>

            {/* Body */}
            {history.length === 0 ? (
              <div className="px-5 py-10 text-center text-sm text-gray-400">
                No previous records for this patient yet.
              </div>
            ) : (
              <div className="max-h-[60vh] overflow-y-auto divide-y divide-gray-100">
                {history.map((r, i) => (
                  <div key={i} className="flex items-center justify-between px-5 py-3">
                    <div className="flex items-baseline gap-1">
                      <span className="text-lg font-bold text-gray-900">
                        {r.value}
                        {r.value2 !== undefined ? `/${r.value2}` : ""}
                      </span>
                      <span className="text-xs text-gray-400">{unit}</span>
                      {r.value2 !== undefined && config.secondaryLabel && (
                        <span className="text-xs text-gray-400 ml-1">
                          ({config.secondaryLabel} {r.value2})
                        </span>
                      )}
                    </div>
                    <div className="flex items-center gap-2">
                      <span
                        className={`text-[10px] px-1.5 py-0.5 rounded-full ${
                          r.source === "device"
                            ? "bg-blue-50 text-blue-600"
                            : "bg-gray-100 text-gray-500"
                        }`}
                      >
                        {r.source === "device" ? "Device" : "Manual"}
                      </span>
                      <span className="flex items-center gap-1 text-xs text-gray-400">
                        <Clock className="h-3 w-3" />
                        {new Date(r.timestamp).toLocaleString()}
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            )}

            {/* Footer */}
            <div className="px-5 py-3 border-t border-gray-100">
              <button
                type="button"
                onClick={() => setHistoryOpen(false)}
                className="w-full rounded-xl border border-gray-200 py-2.5 text-sm text-gray-600 hover:bg-gray-50 transition-colors"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Bluetooth scanner modal */}
      <DeviceModal
        isOpen={modalOpen}
        scanState={scanState}
        vitalName={label}
        errorMessage={bleError}
        onClose={() => {
          if (scanState !== "connecting") {
            if (scanState !== "connected") setScanState("idle");
            setModalOpen(false);
          }
        }}
        onRequestDevice={requestDevice}
      />
    </>
  );
};
