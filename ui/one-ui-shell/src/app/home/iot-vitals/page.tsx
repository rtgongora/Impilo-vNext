"use client";

/**
 * IoT Vitals — BLE medical-device integration page accessible from the Life tab.
 *
 * Flow:
 *   1. Clinician searches and selects a patient (VITO client registry via BFF).
 *   2. Page loads that patient's vital history from PCT via BFF.
 *   3. Each VitalCard supports:
 *        a) Web Bluetooth "Connect device" → live stream → one-tap save
 *        b) Manual numeric entry fallback
 *   4. Saves POSTed to  POST /internal/v1/vitals  (BFF → PCT).
 *   5. History refreshed after each save.
 *
 * Bluetooth wiring (vendor UUIDs + byte parsing) is ported from the proven Impilo
 * Practitioner desktop app. See components/iot/deviceConfig.ts and dataParser.ts.
 */

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import {
  ArrowLeft,
  Bluetooth,
  Info,
  Loader2,
  Search,
  User,
  X,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";
import { VitalCard, type VitalCardConfig, type VitalReading } from "@/components/iot/VitalCard";
import {
  BLE_BLOOD_PRESSURE,
  BLE_GLUCOMETER,
  BLE_OXIMETER,
  BLE_THERMOMETER,
} from "@/components/iot/deviceConfig";

// ── Vital configs ─────────────────────────────────────────────────────────────
//
// Bluetooth-enabled cards use real device kinds (oximeter, thermometer,
// blood-pressure monitor, glucometer). The pulse oximeter streams both SpO₂ and
// pulse, so that card saves oxygen_saturation + heart_rate together. Glucose has
// no dedicated PCT field, so it is stored in the visit notes.
const VITAL_CONFIGS: VitalCardConfig[] = [
  {
    key: "spo2",
    label: "Pulse Oximeter",
    unit: "%",
    icon: "🫁",
    color: "bg-gradient-to-br from-blue-500 to-cyan-600",
    postField: "oxygen_saturation",
    postField2: "heart_rate",
    secondaryLabel: "Pulse",
    placeholder: "SpO₂ %",
    placeholder2: "Pulse (bpm)",
    min: 70,
    max: 100,
    ble: BLE_OXIMETER,
  },
  {
    key: "temp",
    label: "Body Temperature",
    unit: "°C",
    icon: "🌡️",
    color: "bg-gradient-to-br from-amber-500 to-orange-500",
    postField: "temperature",
    placeholder: "°C",
    decimals: 1,
    min: 34,
    max: 42,
    ble: BLE_THERMOMETER,
  },
  {
    key: "bp",
    label: "Blood Pressure",
    unit: "mmHg",
    icon: "🩺",
    color: "bg-gradient-to-br from-red-500 to-rose-600",
    postField: "systolic",
    postField2: "diastolic",
    secondaryLabel: "Diastolic",
    placeholder: "Systolic",
    placeholder2: "Diastolic",
    min: 60,
    max: 250,
    ble: BLE_BLOOD_PRESSURE,
  },
  {
    key: "glucose",
    label: "Blood Glucose",
    unit: "mmol/L",
    icon: "🩸",
    color: "bg-gradient-to-br from-fuchsia-500 to-pink-600",
    postField: "notes",
    placeholder: "mmol/L",
    decimals: 1,
    min: 1,
    max: 40,
    ble: BLE_GLUCOMETER,
  },
  {
    key: "rr",
    label: "Respiratory Rate",
    unit: "rpm",
    icon: "💨",
    color: "bg-gradient-to-br from-teal-500 to-emerald-600",
    postField: "respiratory_rate",
    placeholder: "Breaths/min",
    min: 8,
    max: 40,
  },
  {
    key: "weight",
    label: "Weight",
    unit: "kg",
    icon: "⚖️",
    color: "bg-gradient-to-br from-violet-500 to-purple-600",
    postField: "weight",
    placeholder: "kg",
    decimals: 1,
    min: 1,
    max: 300,
  },
  {
    key: "height",
    label: "Height",
    unit: "cm",
    icon: "📏",
    color: "bg-gradient-to-br from-indigo-500 to-blue-600",
    postField: "height",
    placeholder: "cm",
    min: 30,
    max: 250,
  },
  {
    key: "pain",
    label: "Pain Score",
    unit: "/10",
    icon: "😣",
    color: "bg-gradient-to-br from-orange-500 to-red-500",
    postField: "pain_score",
    placeholder: "0–10",
    min: 0,
    max: 10,
  },
];

// ── Patient type ──────────────────────────────────────────────────────────────

interface PatientResult {
  id: string;
  cpid?: string;
  firstName?: string;
  lastName?: string;
  dateOfBirth?: string;
  gender?: string;
  attributes?: Record<string, unknown>;
}

function patientDisplayName(p: PatientResult): string {
  const first = p.firstName ?? (p.attributes?.firstName as string) ?? "";
  const last = p.lastName ?? (p.attributes?.lastName as string) ?? "";
  if (first || last) return `${first} ${last}`.trim();
  return p.cpid ?? p.id;
}

function patientSubline(p: PatientResult): string {
  const cpid = p.cpid ?? (p.attributes?.cpid as string) ?? p.id;
  const dob = p.dateOfBirth ?? (p.attributes?.dateOfBirth as string);
  const gender = p.gender ?? (p.attributes?.gender as string);
  return [cpid, dob, gender].filter(Boolean).join(" · ");
}

// ── History normaliser ────────────────────────────────────────────────────────

interface RawVital {
  id?: string;
  attributes?: Record<string, unknown>;
  [key: string]: unknown;
}

function extractHistory(raw: RawVital[], config: VitalCardConfig): VitalReading[] {
  const readings = raw.flatMap((r): VitalReading[] => {
    const attrs = r.attributes ?? r;
    const ts = String(
      attrs.recorded_at ?? attrs.created_at ?? attrs.createdAt ?? attrs.timestamp ?? "",
    );

    // Vitals with no dedicated PCT column (e.g. glucose) are stored in notes as
    // "<label>: <value> <unit>" — parse them back out for the history view.
    if (config.postField === "notes") {
      const notes = String(attrs.notes ?? "");
      const re = new RegExp(`${config.label}\\s*:\\s*([\\d.]+)`, "i");
      const match = notes.match(re);
      if (!match) return [];
      const value = Number(match[1]);
      if (!Number.isFinite(value) || value <= 0) return [];
      return [{ value, timestamp: ts || new Date().toISOString(), source: "device" }];
    }

    const primary = Number(attrs[config.postField] ?? 0);
    const secondary = config.postField2 ? Number(attrs[config.postField2] ?? 0) : undefined;
    if (!primary) return [];
    return [
      {
        value: primary,
        value2: secondary || undefined,
        timestamp: ts || new Date().toISOString(),
        source: "device",
      },
    ];
  });

  // Newest first.
  return readings.sort(
    (a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime(),
  );
}

// ── Page ──────────────────────────────────────────────────────────────────────

export default function IotVitalsPage() {
  // Patient search
  const [query, setQuery] = useState("");
  const [searching, setSearching] = useState(false);
  const [searchResults, setSearchResults] = useState<PatientResult[]>([]);
  const [selectedPatient, setSelectedPatient] = useState<PatientResult | null>(null);
  const searchRef = useRef<HTMLInputElement>(null);

  // Vitals history keyed by vitalConfig.key
  const [history, setHistory] = useState<Record<string, VitalReading[]>>({});
  const [historyLoading, setHistoryLoading] = useState(false);

  const [toast, setToast] = useState<{ msg: string; ok: boolean } | null>(null);
  const toastTimer = useRef<NodeJS.Timeout | null>(null);

  const showToast = useCallback((msg: string, ok: boolean) => {
    setToast({ msg, ok });
    if (toastTimer.current) clearTimeout(toastTimer.current);
    toastTimer.current = setTimeout(() => setToast(null), 3000);
  }, []);

  // ── Patient search ─────────────────────────────────────────────────────────

  const doSearch = useCallback(async (q: string) => {
    if (!q.trim()) { setSearchResults([]); return; }
    setSearching(true);
    try {
      const res = await apiClient.get<{ data: PatientResult[] }>(
        `/internal/v1/patients?q=${encodeURIComponent(q.trim())}&size=10`,
      );
      setSearchResults(res.data ?? []);
    } catch {
      setSearchResults([]);
    } finally {
      setSearching(false);
    }
  }, []);

  // Debounce search
  const searchTimer = useRef<NodeJS.Timeout | null>(null);
  const handleQueryChange = (v: string) => {
    setQuery(v);
    if (searchTimer.current) clearTimeout(searchTimer.current);
    searchTimer.current = setTimeout(() => doSearch(v), 400);
  };

  // ── Vitals history ─────────────────────────────────────────────────────────

  const fetchHistory = useCallback(async (patient: PatientResult) => {
    const cpid = patient.cpid ?? (patient.attributes?.cpid as string) ?? patient.id;
    setHistoryLoading(true);
    try {
      const res = await apiClient.get<{ data: RawVital[] }>(
        `/internal/v1/vitals?patient_id=${encodeURIComponent(cpid)}&size=50`,
      );
      const raw: RawVital[] = res.data ?? [];
      const map: Record<string, VitalReading[]> = {};
      for (const cfg of VITAL_CONFIGS) {
        map[cfg.key] = extractHistory(raw, cfg);
      }
      setHistory(map);
    } catch {
      setHistory({});
    } finally {
      setHistoryLoading(false);
    }
  }, []);

  useEffect(() => {
    if (selectedPatient) fetchHistory(selectedPatient);
  }, [selectedPatient, fetchHistory]);

  // ── Save vital ─────────────────────────────────────────────────────────────

  const handleSaveVital = useCallback(
    async (
      cfg: VitalCardConfig,
      primary: number,
      secondary?: number,
      _source: "device" | "manual" = "device",
    ) => {
      if (!selectedPatient) return;
      const cpid = selectedPatient.cpid ?? (selectedPatient.attributes?.cpid as string) ?? selectedPatient.id;

      const body: Record<string, unknown> = { patient_id: cpid };
      if (cfg.postField === "notes") {
        // Vitals with no dedicated PCT field (e.g. glucose) are recorded in notes.
        body.notes = `${cfg.label}: ${primary} ${cfg.unit}`;
      } else {
        body[cfg.postField] = primary;
        if (cfg.postField2 && secondary !== undefined) {
          body[cfg.postField2] = secondary;
        }
      }

      try {
        await apiClient.post("/internal/v1/vitals", body);
        showToast(`${cfg.label} saved`, true);
        await fetchHistory(selectedPatient);
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : "Save failed";
        showToast(msg, false);
        throw err;
      }
    },
    [selectedPatient, fetchHistory, showToast],
  );

  // ── Bluetooth permission notice ────────────────────────────────────────────

  const bleSupported = typeof navigator !== "undefined" && "bluetooth" in navigator;

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <AppLayout>
      <PageShell title="IoT Vitals">
        <div className="max-w-7xl mx-auto space-y-6">

          {/* Back + heading */}
          <div className="flex items-center gap-3">
            <Link
              href="/home"
              className="p-2 rounded-xl border border-gray-200 hover:bg-gray-50 transition-colors"
            >
              <ArrowLeft className="h-4 w-4 text-gray-500" />
            </Link>
            <div>
              <h1 className="text-xl font-bold text-gray-900 flex items-center gap-2">
                <Bluetooth className="h-5 w-5 text-impilo-600" />
                IoT Based Integration
              </h1>
              <p className="text-sm text-gray-500">
                Connect Bluetooth devices to record patient vitals
              </p>
            </div>
          </div>

          {/* Bluetooth permission notice */}
          {!bleSupported && (
            <div className="flex items-start gap-3 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3">
              <Info className="h-5 w-5 text-amber-600 mt-0.5 shrink-0" />
              <p className="text-sm text-amber-800">
                Web Bluetooth is not supported in this browser. Use <strong>Chrome</strong> or{" "}
                <strong>Microsoft Edge</strong> on desktop to connect Bluetooth devices. Manual entry is still available.
              </p>
            </div>
          )}

          {/* Patient selector */}
          <div className="bg-white rounded-2xl border border-gray-200 shadow-sm">
            <div className="px-5 py-4 border-b border-gray-100">
              <h2 className="text-sm font-semibold text-gray-700">Patient</h2>
            </div>

            {selectedPatient ? (
              <div className="px-5 py-4 flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <div className="h-10 w-10 rounded-full bg-impilo-100 flex items-center justify-center shrink-0">
                    <User className="h-5 w-5 text-impilo-600" />
                  </div>
                  <div>
                    <p className="text-sm font-semibold text-gray-900">
                      {patientDisplayName(selectedPatient)}
                    </p>
                    <p className="text-xs text-gray-500">{patientSubline(selectedPatient)}</p>
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => {
                    setSelectedPatient(null);
                    setHistory({});
                    setQuery("");
                    setSearchResults([]);
                    setTimeout(() => searchRef.current?.focus(), 100);
                  }}
                  className="p-2 rounded-lg text-gray-400 hover:text-gray-600 hover:bg-gray-100 transition-colors"
                >
                  <X className="h-4 w-4" />
                </button>
              </div>
            ) : (
              <div className="px-5 py-4">
                <div className="relative">
                  <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
                  <input
                    ref={searchRef}
                    type="text"
                    value={query}
                    onChange={(e) => handleQueryChange(e.target.value)}
                    placeholder="Search by name, CPID, or national ID…"
                    className="w-full pl-9 pr-4 py-2.5 text-sm border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-impilo-400"
                    autoFocus
                  />
                  {searching && (
                    <Loader2 className="absolute right-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400 animate-spin" />
                  )}
                </div>

                {searchResults.length > 0 && (
                  <ul className="mt-2 rounded-xl border border-gray-200 divide-y divide-gray-100 overflow-hidden shadow-sm max-h-64 overflow-y-auto">
                    {searchResults.map((p) => (
                      <li key={p.id}>
                        <button
                          type="button"
                          onClick={() => {
                            setSelectedPatient(p);
                            setQuery("");
                            setSearchResults([]);
                          }}
                          className="w-full flex items-center gap-3 px-4 py-3 text-left hover:bg-impilo-50 transition-colors"
                        >
                          <div className="h-8 w-8 rounded-full bg-impilo-100 flex items-center justify-center shrink-0">
                            <User className="h-4 w-4 text-impilo-600" />
                          </div>
                          <div className="min-w-0">
                            <p className="text-sm font-medium text-gray-900 truncate">
                              {patientDisplayName(p)}
                            </p>
                            <p className="text-xs text-gray-500 truncate">{patientSubline(p)}</p>
                          </div>
                        </button>
                      </li>
                    ))}
                  </ul>
                )}

                {!searching && query.trim() && searchResults.length === 0 && (
                  <p className="mt-2 text-sm text-gray-400 text-center py-2">
                    No patients found for &quot;{query}&quot;
                  </p>
                )}
              </div>
            )}
          </div>

          {/* Vitals grid — only shown when a patient is selected */}
          {selectedPatient && (
            <>
              {historyLoading ? (
                <div className="flex items-center justify-center py-12 gap-3">
                  <Loader2 className="h-6 w-6 text-impilo-500 animate-spin" />
                  <span className="text-sm text-gray-500">Loading vitals history…</span>
                </div>
              ) : (
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
                  {VITAL_CONFIGS.map((cfg) => (
                    <VitalCard
                      key={cfg.key}
                      config={cfg}
                      history={history[cfg.key] ?? []}
                      onSave={(primary, secondary, source) =>
                        handleSaveVital(cfg, primary, secondary, source)
                      }
                    />
                  ))}
                </div>
              )}
            </>
          )}

          {/* Empty state before patient is selected */}
          {!selectedPatient && !query && (
            <div className="flex flex-col items-center gap-4 py-16 text-center">
              <div className="h-16 w-16 rounded-full bg-impilo-50 flex items-center justify-center">
                <Bluetooth className="h-8 w-8 text-impilo-400" />
              </div>
              <div>
                <p className="text-base font-semibold text-gray-900">
                  Search for a patient to begin
                </p>
                <p className="text-sm text-gray-500 mt-1 max-w-sm">
                  Once you select a patient, vital cards will appear. Connect a Bluetooth device
                  to each card or enter values manually — readings are saved to the patient
                  record automatically.
                </p>
              </div>
              {bleSupported && (
                <div className="flex items-center gap-2 text-xs text-impilo-600 bg-impilo-50 border border-impilo-100 rounded-full px-4 py-2">
                  <Bluetooth className="h-3.5 w-3.5" />
                  Web Bluetooth is supported in this browser
                </div>
              )}
            </div>
          )}
        </div>

        {/* Toast */}
        {toast && (
          <div
            className={`fixed bottom-6 right-6 z-50 flex items-center gap-2 px-4 py-3 rounded-xl shadow-lg text-sm font-medium text-white transition-all ${
              toast.ok ? "bg-emerald-600" : "bg-red-500"
            }`}
          >
            {toast.ok ? "✓" : "✗"} {toast.msg}
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
