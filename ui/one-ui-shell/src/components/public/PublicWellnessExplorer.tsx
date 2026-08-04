"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Loader2, CalendarClock, Calculator } from "lucide-react";
import { apiClient } from "@/lib/api-client";

/**
 * Public wellness explorer (PF-W5). Discovery + one-off use without sign-in: screening
 * schedules and a COMPUTE-ONLY calculator (BMI / blood pressure) that keeps nothing.
 * Tracking, saving plans, reminders and device data require sign-in (doctrine §13.3).
 */

interface ScreeningProgramme {
  programmeCode?: string;
  title?: string;
  description?: string;
  ageMin?: number;
  ageMax?: number;
  frequencyMonths?: number;
}

interface CalcResult {
  kind?: string;
  bmi?: number;
  band?: string;
  category?: string;
  advice?: string;
}

function freq(months?: number): string | null {
  if (!months) return null;
  if (months === 12) return "every year";
  if (months % 12 === 0) return `every ${months / 12} years`;
  return `every ${months} months`;
}

export function PublicWellnessExplorer() {
  const [programmes, setProgrammes] = useState<ScreeningProgramme[] | null>(null);
  const [tab, setTab] = useState<"bmi" | "bp">("bmi");
  const [inputs, setInputs] = useState<Record<string, string>>({});
  const [result, setResult] = useState<CalcResult | null>(null);
  const [calcError, setCalcError] = useState("");
  const [calculating, setCalculating] = useState(false);

  useEffect(() => {
    let cancelled = false;
    apiClient
      .get<ScreeningProgramme[]>("/internal/v1/public/gateway/wellness/screening-programmes")
      .then((r) => {
        if (!cancelled) setProgrammes(Array.isArray(r) ? r : []);
      })
      .catch(() => {
        if (!cancelled) setProgrammes([]);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  async function calculate() {
    setCalculating(true);
    setCalcError("");
    setResult(null);
    const body: Record<string, unknown> = { kind: tab };
    if (tab === "bmi") {
      body.heightCm = Number(inputs.heightCm);
      body.weightKg = Number(inputs.weightKg);
    } else {
      body.systolic = Number(inputs.systolic);
      body.diastolic = Number(inputs.diastolic);
    }
    try {
      const r = await apiClient.post<CalcResult>("/internal/v1/public/gateway/wellness/calculate", body);
      setResult(r);
    } catch {
      setCalcError("Enter valid numbers for all fields.");
    } finally {
      setCalculating(false);
    }
  }

  const field =
    "w-full rounded-lg border border-slate-300 px-3 py-2.5 text-[15px] focus:border-emerald-500 focus:outline-none";

  return (
    <div className="space-y-10">
      {/* Calculator */}
      <section aria-labelledby="calc-h" data-testid="wellness-calculator">
        <h2 id="calc-h" className="flex items-center gap-2 text-lg font-bold text-slate-900">
          <Calculator className="h-5 w-5 text-emerald-600" aria-hidden /> Quick health check
        </h2>
        <p className="mt-1 text-sm text-slate-600">
          A one-off check — nothing is saved. Sign in to track results over time.
        </p>
        <div className="mt-3 flex gap-2" role="tablist" aria-label="Calculator type">
          {(["bmi", "bp"] as const).map((k) => (
            <button
              key={k}
              type="button"
              role="tab"
              aria-selected={tab === k}
              data-testid={`calc-tab-${k}`}
              onClick={() => {
                setTab(k);
                setResult(null);
                setCalcError("");
              }}
              className={`min-h-[40px] rounded-full border px-4 py-1.5 text-sm font-medium ${
                tab === k
                  ? "border-emerald-600 bg-emerald-600 text-white"
                  : "border-slate-300 bg-white text-slate-700 hover:bg-emerald-50"
              }`}
            >
              {k === "bmi" ? "Body mass index" : "Blood pressure"}
            </button>
          ))}
        </div>

        <div className="mt-3 max-w-md rounded-xl border border-white/60 bg-white/70 backdrop-blur-sm [.low-blur_&]:bg-white p-4">
          {tab === "bmi" ? (
            <div className="grid grid-cols-2 gap-3">
              <label className="text-sm">
                Height (cm)
                <input
                  type="number"
                  inputMode="numeric"
                  data-testid="calc-heightCm"
                  className={`mt-1 ${field}`}
                  value={inputs.heightCm ?? ""}
                  onChange={(e) => setInputs((p) => ({ ...p, heightCm: e.target.value }))}
                />
              </label>
              <label className="text-sm">
                Weight (kg)
                <input
                  type="number"
                  inputMode="numeric"
                  data-testid="calc-weightKg"
                  className={`mt-1 ${field}`}
                  value={inputs.weightKg ?? ""}
                  onChange={(e) => setInputs((p) => ({ ...p, weightKg: e.target.value }))}
                />
              </label>
            </div>
          ) : (
            <div className="grid grid-cols-2 gap-3">
              <label className="text-sm">
                Systolic (top)
                <input
                  type="number"
                  inputMode="numeric"
                  data-testid="calc-systolic"
                  className={`mt-1 ${field}`}
                  value={inputs.systolic ?? ""}
                  onChange={(e) => setInputs((p) => ({ ...p, systolic: e.target.value }))}
                />
              </label>
              <label className="text-sm">
                Diastolic (bottom)
                <input
                  type="number"
                  inputMode="numeric"
                  data-testid="calc-diastolic"
                  className={`mt-1 ${field}`}
                  value={inputs.diastolic ?? ""}
                  onChange={(e) => setInputs((p) => ({ ...p, diastolic: e.target.value }))}
                />
              </label>
            </div>
          )}
          <button
            type="button"
            data-testid="calc-run"
            onClick={calculate}
            disabled={calculating}
            className="mt-3 inline-flex min-h-[44px] items-center gap-2 rounded-lg bg-emerald-600 px-5 py-2 text-sm font-semibold text-white hover:bg-emerald-700 disabled:opacity-60"
          >
            {calculating && <Loader2 className="h-4 w-4 animate-spin" aria-hidden />}
            Check
          </button>
          {calcError && <p className="mt-2 text-sm text-red-700">{calcError}</p>}
          {result && (
            <div data-testid="calc-result" className="mt-3 rounded-lg bg-emerald-50 p-3 text-sm text-emerald-900">
              <p className="font-bold">
                {result.bmi !== undefined ? `BMI ${result.bmi} · ${result.band}` : result.category}
              </p>
              {result.advice && <p className="mt-1">{result.advice}</p>}
            </div>
          )}
        </div>
        <p className="mt-2 text-xs text-slate-500">
          General information only, not a diagnosis. Speak to a health worker about your own health.
        </p>
      </section>

      {/* Screening schedules */}
      <section aria-labelledby="screen-h" data-testid="wellness-screening">
        <h2 id="screen-h" className="flex items-center gap-2 text-lg font-bold text-slate-900">
          <CalendarClock className="h-5 w-5 text-emerald-600" aria-hidden /> Screening &amp; prevention
        </h2>
        {programmes === null ? (
          <p className="mt-3 flex items-center gap-2 text-sm text-slate-500">
            <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> Loading…
          </p>
        ) : programmes.length === 0 ? (
          <p className="mt-3 text-sm text-slate-600">No screening programmes are published yet.</p>
        ) : (
          <div className="mt-3 grid gap-3 sm:grid-cols-2">
            {programmes.map((p) => {
              const cadence = freq(p.frequencyMonths);
              const ages =
                p.ageMin !== undefined && p.ageMax !== undefined ? `Ages ${p.ageMin}–${p.ageMax}` : null;
              return (
                <div key={p.programmeCode} className="rounded-xl border border-white/60 bg-white/70 backdrop-blur-sm [.low-blur_&]:bg-white p-4">
                  <p className="font-semibold text-slate-900">{p.title}</p>
                  {p.description && <p className="mt-1 text-[13px] leading-relaxed text-slate-600">{p.description}</p>}
                  <p className="mt-2 text-xs text-slate-500">{[ages, cadence].filter(Boolean).join(" · ")}</p>
                </div>
              );
            })}
          </div>
        )}
      </section>

      <div className="rounded-xl border border-emerald-200 bg-emerald-50 p-4">
        <p className="text-sm font-medium text-emerald-900">Want to track your health?</p>
        <p className="mt-1 text-sm text-emerald-800">
          Sign in to save your progress and continue across devices — track steps, weight, blood
          pressure and more, set goals and get reminders.
        </p>
        <Link
          href="/auth/login?returnTo=%2Fwellness"
          className="mt-2 inline-block rounded-lg bg-emerald-700 px-4 py-2 text-sm font-semibold text-white hover:bg-emerald-800"
        >
          Sign in to track
        </Link>
      </div>
    </div>
  );
}
