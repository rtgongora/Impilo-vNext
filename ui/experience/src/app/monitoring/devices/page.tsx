"use client";

/**
 * My Devices — Health OS §2
 * Paired health devices and sensors; persisted via BFF → wellness-service (same contract as mobile).
 * Route: /monitoring/devices | Zone: monitoring | Guard: auth
 */

import { useState } from "react";
import { Smartphone, Plus, Bluetooth, Wifi, BatteryMedium, Loader2, RefreshCw } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useMonitoringDevices, usePairMonitoringDevice, useSyncMonitoringDevice } from "@/hooks/queries/useCitizenMonitoring";

export default function DevicesPage() {
  const patientId = useAuthStore((s) => s.user?.id);
  const { data: devices = [], isLoading, isError, error, refetch } = useMonitoringDevices(patientId);
  const pairDevice = usePairMonitoringDevice(patientId);
  const syncDevice = useSyncMonitoringDevice(patientId);

  const [showPair, setShowPair] = useState(false);
  const [pairForm, setPairForm] = useState({
    deviceName: "",
    deviceType: "BLOOD_PRESSURE",
    manufacturer: "",
    model: "",
  });

  const submitPair = () => {
    if (!pairForm.deviceName.trim()) return;
    pairDevice.mutate(
      {
        deviceName: pairForm.deviceName.trim(),
        deviceType: pairForm.deviceType,
        manufacturer: pairForm.manufacturer.trim() || undefined,
        model: pairForm.model.trim() || undefined,
      },
      {
        onSuccess: () => {
          setShowPair(false);
          setPairForm({ deviceName: "", deviceType: "BLOOD_PRESSURE", manufacturer: "", model: "" });
        },
      },
    );
  };

  return (
    <AppLayout>
      <PageShell
        title="My Devices"
        subtitle="Paired health devices and sensors for remote monitoring"
        icon={<Smartphone className="h-6 w-6" />}
      >
        {!patientId && (
          <p className="text-sm text-amber-700 bg-amber-50 border border-amber-200 rounded-lg px-4 py-3 mb-6">
            Sign in to load devices for your Health ID.
          </p>
        )}

        {patientId && isLoading && (
          <div className="flex items-center gap-2 text-gray-600 py-8">
            <Loader2 className="h-5 w-5 animate-spin" /> Loading devices…
          </div>
        )}

        {patientId && isError && (
          <p className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-lg px-4 py-3 mb-6">
            Could not load devices. {error instanceof Error ? error.message : "Try again later."}
          </p>
        )}

        {patientId && !isLoading && !isError && (
          <div className="space-y-6">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-3">
                <span className="text-sm text-gray-500">Connected devices: {devices.length}</span>
                <button
                  type="button"
                  onClick={() => void refetch()}
                  className="inline-flex items-center gap-1 text-sm text-gray-600 hover:text-gray-900"
                  title="Refresh list"
                >
                  <RefreshCw className="h-4 w-4" /> Refresh
                </button>
              </div>
              <button
                type="button"
                onClick={() => setShowPair(true)}
                className="inline-flex items-center gap-2 rounded-lg bg-orange-600 px-4 py-2 text-sm font-medium text-white hover:bg-orange-700 transition-colors"
              >
                <Plus className="h-4 w-4" />
                Pair Device
              </button>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              {[
                { label: "Bluetooth Devices", description: "Blood pressure monitors, glucometers, pulse oximeters", Icon: Bluetooth, color: "bg-blue-50 text-blue-600" },
                { label: "Wi-Fi Devices", description: "Smart scales, sleep trackers, home monitors", Icon: Wifi, color: "bg-green-50 text-green-600" },
                { label: "Wearables", description: "Fitness bands, smartwatches, CGM sensors", Icon: BatteryMedium, color: "bg-purple-50 text-purple-600" },
              ].map(({ label, description, Icon, color }) => (
                <div key={label} className="rounded-lg border border-gray-200 bg-white p-5">
                  <div className="flex items-center gap-3 mb-2">
                    <div className={`rounded-lg p-2 ${color.split(" ")[0]}`}>
                      <Icon className={`h-5 w-5 ${color.split(" ")[1]}`} />
                    </div>
                    <h3 className="font-semibold text-gray-900">{label}</h3>
                  </div>
                  <p className="text-sm text-gray-600">{description}</p>
                </div>
              ))}
            </div>

            {devices.length === 0 ? (
              <div className="rounded-lg border border-dashed border-gray-300 bg-gray-50 p-12 text-center">
                <Smartphone className="mx-auto h-12 w-12 text-gray-400" />
                <h3 className="mt-4 text-sm font-semibold text-gray-900">No devices paired</h3>
                <p className="mt-2 text-sm text-gray-600">
                  Pair a health device to register it for your account. Readings and timeline views follow in later
                  releases.
                </p>
              </div>
            ) : (
              <ul className="divide-y divide-gray-200 rounded-lg border border-gray-200 bg-white">
                {devices.map((d) => (
                  <li key={d.id} className="flex flex-wrap items-center justify-between gap-3 px-4 py-4">
                    <div>
                      <p className="font-medium text-gray-900">{d.deviceName}</p>
                      <p className="text-sm text-gray-500">
                        {d.deviceType} · {d.connectionType}
                        {d.manufacturer ? ` · ${d.manufacturer}` : ""}
                        {d.model ? ` ${d.model}` : ""}
                      </p>
                      {d.lastSyncAt && (
                        <p className="text-xs text-gray-400 mt-1">Last sync: {d.lastSyncAt}</p>
                      )}
                    </div>
                    <button
                      type="button"
                      onClick={() => syncDevice.mutate(d.id)}
                      disabled={syncDevice.isPending}
                      className="text-sm font-medium text-orange-600 hover:text-orange-800 disabled:opacity-50"
                    >
                      {syncDevice.isPending ? "Syncing…" : "Record sync"}
                    </button>
                  </li>
                ))}
              </ul>
            )}

            {showPair && (
              <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
                <div className="w-full max-w-md rounded-xl bg-white p-6 shadow-xl">
                  <h3 className="text-lg font-semibold text-gray-900">Pair a device</h3>
                  <p className="text-sm text-gray-500 mt-1">Registers the device against your Health ID (CPID scope).</p>
                  <div className="mt-4 space-y-3">
                    <label className="block text-sm font-medium text-gray-700">
                      Display name
                      <input
                        className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
                        value={pairForm.deviceName}
                        onChange={(e) => setPairForm((f) => ({ ...f, deviceName: e.target.value }))}
                        placeholder="e.g. Home blood pressure cuff"
                      />
                    </label>
                    <label className="block text-sm font-medium text-gray-700">
                      Device type
                      <select
                        className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
                        value={pairForm.deviceType}
                        onChange={(e) => setPairForm((f) => ({ ...f, deviceType: e.target.value }))}
                      >
                        <option value="BLOOD_PRESSURE">Blood pressure</option>
                        <option value="PULSE_OXIMETER">Pulse oximeter</option>
                        <option value="GLUCOMETER">Glucometer</option>
                        <option value="SCALE">Scale</option>
                        <option value="WEARABLE">Wearable</option>
                        <option value="OTHER">Other</option>
                      </select>
                    </label>
                    <div className="grid grid-cols-2 gap-2">
                      <label className="block text-sm font-medium text-gray-700">
                        Manufacturer
                        <input
                          className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
                          value={pairForm.manufacturer}
                          onChange={(e) => setPairForm((f) => ({ ...f, manufacturer: e.target.value }))}
                        />
                      </label>
                      <label className="block text-sm font-medium text-gray-700">
                        Model
                        <input
                          className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
                          value={pairForm.model}
                          onChange={(e) => setPairForm((f) => ({ ...f, model: e.target.value }))}
                        />
                      </label>
                    </div>
                  </div>
                  {pairDevice.isError && (
                    <p className="mt-3 text-sm text-red-600">Pairing failed. Check network and try again.</p>
                  )}
                  <div className="mt-6 flex justify-end gap-2">
                    <button
                      type="button"
                      onClick={() => setShowPair(false)}
                      className="rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
                    >
                      Cancel
                    </button>
                    <button
                      type="button"
                      onClick={submitPair}
                      disabled={pairDevice.isPending || !pairForm.deviceName.trim()}
                      className="rounded-lg bg-orange-600 px-4 py-2 text-sm font-medium text-white hover:bg-orange-700 disabled:opacity-50"
                    >
                      {pairDevice.isPending ? "Saving…" : "Save"}
                    </button>
                  </div>
                </div>
              </div>
            )}
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
