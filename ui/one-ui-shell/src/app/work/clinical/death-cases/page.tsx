"use client";

/**
 * Death cases — provider board. Real death cases via experience-BFF
 * (GET /internal/v1/death/cases). Confirm a new death, then open a case to
 * screen, certify, manage mortuary care and registration. No fake data.
 * Route: /work/clinical/death-cases
 */

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { HeartCrack, Loader2, RefreshCw, Plus } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NompiloContextualGuidance } from "@/components/intelligent/NompiloContextualGuidance";
import { apiClient } from "@/lib/api-client";

interface DeathCase {
  caseId?: string;
  id?: string;
  patientCpid?: string;
  deceasedIdentityStatus?: string;
  placeOfDeathContext?: string;
  confirmedAt?: string;
  certificationStatus?: string;
  coronerReferralRequired?: boolean;
  civilRegistrationStatus?: string;
}

function asArray(p: unknown): DeathCase[] {
  return Array.isArray(p) ? (p as DeathCase[]) : [];
}
function caseId(c: DeathCase): string {
  return c.caseId ?? c.id ?? "";
}
function errMessage(e: unknown): string {
  if (e && typeof e === "object") {
    const obj = e as { error?: { message?: string }; status?: number };
    if (obj.error?.message) return obj.error.message;
    if (obj.status) return `Request failed (HTTP ${obj.status}).`;
  }
  return "Could not load death cases. Please try again.";
}

const CERT_TONE: Record<string, string> = {
  NOT_STARTED: "bg-slate-100 text-slate-600",
  DRAFT: "bg-amber-100 text-amber-700",
  SIGNED: "bg-emerald-100 text-emerald-700",
  AMENDED: "bg-blue-100 text-blue-700",
};

export default function DeathCasesPage() {
  const [data, setData] = useState<DeathCase[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showConfirm, setShowConfirm] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [confirmMsg, setConfirmMsg] = useState<string | null>(null);
  const [cf, setCf] = useState({ deathDatetime: "", placeOfDeathContext: "INPATIENT", deceasedCpid: "", deceasedIdentityStatus: "KNOWN" });

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await apiClient.get<unknown>("/internal/v1/death/cases");
      setData(asArray(res));
    } catch (e) {
      setError(errMessage(e));
    } finally {
      setLoading(false);
    }
  }, []);

  const confirmDeath = useCallback(async () => {
    if (!cf.deathDatetime) return;
    setConfirming(true);
    setConfirmMsg(null);
    try {
      await apiClient.post<DeathCase>("/internal/v1/death/confirm", {
        deathDatetime: new Date(cf.deathDatetime).toISOString(),
        placeOfDeathContext: cf.placeOfDeathContext,
        deceasedCpid: cf.deceasedCpid || undefined,
        deceasedIdentityStatus: cf.deceasedIdentityStatus,
        sourceContext: "FACILITY",
      });
      setConfirmMsg("Death confirmed. The case is now on the board below.");
      setShowConfirm(false);
      void load();
    } catch (e) {
      setConfirmMsg(errMessage(e));
    } finally {
      setConfirming(false);
    }
  }, [cf, load]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <AppLayout>
      <PageShell title="Death cases" subtitle="Recorded with dignity — confirmation through registration" icon={<HeartCrack className="h-5 w-5" />}>
        <div className="mb-4 flex items-center justify-between">
          <button
            type="button"
            onClick={() => void load()}
            className="inline-flex items-center gap-1.5 rounded-lg border border-border bg-card px-3 py-1.5 text-sm font-medium hover:bg-background"
          >
            <RefreshCw className="h-3.5 w-3.5" /> Refresh
          </button>
          <button
            type="button"
            onClick={() => setShowConfirm((v) => !v)}
            className="inline-flex items-center gap-1.5 rounded-lg bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground hover:opacity-90"
          >
            <Plus className="h-3.5 w-3.5" /> Confirm a death
          </button>
        </div>

        {showConfirm && (
          <div className="mb-5 rounded-xl border border-border bg-card p-4">
            <h3 className="mb-3 text-sm font-semibold">Confirm a death</h3>
            <div className="grid gap-3 sm:grid-cols-2">
              <label className="block text-sm">
                <span className="mb-1 block text-xs font-medium text-muted-foreground">Time of death</span>
                <input type="datetime-local" value={cf.deathDatetime} onChange={(e) => setCf({ ...cf, deathDatetime: e.target.value })} className="w-full rounded-lg border border-border bg-background px-3 py-1.5" />
              </label>
              <label className="block text-sm">
                <span className="mb-1 block text-xs font-medium text-muted-foreground">Place of death</span>
                <select value={cf.placeOfDeathContext} onChange={(e) => setCf({ ...cf, placeOfDeathContext: e.target.value })} className="w-full rounded-lg border border-border bg-background px-3 py-1.5">
                  {["INPATIENT", "ED", "THEATRE", "COMMUNITY", "BROUGHT_IN_DEAD", "IN_TRANSIT", "OTHER"].map((p) => <option key={p}>{p}</option>)}
                </select>
              </label>
              <label className="block text-sm">
                <span className="mb-1 block text-xs font-medium text-muted-foreground">Person (CPID, if known)</span>
                <input type="text" value={cf.deceasedCpid} onChange={(e) => setCf({ ...cf, deceasedCpid: e.target.value })} className="w-full rounded-lg border border-border bg-background px-3 py-1.5" />
              </label>
              <label className="block text-sm">
                <span className="mb-1 block text-xs font-medium text-muted-foreground">Identity status</span>
                <select value={cf.deceasedIdentityStatus} onChange={(e) => setCf({ ...cf, deceasedIdentityStatus: e.target.value })} className="w-full rounded-lg border border-border bg-background px-3 py-1.5">
                  {["KNOWN", "UNKNOWN", "TEMPORARY"].map((s) => <option key={s}>{s}</option>)}
                </select>
              </label>
            </div>
            {confirmMsg && <p className="mt-3 rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800">{confirmMsg}</p>}
            <button
              type="button"
              disabled={confirming || !cf.deathDatetime}
              onClick={() => void confirmDeath()}
              className="mt-3 rounded-lg bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground hover:opacity-90 disabled:opacity-50"
            >
              {confirming ? "Recording…" : "Record confirmation"}
            </button>
          </div>
        )}

        {loading ? (
          <div className="flex items-center justify-center py-16 text-muted-foreground">
            <Loader2 className="mr-2 h-5 w-5 animate-spin" /> Loading death cases…
          </div>
        ) : error ? (
          <div className="rounded-xl border border-red-200 bg-red-50 p-6 text-sm text-red-700">
            <p className="mb-3">{error}</p>
            <button
              type="button"
              onClick={() => void load()}
              className="inline-flex items-center gap-1.5 rounded-lg border border-red-300 bg-white px-3 py-1.5 font-medium text-red-700 hover:bg-red-100"
            >
              <RefreshCw className="h-3.5 w-3.5" /> Retry
            </button>
          </div>
        ) : !data || data.length === 0 ? (
          <div className="rounded-xl border border-border bg-card p-8 text-center text-sm text-muted-foreground">
            <HeartCrack className="mx-auto mb-2 h-6 w-6 opacity-50" />
            No death cases recorded for this facility.
          </div>
        ) : (
          <div className="overflow-x-auto rounded-xl border border-border bg-card shadow-sm">
            <table className="w-full min-w-[820px] text-left text-sm">
              <thead className="border-b border-border bg-background text-xs uppercase tracking-wide text-muted-foreground">
                <tr>
                  <th className="px-4 py-3">Person</th>
                  <th className="px-4 py-3">Place of death</th>
                  <th className="px-4 py-3">Confirmed</th>
                  <th className="px-4 py-3">Coroner</th>
                  <th className="px-4 py-3">Certification</th>
                  <th className="px-4 py-3">Registration</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {data.map((c) => (
                  <tr key={caseId(c)} className="hover:bg-background/80">
                    <td className="px-4 py-3">
                      <Link href={`/work/clinical/death-cases/${caseId(c)}`} className="font-medium text-primary hover:underline">
                        {c.patientCpid ?? (c.deceasedIdentityStatus === "KNOWN" ? "—" : "Unidentified")}
                      </Link>
                    </td>
                    <td className="px-4 py-3 text-muted-foreground">{c.placeOfDeathContext ?? "—"}</td>
                    <td className="px-4 py-3 text-muted-foreground">{c.confirmedAt ? new Date(c.confirmedAt).toLocaleString() : "—"}</td>
                    <td className="px-4 py-3">
                      {c.coronerReferralRequired ? (
                        <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-700">Referral required</span>
                      ) : (
                        <span className="text-muted-foreground">—</span>
                      )}
                    </td>
                    <td className="px-4 py-3">
                      <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${CERT_TONE[c.certificationStatus ?? "NOT_STARTED"] ?? "bg-slate-100 text-slate-600"}`}>
                        {c.certificationStatus ?? "NOT_STARTED"}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-muted-foreground">{c.civilRegistrationStatus ?? "NOT_STARTED"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        <div className="mt-6">
          <NompiloContextualGuidance routePath="/work/clinical/death-cases" />
        </div>
      </PageShell>
    </AppLayout>
  );
}
