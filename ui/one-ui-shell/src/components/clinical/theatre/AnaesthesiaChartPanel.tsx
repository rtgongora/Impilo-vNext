"use client";

/**
 * Anaesthesia chart panel (Lane 3). A reviewable multi-channel time series for the procedure. The BLOOD
 * channel is PROJECTED by the service from the MADI transfusion episode (single source of truth) and is
 * shown read-only — you cannot hand-enter blood here. Other channels accept an entry. Nothing is mocked.
 * Binds: GET /internal/v1/procedures/{id}/anaesthesia/chart, POST same
 */

import { useCallback, useEffect, useState } from "react";
import { Activity, Loader2, RefreshCw } from "lucide-react";
import { apiClient } from "@/lib/api-client";

interface ChartEntry {
  channel?: string;
  label?: string;
  value_text?: string | null;
  value_numeric?: number | null;
  unit?: string | null;
  recorded_at?: string | null;
  technique?: string | null;
  projected?: boolean;
}

const CHANNELS = ["AGENT", "FLUID", "MEDICATION", "MONITORING", "VITAL", "EVENT"] as const;

const CHANNEL_TONE: Record<string, string> = {
  AGENT: "bg-purple-100 text-purple-700",
  FLUID: "bg-cyan-100 text-cyan-700",
  BLOOD: "bg-red-100 text-red-700",
  MEDICATION: "bg-blue-100 text-blue-700",
  MONITORING: "bg-slate-100 text-slate-600",
  VITAL: "bg-emerald-100 text-emerald-700",
  EVENT: "bg-amber-100 text-amber-700",
};

function unwrap<T>(res: unknown): T {
  const o = res as { data?: T };
  return (o && o.data !== undefined ? o.data : res) as T;
}
function errMessage(e: unknown): string {
  const o = e as { error?: { message?: string }; status?: number };
  if (o?.error?.message) return o.error.message;
  if (o?.status) return `Request failed (HTTP ${o.status}).`;
  return "Chart action failed. Please try again.";
}

export function AnaesthesiaChartPanel({ caseId }: { caseId: string }) {
  const [entries, setEntries] = useState<ChartEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const [channel, setChannel] = useState<string>("MEDICATION");
  const [label, setLabel] = useState("");
  const [valueText, setValueText] = useState("");
  const [unit, setUnit] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await apiClient.get(`/internal/v1/procedures/${caseId}/anaesthesia/chart`);
      const chart = unwrap<{ entries?: ChartEntry[] }>(res);
      setEntries(Array.isArray(chart?.entries) ? chart.entries : []);
    } catch {
      setEntries([]);
    } finally {
      setLoading(false);
    }
  }, [caseId]);

  useEffect(() => {
    void load();
  }, [load]);

  const add = useCallback(async () => {
    setBusy(true);
    setMsg(null);
    try {
      await apiClient.post(`/internal/v1/procedures/${caseId}/anaesthesia/chart`, {
        channel,
        label,
        ...(valueText ? { valueText } : {}),
        ...(unit ? { unit } : {}),
      });
      setMsg("Chart entry added.");
      setLabel("");
      setValueText("");
      setUnit("");
      await load();
    } catch (e) {
      setMsg(errMessage(e));
    } finally {
      setBusy(false);
    }
  }, [caseId, channel, label, valueText, unit, load]);

  return (
    <section className="rounded-xl border border-border bg-card p-4" data-testid="anaesthesia-chart-panel">
      <div className="mb-3 flex items-center justify-between gap-2">
        <h3 className="flex items-center gap-1.5 text-sm font-semibold"><Activity className="h-4 w-4" /> Anaesthesia chart</h3>
        <button type="button" onClick={() => void load()} className="inline-flex items-center gap-1 rounded-lg border border-border bg-background px-2 py-1 text-xs hover:bg-card">
          <RefreshCw className="h-3 w-3" /> Refresh
        </button>
      </div>

      {msg && <p className="mb-2 rounded-lg border border-border bg-background p-2 text-xs">{msg}</p>}

      {loading ? (
        <p className="flex items-center gap-2 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" /> Loading chart…</p>
      ) : entries.length === 0 ? (
        <p className="text-sm text-muted-foreground">No chart entries yet. Blood administered on this case will appear here automatically (projected from MADI).</p>
      ) : (
        <ol className="space-y-1">
          {entries.map((e, i) => (
            <li key={i} className="flex flex-wrap items-center gap-2 rounded-lg border border-border p-2 text-xs">
              <span className={`rounded-full px-2 py-0.5 font-medium ${CHANNEL_TONE[e.channel ?? ""] ?? "bg-slate-100 text-slate-600"}`}>{e.channel}</span>
              <span className="font-medium">{e.label ?? "—"}</span>
              {(e.value_text || e.value_numeric != null) && <span>{e.value_text ?? e.value_numeric}{e.unit ? ` ${e.unit}` : ""}</span>}
              {e.projected && <span className="rounded-full bg-red-50 px-2 py-0.5 text-[10px] font-medium text-red-600">projected · MADI</span>}
              {e.recorded_at && <span className="ml-auto text-muted-foreground">{String(e.recorded_at).slice(11, 19) || String(e.recorded_at).slice(0, 16)}</span>}
            </li>
          ))}
        </ol>
      )}

      <div className="mt-3 flex flex-wrap items-end gap-2">
        <label className="block text-xs"><span className="mb-1 block font-medium text-muted-foreground">Channel</span>
          <select value={channel} onChange={(e) => setChannel(e.target.value)} className="rounded-lg border border-border bg-background px-2 py-1">
            {CHANNELS.map((c) => <option key={c}>{c}</option>)}
          </select>
        </label>
        <label className="block text-xs"><span className="mb-1 block font-medium text-muted-foreground">Label</span><input type="text" value={label} onChange={(e) => setLabel(e.target.value)} placeholder="e.g. Sevoflurane" className="w-40 rounded-lg border border-border bg-background px-2 py-1" /></label>
        <label className="block text-xs"><span className="mb-1 block font-medium text-muted-foreground">Value</span><input type="text" value={valueText} onChange={(e) => setValueText(e.target.value)} className="w-24 rounded-lg border border-border bg-background px-2 py-1" /></label>
        <label className="block text-xs"><span className="mb-1 block font-medium text-muted-foreground">Unit</span><input type="text" value={unit} onChange={(e) => setUnit(e.target.value)} placeholder="%" className="w-16 rounded-lg border border-border bg-background px-2 py-1" /></label>
        <button type="button" disabled={busy || !label} onClick={() => void add()} className="rounded-lg bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground hover:opacity-90 disabled:opacity-50">Add entry</button>
      </div>
      <p className="mt-1 text-[11px] text-muted-foreground">Blood is the single source of truth in MADI — it cannot be hand-entered here and appears as a projected channel.</p>
    </section>
  );
}
