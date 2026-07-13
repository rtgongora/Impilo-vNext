"use client";

import { useState } from "react";
import { GraduationCap, Loader2, Plus, MapPin, CheckCircle2 } from "lucide-react";
import {
  useProviderQualifications,
  useCreateQualification,
  useVerifyQualification,
  useProviderPracticeContexts,
  useCreatePracticeContext,
  usePracticeContextOp,
} from "@/hooks/queries/useProviderCredentials";

const CONTEXT_TYPES = ["EMPLOYMENT", "VISITING", "LOCUM", "TELEHEALTH", "SUPERVISION", "OTHER"];

const STATUS_STYLE: Record<string, string> = {
  VERIFIED: "bg-green-100 text-green-700",
  AUTHORISED: "bg-green-100 text-green-700",
  ACTIVE: "bg-green-100 text-green-700",
  PENDING: "bg-amber-100 text-amber-700",
  PENDING_VERIFICATION: "bg-amber-100 text-amber-700",
  REVOKED: "bg-red-100 text-red-700",
  REJECTED: "bg-red-100 text-red-700",
  EXPIRED: "bg-neutral-100 text-neutral-600",
};

/** Registrar qualifications + practice-context casework for a provider (R2/G11). */
export function ProviderCredentialsPanel({ providerPublicId }: { providerPublicId: string }) {
  const quals = useProviderQualifications(providerPublicId);
  const createQual = useCreateQualification(providerPublicId);
  const verifyQual = useVerifyQualification(providerPublicId);
  const contexts = useProviderPracticeContexts(providerPublicId);
  const createContext = useCreatePracticeContext(providerPublicId);
  const contextOp = usePracticeContextOp(providerPublicId);

  const [showQual, setShowQual] = useState(false);
  const [qualForm, setQualForm] = useState({ qualificationName: "", awardingBody: "", dateAwarded: "" });
  const [showContext, setShowContext] = useState(false);
  const [contextForm, setContextForm] = useState({ contextType: CONTEXT_TYPES[0], authorisationBasis: "" });

  return (
    <div className="bg-card rounded-xl border border-border shadow-sm">
      <div className="flex items-center gap-2 px-4 py-3 border-b border-border">
        <GraduationCap className="h-4 w-4 text-primary" />
        <h3 className="text-sm font-semibold text-foreground">Qualifications &amp; practice contexts</h3>
      </div>

      {/* Qualifications */}
      <div className="p-4 border-b border-border">
        <div className="mb-2 flex items-center justify-between">
          <p className="text-xs font-semibold uppercase text-muted-foreground">Qualifications</p>
          <button type="button" onClick={() => setShowQual((s) => !s)}
            className="inline-flex items-center gap-1 rounded-lg border border-border px-2 py-1 text-xs hover:bg-background">
            <Plus className="h-3 w-3" /> Add
          </button>
        </div>
        {showQual && (
          <div className="mb-3 space-y-2 rounded-lg border border-border bg-background p-2">
            <input value={qualForm.qualificationName} onChange={(e) => setQualForm({ ...qualForm, qualificationName: e.target.value })}
              placeholder="Qualification (e.g. MBChB)" className="w-full rounded border border-border px-2 py-1.5 text-sm" />
            <input value={qualForm.awardingBody} onChange={(e) => setQualForm({ ...qualForm, awardingBody: e.target.value })}
              placeholder="Awarding body" className="w-full rounded border border-border px-2 py-1.5 text-sm" />
            <label className="block text-xs text-muted-foreground">Date awarded
              <input type="date" value={qualForm.dateAwarded} onChange={(e) => setQualForm({ ...qualForm, dateAwarded: e.target.value })}
                className="mt-0.5 w-full rounded border border-border px-2 py-1.5 text-sm" />
            </label>
            <button type="button" disabled={createQual.isPending || !qualForm.qualificationName}
              onClick={() => createQual.mutate(
                { qualificationName: qualForm.qualificationName, awardingBody: qualForm.awardingBody || undefined, dateAwarded: qualForm.dateAwarded || undefined },
                { onSuccess: () => { setShowQual(false); setQualForm({ qualificationName: "", awardingBody: "", dateAwarded: "" }); } })}
              className="rounded-lg bg-primary px-3 py-1.5 text-xs font-medium text-white disabled:opacity-50">Submit</button>
          </div>
        )}
        {quals.isPending ? (
          <div className="flex items-center gap-2 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" /> Loading…</div>
        ) : (quals.data?.length ?? 0) === 0 ? (
          <p className="text-sm text-muted-foreground">No qualifications recorded.</p>
        ) : (
          <ul className="space-y-1.5">
            {quals.data!.map((q) => {
              const status = String(q.verificationStatus ?? "").toUpperCase();
              const verified = status === "VERIFIED";
              return (
                <li key={q.id} className="flex items-center justify-between gap-2 text-sm">
                  <span className="min-w-0 truncate">{q.title ?? q.qualificationName ?? "Qualification"}{q.institutionName || q.awardingBody ? ` · ${q.institutionName ?? q.awardingBody}` : ""}</span>
                  <span className="flex shrink-0 items-center gap-2">
                    <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_STYLE[status] ?? "bg-neutral-100 text-neutral-600"}`}>{status || "—"}</span>
                    {!verified && (
                      <button type="button" onClick={() => verifyQual.mutate({ qualificationId: q.id, body: { verificationStatus: "VERIFIED", verificationDate: new Date().toISOString().slice(0, 10) } })}
                        className="inline-flex items-center gap-1 rounded border border-green-300 px-2 py-1 text-xs text-green-700 hover:bg-green-50">
                        <CheckCircle2 className="h-3 w-3" /> Verify
                      </button>
                    )}
                  </span>
                </li>
              );
            })}
          </ul>
        )}
      </div>

      {/* Practice contexts */}
      <div className="p-4">
        <div className="mb-2 flex items-center justify-between">
          <p className="flex items-center gap-1 text-xs font-semibold uppercase text-muted-foreground"><MapPin className="h-3 w-3" /> Practice contexts</p>
          <button type="button" onClick={() => setShowContext((s) => !s)}
            className="inline-flex items-center gap-1 rounded-lg border border-border px-2 py-1 text-xs hover:bg-background">
            <Plus className="h-3 w-3" /> Add
          </button>
        </div>
        {showContext && (
          <div className="mb-3 space-y-2 rounded-lg border border-border bg-background p-2">
            <select value={contextForm.contextType} onChange={(e) => setContextForm({ ...contextForm, contextType: e.target.value })}
              className="w-full rounded border border-border px-2 py-1.5 text-sm bg-card">
              {CONTEXT_TYPES.map((t) => <option key={t} value={t}>{t.replace(/_/g, " ")}</option>)}
            </select>
            <input value={contextForm.authorisationBasis} onChange={(e) => setContextForm({ ...contextForm, authorisationBasis: e.target.value })}
              placeholder="Authorisation basis" className="w-full rounded border border-border px-2 py-1.5 text-sm" />
            <button type="button" disabled={createContext.isPending}
              onClick={() => createContext.mutate(
                { contextType: contextForm.contextType, authorisationBasis: contextForm.authorisationBasis || undefined },
                { onSuccess: () => { setShowContext(false); setContextForm({ contextType: CONTEXT_TYPES[0], authorisationBasis: "" }); } })}
              className="rounded-lg bg-primary px-3 py-1.5 text-xs font-medium text-white disabled:opacity-50">Create</button>
          </div>
        )}
        {contexts.isPending ? (
          <div className="flex items-center gap-2 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" /> Loading…</div>
        ) : (contexts.data?.length ?? 0) === 0 ? (
          <p className="text-sm text-muted-foreground">No practice contexts.</p>
        ) : (
          <ul className="space-y-1.5">
            {contexts.data!.map((c) => {
              const status = String(c.status ?? "").toUpperCase();
              const active = status === "AUTHORISED" || status === "ACTIVE";
              return (
                <li key={c.id} className="flex items-center justify-between gap-2 text-sm">
                  <span className="min-w-0 truncate">{(c.contextType ?? "Context").replace(/_/g, " ")}{c.authorisationBasis ? ` · ${c.authorisationBasis}` : ""}</span>
                  <span className="flex shrink-0 items-center gap-2">
                    <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_STYLE[status] ?? "bg-neutral-100 text-neutral-600"}`}>{status || "—"}</span>
                    {active ? (
                      <button type="button" onClick={() => contextOp.mutate({ contextId: c.id, op: "revoke", body: { reason: "Registrar revocation" } })}
                        className="rounded border border-red-300 px-2 py-1 text-xs text-red-700 hover:bg-red-50">Revoke</button>
                    ) : (
                      <button type="button" onClick={() => contextOp.mutate({ contextId: c.id, op: "authorize", body: {} })}
                        className="rounded border border-border px-2 py-1 text-xs hover:bg-background">Authorize</button>
                    )}
                  </span>
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </div>
  );
}
