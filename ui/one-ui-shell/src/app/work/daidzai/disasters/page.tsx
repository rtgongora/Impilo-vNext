"use client";

/**
 * Disaster / MCI command dashboard — real disaster incidents via experience-BFF
 * (GET /internal/v1/daidzai/disasters), declare a new disaster, and close (routes
 * after-action to Rito). No fabricated map or casualty counters — counts come from
 * the real records.
 * Route: /work/daidzai/disasters
 */

import { useCallback, useEffect, useState } from "react";
import { AlertTriangle, Loader2, RefreshCw, ShieldAlert, Plus } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NompiloContextualGuidance } from "@/components/intelligent/NompiloContextualGuidance";
import { apiClient } from "@/lib/api-client";

interface Disaster {
  id: string;
  incidentReference?: string;
  incidentType?: string;
  emergencyCategory?: string;
  severity?: string;
  status?: string;
  title?: string;
  commandPost?: string;
  openedAt?: string;
}

function asArray(p: unknown): Disaster[] {
  return Array.isArray(p) ? (p as Disaster[]) : [];
}
function errMessage(e: unknown): string {
  if (e && typeof e === "object") {
    const obj = e as { error?: { message?: string }; status?: number };
    if (obj.error?.message) return obj.error.message;
    if (obj.status) return `Request failed (HTTP ${obj.status}).`;
  }
  return "Could not load disasters. Please try again.";
}

export default function DaidzaiDisastersPage() {
  const [data, setData] = useState<Disaster[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showNew, setShowNew] = useState(false);
  const [busy, setBusy] = useState(false);
  const [form, setForm] = useState({
    incidentType: "DISASTER",
    emergencyCategory: "FLOOD",
    severity: "HIGH",
    title: "",
    commandPost: "",
  });

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setData(asArray(await apiClient.get<unknown>("/internal/v1/daidzai/disasters")));
    } catch (e) {
      setError(errMessage(e));
      setData(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const declare = async () => {
    setBusy(true);
    setError(null);
    try {
      await apiClient.post("/internal/v1/daidzai/disasters", {
        ...form,
        title: form.title || `${form.emergencyCategory} incident`,
      });
      setShowNew(false);
      await load();
    } catch (e) {
      setError(errMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const close = async (id: string) => {
    setBusy(true);
    try {
      await apiClient.post(`/internal/v1/daidzai/disasters/${encodeURIComponent(id)}/close`, {});
      await load();
    } catch (e) {
      setError(errMessage(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <AppLayout>
      <PageShell
        title="Disaster command"
        subtitle="Disaster & mass-casualty coordination — closure routes after-action to Rito"
        serviceSlug="daidzai"
      >
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <button
            type="button"
            onClick={() => setShowNew((s) => !s)}
            className="inline-flex items-center gap-1.5 rounded-lg bg-red-600 px-3 py-1.5 text-sm font-semibold text-white hover:bg-red-700"
          >
            <Plus className="h-4 w-4" /> Declare disaster
          </button>
          <button
            type="button"
            onClick={() => void load()}
            className="inline-flex items-center gap-1.5 rounded-lg border border-border px-3 py-1.5 text-sm text-muted-foreground hover:text-foreground"
          >
            <RefreshCw className="h-3.5 w-3.5" /> Refresh
          </button>
        </div>

        {showNew ? (
          <div className="mb-5 grid gap-3 rounded-xl border border-border bg-card p-4 shadow-sm sm:grid-cols-2">
            <label className="text-sm">
              <span className="mb-1 block font-medium">Type</span>
              <select
                value={form.incidentType}
                onChange={(e) => setForm({ ...form, incidentType: e.target.value })}
                className="w-full rounded-lg border border-border bg-background px-3 py-2"
              >
                <option value="DISASTER">Disaster</option>
                <option value="MCI">Mass-casualty (MCI)</option>
                <option value="OUTBREAK">Outbreak</option>
              </select>
            </label>
            <label className="text-sm">
              <span className="mb-1 block font-medium">Category</span>
              <input
                value={form.emergencyCategory}
                onChange={(e) => setForm({ ...form, emergencyCategory: e.target.value })}
                className="w-full rounded-lg border border-border bg-background px-3 py-2"
              />
            </label>
            <label className="text-sm">
              <span className="mb-1 block font-medium">Severity</span>
              <select
                value={form.severity}
                onChange={(e) => setForm({ ...form, severity: e.target.value })}
                className="w-full rounded-lg border border-border bg-background px-3 py-2"
              >
                <option value="CRITICAL">Critical</option>
                <option value="HIGH">High</option>
                <option value="MODERATE">Moderate</option>
              </select>
            </label>
            <label className="text-sm">
              <span className="mb-1 block font-medium">Command post</span>
              <input
                value={form.commandPost}
                onChange={(e) => setForm({ ...form, commandPost: e.target.value })}
                className="w-full rounded-lg border border-border bg-background px-3 py-2"
              />
            </label>
            <label className="text-sm sm:col-span-2">
              <span className="mb-1 block font-medium">Title</span>
              <input
                value={form.title}
                onChange={(e) => setForm({ ...form, title: e.target.value })}
                className="w-full rounded-lg border border-border bg-background px-3 py-2"
                placeholder="e.g. Tokwe flooding"
              />
            </label>
            <div className="sm:col-span-2">
              <button
                type="button"
                onClick={() => void declare()}
                disabled={busy}
                className="inline-flex items-center gap-1.5 rounded-lg bg-red-600 px-4 py-2 text-sm font-semibold text-white hover:bg-red-700 disabled:opacity-60"
              >
                {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <ShieldAlert className="h-4 w-4" />}
                Declare
              </button>
            </div>
          </div>
        ) : null}

        {loading ? (
          <div className="flex items-center gap-2 py-16 text-sm text-muted-foreground">
            <Loader2 className="h-5 w-5 animate-spin text-teal-600" /> Loading disasters…
          </div>
        ) : error ? (
          <div className="rounded-xl border border-red-200 bg-red-50 p-5 text-sm text-red-800">
            <div className="mb-2 flex items-center gap-2 font-semibold">
              <AlertTriangle className="h-4 w-4" /> Could not load disasters
            </div>
            <p>{error}</p>
          </div>
        ) : !data || data.length === 0 ? (
          <div className="rounded-xl border border-border bg-card p-8 text-center text-sm text-muted-foreground">
            <ShieldAlert className="mx-auto mb-2 h-6 w-6 opacity-50" />
            No active disasters or mass-casualty incidents.
          </div>
        ) : (
          <ul className="space-y-3">
            {data.map((d) => (
              <li
                key={d.id}
                className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-border bg-card p-4 shadow-sm"
              >
                <div>
                  <div className="font-mono text-xs text-muted-foreground">
                    {d.incidentReference ?? d.id}
                  </div>
                  <div className="text-sm font-semibold">{d.title ?? d.emergencyCategory}</div>
                  <div className="text-xs text-muted-foreground">
                    {d.incidentType} · {d.severity} · {d.status}
                    {d.commandPost ? ` · ${d.commandPost}` : ""}
                  </div>
                </div>
                {d.status !== "CLOSED" ? (
                  <button
                    type="button"
                    onClick={() => void close(d.id)}
                    disabled={busy}
                    className="rounded-lg border border-border px-3 py-2 text-sm font-medium text-muted-foreground hover:bg-background disabled:opacity-60"
                  >
                    Close + after-action
                  </button>
                ) : (
                  <span className="text-xs font-medium text-emerald-700">Closed</span>
                )}
              </li>
            ))}
          </ul>
        )}

        <div className="mt-6">
          <NompiloContextualGuidance routePath="/work/daidzai/disasters" />
        </div>
      </PageShell>
    </AppLayout>
  );
}
