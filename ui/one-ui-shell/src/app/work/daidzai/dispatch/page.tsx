"use client";

/**
 * EMS dispatch console — lists incidents needing an ambulance and dispatches a clinical
 * EMS mission onto the validated daidzai state machine (POST /daidzai/ems/incidents/{id}
 * /dispatch, idempotent per incident). On dispatch the mission board opens to advance the
 * crew and capture the prehospital ePCR. Route: /work/daidzai/dispatch
 */

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { AlertTriangle, Loader2, RefreshCw, Ambulance } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NompiloContextualGuidance } from "@/components/intelligent/NompiloContextualGuidance";
import { apiClient } from "@/lib/api-client";
import { useDispatchEmsMission } from "@/hooks/queries/useDaidzai";

interface Incident {
  id: string;
  incidentReference?: string;
  emergencyCategory?: string;
  severity?: string;
  triageCategory?: string;
  status?: string;
}

function asArray(p: unknown): Incident[] {
  return Array.isArray(p) ? (p as Incident[]) : [];
}
function errMessage(e: unknown): string {
  if (e && typeof e === "object") {
    const obj = e as { error?: { message?: string }; status?: number };
    if (obj.error?.message) return obj.error.message;
    if (obj.status) return `Request failed (HTTP ${obj.status}).`;
  }
  return "Could not load incidents. Please try again.";
}

export default function DaidzaiDispatchPage() {
  const router = useRouter();
  const dispatchEms = useDispatchEmsMission();
  const [data, setData] = useState<Incident[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setData(asArray(await apiClient.get<unknown>("/internal/v1/daidzai/incidents")));
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

  const dispatch = async (id: string) => {
    setBusy(id);
    setError(null);
    try {
      await dispatchEms.mutateAsync({ incidentId: id, priority: "CRITICAL" });
      router.push(`/work/daidzai/missions?incident=${encodeURIComponent(id)}`);
    } catch (e) {
      setError(errMessage(e));
    } finally {
      setBusy(null);
    }
  };

  return (
    <AppLayout>
      <PageShell
        title="EMS dispatch"
        subtitle="Dispatch a clinical ambulance mission — advance the crew + capture the ePCR"
        serviceSlug="daidzai"
      >
        <div className="mb-4 flex justify-end">
          <button
            type="button"
            onClick={() => void load()}
            className="inline-flex items-center gap-1.5 rounded-lg border border-border px-3 py-1.5 text-sm text-muted-foreground hover:text-foreground"
          >
            <RefreshCw className="h-3.5 w-3.5" /> Refresh
          </button>
        </div>

        {loading ? (
          <div className="flex items-center gap-2 py-16 text-sm text-muted-foreground">
            <Loader2 className="h-5 w-5 animate-spin text-teal-600" /> Loading…
          </div>
        ) : error ? (
          <div className="rounded-xl border border-red-200 bg-red-50 p-5 text-sm text-red-800">
            <div className="mb-2 flex items-center gap-2 font-semibold">
              <AlertTriangle className="h-4 w-4" /> Could not dispatch
            </div>
            <p>{error}</p>
          </div>
        ) : !data || data.length === 0 ? (
          <div className="rounded-xl border border-border bg-card p-8 text-center text-sm text-muted-foreground">
            No incidents awaiting dispatch.
          </div>
        ) : (
          <ul className="space-y-3">
            {data.map((inc) => (
              <li
                key={inc.id}
                className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-border bg-card p-4 shadow-sm"
              >
                <div>
                  <div className="font-mono text-xs text-muted-foreground">
                    {inc.incidentReference ?? inc.id}
                  </div>
                  <div className="text-sm font-medium">
                    {inc.emergencyCategory?.replace(/_/g, " ")} · {inc.severity}
                    {inc.triageCategory ? ` · ${inc.triageCategory}` : ""}
                  </div>
                  <div className="text-xs text-muted-foreground">Status: {inc.status}</div>
                </div>
                <button
                  type="button"
                  onClick={() => void dispatch(inc.id)}
                  disabled={busy === inc.id}
                  className="inline-flex items-center gap-1.5 rounded-lg bg-red-600 px-3 py-2 text-sm font-semibold text-white hover:bg-red-700 disabled:opacity-60"
                >
                  {busy === inc.id ? <Loader2 className="h-4 w-4 animate-spin" /> : <Ambulance className="h-4 w-4" />}
                  Dispatch EMS
                </button>
              </li>
            ))}
          </ul>
        )}

        <div className="mt-6">
          <NompiloContextualGuidance routePath="/work/daidzai/dispatch" />
        </div>
      </PageShell>
    </AppLayout>
  );
}
