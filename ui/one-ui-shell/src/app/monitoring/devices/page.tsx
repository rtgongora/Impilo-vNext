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
  const [syncTargetId, setSyncTargetId] = useState<string | null>(null);
  const [syncValue, setSyncValue] = useState("");
  const [syncUnit, setSyncUnit] = useState("");
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

  function defaultUnitForDeviceType(deviceType: string) {
    switch (deviceType) {
      case "BLOOD_PRESSURE":
        return "mmHg";
      case "PULSE_OXIMETER":
        return "%";
      case "GLUCOMETER":
        return "mmol/L";
      case "SCALE":
        return "kg";
      case "WEARABLE":
        return "bpm";
      default:
        return "";
    }
  }

  function openSyncPanel(deviceId: string, deviceType: string) {
    setSyncTargetId(deviceId);
    setSyncValue("");
    setSyncUnit(defaultUnitForDeviceType(deviceType));
  }

  function submitSync(deviceId: string, deviceType: string) {
    const value = Number(syncValue);
    if (Number.isNaN(value)) return;
    syncDevice.mutate(
      {
        deviceId,
        readings: [
          {
            value,
            unit: syncUnit.trim() || defaultUnitForDeviceType(deviceType),
          },
        ],
      },
      {
        onSuccess: () => {
          setSyncTargetId(null);
          setSyncValue("");
        },
      },
    );
  }

  return (
    <AppLayout>
      <PageShell
        title="My Devices"
        subtitle="Paired health devices and sensors for remote monitoring"
        icon={<Smartphone className="h-6 w-6" />}
      >
        {!patientId && (
          <p className="text-sm text-warning-foreground bg-warning-soft border border-warning/35 rounded-lg px-4 py-3 mb-6">
            Sign in to load devices for your Health ID.
          </p>
        )}

        {patientId && isLoading && (
          <div className="flex items-center gap-2 text-muted-foreground py-8">
            <Loader2 className="h-5 w-5 animate-spin" /> Loading devices…
          </div>
        )}

        {patientId && isError && (
          <p className="text-sm text-danger bg-danger-soft border border-danger/28 rounded-lg px-4 py-3 mb-6">
            Could not load devices. {error instanceof Error ? error.message : "Try again later."}
          </p>
        )}

        {patientId && !isLoading && !isError && (
          <div className="space-y-6">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-3">
                <span className="text-sm text-muted-foreground">Connected devices: {devices.length}</span>
                <button
                  type="button"
                  onClick={() => void refetch()}
                  className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
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
                { label: "Bluetooth Devices", description: "Blood pressure monitors, glucometers, pulse oximeters", Icon: Bluetooth, color: "bg-primary-soft text-primary" },
                { label: "Wi-Fi Devices", description: "Smart scales, sleep trackers, home monitors", Icon: Wifi, color: "bg-green-50 text-green-600" },
                { label: "Wearables", description: "Fitness bands, smartwatches, CGM sensors", Icon: BatteryMedium, color: "bg-warning-soft text-purple-600" },
              ].map(({ label, description, Icon, color }) => (
                <div key={label} className="rounded-lg border border-border bg-card p-5">
                  <div className="flex items-center gap-3 mb-2">
                    <div className={`rounded-lg p-2 ${color.split(" ")[0]}`}>
                      <Icon className={`h-5 w-5 ${color.split(" ")[1]}`} />
                    </div>
                    <h3 className="font-semibold text-foreground">{label}</h3>
                  </div>
                  <p className="text-sm text-muted-foreground">{description}</p>
                </div>
              ))}
            </div>

            {devices.length === 0 ? (
              <div className="rounded-lg border border-dashed border-border bg-background p-12 text-center">
                <Smartphone className="mx-auto h-12 w-12 text-muted-foreground" />
                <h3 className="mt-4 text-sm font-semibold text-foreground">No devices paired</h3>
                <p className="mt-2 text-sm text-muted-foreground">
                  Pair a health device to register it for your account. Sync readings to populate your vitals timeline.
                </p>
              </div>
            ) : (
              <ul className="divide-y divide-gray-200 rounded-lg border border-border bg-card">
                {devices.map((d) => (
                  <li key={d.id} className="px-4 py-4 space-y-3">
                    <div className="flex flex-wrap items-center justify-between gap-3">
                      <div>
                        <p className="font-medium text-foreground">{d.deviceName}</p>
                        <p className="text-sm text-muted-foreground">
                          {d.deviceType} · {d.connectionType}
                          {d.manufacturer ? ` · ${d.manufacturer}` : ""}
                          {d.model ? ` ${d.model}` : ""}
                        </p>
                        {d.lastSyncAt && (
                          <p className="text-xs text-muted-foreground mt-1">Last sync: {d.lastSyncAt}</p>
                        )}
                      </div>
                      <button
                        type="button"
                        onClick={() => openSyncPanel(d.id, d.deviceType)}
                        disabled={syncDevice.isPending}
                        className="text-sm font-medium text-orange-600 hover:text-orange-800 disabled:opacity-50"
                      >
                        {syncDevice.isPending && syncTargetId === d.id ? "Syncing…" : "Sync reading"}
                      </button>
                    </div>
                    {syncTargetId === d.id && (
                      <div className="rounded-lg border border-orange-100 bg-orange-50/60 p-3 flex flex-wrap items-end gap-3">
                        <label className="text-xs text-muted-foreground flex flex-col gap-1">
                          Latest value
                          <input
                            type="number"
                            step="any"
                            className="rounded-lg border border-border px-3 py-1.5 text-sm w-32"
                            value={syncValue}
                            onChange={(e) => setSyncValue(e.target.value)}
                            placeholder="e.g. 120"
                          />
                        </label>
                        <label className="text-xs text-muted-foreground flex flex-col gap-1">
                          Unit
                          <input
                            className="rounded-lg border border-border px-3 py-1.5 text-sm w-24"
                            value={syncUnit}
                            onChange={(e) => setSyncUnit(e.target.value)}
                          />
                        </label>
                        <button
                          type="button"
                          disabled={syncDevice.isPending || !syncValue.trim()}
                          onClick={() => submitSync(d.id, d.deviceType)}
                          className="rounded-lg bg-orange-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-orange-700 disabled:opacity-50"
                        >
                          Submit sync
                        </button>
                        <button
                          type="button"
                          onClick={() => setSyncTargetId(null)}
                          className="text-sm text-muted-foreground hover:text-foreground"
                        >
                          Cancel
                        </button>
                      </div>
                    )}
                  </li>
                ))}
              </ul>
            )}

            {showPair && (
              <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
                <div className="w-full max-w-md rounded-xl bg-card p-6 shadow-xl">
                  <h3 className="text-lg font-semibold text-foreground">Pair a device</h3>
                  <p className="text-sm text-muted-foreground mt-1">Registers the device against your Health ID (CPID scope).</p>
                  <div className="mt-4 space-y-3">
                    <label className="block text-sm font-medium text-foreground">
                      Display name
                      <input
                        className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm"
                        value={pairForm.deviceName}
                        onChange={(e) => setPairForm((f) => ({ ...f, deviceName: e.target.value }))}
                        placeholder="e.g. Home blood pressure cuff"
                      />
                    </label>
                    <label className="block text-sm font-medium text-foreground">
                      Device type
                      <select
                        className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm"
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
                      <label className="block text-sm font-medium text-foreground">
                        Manufacturer
                        <input
                          className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm"
                          value={pairForm.manufacturer}
                          onChange={(e) => setPairForm((f) => ({ ...f, manufacturer: e.target.value }))}
                        />
                      </label>
                      <label className="block text-sm font-medium text-foreground">
                        Model
                        <input
                          className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm"
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
                      className="rounded-lg border border-border px-4 py-2 text-sm font-medium text-foreground hover:bg-background"
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
