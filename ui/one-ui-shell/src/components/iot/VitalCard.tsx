"use client";

/**
 * VitalCard — displays a single vital metric with:
 *   - Latest reading + mini history list
 *   - Web Bluetooth "Connect device" button
 *   - Live reading while connected
 *   - Manual entry fallback
 *
 * Bluetooth logic mirrors the Electron pattern from the reference files:
 *   requestDevice → gatt.connect → getService → getCharacteristic → startNotifications
 *
 * Each vital config supplies the BLE service/characteristic UUIDs used for that
 * particular device category (blood pressure cuff, oximeter, thermometer, scale…).
 */

import React, { useCallback, useRef, useState } from "react";
import { Bluetooth, BluetoothOff, ChevronDown, ChevronUp, Pencil, Save, X } from "lucide-react";
import type { BleScanState } from "./DeviceModal";
import { DeviceModal } from "./DeviceModal";

// ── Minimal Web Bluetooth API types (avoids needing @types/web-bluetooth) ────
type BleCharValueCallback = (ev: { target: { value: DataView | null } }) => void;

interface BluetoothGATTCharacteristic {
  value: DataView | null;
  addEventListener(type: "characteristicvaluechanged", listener: BleCharValueCallback): void;
  removeEventListener(type: "characteristicvaluechanged", listener: BleCharValueCallback): void;
  startNotifications(): Promise<BluetoothGATTCharacteristic>;
}
interface BluetoothGATTService {
  getCharacteristic(uuid: string): Promise<BluetoothGATTCharacteristic>;
}
interface BluetoothGATTServer {
  connected: boolean;
  connect(): Promise<BluetoothGATTServer>;
  disconnect(): void;
  getPrimaryService(uuid: string): Promise<BluetoothGATTService>;
}
interface BleDevice {
  id: string;
  name?: string;
  gatt: BluetoothGATTServer;
  addEventListener(type: "gattserverdisconnected", listener: () => void): void;
}
interface BleApi {
  requestDevice(options: { filters: Array<{ services: string[] }>; optionalServices: string[] }): Promise<BleDevice>;
}
declare global {
  interface Navigator { bluetooth?: BleApi }
}

// ── BLE UUID helpers ─────────────────────────────────────────────────────────

export interface VitalBleConfig {
  /** BLE service UUID accepted by requestDevice() filters/optionalServices */
  serviceUuid: string;
  /** BLE characteristic UUID to subscribe to (notify/indicate) */
  characteristicUuid: string;
  /** Parse a DataView from the characteristic notification into a numeric value */
  parseValue: (data: DataView) => number | null;
  /** Optional: second value (e.g. diastolic for BP) */
  parseSecondValue?: (data: DataView) => number | null;
}

// ── Types ────────────────────────────────────────────────────────────────────

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
  /** BLE config — omit to disable Bluetooth for this card */
  ble?: VitalBleConfig;
  /** Field names on the POST body (primary and optional secondary) */
  postField: string;
  postField2?: string;
  /** Plausibility guards */
  min?: number;
  max?: number;
  /** Manual entry placeholder */
  placeholder?: string;
  placeholder2?: string;
}

interface VitalCardProps {
  config: VitalCardConfig;
  history: VitalReading[];
  onSave: (primary: number, secondary?: number, source?: "device" | "manual") => Promise<void>;
}

// ── Component ────────────────────────────────────────────────────────────────

export const VitalCard: React.FC<VitalCardProps> = ({ config, history, onSave }) => {
  const { label, unit, icon, color, ble } = config;

  const [modalOpen, setModalOpen] = useState(false);
  const [scanState, setScanState] = useState<BleScanState>("idle");
  const [bleError, setBleError] = useState<string | undefined>();
  const [liveValue, setLiveValue] = useState<number | null>(null);
  const [liveValue2, setLiveValue2] = useState<number | null>(null);
  const [expanded, setExpanded] = useState(false);
  const [saving, setSaving] = useState(false);
  const [manualMode, setManualMode] = useState(false);
  const [manualVal, setManualVal] = useState("");
  const [manualVal2, setManualVal2] = useState("");
  const [saveOk, setSaveOk] = useState(false);

  const deviceRef = useRef<BleDevice | null>(null);
  const charRef = useRef<BluetoothGATTCharacteristic | null>(null);

  const latest = history[0];

  // ── Bluetooth helpers ──────────────────────────────────────────────────────

  const disconnectBle = useCallback(() => {
    try {
      charRef.current?.removeEventListener("characteristicvaluechanged", () => {});
      if (deviceRef.current?.gatt?.connected) {
        deviceRef.current.gatt.disconnect();
      }
    } catch (_) {}
    deviceRef.current = null;
    charRef.current = null;
    setLiveValue(null);
    setLiveValue2(null);
  }, []);

  const requestDevice = useCallback(async () => {
    if (!ble) return;

    if (!navigator.bluetooth) {
      setScanState("error");
      setBleError("Web Bluetooth is not supported in this browser. Use Chrome or Edge.");
      return;
    }

    setScanState("requesting");
    setBleError(undefined);

    try {
      const device = await navigator.bluetooth!.requestDevice({
        filters: [{ services: [ble.serviceUuid] }],
        optionalServices: [ble.serviceUuid],
      });

      setScanState("connecting");
      deviceRef.current = device;

      device.addEventListener("gattserverdisconnected", () => {
        disconnectBle();
        setScanState("idle");
      });

      const server = await device.gatt.connect();
      const service = await server.getPrimaryService(ble.serviceUuid);
      const char = await service.getCharacteristic(ble.characteristicUuid);
      charRef.current = char;

      char.addEventListener("characteristicvaluechanged", (evt) => {
        const dv = evt.target.value as DataView;
        const v = ble.parseValue(dv);
        const v2 = ble.parseSecondValue?.(dv) ?? null;
        if (v !== null) setLiveValue(v);
        if (v2 !== null) setLiveValue2(v2);
      });

      await char.startNotifications();
      setScanState("connected");
      setModalOpen(false);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      if (msg.includes("User cancelled")) {
        setScanState("idle");
        setModalOpen(false);
      } else {
        setScanState("error");
        setBleError(msg);
      }
    }
  }, [ble, disconnectBle]);

  // ── Save handler ────────────────────────────────────────────────────────────

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

  const isConnected = scanState === "connected" && deviceRef.current?.gatt?.connected;

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
                onClick={() => setModalOpen(true)}
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
                  <span className="text-3xl font-bold text-white">{Math.round(liveValue)}</span>
                  {liveValue2 !== null && (
                    <span className="text-lg font-semibold text-white/80 mb-0.5">
                      /{Math.round(liveValue2)}
                    </span>
                  )}
                  <span className="text-sm text-white/70 mb-0.5 ml-0.5">{unit}</span>
                </div>
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
                    <span className="text-lg font-semibold text-white/80 mb-0.5">
                      /{latest.value2}
                    </span>
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
              onClick={() => setModalOpen(true)}
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

        {/* History toggle */}
        {history.length > 0 && (
          <div className="px-4 py-2">
            <button
              type="button"
              onClick={() => setExpanded((v) => !v)}
              className="w-full flex items-center justify-between text-xs text-gray-500 hover:text-gray-700"
            >
              <span>{history.length} reading{history.length !== 1 ? "s" : ""}</span>
              {expanded ? <ChevronUp className="h-3.5 w-3.5" /> : <ChevronDown className="h-3.5 w-3.5" />}
            </button>
          </div>
        )}

        {/* History list */}
        {expanded && history.length > 0 && (
          <div className="px-4 pb-3 space-y-1 max-h-44 overflow-y-auto">
            {history.slice(0, 10).map((r, i) => (
              <div key={i} className="flex items-center justify-between py-1.5 border-b border-gray-50 last:border-0">
                <span className="text-sm font-medium text-gray-800">
                  {r.value}
                  {r.value2 !== undefined ? `/${r.value2}` : ""}
                  <span className="text-xs text-gray-400 ml-1">{unit}</span>
                </span>
                <div className="flex items-center gap-2 text-right">
                  <span className={`text-[10px] px-1.5 py-0.5 rounded-full ${r.source === "device" ? "bg-blue-50 text-blue-600" : "bg-gray-100 text-gray-500"}`}>
                    {r.source === "device" ? "BLE" : "manual"}
                  </span>
                  <span className="text-xs text-gray-400">
                    {new Date(r.timestamp).toLocaleDateString()}
                  </span>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Bluetooth scanner modal */}
      <DeviceModal
        isOpen={modalOpen}
        scanState={scanState}
        vitalName={label}
        errorMessage={bleError}
        onClose={() => {
          if (scanState !== "connecting") {
            setScanState("idle");
            setModalOpen(false);
          }
        }}
        onRequestDevice={requestDevice}
      />
    </>
  );
};
