"use client";

/**
 * Surgical referral worklist (receiving team). Real referrals via the
 * experience-BFF (GET /internal/v1/referrals/surgical/worklist). Accepting a
 * referral emits referral.surgical.accepted — the single funnel trigger that
 * places the case on the surgical waitlist. No parallel entry point.
 * Route: /work/clinical/theatre/referrals
 */

import { useCallback, useEffect, useState } from "react";
import { Inbox, Loader2, RefreshCw, Check, X } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NompiloContextualGuidance } from "@/components/intelligent/NompiloContextualGuidance";
import { apiClient } from "@/lib/api-client";

interface SurgicalReferral {
  id?: string;
  patientId?: string;
  intendedProcedureZiboCode?: string;
  clinicalPriority?: string;
  targetSpecialty?: string;
  decision?: string;
}

function asArray(p: unknown): SurgicalReferral[] {
  const data = (p && typeof p === "object" && "data" in p) ? (p as { data?: unknown }).data : p;
  return Array.isArray(data) ? (data as SurgicalReferral[]) : [];
}
function errMessage(e: unknown): string {
  if (e && typeof e === "object") {
    const obj = e as { error?: { message?: string }; status?: number };
    if (obj.error?.message) return obj.error.message;
    if (obj.status) return `Request failed (HTTP ${obj.status}).`;
  }
  return "Could not load the referral worklist. Please try again.";
}

export default function SurgicalReferralsPage() {
  const [data, setData] = useState<SurgicalReferral[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [msg, setMsg] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await apiClient.get<unknown>("/internal/v1/referrals/surgical/worklist?decision=PENDING");
      setData(asArray(res));
    } catch (e) {
      setError(errMessage(e));
    } finally {
      setLoading(false);
    }
  }, []);

  const decide = useCallback(async (id: string, decision: string) => {
    setMsg(null);
    try {
      await apiClient.post(`/internal/v1/referrals/surgical/${id}/decide`, { decision });
      setMsg(decision === "ACCEPTED"
        ? "Referral accepted — the case is now on the surgical waitlist."
        : `Referral ${decision.toLowerCase()}.`);
      void load();
    } catch (e) {
      setMsg(errMessage(e));
    }
  }, [load]);

  useEffect(() => { void load(); }, [load]);

  return (
    <AppLayout>
      <PageShell title="Surgical referrals" subtitle="Receiving-team worklist — accepting funnels the case onto the surgical waitlist" icon={<Inbox className="h-5 w-5" />}>
        <div className="mb-4 flex items-center justify-between">
          <button type="button" onClick={() => void load()} className="inline-flex items-center gap-1.5 rounded-lg border border-border bg-card px-3 py-1.5 text-sm font-medium hover:bg-background">
            <RefreshCw className="h-3.5 w-3.5" /> Refresh
          </button>
        </div>
        {msg && <p className="mb-4 rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800">{msg}</p>}

        {loading ? (
          <div className="flex items-center justify-center py-16 text-muted-foreground">
            <Loader2 className="mr-2 h-5 w-5 animate-spin" /> Loading the worklist…
          </div>
        ) : error ? (
          <div className="rounded-xl border border-red-200 bg-red-50 p-6 text-sm text-red-700">
            <p className="mb-3">{error}</p>
            <button type="button" onClick={() => void load()} className="inline-flex items-center gap-1.5 rounded-lg border border-red-300 bg-white px-3 py-1.5 font-medium text-red-700 hover:bg-red-100">
              <RefreshCw className="h-3.5 w-3.5" /> Retry
            </button>
          </div>
        ) : !data || data.length === 0 ? (
          <div className="rounded-xl border border-border bg-card p-8 text-center text-sm text-muted-foreground">
            <Inbox className="mx-auto mb-2 h-6 w-6 opacity-50" />
            No pending surgical referrals.
          </div>
        ) : (
          <div className="space-y-2">
            {data.map((r) => (
              <div key={r.id} className="flex items-center justify-between rounded-xl border border-border bg-card p-4 shadow-sm">
                <div>
                  <p className="font-medium">{r.patientId ?? "—"}</p>
                  <p className="text-sm text-muted-foreground">{r.intendedProcedureZiboCode ?? "—"} · {r.targetSpecialty ?? "—"} · {r.clinicalPriority ?? "P4"}</p>
                </div>
                <div className="flex gap-2">
                  <button type="button" onClick={() => void decide(r.id ?? "", "ACCEPTED")} className="inline-flex items-center gap-1 rounded-lg bg-emerald-600 px-3 py-1.5 text-sm font-medium text-white hover:opacity-90">
                    <Check className="h-3.5 w-3.5" /> Accept
                  </button>
                  <button type="button" onClick={() => void decide(r.id ?? "", "DECLINED")} className="inline-flex items-center gap-1 rounded-lg border border-border px-3 py-1.5 text-sm font-medium hover:bg-background">
                    <X className="h-3.5 w-3.5" /> Decline
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}

        <div className="mt-6">
          <NompiloContextualGuidance routePath="/work/clinical/theatre/referrals" />
        </div>
      </PageShell>
    </AppLayout>
  );
}
