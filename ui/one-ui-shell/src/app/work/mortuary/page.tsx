"use client";

/**
 * Mortuary — body custody for death cases. Lists real cases (GET /internal/v1/death/cases), and for
 * a selected case shows custody (GET /internal/v1/death/cases/{id}/body) with receive and release
 * actions. Release is only offered when no legal/coroner/post-mortem hold is active — the service is
 * the source of truth and rejects a blocked release. Dignified language. Route: /work/mortuary
 */

import { useCallback, useEffect, useState } from "react";
import { Building2, Loader2, RefreshCw, Lock, CheckCircle2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NompiloContextualGuidance } from "@/components/intelligent/NompiloContextualGuidance";
import { apiClient } from "@/lib/api-client";

interface DeathCase { caseId?: string; id?: string; patientCpid?: string; bodyReleaseBlocked?: boolean; coronerReferralRequired?: boolean; }
interface Custody {
  custodyId?: string;
  bodyTag?: string;
  storageLocation?: string;
  status?: string;
  legalHold?: boolean;
  postmortemHold?: boolean;
  releasedToName?: string;
}
function asArray(p: unknown): DeathCase[] { return Array.isArray(p) ? (p as DeathCase[]) : []; }
function cid(c: DeathCase): string { return c.caseId ?? c.id ?? ""; }
function errMessage(e: unknown): string {
  if (e && typeof e === "object") {
    const obj = e as { error?: { message?: string }; status?: number };
    if (obj.error?.message) return obj.error.message;
    if (obj.status) return `Request failed (HTTP ${obj.status}).`;
  }
  return "Could not complete the request. Please try again.";
}

export default function MortuaryPage() {
  const [cases, setCases] = useState<DeathCase[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<string>("");
  const [custody, setCustody] = useState<Custody | null>(null);
  const [bodyTag, setBodyTag] = useState("");
  const [storage, setStorage] = useState("");
  const [recipient, setRecipient] = useState("");
  const [relationship, setRelationship] = useState("");
  const [msg, setMsg] = useState<string | null>(null);
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
  useEffect(() => { void load(); }, [load]);

  const selectCase = async (caseId: string) => {
    setSelected(caseId);
    setCustody(null);
    setMsg(null);
    setError(null);
    try {
      setCustody(await apiClient.get<Custody>(`/internal/v1/death/cases/${caseId}/body`));
    } catch {
      setCustody(null);
    }
  };

  const receive = async () => {
    if (!selected || !bodyTag) return;
    setBusy(true); setMsg(null); setError(null);
    try {
      setCustody(await apiClient.post<Custody>(`/internal/v1/death/cases/${selected}/body/receive`, { bodyTag, storageLocation: storage }));
      setMsg("Body received into mortuary custody.");
    } catch (e) { setError(errMessage(e)); } finally { setBusy(false); }
  };

  const release = async () => {
    if (!selected || !recipient) return;
    setBusy(true); setMsg(null); setError(null);
    try {
      setCustody(await apiClient.post<Custody>(`/internal/v1/death/cases/${selected}/body/release`, { releasedToName: recipient, releasedToRelationship: relationship }));
      setMsg("Body released to the named recipient.");
    } catch (e) { setError(errMessage(e)); } finally { setBusy(false); }
  };

  const holdActive = !!(custody?.legalHold || custody?.postmortemHold);

  return (
    <AppLayout>
      <PageShell title="Mortuary" subtitle="Body custody, holds and release — handled with care" icon={<Building2 className="h-5 w-5" />}>
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
              <h3 className="mb-3 text-sm font-semibold">Death cases</h3>
              {(cases ?? []).length === 0 ? (
                <p className="text-sm text-muted-foreground">No death cases.</p>
              ) : (
                <ul className="space-y-1.5">
                  {(cases ?? []).map((c) => (
                    <li key={cid(c)}>
                      <button type="button" onClick={() => void selectCase(cid(c))} className={`w-full rounded-lg border px-3 py-2 text-left text-sm ${selected === cid(c) ? "border-primary bg-primary/5" : "border-border hover:bg-background"}`}>
                        <span className="font-medium">{c.patientCpid ?? "Unidentified"}</span>
                        {c.coronerReferralRequired && <span className="ml-2 rounded-full bg-amber-100 px-2 py-0.5 text-xs text-amber-700">hold likely</span>}
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </div>

            <div className="rounded-xl border border-border bg-card p-4">
              <h3 className="mb-3 text-sm font-semibold">Custody</h3>
              {!selected ? (
                <p className="text-sm text-muted-foreground">Select a case.</p>
              ) : (
                <div className="space-y-3 text-sm">
                  {custody?.custodyId ? (
                    <dl className="space-y-1 text-muted-foreground">
                      <div className="flex justify-between"><dt>Body tag</dt><dd>{custody.bodyTag}</dd></div>
                      <div className="flex justify-between"><dt>Storage</dt><dd>{custody.storageLocation ?? "—"}</dd></div>
                      <div className="flex justify-between"><dt>Status</dt><dd>{custody.status}</dd></div>
                      <div className="flex justify-between"><dt>Holds</dt><dd>{holdActive ? "Active" : "None"}</dd></div>
                      {custody.releasedToName && <div className="flex justify-between"><dt>Released to</dt><dd>{custody.releasedToName}</dd></div>}
                    </dl>
                  ) : (
                    <div className="space-y-2">
                      <p className="text-muted-foreground">No body received yet for this case.</p>
                      <input type="text" placeholder="Body tag" value={bodyTag} onChange={(e) => setBodyTag(e.target.value)} className="w-full rounded-lg border border-border bg-background px-3 py-1.5" />
                      <input type="text" placeholder="Storage location" value={storage} onChange={(e) => setStorage(e.target.value)} className="w-full rounded-lg border border-border bg-background px-3 py-1.5" />
                      <button type="button" disabled={busy || !bodyTag} onClick={() => void receive()} className="rounded-lg bg-primary px-3 py-1.5 font-medium text-primary-foreground hover:opacity-90 disabled:opacity-50">Receive body</button>
                    </div>
                  )}

                  {custody?.custodyId && custody.status !== "RELEASED" && (
                    holdActive ? (
                      <div className="flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 p-3 text-amber-800">
                        <Lock className="mt-0.5 h-4 w-4 shrink-0" /><span>Release is held while a legal or post-mortem hold is in place. It cannot be authorised yet.</span>
                      </div>
                    ) : (
                      <div className="space-y-2 border-t border-border pt-3">
                        <p className="text-xs font-medium text-muted-foreground">Release to a named recipient</p>
                        <input type="text" placeholder="Recipient name" value={recipient} onChange={(e) => setRecipient(e.target.value)} className="w-full rounded-lg border border-border bg-background px-3 py-1.5" />
                        <input type="text" placeholder="Relationship (e.g. next of kin)" value={relationship} onChange={(e) => setRelationship(e.target.value)} className="w-full rounded-lg border border-border bg-background px-3 py-1.5" />
                        <button type="button" disabled={busy || !recipient} onClick={() => void release()} className="rounded-lg bg-primary px-3 py-1.5 font-medium text-primary-foreground hover:opacity-90 disabled:opacity-50">Authorise release</button>
                      </div>
                    )
                  )}

                  {msg && <div className="flex items-center gap-2 rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-emerald-800"><CheckCircle2 className="h-4 w-4" /><span>{msg}</span></div>}
                  {error && <p className="rounded-lg border border-red-200 bg-red-50 p-3 text-red-700">{error}</p>}
                </div>
              )}
            </div>
          </div>
        )}

        <div className="mt-6">
          <NompiloContextualGuidance routePath="/work/mortuary" />
        </div>
      </PageShell>
    </AppLayout>
  );
}
