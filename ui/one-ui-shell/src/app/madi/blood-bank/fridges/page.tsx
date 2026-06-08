"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Thermometer, Loader2, RefreshCw, AlertTriangle } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useBloodFridges, useFridgeReadings, useSyncFridgeIot } from "@/hooks/queries/useMadi";
import { getStoredBloodBankId } from "@/lib/madi-session";

function alarmClass(status?: string) {
  if (status === "ALARM" || status === "CRITICAL") return "border-red-300 bg-red-50 text-red-900";
  if (status === "WARN") return "border-amber-300 bg-amber-50 text-amber-900";
  return "border-green-200 bg-green-50 text-green-900";
}

export default function BloodFridgesPage() {
  const facility = useFacilityStore((s) => s.facility);
  const [bloodBankId, setBloodBankId] = useState("");
  const [selectedFridge, setSelectedFridge] = useState<string | undefined>();
  const { data: fridges = [], isPending } = useBloodFridges(bloodBankId || undefined);
  const { data: readings = [], isPending: readingsLoading } = useFridgeReadings(selectedFridge);
  const sync = useSyncFridgeIot(selectedFridge ?? "");

  useEffect(() => {
    if (facility?.id) setBloodBankId(getStoredBloodBankId(facility.id));
  }, [facility?.id]);

  return (
    <AppLayout>
      <PageShell
        title="Blood fridge monitoring"
        subtitle="IoT temperature telemetry for cold-chain compliance"
        icon={<Thermometer className="h-6 w-6" />}
      >
        {!bloodBankId && (
          <p className="mb-4 text-sm text-amber-800 bg-amber-50 border border-amber-200 rounded-xl p-3">
            Register your blood bank on the{" "}
            <Link href="/madi/blood-bank" className="font-medium underline">
              blood bank hub
            </Link>{" "}
            first to load fridges.
          </p>
        )}

        {isPending && (
          <div className="flex items-center gap-2 text-gray-500 mb-4">
            <Loader2 className="h-4 w-4 animate-spin" /> Loading fridges…
          </div>
        )}

        <div className="grid gap-4 md:grid-cols-2 mb-6">
          {fridges.map((fridge) => {
            const id = fridge.fridgeId ?? "";
            const selected = selectedFridge === id;
            return (
              <button
                key={id}
                type="button"
                onClick={() => setSelectedFridge(id)}
                className={`text-left rounded-2xl border p-4 transition ${
                  selected ? "border-rose-400 ring-2 ring-rose-100" : "border-gray-200 bg-white hover:border-gray-300"
                }`}
              >
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <p className="font-semibold text-gray-900">{fridge.fridgeCode ?? id.slice(0, 8)}</p>
                    <p className="text-xs text-gray-500 mt-1">
                      IoT: {fridge.iotDeviceRef ?? "not linked"}
                    </p>
                  </div>
                  <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${alarmClass(fridge.alarmStatus)}`}>
                    {fridge.alarmStatus ?? "OK"}
                  </span>
                </div>
                <p className="mt-3 text-2xl font-bold text-gray-900">
                  {fridge.temperatureC != null ? `${fridge.temperatureC}°C` : "—"}
                </p>
                <p className="text-xs text-gray-500">
                  Target {fridge.targetTempMinC ?? 2}–{fridge.targetTempMaxC ?? 6}°C
                  {fridge.lastReadingAt ? ` · ${new Date(fridge.lastReadingAt).toLocaleString()}` : ""}
                </p>
              </button>
            );
          })}
        </div>

        {fridges.length === 0 && bloodBankId && !isPending && (
          <p className="text-sm text-gray-600">No fridges registered for this blood bank yet.</p>
        )}

        {selectedFridge && (
          <section className="rounded-2xl border border-gray-200 bg-white p-5 space-y-4">
            <div className="flex items-center justify-between gap-2">
              <h2 className="text-sm font-semibold text-gray-900">Recent readings</h2>
              <button
                type="button"
                onClick={() => sync.mutate()}
                disabled={sync.isPending}
                className="inline-flex items-center gap-1 rounded-lg border border-gray-300 px-3 py-1.5 text-xs font-medium hover:bg-gray-50 disabled:opacity-50"
              >
                {sync.isPending ? <Loader2 className="h-3 w-3 animate-spin" /> : <RefreshCw className="h-3 w-3" />}
                Sync from IoT
              </button>
            </div>

            {readingsLoading && (
              <div className="flex items-center gap-2 text-sm text-gray-500">
                <Loader2 className="h-4 w-4 animate-spin" /> Loading readings…
              </div>
            )}

            {readings.length === 0 && !readingsLoading && (
              <p className="text-sm text-gray-600 flex items-center gap-2">
                <AlertTriangle className="h-4 w-4" /> No readings yet — sync from IoT or wait for ingestion.
              </p>
            )}

            <ul className="divide-y divide-gray-100 text-sm">
              {readings.slice(0, 12).map((r) => (
                <li key={r.readingId ?? `${r.recordedAt}-${r.temperatureC}`} className="py-2 flex justify-between">
                  <span>
                    {r.temperatureC != null ? `${r.temperatureC}°C` : "—"}
                    <span className="ml-2 text-xs text-gray-500">{r.alarmStatus}</span>
                  </span>
                  <span className="text-xs text-gray-500">
                    {r.recordedAt ? new Date(r.recordedAt).toLocaleString() : ""}
                  </span>
                </li>
              ))}
            </ul>
          </section>
        )}
      </PageShell>
    </AppLayout>
  );
}
