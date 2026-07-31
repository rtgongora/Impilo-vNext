"use client";

/**
 * Offline Tier-A WHO IITT preview — TeaVM engine only; never writes of-record acuity.
 *
 * clinical-logic-guard: presentation of WhoIittEngine results; no TS criteria
 */

import { useCallback, useEffect, useState } from "react";
import {
  evaluateIittInBrowser,
  loadIittBrowserEngine,
  type IittEngineResult,
} from "@/lib/offline/iittBrowserEngine";

export function OfflineIittPreviewPanel() {
  const [engineReady, setEngineReady] = useState(false);
  const [engineMissing, setEngineMissing] = useState(false);
  const [ageDays, setAgeDays] = useState("5000");
  const [hr, setHr] = useState("");
  const [rr, setRr] = useState("");
  const [temp, setTemp] = useState("");
  const [spo2, setSpo2] = useState("");
  const [avpu, setAvpu] = useState("0");
  const [result, setResult] = useState<IittEngineResult | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let cancelled = false;
    void loadIittBrowserEngine().then((engine) => {
      if (cancelled) return;
      if (engine?.evaluate) {
        setEngineReady(true);
      } else {
        setEngineMissing(true);
      }
    });
    return () => {
      cancelled = true;
    };
  }, []);

  const run = useCallback(async () => {
    setBusy(true);
    try {
      const vitals: Record<string, number> = {};
      if (hr.trim()) vitals.heart_rate = Number(hr);
      if (rr.trim()) vitals.respiratory_rate = Number(rr);
      if (temp.trim()) vitals.temperature_c = Number(temp);
      if (spo2.trim()) vitals.spo2_percent = Number(spo2);
      if (avpu.trim()) vitals.avpu = Number(avpu);

      const evaluated = await evaluateIittInBrowser({
        ageDays: ageDays.trim() ? Number(ageDays) : null,
        vitals,
      });
      setResult(evaluated);
    } finally {
      setBusy(false);
    }
  }, [ageDays, hr, rr, temp, spo2, avpu]);

  if (engineMissing) {
    return (
      <p
        className="rounded border border-border bg-muted/40 px-3 py-2 text-sm text-muted-foreground"
        data-testid="iitt-engine-missing"
      >
        Browser IITT engine is not available offline. Triage of record stays blocked until reconnect.
      </p>
    );
  }

  if (!engineReady) {
    return (
      <p className="text-sm text-muted-foreground" data-testid="iitt-engine-loading">
        Loading WHO IITT browser engine…
      </p>
    );
  }

  return (
    <div
      className="space-y-3 rounded-lg border border-border bg-card p-4"
      data-testid="offline-iitt-preview"
    >
      <div>
        <h3 className="font-semibold text-foreground">WHO IITT offline preview</h3>
        <p className="text-xs text-muted-foreground">
          Runs the TeaVM-compiled WhoIittEngine locally. Advisory only — does not become the triage
          of record until pct accepts the write online.
        </p>
      </div>

      <div className="grid gap-2 sm:grid-cols-3">
        <label className="text-xs">
          Age (days)
          <input
            className="mt-1 w-full rounded border border-border px-2 py-1 text-sm"
            data-testid="iitt-age-days"
            value={ageDays}
            onChange={(e) => setAgeDays(e.target.value)}
          />
        </label>
        <label className="text-xs">
          HR
          <input
            className="mt-1 w-full rounded border border-border px-2 py-1 text-sm"
            data-testid="iitt-hr"
            value={hr}
            onChange={(e) => setHr(e.target.value)}
          />
        </label>
        <label className="text-xs">
          RR
          <input
            className="mt-1 w-full rounded border border-border px-2 py-1 text-sm"
            data-testid="iitt-rr"
            value={rr}
            onChange={(e) => setRr(e.target.value)}
          />
        </label>
        <label className="text-xs">
          Temp
          <input
            className="mt-1 w-full rounded border border-border px-2 py-1 text-sm"
            data-testid="iitt-temp"
            value={temp}
            onChange={(e) => setTemp(e.target.value)}
          />
        </label>
        <label className="text-xs">
          SpO2
          <input
            className="mt-1 w-full rounded border border-border px-2 py-1 text-sm"
            data-testid="iitt-spo2"
            value={spo2}
            onChange={(e) => setSpo2(e.target.value)}
          />
        </label>
        <label className="text-xs">
          AVPU (0=A)
          <input
            className="mt-1 w-full rounded border border-border px-2 py-1 text-sm"
            data-testid="iitt-avpu"
            value={avpu}
            onChange={(e) => setAvpu(e.target.value)}
          />
        </label>
      </div>

      <button
        type="button"
        className="rounded bg-neutral-900 px-3 py-1.5 text-sm text-white disabled:opacity-50"
        data-testid="iitt-evaluate"
        disabled={busy}
        onClick={() => void run()}
      >
        {busy ? "Evaluating…" : "Evaluate IITT offline"}
      </button>

      {result ? (
        <div
          className="rounded border border-border bg-background px-3 py-2 text-sm"
          data-testid="iitt-result"
          data-priority={result.priority}
        >
          <p className="font-medium">
            Priority: {result.priority}
            {result.requiresClinicianReview ? " — clinician review" : ""}
          </p>
          {result.message ? <p className="text-danger">{result.message}</p> : null}
          {result.chart ? <p className="text-xs text-muted-foreground">Chart: {result.chart}</p> : null}
          {result.destination ? (
            <p className="text-xs text-muted-foreground">Destination: {result.destination}</p>
          ) : null}
          {result.unassessedInputs?.length ? (
            <p className="mt-1 text-xs text-muted-foreground">
              Unassessed: {result.unassessedInputs.join(", ")}
            </p>
          ) : null}
          {result.triggeringFindings?.length ? (
            <p className="mt-1 text-xs">Findings: {result.triggeringFindings.join("; ")}</p>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
