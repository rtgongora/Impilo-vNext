"use client";

/**
 * Readings & Trends — Health OS §2
 * Live vitals from wellness-service (manual entry, Health Connect, etc.).
 * Route: /monitoring/readings | Zone: monitoring | Guard: auth
 */

import { TrendingUp, Loader2, AlertTriangle } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useMonitoringReadings } from "@/hooks/queries/useCitizenMonitoring";

const METRIC_LABELS: Record<string, { label: string; unit: string; color: string }> = {
  BLOOD_PRESSURE: { label: "Blood Pressure", unit: "mmHg", color: "bg-red-50 border-red-200" },
  BLOOD_GLUCOSE: { label: "Blood Glucose", unit: "mmol/L", color: "bg-amber-50 border-amber-200" },
  HEART_RATE: { label: "Heart Rate", unit: "bpm", color: "bg-pink-50 border-pink-200" },
  OXYGEN_SATURATION: { label: "Oxygen Saturation", unit: "%", color: "bg-impilo-50 border-impilo-200" },
  WEIGHT: { label: "Weight", unit: "kg", color: "bg-green-50 border-green-200" },
  TEMPERATURE: { label: "Temperature", unit: "°C", color: "bg-orange-50 border-orange-200" },
};

function fmtSource(source: string) {
  if (source === "HEALTH_CONNECT") return "Health Connect";
  if (source === "MANUAL") return "Manual entry";
  return source.replace(/_/g, " ").toLowerCase().replace(/^\w/, (c) => c.toUpperCase());
}

function fmtWhen(iso: string) {
  if (!iso) return "—";
  return new Date(iso).toLocaleString("en-ZW", { dateStyle: "medium", timeStyle: "short" });
}

export default function ReadingsPage() {
  const patientId = useAuthStore((s) => s.user?.id);
  const { data: readings = [], isLoading, isError, error } = useMonitoringReadings(patientId);

  const grouped = readings.reduce<Record<string, typeof readings>>((acc, reading) => {
    const key = reading.vitalType.toUpperCase();
    acc[key] = acc[key] ?? [];
    acc[key].push(reading);
    return acc;
  }, {});

  const metricKeys = Object.keys(METRIC_LABELS);
  const cardsToShow = metricKeys.filter((key) => grouped[key]?.length);

  return (
    <AppLayout>
      <PageShell
        title="Readings & Trends"
        subtitle="Vitals logged in Impilo wellness — sources include manual entry and connected apps"
        icon={<TrendingUp className="h-6 w-6" />}
      >
        <div className="space-y-6">
          {!patientId && (
            <p className="text-sm text-gray-500">Sign in to view your vitals history.</p>
          )}

          {patientId && isLoading && (
            <div className="flex items-center gap-2 text-sm text-gray-500">
              <Loader2 className="h-4 w-4 animate-spin" />
              Loading readings…
            </div>
          )}

          {patientId && isError && (
            <div className="flex items-center gap-2 rounded-xl border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
              <AlertTriangle className="h-4 w-4 flex-shrink-0" />
              {error instanceof Error ? error.message : "Could not load readings."}
            </div>
          )}

          {patientId && !isLoading && !isError && readings.length === 0 && (
            <p className="text-sm text-gray-500 italic">
              No vitals logged yet. Readings appear here when you log them in wellness or sync from Health Connect.
            </p>
          )}

          {patientId && !isLoading && !isError && cardsToShow.length > 0 && (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {cardsToShow.map((key) => {
                const meta = METRIC_LABELS[key];
                const latest = grouped[key]![0];
                const history = grouped[key]!.slice(0, 5);
                return (
                  <div key={key} className={`rounded-lg border p-5 ${meta.color}`}>
                    <h3 className="font-semibold text-gray-900 mb-1">{meta.label}</h3>
                    <p className="text-2xl font-bold text-gray-900">
                      {latest.value}
                      <span className="text-sm font-normal text-gray-500 ml-1">{latest.unit || meta.unit}</span>
                    </p>
                    <p className="text-xs text-gray-500 mt-1">
                      Latest · {fmtWhen(latest.measuredAt)} · {fmtSource(latest.source)}
                    </p>
                    {history.length > 1 && (
                      <ul className="mt-3 space-y-1 border-t border-gray-200/60 pt-2">
                        {history.slice(1).map((r) => (
                          <li key={r.id} className="text-xs text-gray-600 flex justify-between gap-2">
                            <span>{r.value} {r.unit || meta.unit}</span>
                            <span className="text-gray-400">{fmtWhen(r.measuredAt)}</span>
                          </li>
                        ))}
                      </ul>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}
