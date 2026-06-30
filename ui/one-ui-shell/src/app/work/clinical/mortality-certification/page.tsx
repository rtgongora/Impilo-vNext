"use client";

/**
 * Mortality certification — WHO cause-of-death sequence for a death case. Lists cases awaiting
 * certification (real, via GET /internal/v1/death/cases) and lets an eligible certifier draft/sign
 * a cause of death (POST /internal/v1/death/cases/{id}/certify). Soft quality warnings are surfaced
 * from the service; the certifier's judgement is never overridden. Route: /work/clinical/mortality-certification
 */

import { useCallback, useEffect, useState } from "react";
import { FileText, Loader2, RefreshCw, AlertCircle, CheckCircle2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NompiloContextualGuidance } from "@/components/intelligent/NompiloContextualGuidance";
import { apiClient } from "@/lib/api-client";

interface DeathCase {
  caseId?: string;
  id?: string;
  patientCpid?: string;
  certificationStatus?: string;
  certWarnings?: string;
  coronerReferralRequired?: boolean;
}
function asArray(p: unknown): DeathCase[] {
  return Array.isArray(p) ? (p as DeathCase[]) : [];
}
function cid(c: DeathCase): string {
  return c.caseId ?? c.id ?? "";
}
function errMessage(e: unknown): string {
  if (e && typeof e === "object") {
    const obj = e as { error?: { message?: string }; status?: number };
    if (obj.error?.message) return obj.error.message;
    if (obj.status) return `Request failed (HTTP ${obj.status}).`;
  }
  return "Could not complete the request. Please try again.";
}

export default function MortalityCertificationPage() {
  const [cases, setCases] = useState<DeathCase[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<string>("");
  const [form, setForm] = useState({ immediateCause: "", antecedentCause: "", underlyingCause: "", contributoryCause: "", manner: "NATURAL", certifierLicence: "" });
  const [warnings, setWarnings] = useState<string | null>(null);
  const [done, setDone] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setCases(asArray(await apiClient.get<unknown>("/internal/v1/death/cases")));
    } catch (e) {
      setError(errMessage(e));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const submit = async (sign: boolean) => {
    if (!selected) return;
    setBusy(true);
    setWarnings(null);
    setDone(null);
    setError(null);
    try {
      const res = await apiClient.post<DeathCase>(`/internal/v1/death/cases/${selected}/certify`, { ...form, sign });
      if (res.certWarnings) setWarnings(res.certWarnings);
      setDone(sign ? "Cause of death signed." : "Draft saved.");
      void load();
    } catch (e) {
      setError(errMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const pending = (cases ?? []).filter((c) => c.certificationStatus !== "SIGNED" && c.certificationStatus !== "AMENDED");

  return (
    <AppLayout>
      <PageShell title="Mortality certification" subtitle="Record the cause of death using the WHO sequence" icon={<FileText className="h-5 w-5" />}>
        {loading ? (
          <div className="flex items-center justify-center py-16 text-muted-foreground"><Loader2 className="mr-2 h-5 w-5 animate-spin" /> Loading…</div>
        ) : error && !cases ? (
          <div className="rounded-xl border border-red-200 bg-red-50 p-6 text-sm text-red-700">
            <p className="mb-3">{error}</p>
            <button type="button" onClick={() => void load()} className="inline-flex items-center gap-1.5 rounded-lg border border-red-300 bg-white px-3 py-1.5 font-medium text-red-700 hover:bg-red-100"><RefreshCw className="h-3.5 w-3.5" /> Retry</button>
          </div>
        ) : (
          <div className="grid gap-6 lg:grid-cols-2">
            <div className="rounded-xl border border-border bg-card p-4">
              <h3 className="mb-3 text-sm font-semibold">Awaiting certification</h3>
              {pending.length === 0 ? (
                <p className="text-sm text-muted-foreground">No cases awaiting certification.</p>
              ) : (
                <ul className="space-y-1.5">
                  {pending.map((c) => (
                    <li key={cid(c)}>
                      <button
                        type="button"
                        onClick={() => setSelected(cid(c))}
                        className={`w-full rounded-lg border px-3 py-2 text-left text-sm ${selected === cid(c) ? "border-primary bg-primary/5" : "border-border hover:bg-background"}`}
                      >
                        <span className="font-medium">{c.patientCpid ?? "Unidentified"}</span>
                        <span className="ml-2 text-xs text-muted-foreground">{c.certificationStatus ?? "NOT_STARTED"}</span>
                        {c.coronerReferralRequired && <span className="ml-2 rounded-full bg-amber-100 px-2 py-0.5 text-xs text-amber-700">coroner</span>}
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </div>

            <div className="rounded-xl border border-border bg-card p-4">
              <h3 className="mb-3 text-sm font-semibold">Cause of death</h3>
              {!selected ? (
                <p className="text-sm text-muted-foreground">Select a case to certify.</p>
              ) : (
                <div className="space-y-3 text-sm">
                  {(["immediateCause", "antecedentCause", "underlyingCause", "contributoryCause"] as const).map((f) => (
                    <label key={f} className="block">
                      <span className="mb-1 block text-xs font-medium capitalize text-muted-foreground">{f.replace("Cause", " cause")}</span>
                      <input
                        type="text"
                        value={form[f]}
                        onChange={(e) => setForm({ ...form, [f]: e.target.value })}
                        className="w-full rounded-lg border border-border bg-background px-3 py-1.5"
                      />
                    </label>
                  ))}
                  <label className="block">
                    <span className="mb-1 block text-xs font-medium text-muted-foreground">Manner</span>
                    <select value={form.manner} onChange={(e) => setForm({ ...form, manner: e.target.value })} className="w-full rounded-lg border border-border bg-background px-3 py-1.5">
                      {["NATURAL", "ACCIDENT", "HOMICIDE", "SUICIDE", "UNDETERMINED", "PENDING"].map((m) => <option key={m}>{m}</option>)}
                    </select>
                  </label>
                  <label className="block">
                    <span className="mb-1 block text-xs font-medium text-muted-foreground">Certifier licence number</span>
                    <input type="text" value={form.certifierLicence} onChange={(e) => setForm({ ...form, certifierLicence: e.target.value })} className="w-full rounded-lg border border-border bg-background px-3 py-1.5" />
                  </label>

                  {warnings && (
                    <div className="flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 p-3 text-amber-800">
                      <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" /><span>{warnings}</span>
                    </div>
                  )}
                  {done && (
                    <div className="flex items-center gap-2 rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-emerald-800">
                      <CheckCircle2 className="h-4 w-4" /><span>{done}</span>
                    </div>
                  )}
                  {error && <p className="rounded-lg border border-red-200 bg-red-50 p-3 text-red-700">{error}</p>}

                  <div className="flex gap-2 pt-1">
                    <button type="button" disabled={busy || !form.immediateCause} onClick={() => void submit(false)} className="rounded-lg border border-border bg-card px-3 py-1.5 font-medium hover:bg-background disabled:opacity-50">Save draft</button>
                    <button type="button" disabled={busy || !form.immediateCause || !form.certifierLicence} onClick={() => void submit(true)} className="rounded-lg bg-primary px-3 py-1.5 font-medium text-primary-foreground hover:opacity-90 disabled:opacity-50">{busy ? "Working…" : "Sign certificate"}</button>
                  </div>
                </div>
              )}
            </div>
          </div>
        )}

        <div className="mt-6">
          <NompiloContextualGuidance routePath="/work/clinical/mortality-certification" />
        </div>
      </PageShell>
    </AppLayout>
  );
}
