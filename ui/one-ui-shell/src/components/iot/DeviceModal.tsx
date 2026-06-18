"use client";

/**
 * Bluetooth device scanner modal — ported from the Electron DeviceModal reference.
 * Works with the Web Bluetooth API in browsers.
 *
 * States:
 *   requesting  — navigator.bluetooth.requestDevice() in flight
 *   connecting  — GATT connect in progress
 *   connected   — device is connected
 *   error       — permission denied or scan failed
 */

import React from "react";
import { Bluetooth, X, Loader2, AlertCircle } from "lucide-react";

export type BleScanState =
  | "requesting"
  | "connecting"
  | "connected"
  | "error"
  | "idle";

export interface BleDevice {
  id: string;
  name: string;
}

const SCAN_LABELS: Record<BleScanState, string> = {
  requesting: "Opening Bluetooth picker…",
  connecting: "Connecting to device…",
  connected: "Connected",
  error: "Bluetooth error",
  idle: "Ready",
};

interface DeviceModalProps {
  isOpen: boolean;
  scanState: BleScanState;
  vitalName: string;
  errorMessage?: string;
  onClose: () => void;
  onRequestDevice: () => void;
}

export const DeviceModal: React.FC<DeviceModalProps> = ({
  isOpen,
  scanState,
  vitalName,
  errorMessage,
  onClose,
  onRequestDevice,
}) => {
  if (!isOpen) return null;

  const isBusy = scanState === "requesting" || scanState === "connecting";

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 px-4">
      <div className="w-full max-w-sm rounded-2xl bg-white shadow-2xl overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between px-5 pt-5 pb-4 border-b border-gray-100">
          <div className="flex items-center gap-3">
            <div className="h-9 w-9 rounded-xl bg-impilo-50 flex items-center justify-center">
              <Bluetooth className="h-5 w-5 text-impilo-600" />
            </div>
            <div>
              <h2 className="text-base font-semibold text-gray-900">
                {vitalName} — Bluetooth
              </h2>
              <p className="text-xs text-gray-400 mt-0.5">
                {SCAN_LABELS[scanState]}
              </p>
            </div>
          </div>
          {!isBusy && (
            <button
              type="button"
              onClick={onClose}
              className="p-1.5 rounded-lg text-gray-400 hover:text-gray-600 hover:bg-gray-100 transition-colors"
            >
              <X className="h-4 w-4" />
            </button>
          )}
        </div>

        {/* Body */}
        <div className="px-5 py-6">
          {isBusy && (
            <div className="flex flex-col items-center gap-4 py-4">
              <Loader2 className="h-10 w-10 text-impilo-500 animate-spin" />
              <p className="text-sm text-center text-gray-600 max-w-xs">
                {scanState === "requesting"
                  ? "Your browser will show a Bluetooth picker. Select your device from the list and grant permission."
                  : "Establishing connection and subscribing to notifications…"}
              </p>
            </div>
          )}

          {scanState === "error" && (
            <div className="flex flex-col items-center gap-3 py-2">
              <div className="h-12 w-12 rounded-full bg-red-50 flex items-center justify-center">
                <AlertCircle className="h-6 w-6 text-red-500" />
              </div>
              <p className="text-sm font-medium text-gray-900 text-center">
                Could not connect
              </p>
              <p className="text-xs text-gray-500 text-center leading-relaxed max-w-xs">
                {errorMessage ||
                  "Bluetooth permission was denied or the device was not found. Make sure Bluetooth is enabled and the device is in pairing mode."}
              </p>
              <button
                type="button"
                onClick={onRequestDevice}
                className="mt-2 w-full rounded-xl bg-impilo-600 text-white py-2.5 text-sm font-medium hover:bg-impilo-700 transition-colors"
              >
                Try again
              </button>
            </div>
          )}

          {scanState === "idle" && (
            <div className="flex flex-col items-center gap-4 py-2">
              <p className="text-sm text-center text-gray-600 max-w-xs">
                Make sure your Bluetooth device is powered on and in pairing mode, then press{" "}
                <strong>Scan for device</strong>.
              </p>
              <button
                type="button"
                onClick={onRequestDevice}
                className="w-full rounded-xl bg-impilo-600 text-white py-2.5 text-sm font-medium hover:bg-impilo-700 transition-colors flex items-center justify-center gap-2"
              >
                <Bluetooth className="h-4 w-4" />
                Scan for device
              </button>
            </div>
          )}
        </div>

        {/* Footer */}
        {!isBusy && (
          <div className="px-5 pb-5">
            <button
              type="button"
              onClick={onClose}
              className="w-full rounded-xl border border-gray-200 py-2.5 text-sm text-gray-600 hover:bg-gray-50 transition-colors"
            >
              Close
            </button>
          </div>
        )}
      </div>
    </div>
  );
};
